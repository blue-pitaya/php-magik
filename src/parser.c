#include "parser.h"
#include <string.h>

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
