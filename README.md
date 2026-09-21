# php-magik (Java)

A PHP language server, ported to Java from the C implementation kept under
`.old-project/`. It parses PHP with the same tree-sitter build the C project
used, reached through a hand-written JNI binding.

## Status

The whole LSP surface of the C version is ported: `initialize`, `didOpen`,
`didChange`, `didClose`, `hover`, `definition`, `references`, `documentSymbol`,
`workspace/symbol` and `completion`, over the same stdio JSON-RPC transport.

```
src/main/java/dev/bluepitaya/phpmagik/
  Main.java              --path <dir|file>, indexes, then serves on stdio
  Indexer.java           port of the C parser.c: walks a tree, records symbols
  Workspace.java         port of app_ctx.c + fs.c: the per-kind symbol indexes
  PhpFile.java           one indexed file: source, tree, imports
  SymbolFinder.java      what is written at a position, and what a type means
  phpsymbol/             the symbols the index records, declarations and usages
  resolver/              pairs a usage with the declaration it refers to
  lsp/LspServer.java     port of lsp.c: transport, dispatch, every handler
  ts/                    the tree-sitter binding (below)
```

## Test

`python test.py` builds everything and runs the suite. It is the C project's
`test.py` with only the launch command changed, and `lsp-tests/` holds copies of
that project's fixtures, so the two implementations can be compared check for
check.

## The tree-sitter binding

Both sides of the binding - the C glue and the Java wrappers - are ported from
[seart-group/java-tree-sitter][upstream] (MIT). A checkout of it is kept at
`java-tree-sitter/` purely as the reference to port against: it is not a module
of this project, nothing in it is compiled or on the classpath, and it is
gitignored.

[upstream]: https://github.com/seart-group/java-tree-sitter

## Build and run

```
./build.py serve <dir>   # compile everything, then serve LSP on stdio
./build.py build_java    # mvn package only (also emits the JNI headers)
./build.py build_native  # compile/link libtsjni.so only
./build.py build_all
./build.py clean
./build.py compile_commands  # compile_commands.json for clangd
```

Needs a JDK 21 (`JAVA_HOME`), `mvn`, and `cc`. Object files are cached by mtime
against their source, `tsjni.h` and the generated headers, so only the first
build pays for the 180k-line generated PHP parser.

For editor support in `src/main/c`, run `build_java` once (it emits the headers
the C sources include), then `compile_commands`. The database lands at the repo
root where clangd finds it, and needs regenerating only when the include dirs
or `JAVA_HOME` change.

## What was taken from upstream, and what changed

Taken:

- `External`: opaque C pointers (`Parser`, `Tree`) live in a `long` field that
  JNI reads back, with a `Cleaner` behind `close()`.
- `Node` holds a reference to its `Tree` object rather than the tree pointer, so
  a reachable node keeps its tree alive.
- Class, field and method IDs cached once in `JNI_OnLoad`, plus the
  marshal/unmarshal pair for `Node` and `Point`.
- The `named` flag on `getChild`/`getChildCount`/`getChildren`/`getNextSibling`/
  `getPrevSibling`/`getDescendant`/`getFirstChildForByte`, halving the number of
  native methods, and the public wrappers over them.
- `Node` and `Tree` as `Iterable<Node>`, and `Node.getChildren` as a single
  native returning an array rather than a call per child.
- Bounds checks that throw `IndexOutOfBoundsException` instead of tripping
  tree-sitter's own assertions.

Changed:

- C, not C++, so the build stays a plain `cc` invocation.
- No Lombok, no slf4j, no commons-io, no jetbrains annotations - the fields,
  getters, constructors and library loading are written out.
- **UTF-8, not UTF-16.** Upstream parses with `TSInputEncodingUTF16` and then
  halves every byte offset and column (and doubles them on the way back), and
  re-encodes the whole source on each `getContent()`. Here the source crosses as
  a UTF-8 `byte[]`, offsets pass through untouched, and a slice is a slice.
- No `Query`, `TreeCursor`, `LookaheadIterator`, `Symbol`, `Range`, `InputEdit`,
  logger, or language registry - the parser is pinned to
  `tree_sitter_php_only()`. `ParsingException` is the only exception type kept.
- Two upstream bugs are not carried over, both noted at their site:
  `External`'s cleanup action held the object it was watching, so its `Cleaner`
  could never fire; and `Node.iterator()` documents a depth-first walk but
  appends children to the tail of the deque, which walks breadth-first.

## How Node is ported

C has two kinds of type here, and they port differently.

`TSParser` and `TSTree` are opaque and heap-owned, so Java holds the pointer in
a `long` (`External`) and frees it - both are `AutoCloseable`.

`TSNode` is a **by-value struct**:

```c
typedef struct TSNode {
  uint32_t context[4];
  const void *id;
  const TSTree *tree;
} TSNode;
```

Nothing allocates it, nothing frees it, and it stays valid as long as its tree
does. So it is ported as a field-for-field copy into an immutable class rather
than a pointer wrapper. `__unmarshalNode()` / `__marshalNode()` in
`src/main/c/tsjni.c` copy the fields between the Java object and a stack
`TSNode` on every call, so Java gets the same value semantics C has.
`context[4]` is flattened into four ints - it is opaque to Java, only C reads
it, and the raw 32 bits round-trip unchanged even though Java's ints are signed.
The tree is the one field that is not copied verbatim: Java stores the `Tree`
object and C reads the pointer back out of it.

Methods that take a `TSNode` in C become instance methods (`node.getType()`,
`node.getNamedChild(i)`), and `node.getContent()` slices the tree's UTF-8
source, which is why source is passed across as `byte[]` and not `String`.

## Layout

```
pom.xml                        JDK 21, javac -h -> target/headers
build.py                       native build + run
src/main/c/
  tsjni.h, tsjni.c             cached IDs, throw helpers, marshalling
  tsjni_node.c                 Node natives
  tsjni_tree.c, tsjni_parser.c owned pointers
src/main/java/dev/bluepitaya/phpmagik/ts/
  LibraryLoader.java           loads libtsjni.so
  External.java                base for owned pointers
  Node.java                    the ported struct
  Point.java                   ditto, row/column
  Range.java                   a start and an end point, as LSP wants them
  Nodes.java                   bounds-safe child accessors
  Parser.java, Tree.java       owned pointers
  ParsingException.java
src/main/java/dev/bluepitaya/phpmagik/phpsymbol/
  PhpSymbol.java               sealed over every kind, each with a range
  Php{Var,Function,Method,Property}{Definition,Usage}.java
  PhpClass.java, PhpUseStatement.java, ClassKind.java, UseKind.java
src/main/java/dev/bluepitaya/phpmagik/resolver/
  {Variable,Function,Method,Property}Resolver.java
src/main/java/dev/bluepitaya/phpmagik/lsp/
  LspServer.java               framing, dispatch, responses
  Json.java                    the shared mapper and JSON shapes
  Logger.java                  /tmp/php-magik.log
  dto/                         records the params bind to
  handler/                     one class per LSP request
test.py                        the C project's suite, pointed at the JVM
lsp-tests/                     its fixtures, copied verbatim
```

The one binding added beyond the original proof of concept is
`ts_node_descendant_for_point_range`, which the LSP layer needs to find the node
under an editor position.
