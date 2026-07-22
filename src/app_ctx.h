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

#define PRINT_MODE_FUNCTIONS 0
#define PRINT_MODE_VARS 1

struct app_ctx {
	char *parsing_file_path;
	char *parsing_file_content;
};

void app_ctx_free(struct app_ctx *ctx);

#endif
