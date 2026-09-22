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

#include <ctype.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
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
	if (call->kind == FUNC_DEF) {
		return call;
	}
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

/* most recent known type of variable `name`, at or before `before`, in the
 * same file+function scope; mirrors parser.c's scope_var_type() but at
 * query time over the whole index instead of during a single parse pass */
static const char *type_of_var_before(struct app_ctx *app, int file_id,
				      const char *function_name,
				      const char *name, TSPoint before)
{
	struct php_var *best = NULL;
	for (int i = 0; i < app->vars.len; i++) {
		struct php_var *v = vec_get(&app->vars, i);
		if (v->file_id != file_id || !v->type ||
		    strcmp(v->name, name)) {
			continue;
		}
		int same_fn = (!v->function_name && !function_name) ||
			      (v->function_name && function_name &&
			       !strcmp(v->function_name, function_name));
		if (!same_fn) {
			continue;
		}
		if (v->line > before.row ||
		    (v->line == before.row && v->col > before.column)) {
			continue;
		}
		if (!best || v->line > best->line ||
		    (v->line == best->line && v->col > best->col)) {
			best = v;
		}
	}
	return best ? best->type : NULL;
}

static const char *latest_var_type(struct app_ctx *app, struct php_var *at)
{
	TSPoint p = { at->line, at->col };
	return type_of_var_before(app, at->file_id, at->function_name, at->name,
				  p);
}

static const char *strip_ns(const char *t) /* ?Foo, \Foo -> Foo */
{
	while (t && (*t == '?' || *t == '\\')) {
		t++;
	}
	return t;
}

/* nearest enclosing function/method name, walking up from `node` */
static char *enclosing_function_name(TSNode node, const char *content)
{
	while (!ts_node_is_null(node)) {
		const char *t = ts_node_type(node);
		if (!strcmp(t, "function_definition") ||
		    !strcmp(t, "method_declaration")) {
			TSNode name =
				ts_node_child_by_field_name(node, "name", 4);
			return ts_node_is_null(name) ? NULL :
						       node_text(name, content);
		}
		node = ts_node_parent(node);
	}
	return NULL;
}

/* nearest enclosing class/interface/trait/enum name, walking up from `node` */
static char *enclosing_class_name(TSNode node, const char *content)
{
	while (!ts_node_is_null(node)) {
		const char *t = ts_node_type(node);
		if (!strcmp(t, "class_declaration") ||
		    !strcmp(t, "interface_declaration") ||
		    !strcmp(t, "trait_declaration") ||
		    !strcmp(t, "enum_declaration")) {
			TSNode name =
				ts_node_child_by_field_name(node, "name", 4);
			return ts_node_is_null(name) ? NULL :
						       node_text(name, content);
		}
		node = ts_node_parent(node);
	}
	return NULL;
}

static uint32_t byte_offset_for(const char *content, int line, int character)
{
	uint32_t off = 0;
	int cur_line = 0;
	while (cur_line < line && content[off]) {
		if (content[off] == '\n') {
			cur_line++;
		}
		off++;
	}
	return off + (uint32_t)character;
}

static int ends_with(const char *content, uint32_t off, const char *suffix)
{
	size_t len = strlen(suffix);
	return off >= len && !strncmp(content + off - len, suffix, len);
}

/* "$name" immediately before the "->" ending at byte offset `off`, or NULL
 * if that's not what's there. Text-based on purpose: at completion time the
 * member name after "->" usually isn't typed yet, so there's often no
 * well-formed member_access_expression node to read the object out of. */
static char *var_before_arrow(const char *content, uint32_t off)
{
	if (!ends_with(content, off, "->")) {
		return NULL;
	}
	uint32_t end = off - 2;
	uint32_t start = end;
	while (start > 0 && (isalnum((unsigned char)content[start - 1]) ||
			     content[start - 1] == '_')) {
		start--;
	}
	if (start == 0 || content[start - 1] != '$' || start == end) {
		return NULL;
	}
	start--;
	return strndup(content + start, end - start);
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

/* a bare {start,end} range spanning `node`, for documentSymbol - unlike
 * location_json this reads a live tree node, so it gets the whole span
 * (e.g. a function's entire body) rather than just a name's length */
static yyjson_mut_val *range_json(yyjson_mut_doc *doc, TSNode node)
{
	TSPoint s = ts_node_start_point(node);
	TSPoint e = ts_node_end_point(node);
	yyjson_mut_val *range = yyjson_mut_obj(doc);
	yyjson_mut_val *start = yyjson_mut_obj_add_obj(doc, range, "start");
	yyjson_mut_obj_add_uint(doc, start, "line", s.row);
	yyjson_mut_obj_add_uint(doc, start, "character", s.column);
	yyjson_mut_val *end = yyjson_mut_obj_add_obj(doc, range, "end");
	yyjson_mut_obj_add_uint(doc, end, "line", e.row);
	yyjson_mut_obj_add_uint(doc, end, "character", e.column);
	return range;
}

/* every func-index entry that find_func_def would resolve to `def`: the
 * inverse of find_func_def. `def` itself is included only if include_decl,
 * matched by identity (same slot in app->funcs) since names can repeat
 * across unrelated classes/scopes */
static void collect_func_refs(struct app_ctx *app, struct php_function *def,
			      int include_decl, yyjson_mut_doc *doc,
			      yyjson_mut_val *locs)
{
	for (int i = 0; i < app->funcs.len; i++) {
		struct php_function *f = vec_get(&app->funcs, i);
		int match;

		if (f == def) {
			match = include_decl;
		} else if (f->kind == FUNC_DEF || strcmp(f->name, def->name)) {
			match = 0;
		} else if (def->class_name) {
			match = f->class_name &&
				!strcmp(f->class_name, def->class_name);
		} else {
			match = !f->class_name;
		}

		if (!match) {
			continue;
		}
		struct php_file *file = vec_get(&app->files, f->file_id);
		yyjson_mut_arr_add_val(locs,
				       location_json(doc, file->uri, f->line,
						     f->col, strlen(f->name)));
	}
}

/* every var-index entry that find_var_def would resolve to `def`: the
 * inverse of find_var_def */
static void collect_var_refs(struct app_ctx *app, struct php_var *def,
			     int include_decl, yyjson_mut_doc *doc,
			     yyjson_mut_val *locs)
{
	for (int i = 0; i < app->vars.len; i++) {
		struct php_var *v = vec_get(&app->vars, i);
		int match;

		if (v == def) {
			match = include_decl;
		} else if (strcmp(v->name, def->name)) {
			match = 0;
		} else if (def->kind == VAR_PROPERTY) {
			match = (v->kind == VAR_THIS || v->kind == VAR_OBJ) &&
				v->class_name && def->class_name &&
				!strcmp(v->class_name, def->class_name);
		} else {
			match = v->file_id == def->file_id &&
				((!v->function_name && !def->function_name) ||
				 (v->function_name && def->function_name &&
				  !strcmp(v->function_name,
					  def->function_name)));
		}

		if (!match) {
			continue;
		}
		/* $this->prop / $obj->prop store a synthetic "$"-prefixed
		 * name (to match against property declarations), but the
		 * source text at this position is just the bare property
		 * name - there's no literal "$" there */
		size_t len = strlen(v->name);
		if (v->kind == VAR_THIS || v->kind == VAR_OBJ) {
			len -= 1;
		}
		struct php_file *file = vec_get(&app->files, v->file_id);
		yyjson_mut_arr_add_val(
			locs, location_json(doc, file->uri, v->line, v->col,
					    len));
	}
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
	yyjson_mut_obj_add_bool(doc, caps, "referencesProvider", true);
	yyjson_mut_obj_add_bool(doc, caps, "documentSymbolProvider", true);
	yyjson_mut_obj_add_bool(doc, caps, "workspaceSymbolProvider", true);

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
	size_t len = strlen(text);
	lsp_log(ctx, "didOpen: %s (%zu bytes)\n", uri, len);

	if (app_ctx_reparse_file(ctx->app, uri, text, len)) {
		lsp_log(ctx, "didOpen: %s not in index, skipping reparse\n",
			uri);
	}
}

static void handle_did_change(struct lsp_context *ctx, yyjson_val *params)
{
	yyjson_val *td = yyjson_obj_get(params, "textDocument");
	const char *uri = yyjson_get_str(yyjson_obj_get(td, "uri"));
	yyjson_val *changes = yyjson_obj_get(params, "contentChanges");
	yyjson_val *change = yyjson_arr_get_first(changes);
	const char *text = yyjson_get_str(yyjson_obj_get(change, "text"));
	size_t len = strlen(text);
	lsp_log(ctx, "didChange: %s (%zu bytes)\n", uri, len);

	/* textDocumentSync is Full (1): `text` is always the whole document */
	if (app_ctx_reparse_file(ctx->app, uri, text, len)) {
		lsp_log(ctx, "didChange: %s not in index, skipping reparse\n",
			uri);
	}
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
		const char *type = r.var->type ?
					   r.var->type :
					   latest_var_type(ctx->app, r.var);
		text = type ? xsprintf("```php\n%s: %s\n```", r.var->name,
				       type) :
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
			     xsprintf("```php\nfunction %s()\n```",
				      r.func->name);
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

static yyjson_mut_val *handle_references(struct lsp_context *ctx,
					 yyjson_val *params,
					 yyjson_mut_doc *doc)
{
	yyjson_val *td = yyjson_obj_get(params, "textDocument");
	yyjson_val *pos = yyjson_obj_get(params, "position");
	yyjson_val *rctx = yyjson_obj_get(params, "context");
	const char *uri = yyjson_get_str(yyjson_obj_get(td, "uri"));
	int line = yyjson_get_int(yyjson_obj_get(pos, "line"));
	int col = yyjson_get_int(yyjson_obj_get(pos, "character"));
	int include_decl =
		yyjson_get_bool(yyjson_obj_get(rctx, "includeDeclaration"));
	lsp_log(ctx, "references: %s %d:%d\n", uri, line, col);

	yyjson_mut_val *locs = yyjson_mut_arr(doc);

	struct php_file *file = app_ctx_find_file(ctx->app, uri);
	if (!file) {
		return locs;
	}

	struct resolved r = resolve_at(ctx->app, file, line, col);

	if (r.func) {
		struct php_function *def = find_func_def(ctx->app, r.func);
		if (def) {
			collect_func_refs(ctx->app, def, include_decl, doc,
					  locs);
		}
	} else if (r.var) {
		struct php_var *def = find_var_def(ctx->app, r.var);
		if (def) {
			collect_var_refs(ctx->app, def, include_decl, doc,
					 locs);
		}
	}

	return locs;
}

enum {
	SK_NAMESPACE = 3,
	SK_CLASS = 5,
	SK_METHOD = 6,
	SK_PROPERTY = 7,
	SK_CONSTRUCTOR = 9,
	SK_ENUM = 10,
	SK_INTERFACE = 11,
	SK_FUNCTION = 12,
	SK_ENUM_MEMBER = 22,
};

/* builds a DocumentSymbol for `name_node` (its own name -> selectionRange)
 * spanning `whole` (its full declaration -> range), appends it to
 * `parent_children`, and returns it so the caller can add a "children"
 * array of its own */
static yyjson_mut_val *symbol_new(yyjson_mut_doc *doc,
				  yyjson_mut_val *parent_children,
				  const char *name, int kind, TSNode whole,
				  TSNode name_node)
{
	yyjson_mut_val *sym = yyjson_mut_obj(doc);
	yyjson_mut_obj_add_strcpy(doc, sym, "name", name);
	yyjson_mut_obj_add_int(doc, sym, "kind", kind);
	yyjson_mut_obj_add_val(doc, sym, "range", range_json(doc, whole));
	yyjson_mut_obj_add_val(doc, sym, "selectionRange",
			       range_json(doc, name_node));
	yyjson_mut_arr_add_val(parent_children, sym);
	return sym;
}

/* a class/interface/trait/enum body: its properties and methods */
static void collect_class_members(TSNode list, const char *content,
				  yyjson_mut_doc *doc, yyjson_mut_val *children)
{
	uint32_t count = ts_node_named_child_count(list);
	for (uint32_t i = 0; i < count; i++) {
		TSNode node = ts_node_named_child(list, i);
		const char *type = ts_node_type(node);

		if (!strcmp(type, "method_declaration")) {
			TSNode name = ts_node_child_by_field_name(node, "name", 4);
			if (ts_node_is_null(name)) {
				continue;
			}
			char *text = node_text(name, content);
			int kind = !strcmp(text, "__construct") ?
					   SK_CONSTRUCTOR :
					   SK_METHOD;
			symbol_new(doc, children, text, kind, node, name);
			free(text);
		} else if (!strcmp(type, "property_declaration")) {
			/* one `public int $a, $b;` declares multiple */
			uint32_t pc = ts_node_named_child_count(node);
			for (uint32_t j = 0; j < pc; j++) {
				TSNode el = ts_node_named_child(node, j);
				if (strcmp(ts_node_type(el), "property_element")) {
					continue;
				}
				TSNode varname = ts_node_child_by_field_name(
					el, "name", 4);
				if (ts_node_is_null(varname)) {
					continue;
				}
				char *text = node_text(varname, content);
				symbol_new(doc, children, text, SK_PROPERTY,
					  node, varname);
				free(text);
			}
		} else if (!strcmp(type, "enum_case")) {
			TSNode name = ts_node_child_by_field_name(node, "name", 4);
			if (ts_node_is_null(name)) {
				continue;
			}
			char *text = node_text(name, content);
			symbol_new(doc, children, text, SK_ENUM_MEMBER, node,
				  name);
			free(text);
		}
	}
}

static void collect_class_symbol(TSNode node, const char *content,
				 yyjson_mut_doc *doc, yyjson_mut_val *out,
				 int kind)
{
	TSNode name = ts_node_child_by_field_name(node, "name", 4);
	if (ts_node_is_null(name)) {
		return;
	}
	char *text = node_text(name, content);
	yyjson_mut_val *sym = symbol_new(doc, out, text, kind, node, name);
	free(text);

	yyjson_mut_val *children = yyjson_mut_obj_add_arr(doc, sym, "children");
	uint32_t count = ts_node_named_child_count(node);
	for (uint32_t i = 0; i < count; i++) {
		TSNode c = ts_node_named_child(node, i);
		const char *t = ts_node_type(c);
		if (!strcmp(t, "declaration_list") ||
		    !strcmp(t, "enum_declaration_list")) {
			collect_class_members(c, content, doc, children);
		}
	}
}

/* top-level (or namespace-body) symbols: functions, classes and friends,
 * and braced namespaces (recursed into); the unbraced `namespace X;` form
 * has no body to nest, so its members just stay ordinary top-level symbols */
static void collect_document_symbols(TSNode root, const char *content,
				     yyjson_mut_doc *doc, yyjson_mut_val *out)
{
	uint32_t count = ts_node_named_child_count(root);
	for (uint32_t i = 0; i < count; i++) {
		TSNode node = ts_node_named_child(root, i);
		const char *t = ts_node_type(node);

		if (!strcmp(t, "function_definition")) {
			TSNode name = ts_node_child_by_field_name(node, "name", 4);
			if (ts_node_is_null(name)) {
				continue;
			}
			char *text = node_text(name, content);
			symbol_new(doc, out, text, SK_FUNCTION, node, name);
			free(text);
		} else if (!strcmp(t, "class_declaration") ||
			   !strcmp(t, "trait_declaration")) {
			collect_class_symbol(node, content, doc, out, SK_CLASS);
		} else if (!strcmp(t, "interface_declaration")) {
			collect_class_symbol(node, content, doc, out,
					     SK_INTERFACE);
		} else if (!strcmp(t, "enum_declaration")) {
			collect_class_symbol(node, content, doc, out, SK_ENUM);
		} else if (!strcmp(t, "namespace_definition")) {
			TSNode body = ts_node_child_by_field_name(node, "body", 4);
			TSNode nsname =
				ts_node_child_by_field_name(node, "name", 4);
			if (!ts_node_is_null(body) && !ts_node_is_null(nsname)) {
				char *text = node_text(nsname, content);
				yyjson_mut_val *sym = symbol_new(
					doc, out, text, SK_NAMESPACE, node,
					nsname);
				free(text);
				yyjson_mut_val *children = yyjson_mut_obj_add_arr(
					doc, sym, "children");
				collect_document_symbols(body, content, doc,
							 children);
			}
		}
	}
}

static yyjson_mut_val *handle_document_symbol(struct lsp_context *ctx,
					      yyjson_val *params,
					      yyjson_mut_doc *doc)
{
	yyjson_val *td = yyjson_obj_get(params, "textDocument");
	const char *uri = yyjson_get_str(yyjson_obj_get(td, "uri"));
	lsp_log(ctx, "documentSymbol: %s\n", uri);

	yyjson_mut_val *symbols = yyjson_mut_arr(doc);

	struct php_file *file = app_ctx_find_file(ctx->app, uri);
	if (!file) {
		return symbols;
	}

	TSNode root = ts_tree_root_node(file->tree);
	collect_document_symbols(root, file->content, doc, symbols);
	return symbols;
}

static int matches_query(const char *name, const char *query)
{
	if (!*query) {
		return 1;
	}
	size_t qlen = strlen(query);
	for (const char *p = name; *p; p++) {
		if (!strncasecmp(p, query, qlen)) {
			return 1;
		}
	}
	return 0;
}

/* a flat SymbolInformation: unlike DocumentSymbol, it needs a uri (results
 * span every indexed file) and has one location, not range+selectionRange */
static yyjson_mut_val *symbol_info_json(yyjson_mut_doc *doc, const char *name,
					int kind, const char *uri,
					TSNode name_node,
					const char *container)
{
	yyjson_mut_val *sym = yyjson_mut_obj(doc);
	yyjson_mut_obj_add_strcpy(doc, sym, "name", name);
	yyjson_mut_obj_add_int(doc, sym, "kind", kind);
	TSPoint p = ts_node_start_point(name_node);
	uint32_t len = ts_node_end_byte(name_node) - ts_node_start_byte(name_node);
	yyjson_mut_obj_add_val(doc, sym, "location",
			       location_json(doc, uri, p.row, p.column, len));
	if (container) {
		yyjson_mut_obj_add_strcpy(doc, sym, "containerName", container);
	}
	return sym;
}

/* flat counterpart to collect_class_members: same shape, but filtered by
 * `query` and emitting SymbolInformation (with `container` set to the
 * class name) instead of nesting DocumentSymbol children */
static void collect_class_members_flat(TSNode list, const char *content,
				       const char *uri, const char *query,
				       const char *container,
				       yyjson_mut_doc *doc, yyjson_mut_val *out)
{
	uint32_t count = ts_node_named_child_count(list);
	for (uint32_t i = 0; i < count; i++) {
		TSNode node = ts_node_named_child(list, i);
		const char *type = ts_node_type(node);

		if (!strcmp(type, "method_declaration")) {
			TSNode name = ts_node_child_by_field_name(node, "name", 4);
			if (ts_node_is_null(name)) {
				continue;
			}
			char *text = node_text(name, content);
			if (matches_query(text, query)) {
				int kind = !strcmp(text, "__construct") ?
						   SK_CONSTRUCTOR :
						   SK_METHOD;
				yyjson_mut_arr_add_val(
					out, symbol_info_json(doc, text, kind,
							      uri, name,
							      container));
			}
			free(text);
		} else if (!strcmp(type, "property_declaration")) {
			uint32_t pc = ts_node_named_child_count(node);
			for (uint32_t j = 0; j < pc; j++) {
				TSNode el = ts_node_named_child(node, j);
				if (strcmp(ts_node_type(el), "property_element")) {
					continue;
				}
				TSNode varname = ts_node_child_by_field_name(
					el, "name", 4);
				if (ts_node_is_null(varname)) {
					continue;
				}
				char *text = node_text(varname, content);
				if (matches_query(text, query)) {
					yyjson_mut_arr_add_val(
						out,
						symbol_info_json(
							doc, text, SK_PROPERTY,
							uri, varname,
							container));
				}
				free(text);
			}
		} else if (!strcmp(type, "enum_case")) {
			TSNode name = ts_node_child_by_field_name(node, "name", 4);
			if (ts_node_is_null(name)) {
				continue;
			}
			char *text = node_text(name, content);
			if (matches_query(text, query)) {
				yyjson_mut_arr_add_val(
					out, symbol_info_json(
						     doc, text, SK_ENUM_MEMBER,
						     uri, name, container));
			}
			free(text);
		}
	}
}

/* flat counterpart to collect_document_symbols: walks every indexed file
 * (called once per file from handle_workspace_symbol) and emits matching
 * symbols directly into one flat array spanning the whole workspace */
static void collect_workspace_symbols(TSNode root, const char *content,
				      const char *uri, const char *query,
				      yyjson_mut_doc *doc, yyjson_mut_val *out)
{
	uint32_t count = ts_node_named_child_count(root);
	for (uint32_t i = 0; i < count; i++) {
		TSNode node = ts_node_named_child(root, i);
		const char *t = ts_node_type(node);

		if (!strcmp(t, "function_definition")) {
			TSNode name = ts_node_child_by_field_name(node, "name", 4);
			if (ts_node_is_null(name)) {
				continue;
			}
			char *text = node_text(name, content);
			if (matches_query(text, query)) {
				yyjson_mut_arr_add_val(
					out, symbol_info_json(doc, text,
							      SK_FUNCTION, uri,
							      name, NULL));
			}
			free(text);
		} else if (!strcmp(t, "class_declaration") ||
			   !strcmp(t, "trait_declaration") ||
			   !strcmp(t, "interface_declaration") ||
			   !strcmp(t, "enum_declaration")) {
			int kind = !strcmp(t, "interface_declaration") ?
					   SK_INTERFACE :
				   !strcmp(t, "enum_declaration") ? SK_ENUM :
									 SK_CLASS;
			TSNode name = ts_node_child_by_field_name(node, "name", 4);
			if (ts_node_is_null(name)) {
				continue;
			}
			char *text = node_text(name, content);
			if (matches_query(text, query)) {
				yyjson_mut_arr_add_val(
					out, symbol_info_json(doc, text, kind,
							      uri, name, NULL));
			}
			uint32_t cc = ts_node_named_child_count(node);
			for (uint32_t j = 0; j < cc; j++) {
				TSNode c = ts_node_named_child(node, j);
				const char *ct = ts_node_type(c);
				if (!strcmp(ct, "declaration_list") ||
				    !strcmp(ct, "enum_declaration_list")) {
					collect_class_members_flat(
						c, content, uri, query, text,
						doc, out);
				}
			}
			free(text);
		} else if (!strcmp(t, "namespace_definition")) {
			TSNode body = ts_node_child_by_field_name(node, "body", 4);
			if (!ts_node_is_null(body)) {
				collect_workspace_symbols(body, content, uri,
							  query, doc, out);
			}
		}
	}
}

static yyjson_mut_val *handle_workspace_symbol(struct lsp_context *ctx,
					       yyjson_val *params,
					       yyjson_mut_doc *doc)
{
	const char *query = yyjson_get_str(yyjson_obj_get(params, "query"));
	if (!query) {
		query = "";
	}
	lsp_log(ctx, "workspace/symbol: %s\n", query);

	yyjson_mut_val *out = yyjson_mut_arr(doc);

	for (int i = 0; i < ctx->app->files.len; i++) {
		struct php_file *file = vec_get(&ctx->app->files, i);
		TSNode root = ts_tree_root_node(file->tree);
		collect_workspace_symbols(root, file->content, file->uri,
					  query, doc, out);
	}

	return out;
}

enum {
	CIK_METHOD = 2,
	CIK_FIELD = 5,
	CIK_VARIABLE = 6,
};

static yyjson_mut_val *completion_item(yyjson_mut_doc *doc, const char *label,
				       int kind, const char *detail)
{
	yyjson_mut_val *item = yyjson_mut_obj(doc);
	yyjson_mut_obj_add_strcpy(doc, item, "label", label);
	yyjson_mut_obj_add_int(doc, item, "kind", kind);
	if (detail) {
		yyjson_mut_obj_add_strcpy(doc, item, "detail", detail);
	}
	return item;
}

/* $obj-> : properties + methods of obj's resolved class, from anywhere in
 * the workspace (a class can live in a different file than the usage) */
static void add_member_completions(struct app_ctx *app, yyjson_mut_doc *doc,
				   yyjson_mut_val *items, const char *cls)
{
	for (int i = 0; i < app->vars.len; i++) {
		struct php_var *v = vec_get(&app->vars, i);
		if (v->kind == VAR_PROPERTY && v->class_name &&
		    !strcmp(v->class_name, cls)) {
			/* v->name is "$prop" (real, from the declaration);
			 * strip the "$" since nothing is typed after "->" */
			const char *label =
				v->name[0] == '$' ? v->name + 1 : v->name;
			yyjson_mut_arr_add_val(
				items, completion_item(doc, label, CIK_FIELD,
						       v->type));
		}
	}
	for (int i = 0; i < app->funcs.len; i++) {
		struct php_function *f = vec_get(&app->funcs, i);
		if (f->kind == FUNC_DEF && f->class_name &&
		    !strcmp(f->class_name, cls)) {
			yyjson_mut_arr_add_val(items,
					       completion_item(doc, f->name,
							       CIK_METHOD,
							       f->return_type));
		}
	}
}

/* every variable name in scope (same file + enclosing function), each with
 * whatever type is known for it anywhere in that scope, deduped by name */
static void add_var_completions(struct app_ctx *app, yyjson_mut_doc *doc,
				yyjson_mut_val *items, int file_id,
				const char *function_name)
{
	struct vec seen;
	vec_init(&seen, sizeof(char *));

	for (int i = 0; i < app->vars.len; i++) {
		struct php_var *v = vec_get(&app->vars, i);
		if (v->file_id != file_id) {
			continue;
		}
		int same_fn = (!v->function_name && !function_name) ||
			      (v->function_name && function_name &&
			       !strcmp(v->function_name, function_name));
		if (!same_fn) {
			continue;
		}

		int dup = 0;
		for (int j = 0; j < seen.len; j++) {
			char **sp = vec_get(&seen, j);
			if (!strcmp(*sp, v->name)) {
				dup = 1;
				break;
			}
		}
		if (dup) {
			continue;
		}
		vec_push(&seen, &v->name);

		const char *type = v->type;
		if (!type) {
			TSPoint end = { (uint32_t)-1, (uint32_t)-1 };
			type = type_of_var_before(app, file_id, function_name,
						  v->name, end);
		}
		yyjson_mut_arr_add_val(items,
				       completion_item(doc, v->name,
						       CIK_VARIABLE, type));
	}

	vec_free(&seen);
}

static yyjson_mut_val *handle_completion(struct lsp_context *ctx,
					 yyjson_val *params,
					 yyjson_mut_doc *doc)
{
	yyjson_val *td = yyjson_obj_get(params, "textDocument");
	yyjson_val *pos = yyjson_obj_get(params, "position");
	const char *uri = yyjson_get_str(yyjson_obj_get(td, "uri"));
	int line = yyjson_get_int(yyjson_obj_get(pos, "line"));
	int col = yyjson_get_int(yyjson_obj_get(pos, "character"));
	lsp_log(ctx, "completion: %s %d:%d\n", uri, line, col);

	yyjson_mut_val *items = yyjson_mut_arr(doc);

	struct php_file *file = app_ctx_find_file(ctx->app, uri);
	if (!file) {
		return items;
	}

	uint32_t off = byte_offset_for(file->content, line, col);

	/* "ClassName::" - static member completion isn't supported (no
	 * static-member tracking in the index yet); say so with an empty
	 * list rather than offering irrelevant local variables */
	if (ends_with(file->content, off, "::")) {
		return items;
	}

	TSPoint pt = { (uint32_t)line, (uint32_t)col };
	TSNode root = ts_tree_root_node(file->tree);
	TSNode at = ts_node_named_descendant_for_point_range(root, pt, pt);

	char *obj = var_before_arrow(file->content, off);
	if (obj) {
		char *cls_owned = NULL;
		const char *cls;
		if (!strcmp(obj, "$this")) {
			cls_owned = enclosing_class_name(at, file->content);
			cls = cls_owned;
		} else {
			char *fn = enclosing_function_name(at, file->content);
			cls = strip_ns(type_of_var_before(
				ctx->app, file->file_id, fn, obj, pt));
			free(fn);
		}
		if (cls) {
			add_member_completions(ctx->app, doc, items, cls);
		}
		free(obj);
		free(cls_owned);
		return items;
	}

	char *fn = enclosing_function_name(at, file->content);
	char *cls = enclosing_class_name(at, file->content);
	if (cls) {
		yyjson_mut_arr_add_val(items,
				       completion_item(doc, "$this",
						       CIK_VARIABLE, cls));
	}
	add_var_completions(ctx->app, doc, items, file->file_id, fn);
	free(fn);
	free(cls);
	return items;
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
		} else if (!strcmp(m, "textDocument/references")) {
			send_response(id, resp,
				      handle_references(ctx, params, resp));
		} else if (!strcmp(m, "textDocument/documentSymbol")) {
			send_response(id, resp,
				      handle_document_symbol(ctx, params, resp));
		} else if (!strcmp(m, "workspace/symbol")) {
			send_response(id, resp,
				      handle_workspace_symbol(ctx, params, resp));
		} else if (!strcmp(m, "textDocument/completion")) {
			send_response(id, resp,
				      handle_completion(ctx, params, resp));
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
