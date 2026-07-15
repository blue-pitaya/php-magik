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

#include "debug.h"
#include <stdio.h>
#include <tree_sitter/api.h>

void debug(const char *msg)
{
	printf("debug: %s\n", msg);
}

void debug_node(TSNode node)
{
	char *s = ts_node_string(node);
	if (!s) {
		return;
	}

	printf("%s\n", s);
	free(s);
}
