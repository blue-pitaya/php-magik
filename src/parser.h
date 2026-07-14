#ifndef PARSER_H
#define PARSER_H

#include <tree_sitter/api.h>

struct parser_ctx {
	char *ns;
	char *current_class;
};

//FIXME: add init free

void node_span(TSNode node, const char *src, const char **out_text,
	       uint32_t *out_len);
char *node_text(TSNode node, const char *src);

#endif
