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
