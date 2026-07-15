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

#include "var.h"
#include <stdlib.h>
#include <unistd.h>

const char *php_native_type_str[] = {
	[PHP_TYPE_BOOL] = "bool",
	[PHP_TYPE_INT] = "int",
	[PHP_TYPE_FLOAT] = "float",
	[PHP_TYPE_STRING] = "string",
	[PHP_TYPE_ARRAY] = "array",
	[PHP_TYPE_OBJECT] = "object",
	[PHP_TYPE_RESOURCE] = "resource",
	[PHP_TYPE_NEVER] = "never",
	[PHP_TYPE_VOID] = "void",
	[PHP_TYPE_FALSE] = "false",
	[PHP_TYPE_TRUE] = "true",
	[PHP_TYPE_NULL] = "null",
	[PHP_TYPE_MIXED] = "mixed",
	[PHP_TYPE_USER_DEFINED] = "user_defined",
};

int php_var_refdef_init(struct php_var_refdef *rd)
{
	rd->name = NULL;
	rd->kind = PHP_VAR_KIND_MISC;
	rd->type = PHP_TYPE_MIXED;
	rd->ud_type_ns = NULL;
	rd->ud_type_cls_name = NULL;

	rd->ns = NULL;
	rd->owner_class_name = NULL;
	rd->owner_func_name = NULL;

	rd->file_path = NULL;
	rd->line = 0;
	rd->col_start = 0;
	rd->col_end = 0;

	return 0;
}

void php_var_refdef_free(struct php_var_refdef *rd)
{
	free(rd->name);
	free(rd->ud_type_ns);
	free(rd->ud_type_cls_name);
	free(rd->ns);
	free(rd->owner_class_name);
	free(rd->owner_func_name);
	free(rd->file_path);
	free(rd);
}
