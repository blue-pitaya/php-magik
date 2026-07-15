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

#ifndef PARSER_H
#define PARSER_H

#include "app_ctx.h"
#define get_ts_node_child_by_field_name(node, field) \
	ts_node_child_by_field_name(node, field, sizeof(field) - 1)

#include <tree_sitter/api.h>

void node_span(TSNode node, const char *src, const char **out_text,
	       uint32_t *out_len);
char *node_text(TSNode node, const char *src);
int scan(struct app_ctx *app_ctx);

#endif
