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

static enum php_native_type resolve_expr_type(TSNode expr_node, const char *src,
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
		enum php_native_type lt = resolve_expr_type(left, src, app_ctx);
		enum php_native_type rt =
			resolve_expr_type(right, src, app_ctx);
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
		return resolve_expr_type(ts_node_child(expr_node, 1), src,
					 app_ctx);
	}
	if (!strcmp(type, "variable_name")) {
		char *name = node_text(expr_node, src);
		if (!name) {
			return PHP_TYPE_MIXED;
		}

		struct php_var_refdef var;
		for (int i = 0; i < app_ctx->php_vars.len; i++) {
			var = *(struct php_var_refdef *)vec_get(
				&app_ctx->php_vars, i);
			if (!strcmp(var.name, name)) {
				free(name);
				return var.type;
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
		php_var_refdef_init(&arg);
		arg.name = name;
		arg.kind = PHP_VAR_KIND_FUNC_PARAM;
		arg.type = parse_literal_type(type_node, src);
		arg.ns = def->ns ? strdup(def->ns) : NULL;
		arg.owner_class_name =
			def->class_name ? strdup(def->class_name) : NULL;
		arg.owner_func_name = def->name ? strdup(def->name) : NULL;

		if (vec_push(&app_ctx->php_vars, &arg)) {
			free(name);
			return -1;
		}

		if (vec_push(&def->args,
			     &app_ctx->php_vars
				      .data[app_ctx->php_vars.len - 1])) {
			return -1;
		}
	}

	return 0;
}

static int parse_return_statement_type(TSNode ret_smt_node, const char *src,
				       struct php_function *def,
				       struct app_ctx *app_ctx)
{
	TSNode expr = ts_node_child(ret_smt_node, 1);
	if (ts_node_is_null(expr)) {
		def->return_type = PHP_TYPE_VOID; // bare "return;"
		return 0;
	}

	def->return_type = resolve_expr_type(expr, src, app_ctx);

	return 0;
}

static int parse_function_return_type(TSNode function_node, const char *src,
				      struct php_function *def,
				      struct app_ctx *app_ctx)
{
	TSNode body = ts_node_child_by_field_name(function_node, "body",
						  sizeof("body") - 1);
	if (ts_node_is_null(body)) {
		return -1;
	}

	TSTreeCursor cursor = ts_tree_cursor_new(body);
	do {
		TSNode n = ts_tree_cursor_current_node(&cursor);
		const char *type = ts_node_type(n);

		if (!strcmp(type, "assignment_expression")) {
			TSNode left =
				get_ts_node_child_by_field_name(n, "left");
			TSNode right =
				get_ts_node_child_by_field_name(n, "right");
			char *name = node_text(left, src);
			if (name) {
				struct php_var_refdef var;
				php_var_refdef_init(&var);
				var.name = name;
				var.type =
					resolve_expr_type(right, src, app_ctx);
				vec_push(&app_ctx->php_vars, &var);
			}
		} else if (!strcmp(type, "return_statement")) {
			parse_return_statement_type(n, src, def, app_ctx);
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

done:
	ts_tree_cursor_delete(&cursor);

	return 0;
}

int parse_function(TSNode node, const char *src, struct php_function *f,
		   struct owner_class p_ctx, struct app_ctx *app_ctx)
{
	f->ns = p_ctx.ns ? strdup(p_ctx.ns) : NULL;
	f->class_name = p_ctx.class_name ? strdup(p_ctx.class_name) : NULL;

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

	if (parse_function_return_type(node, src, f, app_ctx)) {
		free(f->name);
		return -1;
	}

	return 0;
}
