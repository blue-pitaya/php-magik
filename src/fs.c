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
#include "app_ctx.h"
#include <dirent.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <tree_sitter/api.h>

const TSLanguage *tree_sitter_php_only(void);

typedef int (*fs_parser_fn)(const char *path, const char *content, size_t size,
			    struct app_ctx *ctx);

static char *read_file(const char *path, size_t *out_size)
{
	FILE *f = fopen(path, "r");
	if (!f) {
		return NULL;
	}
	fseek(f, 0, SEEK_END);
	long sz = ftell(f);
	rewind(f);
	if (sz <= 0) {
		fclose(f);
		return NULL;
	}
	char *buf = malloc(sz + 1);
	if (!buf) {
		fclose(f);
		return NULL;
	}
	*out_size = fread(buf, 1, sz, f);
	buf[*out_size] = '\0';
	fclose(f);
	return buf;
}

static int walk_dir(const char *dir, const char *ext, fs_parser_fn parser,
		    void *ctx)
{
	DIR *d = opendir(dir);
	if (!d) {
		fprintf(stderr, "fs: opendir %s: %s\n", dir, strerror(errno));
		return -1;
	}
	int ret = 0;
	struct dirent *ent;
	while ((ent = readdir(d))) {
		if (ent->d_name[0] == '.') {
			continue;
		}
		char path[4096];
		snprintf(path, sizeof(path), "%s/%s", dir, ent->d_name);
		struct stat st;
		if (stat(path, &st) < 0) {
			continue;
		}
		if (S_ISDIR(st.st_mode)) {
			int err = walk_dir(path, ext, parser, ctx);
			if (err) {
				ret = err;
			}
		} else if (S_ISREG(st.st_mode)) {
			const char *dot = strrchr(ent->d_name, '.');
			if (!dot || strcmp(dot, ext)) {
				continue;
			}
			size_t sz;
			char *content = read_file(path, &sz);
			if (!content) {
				continue;
			}
			int err = parser(path, content, sz, ctx);
			free(content);
			if (err) {
				ret = err;
				break;
			}
		}
	}
	closedir(d);
	return ret;
}

int fs_walk(const char *path, const char *ext, fs_parser_fn parser,
	    struct app_ctx *ctx)
{
	int err;
	struct stat st;

	err = stat(path, &st);
	if (err) {
		fprintf(stderr, "fs: stat %s: %s\n", path, strerror(errno));
		return err;
	}

	if (S_ISREG(st.st_mode)) {

		size_t len;
		char *content = read_file(path, &len);
		if (!content) {
			return -1;
		}

		err = parser(path, content, len, ctx);
		free(content);
		return err;
	}

	return walk_dir(path, ext, parser, ctx);
}

int fs_load_tree(const char *path, TSTree **out_tree, struct app_ctx *app_ctx)
{
	size_t len;
	char *buf = read_file(path, &len);
	if (!buf) {
		return -1;
	}

	app_ctx->parsing_file_content = buf;
	TSParser *parser = ts_parser_new();
	if (!parser) {
		free(buf);
		app_ctx->parsing_file_content = NULL;
		return -1;
	}
	ts_parser_set_language(parser, tree_sitter_php_only());
	TSTree *tree = ts_parser_parse_string(parser, NULL, buf, len);
	ts_parser_delete(parser);
	if (!tree) {
		free(buf);
		app_ctx->parsing_file_content = NULL;
		return -1;
	}
	*out_tree = tree;
	return 0;
}
