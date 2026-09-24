# php-magik

A PHP language server in Java, ported from my older C implementation under `.old-project/`. It parses with tree-sitter over a JNI binding and
serves `hover`, `definition`, `references` and `completion` over stdio JSON-RPC. Project is still in progress.

# Build

Needs JDK 21 (with JAVA_HOME set), maven and c compiler. See `build.py` code for more details.

```
./build.py build_all
./build.py test
```

The `ts/` binding is a minimal port of [seart-group/java-tree-sitter](https://github.com/seart-group/java-tree-sitter) (MIT).
