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

#include "parser.h"
#include <string.h>

void node_span(TSNode node, const char *src, const char **out_text,
	       uint32_t *out_len)
{
	uint32_t start = ts_node_start_byte(node);
	*out_len = ts_node_end_byte(node) - start;
	*out_text = src + start;
}

char *node_text(TSNode node, const char *src)
{
	const char *text;
	uint32_t len;
	node_span(node, src, &text, &len);
	char *s = malloc(len + 1);
	if (!s) {
		return NULL;
	}
	memcpy(s, text, len);
	s[len] = '\0';
	return s;
}
