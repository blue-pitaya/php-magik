#include "debug.h"
#include <stdio.h>
#include <tree_sitter/api.h>

void debug(const char *msg)
{
	printf("debug: %s\n", msg);
}

void debug_node(TSNode node)
{
	char *s = ts_node_string(node);
	if (!s) {
		return;
	}

	printf("%s\n", s);
	free(s);
}
