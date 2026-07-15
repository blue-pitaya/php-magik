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

#define _GNU_SOURCE
#include "app_ctx.h"
#include <dirent.h>
#include <fcntl.h>
#include <getopt.h>
#include "parser.h"
#include <stddef.h>
#include <stdio.h>
#include <sys/stat.h>
#include <tree_sitter/api.h>
#include <unistd.h>

int main(int argc, char *argv[])
{
	static struct option long_opts[] = {
		{ "scan-file", required_argument, 0, 's' }, { 0, 0, 0, 0 }
	};

	struct app_ctx app_ctx;
	if (app_ctx_init(&app_ctx)) {
		return 1;
	}

	int c;
	while ((c = getopt_long(argc, argv, "", long_opts, NULL)) != -1) {
		switch (c) {
		case 's':
			app_ctx.parsing_file_path = optarg;
			break;
		default:
			fprintf(stderr, "usage: %s --scan-file <filename>\n",
				argv[0]);
			return 1;
		}
	}

	if (!app_ctx.parsing_file_path) {
		fprintf(stderr, "error: --scan-file is required\n");
		return 1;
	}

	if (scan(&app_ctx)) {
		fprintf(stderr, "scan error\n");
		return 1;
	}

	return 0;
}
