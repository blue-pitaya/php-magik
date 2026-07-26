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

#include "fs.h"
#include "lsp.h"
#define _GNU_SOURCE
#include "app_ctx.h"
#include <dirent.h>
#include <fcntl.h>
#include <getopt.h>
#include "parser.h"
#include <stddef.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <tree_sitter/api.h>
#include <unistd.h>

static int scan(struct app_ctx *app_ctx)
{
	int err;
	TSTree *tree;
	err = fs_load_tree(app_ctx->parsing_file_path, &tree, app_ctx);
	if (err) {
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

static int parse(const char *path, const char *content, size_t size,
		 struct app_ctx *app_ctx)
{
	app_ctx->parsing_file_path = strdup(path);
	printf("Parsing: %s\n", path);

	int err;
	TSTree *tree;
	err = fs_load_tree(app_ctx->parsing_file_path, &tree, app_ctx);
	if (err) {
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
		{ "path", required_argument, 0, 'p' }, { 0, 0, 0, 0 }
	};
	struct app_ctx ctx = { 0 };

	int c;
	struct stat sb;
	char *arg_value;
	while ((c = getopt_long(argc, argv, "", long_opts, NULL)) != -1) {
		switch (c) {
		case 'p':
			arg_value = strdup(optarg);
			err = stat(arg_value, &sb);
			if (err) {
				fprintf(stderr, "wrong args\n");
				return 1;
			}
			if (S_ISDIR(sb.st_mode)) {
				ctx.fs_mode = FS_MODE_ROOT_DIR;
				ctx.root_path = arg_value;
			} else if (S_ISREG(sb.st_mode)) {
				ctx.fs_mode = FS_MODE_SINGLE_FILE;
				ctx.root_path = arg_value;
			} else {
				fprintf(stderr, "not a file or dir\n");
				return 1;
			}
			break;
		default:
			fprintf(stderr, "wrong args\n");
			return 1;
		}
	}

	switch (ctx.fs_mode) {
	case FS_MODE_ROOT_DIR:
		err = fs_walk(ctx.root_path, ".php", parse, &ctx);
		if (err) {
			fprintf(stderr, "error\n");
			return 1;
		}
		break;
	case FS_MODE_SINGLE_FILE:
		ctx.parsing_file_path = strdup(ctx.root_path);
		err = scan(&ctx);
		if (err) {
			fprintf(stderr, "scan error\n");
			return 1;
		}
		break;
	default:
		fprintf(stderr, "wrong args\n");
		return 1;
	}

	struct lsp_context lsp_ctx = { 0 };
	lsp_run(&lsp_ctx);

	return 0;
}
