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
#include "function.h"
#include "var.h"
#include "vector.h"
#include <stdio.h>

int app_ctx_init(struct app_ctx *ctx)
{
	if (vec_init(&ctx->php_functions, sizeof(struct php_function))) {
		return -1;
	}
	if (vec_init(&ctx->php_vars, sizeof(struct php_var_refdef))) {
		return -1;
	}

	ctx->parsing_file_path = NULL;
	ctx->parsing_file_content = NULL;
	ctx->parsing_ns = NULL;
	ctx->parsing_class_name = NULL;

	return 0;
}

void app_ctx_free(struct app_ctx *ctx)
{
	struct php_function *fs = ctx->php_functions.data;
	for (int i = 0; i < ctx->php_functions.len; i++) {
		php_function_free(&fs[i]);
	}
	vec_free(&ctx->php_functions);

	struct php_var_refdef *vs = ctx->php_vars.data;
	for (int i = 0; i < ctx->php_vars.len; i++) {
		php_var_refdef_free(&vs[i]);
	}
	vec_free(&ctx->php_vars);

	free(ctx->parsing_file_path);
	free(ctx->parsing_ns);
	free(ctx->parsing_class_name);
}

void app_ctx_print(struct app_ctx *ctx)
{
	for (int i = 0; i < ctx->php_functions.len; i++) {
		struct php_function *def = vec_get(&ctx->php_functions, i);
		bool has_ns_prefix = false;
		bool has_cls_prefix = false;

		if (def->ns) {
			printf("%s", def->ns);
			has_ns_prefix = true;
		}
		if (def->class_name) {
			if (has_ns_prefix) {
				printf("\\");
			}
			printf("%s", def->class_name);
			has_cls_prefix = true;
		}
		if (has_ns_prefix || has_cls_prefix) {
			printf("::");
		}

		printf("%s: ", def->name);
		for (int j = 0; j < def->args.len; j++) {
			struct php_var_refdef *arg = vec_get(&def->args, i);
			printf("(%s %s) ", php_native_type_str[arg->type],
			       arg->name);
		}
		printf("-> (%s)\n", php_native_type_str[def->return_type]);
	}
}

void app_ctx_print_verbose(struct app_ctx *ctx)
{
	app_ctx_print(ctx);

	for (int i = 0; i < ctx->php_vars.len; i++) {
		struct php_var_refdef *rd = vec_get(&ctx->php_vars, i);

		if (rd->ns) {
			printf("NS: %s, ", rd->ns);
		}
		if (rd->owner_class_name) {
			printf("CLS: %s, ", rd->owner_class_name);
		}
		if (rd->owner_func_name) {
			printf("FUNC: %s, ", rd->owner_func_name);
		}
		if (rd->name) {
			printf("NAME %s", rd->name);
		}
		printf("\n");
	}
}
