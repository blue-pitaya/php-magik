#ifndef FUNCTION_H
#define FUNCTION_H

#include "vector.h"
#include <tree_sitter/api.h>

// https://www.php.net/manual/en/language.types.type-system.php
enum php_native_type {
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

extern const char *php_native_type_str[];

struct php_var {
	char *name;
	enum php_native_type type;
};

struct php_function {
	char *name;
	struct vec args; /**< of: struct php_var */
	enum php_native_type return_type;
};

//FIXME: init and free

int parse_function(TSNode node, const char *src, struct php_function *def);

#endif
