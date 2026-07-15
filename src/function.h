#ifndef FUNCTION_H
#define FUNCTION_H

#include "app_ctx.h"
#include "var.h"
#include "vector.h"
#include "parser.h"
#include <tree_sitter/api.h>

struct php_function {
	// General
	char *name;
	struct vec args; /**< of: (struct php_var_refdef*) */
	enum php_native_type return_type;
	char *ud_type_ns; /**< Can be null */
	char *ud_type_cls_name; /**< Can be null */
	// Ownership
	char *ns; /**< Can be null */
	char *class_name; /**< Can be null */
};

int php_function_init(struct php_function *f);
void php_function_free(struct php_function *f);

int parse_function(TSNode node, const char *src, struct php_function *def,
		   struct owner_class p_ctx, struct app_ctx *app_ctx);

#endif
