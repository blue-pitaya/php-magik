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

#include "vector.h"
#include <stdlib.h>
#include <string.h>

int vec_init(struct vec *v, int elem_size)
{
	v->elem_size = elem_size;
	v->len = 0;
	v->cap = VECTOR_DEFAULT_CAP;
	v->data = malloc(v->cap * v->elem_size);
	if (!v->data) {
		return -1;
	}
	return 0;
}

void vec_free(struct vec *v)
{
	free(v->data);
	v->data = NULL;
	v->len = 0;
	v->cap = 0;
}

int vec_push(struct vec *v, const void *item)
{
	if (v->len >= v->cap) {
		int new_cap = v->cap ? v->cap * 2 : VECTOR_DEFAULT_CAP;
		struct var_entry *tmp =
			realloc(v->data, new_cap * v->elem_size);
		if (!tmp) {
			return -1;
		}
		v->data = tmp;
		v->cap = new_cap;
	}

	memcpy((char *)v->data + v->len * v->elem_size, item, v->elem_size);
	v->len++;

	return 0;
}

void *vec_get(struct vec *v, int i)
{
	return (char *)v->data + i * v->elem_size;
}
