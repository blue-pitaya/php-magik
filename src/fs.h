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

#ifndef FS_H
#define FS_H

#include <stddef.h>
#include <sys/types.h>
#include <tree_sitter/api.h>
#include "app_ctx.h"

typedef int (*fs_parser_fn)(const char *path, const char *content, size_t size,
			    struct app_ctx *ctx);

int fs_walk(const char *path, const char *ext, fs_parser_fn parser,
	    struct app_ctx *ctx);
int fs_parse_tree(const char *content, size_t len, TSTree **out_tree);

#endif
