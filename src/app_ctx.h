#ifndef APP_CTX_H
#define APP_CTX_H

#include "vector.h"

struct app_ctx {
	struct vec php_functions; /**< of: struct php_function */
	struct vec php_vars; /**< of: struct php_var_refdef */
};

int app_ctx_init(struct app_ctx *ctx);
void app_ctx_free(struct app_ctx *ctx);
void app_ctx_print(struct app_ctx *ctx);

#endif
