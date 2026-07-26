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

#ifndef VECTOR_H
#define VECTOR_H

#define VECTOR_DEFAULT_CAP 16

struct vec {
	void *data;
	int len;
	int cap;
	int elem_size;
};

int vec_init(struct vec *v, int elem_size);
void vec_free(struct vec *v);

int vec_push(struct vec *v, const void *item);
void *vec_get(const struct vec *v, int i);
void vec_concat(struct vec *v, const struct vec *other);

#endif
