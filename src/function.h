#ifndef FUNCTION_H
#define FUNCTION_H

#include <stddef.h>
#include <tree_sitter/api.h>

// https://www.php.net/manual/en/language.types.type-system.php
enum php_type {
	// Scalar
	PHP_TYPE_BOOL,
	PHP_TYPE_INT,
	PHP_TYPE_FLOAT,
	PHP_TYPE_STRING,
	// Other
	PHP_TYPE_ARRAY,
	PHP_TYPE_OBJECT,
	PHP_TYPE_RESOURCE,
	PHP_TYPE_NEVER,
	PHP_TYPE_VOID,
	PHP_TYPE_FALSE,
	PHP_TYPE_TRUE,
	PHP_TYPE_NULL,
	PHP_TYPE_MIXED,
	// User-defined types (generally referred to as class-types): Interfaces Classes Enumerations
	PHP_TYPE_USER_DEFINED
};

extern const char *php_type_str[];

struct var_entry {
	enum php_type type;
	char *name;
};

struct var_table {
	struct var_entry *entries;
	int len;
	int cap;
};

struct function_argument_def {
	enum php_type type;
	char *name;
};

struct function_def {
	char *name;
	struct function_argument_def *args;
	size_t args_len;
	enum php_type return_type;
	struct var_table var_table;
};

int build_function_def(TSNode node, const char *src, struct function_def *def);

#endif
