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

#include "app_ctx.h"
#include "parser.h"
#include "vector.h"

int app_ctx_init(struct app_ctx *ctx)
{
	int err;

	err = vec_init(&ctx->files, sizeof(struct php_file));
	if (err) {
		return err;
	}
	err = vec_init(&ctx->vars, sizeof(struct php_var));
	if (err) {
		return err;
	}
	err = vec_init(&ctx->funcs, sizeof(struct php_function));
	if (err) {
		return err;
	}

	return 0;
}
