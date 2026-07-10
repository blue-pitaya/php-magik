#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <tree_sitter/api.h>

const TSLanguage *tree_sitter_php_only(void);

int main(void)
{
	TSParser *parser = ts_parser_new();
	ts_parser_set_language(parser, tree_sitter_php_only());

	const char *src = "<?php echo 'hi'; ?>";
	TSTree *tree = ts_parser_parse_string(parser, NULL, src, strlen(src));
	TSNode root = ts_tree_root_node(tree);

	char *s = ts_node_string(root);
	printf("%s\n", s);
	free(s);

	ts_tree_delete(tree);
	ts_parser_delete(parser);
}
