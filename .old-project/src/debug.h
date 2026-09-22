#include <stdio.h>
#include <stdlib.h>
#include "yyjson.h"

#ifdef NDEBUG
#define DEBUG(fmt, ...) ((void)0)
#define DEBUG_JSON(label, val) ((void)0)
#define DEBUG_MJSON(label, val) ((void)0)
#else
#define DEBUG(fmt, ...)                                              \
	fprintf(stderr, "[DBG %s:%d] " fmt "\n", __FILE__, __LINE__, \
		##__VA_ARGS__)
#define DEBUG_JSON(label, val)                             \
	do {                                               \
		char *_s = yyjson_val_write(val, 0, NULL); \
		DEBUG("%s: %s", label, _s);                \
		free(_s);                                  \
	} while (0)
#define DEBUG_MJSON(label, val)                                \
	do {                                                   \
		char *_s = yyjson_mut_val_write(val, 0, NULL); \
		DEBUG("%s: %s", label, _s);                    \
		free(_s);                                      \
	} while (0)
#endif
