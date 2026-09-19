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

#include "app_ctx.h"
#include "fs.h"
#include "parser.h"
#include "vector.h"
#include <stdlib.h>
#include <string.h>

int app_ctx_init(struct app_ctx *ctx)
{
	int err;

	err = vec_init(&ctx->files, sizeof(struct php_file));
	if (err) {
		return err;
	}
	err = vec_init(&ctx->vars, sizeof(struct php_var));
	if (err) {
		return err;
	}
	err = vec_init(&ctx->funcs, sizeof(struct php_function));
	if (err) {
		return err;
	}

	return 0;
}

struct php_file *app_ctx_find_file(struct app_ctx *ctx, const char *uri)
{
	for (int i = 0; i < ctx->files.len; i++) {
		struct php_file *f = vec_get(&ctx->files, i);
		if (f->uri && !strcmp(f->uri, uri)) {
			return f;
		}
	}
	return NULL;
}

int app_ctx_reparse_file(struct app_ctx *ctx, const char *uri,
			 const char *content, size_t len)
{
	struct php_file *file = app_ctx_find_file(ctx, uri);
	if (!file) {
		return -1;
	}

	TSTree *tree;
	if (fs_parse_tree(content, len, &tree)) {
		return -1;
	}
	char *owned_content = strndup(content, len);
	if (!owned_content) {
		ts_tree_delete(tree);
		return -1;
	}

	struct parser_ctx p_ctx = { 0 };
	p_ctx.file_id = file->file_id;
	p_ctx.file_content = owned_content;
	vec_init(&p_ctx.vars, sizeof(struct php_var));
	vec_init(&p_ctx.funcs, sizeof(struct php_function));

	parse_program(ts_tree_root_node(tree), &p_ctx);

	/* only drop the old entries/tree/content once the new parse has
	 * fully succeeded, so a bad reparse can't corrupt the index */
	php_vars_remove_file(&ctx->vars, file->file_id);
	php_funcs_remove_file(&ctx->funcs, file->file_id);
	vec_concat(&ctx->vars, &p_ctx.vars);
	vec_concat(&ctx->funcs, &p_ctx.funcs);
	vec_free(&p_ctx.vars);
	vec_free(&p_ctx.funcs);
	free(p_ctx.ns);
	free(p_ctx.class_name);
	free(p_ctx.function_name);

	ts_tree_delete(file->tree);
	free(file->content);
	file->tree = tree;
	file->content = owned_content;

	return 0;
}
