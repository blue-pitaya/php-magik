# php-magik

A PHP language server in Java, ported from my older C implementation under `.old-project/`. It parses with tree-sitter over a JNI binding and
serves `hover`, `definition`, `references` and `completion` over stdio JSON-RPC. Project is still in progress.

# Build

Needs JDK 21 (with JAVA_HOME set), maven and c compiler. See `build.py` code for more details.

```
./build.py build_all
./build.py test
```

The `ts/` binding parses in a single JNI call that copies the whole syntax tree into a Java `int[]`; a `Node` is an index into it.

# Neovim

Source `editors/nvim/php-magik.lua` from your config after building. It starts the server for PHP buffers and adds `:PhpMagikLog` and
`:PhpMagikRestart`. Set `PHP_MAGIK_HOME` if the repo is not at `/var/www`.

```lua
dofile("/path/to/php-magik/editors/nvim/php-magik.lua")
```
