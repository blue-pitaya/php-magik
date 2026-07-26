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

#ifndef APP_CTX_H
#define APP_CTX_H

#include "vector.h"

struct php_file {
	char *uri;
	char *path;
};

struct app_ctx {
	char *root_path;
	struct vec files; /**< struct php_file */
	struct vec vars; /**< struct php_var */
	struct vec funcs; /**< struct php_function */
	// dynamic
	char *parsing_file_path;
	char *parsing_file_content;
};

int app_ctx_init(struct app_ctx *ctx);

#endif
