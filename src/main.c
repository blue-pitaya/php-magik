#include "debug.h"
#include <dirent.h>
#include <fcntl.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <tree_sitter/api.h>
#include <unistd.h>
#include "function.h"

const TSLanguage *tree_sitter_php_only(void);

struct ctx {
	struct function_def *function_defs;
	size_t function_defs_len;
	size_t function_defs_capacity;
};

int ctx_init(struct ctx *ctx)
{
	ctx->function_defs_len = 0;
	ctx->function_defs_capacity = 64;
	ctx->function_defs = malloc(ctx->function_defs_capacity *
				    sizeof(*ctx->function_defs));
	if (!ctx->function_defs) {
		return -1;
	}

	return 0;
}

void ctx_free(struct ctx *ctx)
{
	for (size_t i = 0; i < ctx->function_defs_len; i++) {
		struct function_def *f = &ctx->function_defs[i];
		for (size_t j = 0; j < f->args_len; j++) {
			free(f->args[j].name);
		}
		free(f->args);
	}
	free(ctx->function_defs);
}

int ctx_add_function_def(struct ctx *ctx, const struct function_def *def)
{
	if (ctx->function_defs_len == ctx->function_defs_capacity) {
		size_t new_cap = ctx->function_defs_capacity * 2;
		struct function_def *tmp =
			realloc(ctx->function_defs, new_cap * sizeof(*tmp));
		if (!tmp) {
			return -1;
		}
		ctx->function_defs = tmp;
		ctx->function_defs_capacity = new_cap;
	}
	ctx->function_defs[ctx->function_defs_len++] = *def;

	return 0;
}

void ctx_print(struct ctx *ctx)
{
	for (int i = 0; i < ctx->function_defs_len; i++) {
		const struct function_def def = ctx->function_defs[i];
		printf("%s: ", def.name);
		for (int j = 0; j < def.args_len; j++) {
			const struct function_argument_def arg = def.args[j];
			printf("(%s %s) ", php_type_str[arg.type], arg.name);
		}
		printf("-> (%s)\n", php_type_str[def.return_type]);
	}
}

enum scan_mode {
	scan_file,
	scan_dir,
};

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

char *parse_args(int args, char **argv, enum scan_mode *sm)
{
	if (args < 2) {
		return NULL;
	}

	struct stat sb;
	char *path = argv[1];
	if (stat(path, &sb)) {
		fprintf(stderr, "invalid path\n");
		return NULL;
	}
	if (S_ISREG(sb.st_mode)) {
		*sm = scan_file;
	}
	if (S_ISDIR(sb.st_mode)) {
		*sm = scan_dir;
	}

	return path;
}

int walk(TSTreeCursor *cursor, const char *src, struct ctx *ctx)
{
	do {
		TSNode node = ts_tree_cursor_current_node(cursor);
		const char *type = ts_node_type(node);
		if (!strcmp(type, "function_definition") ||
		    !strcmp(type, "method_declaration")) {
			struct function_def def = { 0 };
			if (build_function_def(node, src, &def)) {
				return -1;
			}
			if (ctx_add_function_def(ctx, &def)) {
				//TODO: free def!
				return -1;
			}
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

int scan(const char *path, struct ctx *ctx)
{
	TSTree *tree;
	char *content;
	if (load_tree(path, &tree, &content)) {
		return -1;
	}

	TSNode root = ts_tree_root_node(tree);

	debug_node(root);

	TSTreeCursor cursor = ts_tree_cursor_new(root);
	walk(&cursor, content, ctx);

	ctx_print(ctx);

	ts_tree_cursor_delete(&cursor);
	ts_tree_delete(tree);
	free(content);

	return 0;
}

int main(int args, char **argv)
{
	enum scan_mode sm;
	char *path = parse_args(args, argv, &sm);
	if (!path) {
		fprintf(stderr, "wrong args\n");
		return 1;
	}

	struct ctx *ctx = malloc(sizeof(*ctx));
	if (!ctx) {
		return 1;
	}
	if (ctx_init(ctx)) {
		free(ctx);
		return 1;
	}

	if (sm == scan_file) {
		if (scan(path, ctx)) {
			fprintf(stderr, "scan error\n");
			ctx_free(ctx);
			return 1;
		}
	} else {
		fprintf(stderr, "not implemented\n");
		ctx_free(ctx);
		return 1;
	}

	ctx_free(ctx);
	return 0;
}
