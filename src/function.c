#include "function.h"
#include <string.h>
#include <tree_sitter/api.h>
#include "debug.h"

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

static int parse_return_statement_type(TSNode node, const char *src,
				       enum php_type *out)
{
	TSNode expr = ts_node_child(node, 1);
	if (ts_node_is_null(expr)) {
		*out = PHP_TYPE_VOID; // bare "return;"
		return 0;
	}

	const char *type = ts_node_type(expr);
	if (!strcmp(type, "integer")) {
		*out = PHP_TYPE_INT;
	} else if (!strcmp(type, "float")) {
		*out = PHP_TYPE_FLOAT;
	} else if (!strcmp(type, "string")) {
		*out = PHP_TYPE_STRING;
	} else if (!strcmp(type, "boolean")) {
		*out = PHP_TYPE_BOOL;
	} else if (!strcmp(type, "null")) {
		*out = PHP_TYPE_NULL;
	} else {
		*out = PHP_TYPE_MIXED;
	}

	//TODO: complicated statemets

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
	enum php_type ret_type = PHP_TYPE_VOID;
	do {
		TSNode node = ts_tree_cursor_current_node(&cursor);
		if (!strcmp(ts_node_type(node), "return_statement")) {
			parse_return_statement_type(node, src, &ret_type);
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

	def->return_type = ret_type;

	return 0;
}

//TODO: not all is free on error
int build_function_def(TSNode node, const char *src, struct function_def *def)
{
	TSNode name_node =
		ts_node_child_by_field_name(node, "name", sizeof("name") - 1);
	if (ts_node_is_null(name_node)) {
		return -1;
	}

	def->name = node_text(name_node, src);
	if (!def->name) {
		goto error;
	}

	if (build_function_def_args(node, src, def)) {
		goto free_name;
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
