// Copyright (C) 2026 blue-pitaya
//
// This file is part of php-magik.
//
// php-magik is free software: you can redistribute it and/or modify it
// under the terms of the GNU General Public License as published by the
// Free Software Foundation, either version 3 of the License, or (at your
// option) any later version.
//
// php-magik is distributed in the hope that it will be useful, but
// WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
// General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with php-magik. If not, see <https://www.gnu.org/licenses/>.

#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <tree_sitter/api.h>
#include "yyjson.h"
#include "lsp.h"
#include "app_ctx.h"
#include "parser.h"
#include "vector.h"
#include "debug.h"

static char *xsprintf(const char *fmt, ...)
{
	va_list ap, ap2;
	va_start(ap, fmt);
	va_copy(ap2, ap);
	int n = vsnprintf(NULL, 0, fmt, ap);
	va_end(ap);
	char *buf = n < 0 ? NULL : malloc(n + 1);
	if (buf) {
		vsnprintf(buf, n + 1, fmt, ap2);
	}
	va_end(ap2);
	return buf;
}

static char *node_text(TSNode node, const char *content)
{
	uint32_t start = ts_node_start_byte(node);
	uint32_t end = ts_node_end_byte(node);
	return strndup(content + start, end - start);
}

/* "foo" -> "$foo", to match how property names are stored */
static char *prop_name(const char *text)
{
	char *name = malloc(strlen(text) + 2);
	if (name) {
		name[0] = '$';
		strcpy(name + 1, text);
	}
	return name;
}

static struct php_var *find_var_at(struct app_ctx *app, int file_id, TSPoint p,
				   const char *name)
{
	for (int i = 0; i < app->vars.len; i++) {
		struct php_var *v = vec_get(&app->vars, i);
		if (v->file_id == file_id && v->line == p.row &&
		    v->col == p.column && !strcmp(v->name, name)) {
			return v;
		}
	}
	return NULL;
}

static struct php_function *find_func_at(struct app_ctx *app, int file_id,
					 TSPoint p, const char *name)
{
	for (int i = 0; i < app->funcs.len; i++) {
		struct php_function *f = vec_get(&app->funcs, i);
		if (f->file_id == file_id && f->line == p.row &&
		    f->col == p.column && !strcmp(f->name, name)) {
			return f;
		}
	}
	return NULL;
}

struct resolved {
	struct php_var *var;
	struct php_function *func;
};

/* whichever indexed var/function usage or declaration sits exactly at
 * line:character, matched by the AST node's own text and position */
static struct resolved resolve_at(struct app_ctx *app, struct php_file *file,
				  int line, int character)
{
	struct resolved r = { 0 };
	TSPoint pt = { (uint32_t)line, (uint32_t)character };
	TSNode root = ts_tree_root_node(file->tree);
	/* named variant: variable_name is ["$" (anonymous), name (named)], so
	 * the plain descendant lookup would land on the bare "$" leaf when
	 * hovering exactly on it instead of variable_name itself */
	TSNode node = ts_node_named_descendant_for_point_range(root, pt, pt);
	if (ts_node_is_null(node)) {
		return r;
	}

	const char *t = ts_node_type(node);
	TSPoint p = ts_node_start_point(node);
	char *text = node_text(node, file->content);

	if (!strcmp(t, "variable_name")) {
		r.var = find_var_at(app, file->file_id, p, text);
	} else if (!strcmp(t, "name") || !strcmp(t, "qualified_name")) {
		r.func = find_func_at(app, file->file_id, p, text);
		if (!r.func) {
			char *pn = prop_name(text);
			r.var = find_var_at(app, file->file_id, p, pn);
			free(pn);
		}
	}

	free(text);
	return r;
}

/* definition target for a function/method usage: the matching FUNC_DEF,
 * same class for methods, no class for plain function calls */
static struct php_function *find_func_def(struct app_ctx *app,
					  struct php_function *call)
{
	for (int i = 0; i < app->funcs.len; i++) {
		struct php_function *f = vec_get(&app->funcs, i);
		if (f->kind != FUNC_DEF || strcmp(f->name, call->name)) {
			continue;
		}
		if (call->kind == FUNC_METHOD) {
			if (!f->class_name || !call->class_name ||
			    strcmp(f->class_name, call->class_name)) {
				continue;
			}
		} else if (f->class_name) {
			continue;
		}
		return f;
	}
	return NULL;
}

/* definition target for a variable usage: the class property for
 * $this/$obj access, else the earliest occurrence in the same scope */
static struct php_var *find_var_def(struct app_ctx *app, struct php_var *use)
{
	if (use->kind == VAR_PROPERTY) {
		return use;
	}
	if (use->kind == VAR_OBJ || use->kind == VAR_THIS) {
		for (int i = 0; i < app->vars.len; i++) {
			struct php_var *v = vec_get(&app->vars, i);
			if (v->kind == VAR_PROPERTY && v->class_name &&
			    use->class_name &&
			    !strcmp(v->class_name, use->class_name) &&
			    !strcmp(v->name, use->name)) {
				return v;
			}
		}
		return NULL;
	}

	struct php_var *best = NULL;
	for (int i = 0; i < app->vars.len; i++) {
		struct php_var *v = vec_get(&app->vars, i);
		if (v->file_id != use->file_id || strcmp(v->name, use->name)) {
			continue;
		}
		int same_fn = (!v->function_name && !use->function_name) ||
			      (v->function_name && use->function_name &&
			       !strcmp(v->function_name, use->function_name));
		if (!same_fn) {
			continue;
		}
		if (!best || v->line < best->line ||
		    (v->line == best->line && v->col < best->col)) {
			best = v;
		}
	}
	return best;
}

/* most recent known type of the same variable, at or before `at`, in the
 * same file+function scope; mirrors parser.c's scope_var_type() but at
 * query time over the whole index instead of during a single parse pass */
static const char *latest_var_type(struct app_ctx *app, struct php_var *at)
{
	struct php_var *best = NULL;
	for (int i = 0; i < app->vars.len; i++) {
		struct php_var *v = vec_get(&app->vars, i);
		if (v->file_id != at->file_id || !v->type ||
		    strcmp(v->name, at->name)) {
			continue;
		}
		int same_fn = (!v->function_name && !at->function_name) ||
			      (v->function_name && at->function_name &&
			       !strcmp(v->function_name, at->function_name));
		if (!same_fn) {
			continue;
		}
		if (v->line > at->line ||
		    (v->line == at->line && v->col > at->col)) {
			continue;
		}
		if (!best || v->line > best->line ||
		    (v->line == best->line && v->col > best->col)) {
			best = v;
		}
	}
	return best ? best->type : NULL;
}

static yyjson_mut_val *location_json(yyjson_mut_doc *doc, const char *uri,
				     uint32_t line, uint32_t col, size_t len)
{
	yyjson_mut_val *loc = yyjson_mut_obj(doc);
	yyjson_mut_obj_add_str(doc, loc, "uri", uri);
	yyjson_mut_val *range = yyjson_mut_obj_add_obj(doc, loc, "range");
	yyjson_mut_val *start = yyjson_mut_obj_add_obj(doc, range, "start");
	yyjson_mut_obj_add_uint(doc, start, "line", line);
	yyjson_mut_obj_add_uint(doc, start, "character", col);
	yyjson_mut_val *end = yyjson_mut_obj_add_obj(doc, range, "end");
	yyjson_mut_obj_add_uint(doc, end, "line", line);
	yyjson_mut_obj_add_uint(doc, end, "character", col + len);
	return loc;
}

static void lsp_log(struct lsp_context *ctx, const char *fmt, ...)
{
	va_list ap;
	va_start(ap, fmt);
	vfprintf(ctx->log_file, fmt, ap);
	va_end(ap);
	fflush(ctx->log_file);
}

static int lsp_read(char **out, size_t *out_len)
{
	int err;
	int content_length = -1;
	char line[256];

	while (fgets(line, sizeof(line), stdin)) {
		err = sscanf(line, "Content-Length: %d", &content_length);
		if (err == 1) {
			continue;
		}
		if (!strcmp(line, "\r\n") || !strcmp(line, "\n")) {
			break;
		}
	}

	if (content_length <= 0) {
		return -1;
	}

	char *buf = malloc(content_length + 1);
	if (!buf) {
		return -1;
	}

	size_t n = fread(buf, 1, content_length, stdin);
	if ((int)n != content_length) {
		free(buf);
		return -1;
	}

	buf[n] = '\0';
	*out = buf;
	*out_len = n;
	return 0;
}

static void lsp_write(yyjson_mut_doc *doc)
{
	size_t len;
	char *json = yyjson_mut_write(doc, 0, &len);
	if (!json) {
		return;
	}
	fprintf(stdout, "Content-Length: %zu\r\n\r\n%s", len, json);
	fflush(stdout);
	free(json);
}

static void send_response(yyjson_val *id, yyjson_mut_doc *doc,
			  yyjson_mut_val *result)
{
	yyjson_mut_val *root = yyjson_mut_obj(doc);
	yyjson_mut_doc_set_root(doc, root);
	yyjson_mut_obj_add_str(doc, root, "jsonrpc", "2.0");
	yyjson_mut_obj_add_val(doc, root, "id", yyjson_val_mut_copy(doc, id));
	yyjson_mut_obj_add_val(doc, root, "result", result);
	DEBUG_MJSON("Respond", root);
	lsp_write(doc);
}

static yyjson_mut_val *handle_initialize(yyjson_mut_doc *doc)
{
	yyjson_mut_val *result = yyjson_mut_obj(doc);
	yyjson_mut_val *caps =
		yyjson_mut_obj_add_obj(doc, result, "capabilities");
	yyjson_mut_obj_add_int(doc, caps, "textDocumentSync", 1);
	yyjson_mut_obj_add_bool(doc, caps, "hoverProvider", true);
	yyjson_mut_obj_add_bool(doc, caps, "definitionProvider", true);

	yyjson_mut_val *comp =
		yyjson_mut_obj_add_obj(doc, caps, "completionProvider");
	yyjson_mut_val *triggers =
		yyjson_mut_obj_add_arr(doc, comp, "triggerCharacters");
	yyjson_mut_arr_add_str(doc, triggers, ">");
	yyjson_mut_arr_add_str(doc, triggers, "$");
	yyjson_mut_arr_add_str(doc, triggers, ":");

	return result;
}

static void handle_did_open(struct lsp_context *ctx, yyjson_val *params)
{
	yyjson_val *td = yyjson_obj_get(params, "textDocument");
	const char *uri = yyjson_get_str(yyjson_obj_get(td, "uri"));
	const char *text = yyjson_get_str(yyjson_obj_get(td, "text"));
	lsp_log(ctx, "didOpen: %s (%zu bytes)\n", uri, strlen(text));
}

static void handle_did_change(struct lsp_context *ctx, yyjson_val *params)
{
	yyjson_val *td = yyjson_obj_get(params, "textDocument");
	const char *uri = yyjson_get_str(yyjson_obj_get(td, "uri"));
	yyjson_val *changes = yyjson_obj_get(params, "contentChanges");
	yyjson_val *change = yyjson_arr_get_first(changes);
	const char *text = yyjson_get_str(yyjson_obj_get(change, "text"));
	lsp_log(ctx, "didChange: %s (%zu bytes)\n", uri, strlen(text));
}

static void handle_did_close(struct lsp_context *ctx, yyjson_val *params)
{
	yyjson_val *td = yyjson_obj_get(params, "textDocument");
	const char *uri = yyjson_get_str(yyjson_obj_get(td, "uri"));
	lsp_log(ctx, "didClose: %s\n", uri);
}

static yyjson_mut_val *handle_hover(struct lsp_context *ctx, yyjson_val *params,
				    yyjson_mut_doc *doc)
{
	yyjson_val *td = yyjson_obj_get(params, "textDocument");
	yyjson_val *pos = yyjson_obj_get(params, "position");
	const char *uri = yyjson_get_str(yyjson_obj_get(td, "uri"));
	int line = yyjson_get_int(yyjson_obj_get(pos, "line"));
	int col = yyjson_get_int(yyjson_obj_get(pos, "character"));
	lsp_log(ctx, "hover: %s %d:%d\n", uri, line, col);

	struct php_file *file = app_ctx_find_file(ctx->app, uri);
	if (!file) {
		return yyjson_mut_null(doc);
	}

	struct resolved r = resolve_at(ctx->app, file, line, col);
	char *text = NULL;
	if (r.var) {
		const char *type =
			r.var->type ? r.var->type :
				      latest_var_type(ctx->app, r.var);
		text = type ? xsprintf("```php\n%s: %s\n```", r.var->name, type) :
			     xsprintf("```php\n%s\n```", r.var->name);
	} else if (r.func) {
		const char *ret = r.func->return_type;
		if (!ret && r.func->kind != FUNC_DEF) {
			struct php_function *def =
				find_func_def(ctx->app, r.func);
			if (def) {
				ret = def->return_type;
			}
		}
		text = ret ? xsprintf("```php\nfunction %s(): %s\n```",
				      r.func->name, ret) :
			    xsprintf("```php\nfunction %s()\n```", r.func->name);
	}
	if (!text) {
		return yyjson_mut_null(doc);
	}

	yyjson_mut_val *result = yyjson_mut_obj(doc);
	yyjson_mut_val *contents =
		yyjson_mut_obj_add_obj(doc, result, "contents");
	yyjson_mut_obj_add_str(doc, contents, "kind", "markdown");
	yyjson_mut_obj_add_strcpy(doc, contents, "value", text);
	free(text);
	return result;
}

static yyjson_mut_val *handle_definition(struct lsp_context *ctx,
					 yyjson_val *params,
					 yyjson_mut_doc *doc)
{
	yyjson_val *td = yyjson_obj_get(params, "textDocument");
	yyjson_val *pos = yyjson_obj_get(params, "position");
	const char *uri = yyjson_get_str(yyjson_obj_get(td, "uri"));
	int line = yyjson_get_int(yyjson_obj_get(pos, "line"));
	int col = yyjson_get_int(yyjson_obj_get(pos, "character"));
	lsp_log(ctx, "definition: %s %d:%d\n", uri, line, col);

	struct php_file *file = app_ctx_find_file(ctx->app, uri);
	if (!file) {
		return yyjson_mut_null(doc);
	}

	struct resolved r = resolve_at(ctx->app, file, line, col);

	if (r.func) {
		struct php_function *def = find_func_def(ctx->app, r.func);
		if (def) {
			struct php_file *df =
				vec_get(&ctx->app->files, def->file_id);
			return location_json(doc, df->uri, def->line, def->col,
					     strlen(def->name));
		}
	} else if (r.var) {
		struct php_var *def = find_var_def(ctx->app, r.var);
		if (def) {
			struct php_file *df =
				vec_get(&ctx->app->files, def->file_id);
			return location_json(doc, df->uri, def->line, def->col,
					     strlen(def->name));
		}
	}

	return yyjson_mut_null(doc);
}

int lsp_run(struct lsp_context *ctx)
{
	DEBUG("Started");
	ctx->log_file = fopen("/tmp/php-magik.log", "w");
	if (!ctx->log_file) {
		return -1;
	}
	int running = 1;
	int shutdown = 0;
	size_t msg_len;
	char *msg;

	int i = 0;
	while (running) {
		DEBUG("Loop: %d", i);
		if (lsp_read(&msg, &msg_len)) {
			break;
		}

		yyjson_doc *req = yyjson_read(msg, msg_len, 0);
		free(msg);
		if (!req) {
			continue;
		}
		DEBUG_JSON("Requet parsed", yyjson_doc_get_root(req));

		yyjson_val *root = yyjson_doc_get_root(req);
		yyjson_val *id = yyjson_obj_get(root, "id");
		const char *m = yyjson_get_str(yyjson_obj_get(root, "method"));
		yyjson_val *params = yyjson_obj_get(root, "params");

		yyjson_mut_doc *resp = yyjson_mut_doc_new(NULL);

		if (!m) {
			lsp_log(ctx, "message without method\n");
		} else if (!strcmp(m, "initialize")) {
			DEBUG("Handle: %s", m);
			yyjson_mut_val *zz = handle_initialize(resp);
			send_response(id, resp, zz);
		} else if (!strcmp(m, "initialized")) {
			lsp_log(ctx, "initialized\n");
		} else if (!strcmp(m, "shutdown")) {
			shutdown = 1;
			send_response(id, resp, yyjson_mut_null(resp));
		} else if (!strcmp(m, "exit")) {
			running = 0;
		} else if (!strcmp(m, "textDocument/didOpen")) {
			handle_did_open(ctx, params);
		} else if (!strcmp(m, "textDocument/didChange")) {
			handle_did_change(ctx, params);
		} else if (!strcmp(m, "textDocument/didClose")) {
			handle_did_close(ctx, params);
		} else if (!strcmp(m, "textDocument/hover")) {
			send_response(id, resp,
				      handle_hover(ctx, params, resp));
		} else if (!strcmp(m, "textDocument/definition")) {
			send_response(id, resp,
				      handle_definition(ctx, params, resp));
		} else if (id) {
			yyjson_mut_val *err = yyjson_mut_obj(resp);
			yyjson_mut_obj_add_int(resp, err, "code", -32601);
			yyjson_mut_obj_add_str(resp, err, "message",
					       "method not found");
			yyjson_mut_val *r = yyjson_mut_obj(resp);
			yyjson_mut_doc_set_root(resp, r);
			yyjson_mut_obj_add_str(resp, r, "jsonrpc", "2.0");
			yyjson_mut_obj_add_val(resp, r, "id",
					       yyjson_val_mut_copy(resp, id));
			yyjson_mut_obj_add_val(resp, r, "error", err);
			lsp_write(resp);
		}

		yyjson_mut_doc_free(resp);
		yyjson_doc_free(req);
		++i;
	}

	fclose(ctx->log_file);
	return shutdown ? 0 : 1;
}
