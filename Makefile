CC      = cc
CFLAGS  = -Wall -Wextra -Itree-sitter/lib/include -Itree-sitter-php/php_only/src
LDFLAGS = tree-sitter/libtree-sitter.a

SRC = src/main.c \
      tree-sitter-php/php_only/src/parser.c \
      tree-sitter-php/php_only/src/scanner.c

target/main: $(SRC)
	@mkdir -p target
	$(CC) $(CFLAGS) -o $@ $(SRC) $(LDFLAGS)

clean:
	rm -rf target

.PHONY: clean
