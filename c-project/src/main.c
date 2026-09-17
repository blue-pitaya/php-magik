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

#include "vector.h"
#define _GNU_SOURCE
#include "fs.h"
#include "lsp.h"
#include "app_ctx.h"
#include <dirent.h>
#include <fcntl.h>
#include <getopt.h>
#include <limits.h>
#include "parser.h"
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <tree_sitter/api.h>
#include <unistd.h>

/* "file://" + absolute path, so it matches the uris editors send */
static char *path_to_uri(const char *path)
{
	char resolved[PATH_MAX];
	const char *abs = realpath(path, resolved) ? resolved : path;
	char *uri = malloc(strlen("file://") + strlen(abs) + 1);
	if (!uri) {
		return NULL;
	}
	sprintf(uri, "file://%s", abs);
	return uri;
}

static int parse(const char *path, const char *content, size_t size,
		 struct app_ctx *ctx)
{
	int err;
	TSTree *tree;
	err = fs_parse_tree(content, size, &tree);
	if (err) {
		return -1;
	}

	TSNode root = ts_tree_root_node(tree);

	struct php_file file = {
		.file_id = ctx->files.len,
		.uri = path_to_uri(path),
		.path = strdup(path),
		.content = strndup(content, size),
		.tree = tree,
	};

	struct parser_ctx p_ctx = { 0 };
	p_ctx.file_id = file.file_id;
	p_ctx.file_content = file.content;
	vec_init(&p_ctx.vars, sizeof(struct php_var));
	vec_init(&p_ctx.funcs, sizeof(struct php_function));

	parse_program(root, &p_ctx);

	vec_concat(&ctx->vars, &p_ctx.vars);
	vec_concat(&ctx->funcs, &p_ctx.funcs);
	vec_free(&p_ctx.vars);
	vec_free(&p_ctx.funcs);

	free(p_ctx.ns);
	free(p_ctx.class_name);
	free(p_ctx.function_name);

	/* tree/content are kept alive in ctx->files for the LSP's lifetime */
	vec_push(&ctx->files, &file);
	return 0;
}

int main(int argc, char *argv[])
{
	int err;
	static struct option long_opts[] = {
		{ "path", required_argument, 0, 'p' }, { 0, 0, 0, 0 }
	};

	struct app_ctx ctx = { 0 };
	app_ctx_init(&ctx);

	int c;
	struct stat sb;
	char *arg_value;
	while ((c = getopt_long(argc, argv, "", long_opts, NULL)) != -1) {
		switch (c) {
		case 'p':
			arg_value = strdup(optarg);
			ctx.root_path = arg_value;
			break;
		default:
			fprintf(stderr, "wrong args\n");
			return 1;
		}
	}

	err = fs_walk(ctx.root_path, ".php", parse, &ctx);
	if (err) {
		fprintf(stderr, "scan error\n");
		return 1;
	}

	struct lsp_context lsp_ctx = { 0 };
	lsp_ctx.app = &ctx;
	lsp_run(&lsp_ctx);

	return 0;
}
