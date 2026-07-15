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
#include <fcntl.h>
#include "parser.h"
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <tree_sitter/api.h>
#include <unistd.h>

const TSLanguage *tree_sitter_php_only(void);

void node_span(TSNode node, const char *src, const char **out_text,
	       uint32_t *out_len)
{
	uint32_t start = ts_node_start_byte(node);
	*out_len = ts_node_end_byte(node) - start;
	*out_text = src + start;
}

char *node_text(TSNode node, const char *src)
{
	const char *text;
	uint32_t len;
	node_span(node, src, &text, &len);
	char *s = malloc(len + 1);
	if (!s) {
		return NULL;
	}
	memcpy(s, text, len);
	s[len] = '\0';
	return s;
}

static char *node_text_ctx(TSNode node, struct app_ctx *ctx)
{
	const char *text;
	uint32_t len;
	node_span(node, ctx->parsing_file_content, &text, &len);

	char *s = malloc(len + 1);
	if (!s) {
		return NULL;
	}

	memcpy(s, text, len);
	s[len] = '\0';
	return s;
}

static int load_tree(const char *path, struct TSTree **out_tree,
		     struct app_ctx *app_ctx)
{
	struct stat sb;
	if (stat(path, &sb) == -1) {
		return -1;
	}

	char *buf = malloc(sb.st_size + 1);
	if (!buf) {
		return -1;
	}

	int fd = open(path, O_RDONLY);
	if (fd == -1) {
		free(buf);
		return -1;
	}

	ssize_t len = read(fd, buf, sb.st_size);
	if (len < 0) {
		free(buf);
		close(fd);
		return -1;
	}

	buf[len] = '\0';
	app_ctx->parsing_file_content = buf;
	close(fd);

	TSParser *parser = ts_parser_new();
	if (!parser) {
		return -1;
	}
	ts_parser_set_language(parser, tree_sitter_php_only());

	TSTree *tree = ts_parser_parse_string(
		parser, NULL, app_ctx->parsing_file_content, len);
	if (!tree) {
		return -1;
	}

	*out_tree = tree;

	ts_parser_delete(parser);
	return 0;
}

static int parse_namespace_definition(TSNode node, struct app_ctx *ctx)
{
	TSNode name_node = get_ts_node_child_by_field_name(node, "name");
	if (ts_node_is_null(name_node)) {
		return -1;
	}

	ctx->parsing_ns = node_text(name_node, ctx->parsing_file_content);
	if (!ctx->parsing_ns) {
		return -1;
	}

	return 0;
}

static int parse_class_declaration(TSNode node, struct app_ctx *ctx)
{
	TSNode name_node = get_ts_node_child_by_field_name(node, "name");
	if (ts_node_is_null(name_node)) {
		return -1;
	}

	ctx->parsing_class_name =
		node_text(name_node, ctx->parsing_file_content);
	if (!ctx->parsing_class_name) {
		return -1;
	}

	return 0;
}

static int parse_property_declaration(TSNode node, struct app_ctx *ctx)
{
	TSNode name_node = get_ts_node_child_by_field_name(node, "name");
	if (ts_node_is_null(name_node)) {
		return -1;
	}

	TSNode type_node = get_ts_node_child_by_field_name(node, "type");
	if (ts_node_is_null(type_node)) {
		return -1;
	}

	char *type_text = node_text_ctx(name_node, ctx);
	if (!type_text) {
		return -1;
	}

	struct php_var_refdef rd;
	php_var_refdef_init(&rd, ctx);

	rd.name = node_text_ctx(name_node, ctx);
	rd.kind = PHP_VAR_KIND_PROPERTY;

	for (size_t i = 0; i < PHP_TYPE_COUNT; i++) {
		if (!strcmp(php_native_type_str[i], type_text)) {
			rd.type = (enum php_native_type)i;
			break;
		}
	}

	free(type_text);

	return 0;
}

int scan(struct app_ctx *app_ctx)
{
	TSTree *tree;
	if (load_tree(app_ctx->parsing_file_path, &tree, app_ctx)) {
		return -1;
	}

	TSNode root = ts_tree_root_node(tree);
	TSTreeCursor cursor = ts_tree_cursor_new(root);

	struct php_function func_def;
	TSNode node;
	const char *type;
	do {
		node = ts_tree_cursor_current_node(&cursor);
		type = ts_node_type(node);

		if (!strcmp(type, "namespace_definition")) {
			if (parse_namespace_definition(node, app_ctx)) {
				return -1;
			}
		}

		if (!strcmp(type, "class_declaration")) {
			if (parse_class_declaration(node, app_ctx)) {
				return -1;
			}
		}

		if (!strcmp(type, "property_declaration")) {
			if (parse_property_declaration(node, app_ctx)) {
				return -1;
			}
		}

		if (!strcmp(type, "function_definition") ||
		    !strcmp(type, "method_declaration")) {
			php_function_init(&func_def);
			if (parse_function(node, app_ctx->parsing_file_content,
					   &func_def, app_ctx)) {
				return -1;
			}
			vec_push(&app_ctx->php_functions, &func_def);
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
	app_ctx_print(app_ctx);

	ts_tree_cursor_delete(&cursor);
	ts_tree_delete(tree);
	return 0;
}
