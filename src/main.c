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

#include "parser.h"
#include <string.h>
#define _GNU_SOURCE
#include "app_ctx.h"
#include <dirent.h>
#include <fcntl.h>
#include <getopt.h>
#include <stddef.h>
#include <stdio.h>
#include <sys/stat.h>
#include <tree_sitter/api.h>
#include <unistd.h>

const TSLanguage *tree_sitter_php_only(void);

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

static int scan(struct app_ctx *app_ctx)
{
	TSTree *tree;
	if (load_tree(app_ctx->parsing_file_path, &tree, app_ctx)) {
		return -1;
	}

	TSNode root = ts_tree_root_node(tree);

	struct parser_ctx ctx = { 0 };
	ctx.file_content = app_ctx->parsing_file_content;
	vec_init(&ctx.vars, sizeof(struct php_var));
	vec_init(&ctx.funcs, sizeof(struct php_function));

	parse_program(root, &ctx);
	parser_print_php_funcs(&ctx);
	parser_print_php_vars(&ctx);

	php_vars_free(&ctx.vars);
	php_funcs_free(&ctx.funcs);
	free(ctx.ns);
	ts_tree_delete(tree);
	return 0;
}

int main(int argc, char *argv[])
{
	int err;
	static struct option long_opts[] = {
		{ "scan-file", required_argument, 0, 's' },
		{ "print", required_argument, 0, 'p' },
		{ 0, 0, 0, 0 }
	};
	struct app_ctx app_ctx = { 0 };

	int c;
	while ((c = getopt_long(argc, argv, "", long_opts, NULL)) != -1) {
		switch (c) {
		case 's':
			app_ctx.parsing_file_path = strdup(optarg);
			break;
		default:
			fprintf(stderr, "wrong args\n");
			return 1;
		}
	}

	if (!app_ctx.parsing_file_path) {
		fprintf(stderr, "wrong args\n");
		return 1;
	}

	err = scan(&app_ctx);
	if (err) {
		fprintf(stderr, "scan error\n");
		return 1;
	}

	return 0;
}
