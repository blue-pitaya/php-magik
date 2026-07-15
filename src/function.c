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

#include "function.h"
#include "app_ctx.h"
#include "parser.h"
#include "var.h"
#include "vector.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <tree_sitter/api.h>

int php_function_init(struct php_function *f)
{
	f->name = NULL;
	if (vec_init(&f->args, sizeof(struct php_var_refdef))) {
		return -1;
	}
	f->return_type = PHP_TYPE_MIXED;
	f->ud_type_ns = NULL;
	f->ud_type_cls_name = NULL;
	f->ns = NULL;
	f->class_name = NULL;

	return 0;
}

void php_function_free(struct php_function *f)
{
	free(f->name);
	vec_free(&f->args);
	free(f->ud_type_ns);
	free(f->ud_type_cls_name);
	free(f->ns);
	free(f->class_name);
}

static enum php_native_type parse_literal_type(TSNode literal_type_node,
					       const char *src)
{
	if (ts_node_is_null(literal_type_node)) {
		return PHP_TYPE_MIXED;
	}

	const char *text;
	uint32_t len;
	node_span(literal_type_node, src, &text, &len);
	for (size_t i = 0; i < PHP_TYPE_COUNT; i++) {
		if (strlen(php_native_type_str[i]) == len &&
		    !memcmp(php_native_type_str[i], text, len)) {
			return (enum php_native_type)i;
		}
	}

	return PHP_TYPE_MIXED;
}

static enum php_native_type resolve_expr_type(TSNode expr_node,
					      struct app_ctx *app_ctx)
{
	if (ts_node_is_null(expr_node)) {
		return PHP_TYPE_MIXED;
	}

	const char *type = ts_node_type(expr_node);
	if (!strcmp(type, "integer")) {
		return PHP_TYPE_INT;
	}
	if (!strcmp(type, "float")) {
		return PHP_TYPE_FLOAT;
	}
	if (!strcmp(type, "string")) {
		return PHP_TYPE_STRING;
	}
	if (!strcmp(type, "boolean")) {
		return PHP_TYPE_BOOL;
	}
	if (!strcmp(type, "null")) {
		return PHP_TYPE_NULL;
	}
	if (!strcmp(type, "binary_expression")) {
		TSNode left =
			get_ts_node_child_by_field_name(expr_node, "left");
		TSNode right =
			get_ts_node_child_by_field_name(expr_node, "right");
		enum php_native_type lt = resolve_expr_type(left, app_ctx);
		enum php_native_type rt = resolve_expr_type(right, app_ctx);
		if (lt == rt) {
			return lt;
		}
		if ((lt == PHP_TYPE_INT && rt == PHP_TYPE_FLOAT) ||
		    (lt == PHP_TYPE_FLOAT && rt == PHP_TYPE_INT)) {
			return PHP_TYPE_FLOAT;
		}
		return PHP_TYPE_MIXED;
	}
	if (!strcmp(type, "parenthesized_expression")) {
		return resolve_expr_type(ts_node_child(expr_node, 1), app_ctx);
	}
	if (!strcmp(type, "variable_name")) {
		char *name =
			node_text(expr_node, app_ctx->parsing_file_content);
		if (!name) {
			return PHP_TYPE_MIXED;
		}

		struct php_var_refdef *vars = app_ctx->php_vars.data;
		for (int i = 0; i < app_ctx->php_vars.len; i++) {
			if (!strcmp(vars[i].name, name)) {
				free(name);
				return vars[i].type;
			}
		}

		free(name);
		return PHP_TYPE_MIXED;
	}

	return PHP_TYPE_MIXED;
}

static int parse_function_args(TSNode function_node, const char *src,
			       struct php_function *def,
			       struct app_ctx *app_ctx)
{
	TSNode params =
		get_ts_node_child_by_field_name(function_node, "parameters");
	if (ts_node_is_null(params)) {
		return -1;
	}

	uint32_t count = ts_node_named_child_count(params);
	for (uint32_t i = 0; i < count; i++) {
		TSNode param = ts_node_named_child(params, i);
		if (strcmp(ts_node_type(param), "simple_parameter")) {
			return -1;
		}

		TSNode type_node =
			get_ts_node_child_by_field_name(param, "type");
		if (ts_node_is_null(type_node)) {
			return -1;
		}

		TSNode param_name =
			get_ts_node_child_by_field_name(param, "name");
		if (ts_node_is_null(param_name)) {
			return -1;
		}

		char *name = node_text(param_name, src);
		if (!name) {
			return -1;
		}

		struct php_var_refdef arg;
		php_var_refdef_init(&arg, app_ctx);
		arg.name = name;
		arg.kind = PHP_VAR_KIND_FUNC_PARAM;
		arg.type = parse_literal_type(type_node, src);
		arg.owner_func_name = def->name ? strdup(def->name) : NULL;

		if (vec_push(&app_ctx->php_vars, &arg)) {
			free(name);
			return -1;
		}

		struct php_var_refdef *vars = app_ctx->php_vars.data;
		if (vec_push(&def->args, &vars[app_ctx->php_vars.len - 1])) {
			return -1;
		}
	}

	return 0;
}

static int parse_return_statement_type(TSNode ret_smt_node,
				       struct php_function *def,
				       struct app_ctx *app_ctx)
{
	TSNode expr = ts_node_child(ret_smt_node, 1);
	if (ts_node_is_null(expr)) {
		def->return_type = PHP_TYPE_VOID; // bare "return;"
		return 0;
	}

	def->return_type = resolve_expr_type(expr, app_ctx);

	return 0;
}

static int parse_assignment_expression(TSNode node, const char *src,
				       struct app_ctx *app_ctx)
{
	TSNode l_node = get_ts_node_child_by_field_name(node, "left");
	TSNode r_node = get_ts_node_child_by_field_name(node, "right");

	struct php_var_refdef var;
	php_var_refdef_init(&var, app_ctx);
	var.name = node_text(l_node, src);
	if (!var.name) {
		return -1;
	}
	var.kind = PHP_VAR_KIND_LOCAL_DEF;
	var.type = resolve_expr_type(r_node, app_ctx);
	vec_push(&app_ctx->php_vars, &var);

	return 0;
}

static int parse_function_body(TSNode function_node, const char *src,
			       struct php_function *def,
			       struct app_ctx *app_ctx)
{
	TSNode body;
	TSTreeCursor cursor;
	TSNode curr_node;
	const char *node_type;

	body = get_ts_node_child_by_field_name(function_node, "body");
	if (ts_node_is_null(body)) {
		return -1;
	}

	cursor = ts_tree_cursor_new(body);
	do {
		curr_node = ts_tree_cursor_current_node(&cursor);
		node_type = ts_node_type(curr_node);

		if (!strcmp(node_type, "assignment_expression")) {
			if (parse_assignment_expression(curr_node, src,
							app_ctx)) {
				goto error;
			}
		}
		if (!strcmp(node_type, "return_statement")) {
			if (parse_return_statement_type(curr_node, def,
							app_ctx)) {
				goto error;
			}
		}

		if (ts_tree_cursor_goto_first_child(&cursor)) {
			continue;
		}
		while (!ts_tree_cursor_goto_next_sibling(&cursor)) {
			if (!ts_tree_cursor_goto_parent(&cursor)) {
				goto done;
			}
		}
	} while (1);

error:
	ts_tree_cursor_delete(&cursor);
	return -1;
done:
	ts_tree_cursor_delete(&cursor);
	return 0;
}

int parse_function(TSNode node, const char *src, struct php_function *f,
		   struct app_ctx *app_ctx)
{
	f->ns = app_ctx->parsing_ns ? strdup(app_ctx->parsing_ns) : NULL;
	f->class_name = app_ctx->parsing_class_name ?
				strdup(app_ctx->parsing_class_name) :
				NULL;

	TSNode name_node = get_ts_node_child_by_field_name(node, "name");
	if (ts_node_is_null(name_node)) {
		return -1;
	}

	f->name = node_text(name_node, src);
	if (!f->name) {
		return -1;
	}

	if (parse_function_args(node, src, f, app_ctx)) {
		free(f->name);
		return -1;
	}

	if (parse_function_body(node, src, f, app_ctx)) {
		free(f->name);
		return -1;
	}

	return 0;
}
