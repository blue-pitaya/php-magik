#include "function.h"
#include <stdlib.h>
#include <string.h>
#include <tree_sitter/api.h>

const char *php_type_str[] = {
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

static int var_table_add(struct var_table *t, enum php_type type,
			 const char *name)
{
	if (t->len == t->cap) {
		int new_cap = t->cap ? t->cap * 2 : 16;
		struct var_entry *tmp =
			realloc(t->entries, new_cap * sizeof(*tmp));
		if (!tmp) {
			return -1;
		}
		t->entries = tmp;
		t->cap = new_cap;
	}

	t->entries[t->len].name = strdup(name);
	if (!t->entries[t->len].name) {
		return -1;
	}
	t->entries[t->len].type = type;
	t->len++;

	return 0;
}

static enum php_type var_table_lookup_type(struct var_table *t,
					   const char *name)
{
	for (int i = 0; i < t->len; i++) {
		if (!strcmp(t->entries[i].name, name)) {
			return t->entries[i].type;
		}
	}

	return PHP_TYPE_MIXED;
}

static char *node_text(TSNode node, const char *src)
{
	uint32_t start = ts_node_start_byte(node);
	uint32_t end = ts_node_end_byte(node);
	uint32_t len = end - start;

	char *s = malloc(len + 1);
	if (!s) {
		return NULL;
	}

	memcpy(s, src + start, len);
	s[len] = '\0';
	return s;
}

static enum php_type resolve_expr_type(TSNode expr, const char *src,
				       struct function_def *def)
{
	const char *type = ts_node_type(expr);
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
		TSNode left = ts_node_child_by_field_name(expr, "left",
							  sizeof("left") - 1);
		TSNode right = ts_node_child_by_field_name(expr, "right",
							   sizeof("right") - 1);
		enum php_type lt = resolve_expr_type(left, src, def);
		enum php_type rt = resolve_expr_type(right, src, def);
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
		return resolve_expr_type(ts_node_child(expr, 1), src, def);
	}
	if (!strcmp(type, "variable_name")) {
		char *name = node_text(expr, src);
		if (!name) {
			return PHP_TYPE_MIXED;
		}
		enum php_type t = var_table_lookup_type(&def->var_table, name);
		free(name);
		return t;
	}

	return PHP_TYPE_MIXED;
}

static enum php_type parse_type(TSNode node, const char *src)
{
	if (ts_node_is_null(node)) {
		return PHP_TYPE_MIXED;
	}

	const char *t = ts_node_type(node);

	uint32_t start = ts_node_start_byte(node);
	uint32_t end = ts_node_end_byte(node);
	uint32_t len = end - start;
	const char *text = src + start;

	for (size_t i = 0; i < sizeof(php_type_str) / sizeof(*php_type_str);
	     i++) {
		if (strlen(php_type_str[i]) == len &&
		    !memcmp(php_type_str[i], text, len)) {
			return (enum php_type)i;
		}
	}

	return PHP_TYPE_MIXED;
}

static int build_function_def_args(TSNode node, const char *src,
				   struct function_def *def)
{
	TSNode params = ts_node_child_by_field_name(node, "parameters",
						    sizeof("parameters") - 1);
	if (ts_node_is_null(params)) {
		return -1;
	}

	uint32_t count = ts_node_named_child_count(params);
	if (count == 0) {
		return 0;
	}

	def->args = malloc(count * sizeof(*def->args));
	if (!def->args) {
		return -1;
	}

	for (uint32_t i = 0; i < count; i++) {
		TSNode param = ts_node_named_child(params, i);
		if (strcmp(ts_node_type(param), "simple_parameter")) {
			return -1;
		}

		TSNode type_node = ts_node_child_by_field_name(
			param, "type", sizeof("type") - 1);
		TSNode param_name = ts_node_child_by_field_name(
			param, "name", sizeof("name") - 1);
		if (ts_node_is_null(param_name)) {
			return -1;
		}

		def->args[def->args_len].type = parse_type(type_node, src);
		def->args[def->args_len].name = node_text(param_name, src);
		if (!def->args[def->args_len].name) {
			return -1;
		}

		def->args_len++;
	}

	return 0;
}

static int parse_return_statement_type(TSNode ret_smt_node, const char *src,
				       struct function_def *def)
{
	TSNode expr = ts_node_child(ret_smt_node, 1);
	if (ts_node_is_null(expr)) {
		def->return_type = PHP_TYPE_VOID; // bare "return;"
		return 0;
	}

	def->return_type = resolve_expr_type(expr, src, def);

	return 0;
}

static int build_function_def_return(TSNode node, const char *src,
				     struct function_def *def)
{
	TSNode body =
		ts_node_child_by_field_name(node, "body", sizeof("body") - 1);
	if (ts_node_is_null(body)) {
		return -1;
	}

	TSTreeCursor cursor = ts_tree_cursor_new(body);
	def->return_type = PHP_TYPE_VOID;
	do {
		TSNode node = ts_tree_cursor_current_node(&cursor);
		if (!strcmp(ts_node_type(node), "return_statement")) {
			parse_return_statement_type(node, src, def);
			goto done;
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

//TODO: not all is free on error
int build_function_def(TSNode node, const char *src, struct function_def *def)
{
	TSNode name_node =
		ts_node_child_by_field_name(node, "name", sizeof("name") - 1);
	if (ts_node_is_null(name_node)) {
		goto error;
	}

	def->name = node_text(name_node, src);
	if (!def->name) {
		goto error;
	}

	if (build_function_def_args(node, src, def)) {
		goto free_name;
	}

	for (int i = 0; i < def->args_len; i++) {
		char *name = strdup(def->args[i].name);
		if (!name) {
			return -1;
		}
		var_table_add(&def->var_table, def->args[i].type, name);
	}

	if (build_function_def_return(node, src, def)) {
		goto free_name;
	}

	return 0;

free_name:
	free(def->name);
error:
	return -1;
}
