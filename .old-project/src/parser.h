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

#ifndef PARSER_H
#define PARSER_H

#include <tree_sitter/api.h>
#include "vector.h"

enum php_var_kind { VAR_PROPERTY, VAR_PARAM, VAR_USE, VAR_THIS, VAR_OBJ };

struct php_var {
	char *name; /* without leading $ is not stripped: "$foo" */
	char *ns; /* owned copies, NULL if not in scope */
	char *class_name;
	char *function_name;
	char *type;
	enum php_var_kind kind;
	uint32_t line, col; /* 0-based */
	int file_id;
};

enum php_func_kind { FUNC_DEF, FUNC_CALL, FUNC_METHOD };

struct php_function {
	char *name;
	char *ns; /* enclosing scope / owning class */
	char *class_name;
	char *function_name; /* enclosing function for calls, self for defs */
	enum php_func_kind kind;
	char *return_type; /* defs only, NULL if unknown */
	uint32_t line, col;
	int file_id;
};

struct parser_ctx {
	int file_id;
	char *file_content;
	char *ns;
	char *class_name;
	char *function_name;
	struct vec vars; /**< struct php_var */
	struct vec funcs; /**< struct php_function */
};

void php_vars_free(struct vec *v);
void php_funcs_free(struct vec *v);
void php_vars_remove_file(struct vec *v, int file_id);
void php_funcs_remove_file(struct vec *v, int file_id);

/** root is (program) node */
int parse_program(TSNode root, struct parser_ctx *ctx);

#endif
