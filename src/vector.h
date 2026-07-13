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
void *vec_get(struct vec *v, int i);

#endif
