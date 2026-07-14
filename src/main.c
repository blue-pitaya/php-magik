#include "debug.h"
#include "parser.h"
#include "vector.h"
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
	struct vec php_functions;
};

enum scan_mode {
	scan_file,
	scan_dir,
};

int ctx_init(struct ctx *ctx)
{
	if (vec_init(&ctx->php_functions, sizeof(struct php_function))) {
		return -1;
	}

	return 0;
}

//FIXME:
void ctx_free(struct ctx *ctx)
{
	vec_free(&ctx->php_functions);
}

void ctx_print(struct ctx *ctx)
{
	for (int i = 0; i < ctx->php_functions.len; i++) {
		struct php_function *def = vec_get(&ctx->php_functions, i);

		if (def->ns) {
			printf("%s::", def->ns);
		}

		printf("%s: ", def->name);
		for (int j = 0; j < def->args.len; j++) {
			struct php_var *arg = vec_get(&def->args, i);
			printf("(%s %s) ", php_native_type_str[arg->type],
			       arg->name);
		}
		printf("-> (%s)\n", php_native_type_str[def->return_type]);
	}
}

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

int walk(TSTreeCursor *cursor, const char *src, struct ctx *ctx)
{
	struct php_function def;
	struct parser_ctx p_ctx = { 0 }; //FIXME: remove {0}

	do {
		TSNode node = ts_tree_cursor_current_node(cursor);
		const char *type = ts_node_type(node);

		if (!strcmp(type, "namespace_definition")) {
			if (parse_namespace_definition(node, src, &p_ctx.ns)) {
				return -1;
			}
		}

		if (!strcmp(type, "function_definition") ||
		    !strcmp(type, "method_declaration")) {
			php_function_init(&def);
			if (parse_function(node, src, &def, p_ctx)) {
				return -1;
			}
			vec_push(&ctx->php_functions, &def);
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
