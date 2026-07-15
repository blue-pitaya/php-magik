#ifndef VAR_H
#define VAR_H

#include <stdbool.h>

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
	PHP_TYPE_USER_DEFINED,
	PHP_TYPE_COUNT,
};

enum php_var_kind {
	PHP_VAR_KIND_MISC,
	PHP_VAR_KIND_FUNC_PARAM,
	PHP_VAR_KIND_LOCAL_DEF,
	PHP_VAR_KIND_PROPERTY,
	PHP_VAR_KIND_USAGE,
	PHP_VAR_KIND_COUNT,
};

extern const char *php_native_type_str[];

struct php_var_refdef {
	// General
	char *name;
	enum php_var_kind kind;
	enum php_native_type type;
	char *ud_type_ns; /**< Can be null */
	char *ud_type_cls_name; /**< Can be null */
	// Ownership
	char *ns; /**< Can be null */
	char *owner_class_name; /**< Can be null */
	char *owner_func_name; /**< Can be null */
	// File info
	char *file_path;
	int line;
	int col_start;
	int col_end;
};

int php_var_refdef_init(struct php_var_refdef *rd);
void php_var_refdef_free(struct php_var_refdef *rd);

#endif
