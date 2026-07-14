#include "function.h"
#include "parser.h"
#include "vector.h"
#include <stdlib.h>
#include <string.h>
#include <tree_sitter/api.h>

const char *php_native_type_str[] = {
	[PHP_TYPE_BOOL] = "bool",
	[PHP_TYPE_INT] = "int",
	[PHP_TYPE_FLOAT] = "float",
	[PHP_TYPE_STRING] = "string",
	[PHP_TYPE_ARRAY] = "array",
	[PHP_TYPE_OBJECT] = "object",
	[PHP_TYPE_RESOURCE] = "resource",
	[PHP_TYPE_NEVER] = "never",
	[PHP_TYPE_VOID] = "void",
	[PHP_TYPE_FALSE] = "false",
	[PHP_TYPE_TRUE] = "true",
	[PHP_TYPE_NULL] = "null",
	[PHP_TYPE_MIXED] = "mixed",
	[PHP_TYPE_USER_DEFINED] = "user_defined",
};

int php_function_init(struct php_function *f)
{
	f->ns = NULL;
	f->class_name = NULL;
	f->name = NULL;
	if (vec_init(&f->args, sizeof(struct php_var))) {
		return -1;
	}
	f->return_type = PHP_TYPE_MIXED;

	return 0;
}

void php_function_free(struct php_function *f)
{
	free(f->ns);
	free(f->class_name);
	free(f->name);
	for (int i = 0; i < f->args.len; i++) {
		struct php_var *v = vec_get(&f->args, i);
		free(v->name);
	}
	vec_free(&f->args);
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
	for (size_t i = 0;
	     i < sizeof(php_native_type_str) / sizeof(*php_native_type_str);
	     i++) {
		if (strlen(php_native_type_str[i]) == len &&
		    !memcmp(php_native_type_str[i], text, len)) {
			return (enum php_native_type)i;
		}
	}

	return PHP_TYPE_MIXED;
}

static enum php_native_type resolve_expr_type(TSNode expr_node, const char *src,
					      struct vec *vars)
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
		TSNode left = ts_node_child_by_field_name(expr_node, "left",
							  sizeof("left") - 1);
		TSNode right = ts_node_child_by_field_name(expr_node, "right",
							   sizeof("right") - 1);
		enum php_native_type lt = resolve_expr_type(left, src, vars);
		enum php_native_type rt = resolve_expr_type(right, src, vars);
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
					 vars);
	}
	if (!strcmp(type, "variable_name")) {
		char *name = node_text(expr_node, src);
		if (!name) {
			return PHP_TYPE_MIXED;
		}

		struct php_var var;
		for (int i = 0; i < vars->len; i++) {
			var = *(struct php_var *)vec_get(vars, i);
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
			       struct php_function *def)
{
	//FIXME:
	if (vec_init(&def->args, sizeof(struct php_var))) {
		return -1;
	}

	TSNode params = ts_node_child_by_field_name(function_node, "parameters",
						    sizeof("parameters") - 1);
	if (ts_node_is_null(params)) {
		return -1;
	}

	uint32_t count = ts_node_named_child_count(params);
	for (uint32_t i = 0; i < count; i++) {
		TSNode param = ts_node_named_child(params, i);
		if (strcmp(ts_node_type(param), "simple_parameter")) {
			goto fail;
		}

		TSNode type_node = ts_node_child_by_field_name(
			param, "type", sizeof("type") - 1);
		TSNode param_name = ts_node_child_by_field_name(
			param, "name", sizeof("name") - 1);
		if (ts_node_is_null(param_name)) {
			goto fail;
		}

		struct php_var arg = {
			.type = parse_literal_type(type_node, src),
			.name = node_text(param_name, src),
		};
		if (!arg.name) {
			goto fail;
		}

		if (vec_push(&def->args, &arg)) {
			free(arg.name);
			goto fail;
		}
	}

	return 0;
fail:
	for (int i = 0; i < def->args.len; i++) {
		struct php_var *v = vec_get(&def->args, i);
		free(v->name);
	}
	vec_free(&def->args);
	return -1;
}

static int parse_return_statement_type(TSNode ret_smt_node, const char *src,
				       struct php_function *def,
				       struct vec *vars)
{
	TSNode expr = ts_node_child(ret_smt_node, 1);
	if (ts_node_is_null(expr)) {
		def->return_type = PHP_TYPE_VOID; // bare "return;"
		return 0;
	}

	def->return_type = resolve_expr_type(expr, src, vars);

	return 0;
}

static int parse_function_return_type(TSNode function_node, const char *src,
				      struct php_function *def,
				      struct vec *vars)
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
			TSNode left = ts_node_child_by_field_name(
				n, "left", sizeof("left") - 1);
			TSNode right = ts_node_child_by_field_name(
				n, "right", sizeof("right") - 1);
			char *name = node_text(left, src);
			if (name) {
				struct php_var var = {
					.name = name,
					.type = resolve_expr_type(right, src,
								  vars),
				};
				vec_push(vars, &var);
				free(name);
			}
		} else if (!strcmp(type, "return_statement")) {
			parse_return_statement_type(n, src, def, vars);
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

int parse_function(TSNode node, const char *src, struct php_function *def,
		   struct parser_ctx p_ctx)
{
	def->ns = p_ctx.ns ? strdup(p_ctx.ns) : NULL;
	def->class_name = p_ctx.current_class ? strdup(p_ctx.current_class) :
						NULL;

	TSNode name_node =
		ts_node_child_by_field_name(node, "name", sizeof("name") - 1);
	if (ts_node_is_null(name_node)) {
		goto error;
	}

	def->name = node_text(name_node, src);
	if (!def->name) {
		goto error;
	}

	if (parse_function_args(node, src, def)) {
		goto free_name;
	}

	struct vec vars;
	vec_init(&vars, sizeof(struct php_var));
	for (int i = 0; i < def->args.len; i++) {
		struct php_var *var = vec_get(&def->args, i);
		vec_push(&vars, var);
	}

	if (parse_function_return_type(node, src, def, &vars)) {
		goto free_name;
	}

	vec_free(&vars);

	return 0;

free_name:
	free(def->name);
error:
	return -1;
}
