# php-magik (Java)

Step one of porting `../c-project` to Java: a JNI binding that parses PHP with
the same tree-sitter build the C project uses.

Both sides of the binding - the C glue and the Java wrappers - are ported from
[seart-group/java-tree-sitter][upstream] (MIT). The upstream sources they were
ported from are kept verbatim under `src/main/c/java-tree-sitter/`, with their
`LICENSE`, as the reference; they are not compiled.

[upstream]: https://github.com/seart-group/java-tree-sitter

## Build and run

```
./build.py run_demo      # compile everything, then run the demo
./build.py build_java    # mvn package only (also emits the JNI headers)
./build.py build_native  # compile/link libtsjni.so only
./build.py build_all
./build.py clean
```

Needs a JDK 21 (`JAVA_HOME`), `mvn`, and `cc`. Object files are cached by mtime
against their source, `tsjni.h` and the generated headers, so only the first
build pays for the 180k-line generated PHP parser.

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
  java-tree-sitter/            upstream sources + LICENSE, reference only
src/main/java/dev/bluepitaya/phpmagik/ts/
  LibraryLoader.java           loads libtsjni.so
  External.java                base for owned pointers
  Node.java                    the ported struct
  Point.java                   ditto, row/column
  Parser.java, Tree.java       owned pointers
  ParsingException.java
  Main.java                    demo: parse a snippet, walk the tree
```
