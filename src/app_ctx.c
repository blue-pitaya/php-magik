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

	return 0;
}

void app_ctx_free(struct app_ctx *ctx)
{
	for (int i = 0; i < ctx->php_functions.len; i++) {
		php_function_free(&ctx->php_functions.data[i]);
	}
	vec_free(&ctx->php_functions);
	for (int i = 0; i < ctx->php_vars.len; i++) {
		php_function_free(&ctx->php_vars.data[i]);
	}
	vec_free(&ctx->php_vars);
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
