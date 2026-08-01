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
#include "yyjson.h"
#include "lsp.h"
#include "debug.h"

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

	yyjson_mut_val *result = yyjson_mut_obj(doc);
	yyjson_mut_val *contents =
		yyjson_mut_obj_add_obj(doc, result, "contents");
	yyjson_mut_obj_add_str(doc, contents, "kind", "markdown");
	yyjson_mut_obj_add_str(doc, contents, "value",
			       "```php\n// hover info\n```");
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
