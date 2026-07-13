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
