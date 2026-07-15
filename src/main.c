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

#define _GNU_SOURCE
#include "app_ctx.h"
#include <dirent.h>
#include <fcntl.h>
#include "function.h"
#include <getopt.h>
#include "parser.h"
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <tree_sitter/api.h>
#include <unistd.h>
#include "vector.h"

const TSLanguage *tree_sitter_php_only(void);

char *read_file(const char *path, size_t *len)
{
	struct stat sb;
	if (stat(path, &sb) == -1) {
		return NULL;
	}

	*len = sb.st_size;
	char *buf = malloc(*len + 1);
	if (!buf) {
		return NULL;
	}

	int fd = open(path, O_RDONLY);
	if (fd == -1) {
		free(buf);
		return NULL;
	}

	ssize_t n = read(fd, buf, *len);
	if (n < 0) {
		free(buf);
		close(fd);
		return NULL;
	}

	buf[n] = '\0';
	*len = n;

	close(fd);
	return buf;
}

int parse_namespace_definition(TSNode node, const char *src, char **out_name)
{
	TSNode name_node =
		ts_node_child_by_field_name(node, "name", sizeof("name") - 1);
	if (ts_node_is_null(name_node)) {
		return -1;
	}

	char *text = node_text(name_node, src);
	if (!text) {
		return -1;
	}

	*out_name = text;

	return 0;
}

int parse_class_declaration(TSNode node, const char *src, char **out_name)
{
	TSNode name_node =
		ts_node_child_by_field_name(node, "name", sizeof("name") - 1);
	if (ts_node_is_null(name_node)) {
		return -1;
	}

	char *text = node_text(name_node, src);
	if (!text) {
		return -1;
	}

	*out_name = text;

	return 0;
}

int walk(TSTreeCursor *cursor, const char *src, struct vec *php_functions,
	 struct app_ctx *app_ctx)
{
	struct php_function def;
	struct owner_class p_ctx = { 0 }; //FIXME: remove {0}

	do {
		TSNode node = ts_tree_cursor_current_node(cursor);
		const char *type = ts_node_type(node);

		if (!strcmp(type, "namespace_definition")) {
			if (parse_namespace_definition(node, src, &p_ctx.ns)) {
				return -1;
			}
		}
		if (!strcmp(type, "class_declaration")) {
			if (parse_class_declaration(node, src,
						    &p_ctx.class_name)) {
				return -1;
			}
		}

		if (!strcmp(type, "function_definition") ||
		    !strcmp(type, "method_declaration")) {
			php_function_init(&def);
			if (parse_function(node, src, &def, p_ctx, app_ctx)) {
				return -1;
			}
			vec_push(php_functions, &def);
		}

		if (ts_tree_cursor_goto_first_child(cursor)) {
			continue;
		}
		while (!ts_tree_cursor_goto_next_sibling(cursor)) {
			if (!ts_tree_cursor_goto_parent(cursor)) {
				return 0;
			}
		}
	} while (1);
}

int load_tree(const char *path, struct TSTree **out_tree, char **out_content)
{
	size_t content_len;
	char *content = read_file(path, &content_len);
	if (!content) {
		return -1;
	}

	TSParser *parser = ts_parser_new();
	if (!parser) {
		free(content);
		return -1;
	}
	ts_parser_set_language(parser, tree_sitter_php_only());

	TSTree *tree =
		ts_parser_parse_string(parser, NULL, content, content_len);
	if (!tree) {
		free(content);
		return -1;
	}

	*out_content = content;
	*out_tree = tree;

	ts_parser_delete(parser);

	return 0;
}

int scan(const char *path, struct app_ctx *app_ctx)
{
	TSTree *tree;
	char *content;
	if (load_tree(path, &tree, &content)) {
		return -1;
	}

	TSNode root = ts_tree_root_node(tree);

	//debug_node(root);

	TSTreeCursor cursor = ts_tree_cursor_new(root);
	walk(&cursor, content, &app_ctx->php_functions, app_ctx);

	app_ctx_print(app_ctx);

	ts_tree_cursor_delete(&cursor);
	ts_tree_delete(tree);
	free(content);

	return 0;
}

int main(int argc, char *argv[])
{
	char *scan_file = NULL;

	static struct option long_opts[] = {
		{ "scan-file", required_argument, 0, 's' }, { 0, 0, 0, 0 }
	};

	int c;
	while ((c = getopt_long(argc, argv, "", long_opts, NULL)) != -1) {
		switch (c) {
		case 's':
			scan_file = optarg;
			break;
		default:
			fprintf(stderr, "usage: %s --scan-file <filename>\n",
				argv[0]);
			return 1;
		}
	}

	if (!scan_file) {
		fprintf(stderr, "error: --scan-file is required\n");
		return 1;
	}

	struct app_ctx *app_ctx = malloc(sizeof(*app_ctx));
	if (!app_ctx) {
		return 1;
	}

	if (app_ctx_init(app_ctx)) {
		return 1;
	}

	if (scan(scan_file, app_ctx)) {
		fprintf(stderr, "scan error\n");
		return 1;
	}

	return 0;
}
