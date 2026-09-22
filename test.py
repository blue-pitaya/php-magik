#!/usr/bin/env python
"""Unified test suite.

Organized by fixture, not by LSP command: each lsp-tests/test_N/ gets one
test_N() function that spawns its own LspClient (spawns the server, does
the initialize handshake) and runs whatever requests are relevant to that
fixture - hover, definition, or both.

test_N/main.php is the standard single-file layout; test_8 is the one
exception with multiple files (Foo.php + NSA/A.php + NSB/B.php), since
it's specifically testing cross-file indexing and go-to-definition.

The hover_cases() driven tests replace the old tests/test_N.php +
test_N_ex.txt CLI-dump fixtures: instead of diffing a printed index dump,
they hover at the start of each identifier the dump used to describe and
check that the resolved signature/type still matches.

This suite drives the Java rewrite over stdio, and the fixtures under
lsp-tests/ are copies of the C project's, so results can be compared
directly.
"""

import json
import os
import subprocess
import sys
from pathlib import Path
from typing import IO

ROOT = Path(__file__).resolve().parent
CLASSES = ROOT / "target" / "classes"
DEPENDENCY = ROOT / "target" / "dependency"
NATIVE = ROOT / "target" / "native"
# the trailing /* is expanded by the JVM itself, not the shell
CLASSPATH = os.pathsep.join([str(CLASSES), f"{DEPENDENCY}/*"])
SERVER = [
    "java",
    f"-Djava.library.path={NATIVE}",
    "-cp",
    CLASSPATH,
    "dev.bluepitaya.phpmagik.Main",
]
LSP_TEST_DIR = ROOT / "lsp-tests"

SHOW_LOGS = False  # flip to True to see the server's stderr debug logs

failures = 0


# ---------------------------------------------------------------------------
# Output helpers
# ---------------------------------------------------------------------------

USE_COLOR = sys.stdout.isatty()


def _c(text: str, code: str) -> str:
    return f"\033[{code}m{text}\033[0m" if USE_COLOR else text


def check(name, actual, expected):
    global failures
    if actual == expected:
        print(f"  {_c('OK', '32')}    {name}")
    else:
        failures += 1
        print(f"  {_c('ERROR', '31;1')} {name}")
        print(f"    expected: {expected!r}")
        print(f"    actual:   {actual!r}")


def php(text: str) -> str:
    """The exact markdown a hover response renders: a ```php fenced block."""
    return f"```php\n{text}\n```"


def func(qualified: str, signature: str, description: str = "", *tags: str) -> str:
    """The markdown a function hover renders: bold qualified name (backslashes
    escaped for markdown), the doc description, the declaration in a php block,
    then one PHPDoc tag per paragraph."""
    out = "__" + qualified.replace("\\", "\\\\") + "__\n"
    if description:
        out += "\n" + description + "\n"
    out += "\n```php\n<?php\n" + signature + " { }\n```\n"
    for tag in tags:
        name, _, rest = tag.partition(" ")
        out += "\n_" + name + "_" + (f" `{rest}`" if rest else "") + "\n"
    return out.strip()


def sort_locs(locs):
    """Canonical order for a list of Locations spanning multiple files.

    Within one file, index order already matches document order, but across
    files it depends on the filesystem's directory-listing order, which
    isn't guaranteed - sort before comparing so those tests aren't flaky.
    """
    return sorted(
        locs,
        key=lambda l: (
            l["uri"],
            l["range"]["start"]["line"],
            l["range"]["start"]["character"],
        ),
    )


# ---------------------------------------------------------------------------
# LSP client wrapper
# ---------------------------------------------------------------------------


class LspClient:
    """Spawns `main --path <root>` and speaks LSP over stdio.

    Each test constructs its own client against the fixture directory (or
    single file) it needs, does whatever setup it needs (didOpen ...), then
    issues the request(s) it's actually testing.
    """

    def __init__(self, root: Path):
        self.root = root
        self._id = 0
        self.proc = subprocess.Popen(
            SERVER + ["--path", str(root)],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=None if SHOW_LOGS else subprocess.DEVNULL,
        )
        if self.proc.stdin is None or self.proc.stdout is None:
            raise RuntimeError("subprocess did not open stdin/stdout pipes")
        self.stdin: IO[bytes] = self.proc.stdin
        self.stdout: IO[bytes] = self.proc.stdout
        self.request("initialize", {})
        self.notify("initialized", {})

    def _write(self, obj):
        body = json.dumps(obj).encode("utf-8")
        header = f"Content-Length: {len(body)}\r\n\r\n".encode("ascii")
        self.stdin.write(header + body)
        self.stdin.flush()

    def _read(self):
        header = b""
        while not header.endswith(b"\r\n\r\n"):
            byte = self.stdout.read(1)
            if not byte:
                # read(1) keeps returning b"" at EOF, so without this a dead
                # server spins here forever instead of failing
                raise RuntimeError(
                    f"server closed stdout after {header!r} "
                    f"(exit code {self.proc.poll()}); "
                    "set SHOW_LOGS = True to see its stderr"
                )
            header += byte
        length = int(header.split(b":")[1])
        return json.loads(self.stdout.read(length))

    def request(self, method, params=None):
        self._id += 1
        self._write(
            {
                "jsonrpc": "2.0",
                "id": self._id,
                "method": method,
                "params": params or {},
            }
        )
        return self._read()

    def notify(self, method, params=None):
        self._write({"jsonrpc": "2.0", "method": method, "params": params or {}})

    def did_open(self, path: Path) -> str:
        uri = f"file://{path}"
        self.notify(
            "textDocument/didOpen",
            {
                "textDocument": {
                    "uri": uri,
                    "languageId": "php",
                    "version": 1,
                    "text": path.read_text(),
                }
            },
        )
        return uri

    def hover(self, uri: str, line: int, character: int):
        result = self.request(
            "textDocument/hover",
            {
                "textDocument": {"uri": uri},
                "position": {"line": line, "character": character},
            },
        )
        contents = (result.get("result") or {}).get("contents") or {}
        return contents.get("value")

    def did_change(self, uri: str, text: str) -> None:
        self.notify(
            "textDocument/didChange",
            {
                "textDocument": {"uri": uri},
                "contentChanges": [{"text": text}],
            },
        )

    def definition(self, uri: str, line: int, character: int):
        result = self.request(
            "textDocument/definition",
            {
                "textDocument": {"uri": uri},
                "position": {"line": line, "character": character},
            },
        )
        return result.get("result")

    def references(
        self, uri: str, line: int, character: int, include_declaration: bool = False
    ):
        result = self.request(
            "textDocument/references",
            {
                "textDocument": {"uri": uri},
                "position": {"line": line, "character": character},
                "context": {"includeDeclaration": include_declaration},
            },
        )
        return result.get("result")

    def close(self):
        self.request("shutdown")
        self.notify("exit")
        try:
            self.proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            self.proc.kill()
            self.proc.wait()


def hover_cases(n: int, cases):
    """Run a list of (line, character, expected) hover checks against
    lsp-tests/test_n/main.php."""
    path = LSP_TEST_DIR / f"test_{n}" / "main.php"
    client = LspClient(path)
    uri = client.did_open(path)
    for line, character, expected in cases:
        value = client.hover(uri, line, character)
        check(f"hover@{line}:{character}", value, expected)
    client.close()


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------


def test_1():
    # return type inferred from a literal return value
    hover_cases(
        1,
        [
            (2, 9, func("zero", "function zero(): int")),
            (7, 9, func("zerof", "function zerof(): float")),
            (12, 9, func("empty_string", "function empty_string(): string")),
        ],
    )


def test_2():
    # return type inferred through +, *, / on literals
    hover_cases(
        2,
        [
            (2, 9, func("add", "function add(): int")),
            (7, 9, func("add2", "function add2(): int")),
            (12, 9, func("add3", "function add3(): float")),
        ],
    )


def test_3():
    # declared param types, and reads resolving to them
    hover_cases(
        3,
        [
            (2, 9, func("add", "function add(int $a, int $b): int")),
            (2, 17, php("$a: int")),
            (2, 25, php("$b: int")),
            (4, 11, php("$a: int")),
            (4, 16, php("$b: int")),
        ],
    )


def test_4():
    # types propagated through local var assignments
    hover_cases(
        4,
        [
            (2, 9, func("add", "function add(int $a, int $b): int")),
            (2, 17, php("$a: int")),
            (2, 25, php("$b: int")),
            (4, 4, php("$c: int")),
            (4, 9, php("$a: int")),
            (4, 14, php("$b: int")),
            (5, 4, php("$d: int")),
            (5, 9, php("$a: int")),
            (5, 14, php("$b: int")),
            (6, 4, php("$e: int")),
            (6, 9, php("$c: int")),
            (6, 14, php("$d: int")),
            (8, 11, php("$e: int")),
        ],
    )


def test_5():
    # methods across two classes in one namespace
    hover_cases(
        5,
        [
            (6, 20, func("App\\Example\\Foo::print",
                         "public function print(int $a): int")),
            (13, 20, func("App\\Example\\Foo::bar", "public function bar(): string")),
            (21, 21, func("App\\Example\\Baz::ok", "private function ok(): float")),
            (6, 30, php("$a: int")),
            (8, 8, php("$b: int")),
            (8, 13, php("$a: int")),
            (10, 15, php("$b: int")),
        ],
    )


def test_6():
    # typed properties, $this->prop, and bare $this
    hover_cases(
        6,
        [
            (12, 20, func("App\\Example\\Foo::print",
                          "public function print(int $a = 10): int")),
            (21, 20, func("App\\Example\\Foo::xd", "public function xd(): string")),
            (6, 15, php("$bar: int")),
            (8, 18, php("$x1: string")),
            (10, 18, php("$x2: string")),
            (12, 30, php("$a: int")),
            (14, 8, php("$this")),
            (14, 15, php("$bar: int")),
            (15, 8, php("$b: int")),
            (16, 8, php("$c: int")),
            (16, 13, php("$a: int")),
            (16, 18, php("$b: int")),
            (18, 15, php("$this")),
            (18, 22, php("$bar: int")),
            (18, 28, php("$c: int")),
            (23, 15, php("$this")),
            (23, 22, php("$x1: string")),
            (23, 29, php("$this")),
            (23, 36, php("$x2: string")),
        ],
    )


def test_7():
    # object-typed properties, and $this->engine->prop chains: the second hop
    # resolves through the first one's declared type, in any number of steps
    hover_cases(
        7,
        [
            (13, 20, func("Car::__construct", "public function __construct(Engine $engine)")),
            (18, 20, func("Car::describe", "public function describe(): string")),
            (4, 15, php("$power: int")),
            (6, 18, php("$fuel: string")),
            (11, 19, php("$engine: Engine")),
            (13, 39, php("$engine: Engine")),
            (15, 8, php("$this")),
            (15, 15, php("$engine: Engine")),
            (15, 24, php("$engine: Engine")),
            (20, 8, php("$total: int")),
            (20, 17, php("$this")),
            (20, 24, php("$engine: Engine")),
            (20, 32, php("$power: int")),
            (22, 15, php("$this")),
            (22, 22, php("$engine: Engine")),
            (22, 30, php("$fuel: string")),
            (22, 39, php("$total: int")),
        ],
    )


def test_8():
    # hover + definition across files: Foo.php calls into NSA/A.php
    root = LSP_TEST_DIR / "test_8"
    client = LspClient(root)
    uri = client.did_open(root / "Foo.php")

    check(
        "hover $a->get()",
        client.hover(uri, 14, 18),
        func("App\\NSA\\A::get", "public function get(): string"),
    )
    check(
        "definition $a->get()",
        client.definition(uri, 14, 18),
        {
            "uri": f"file://{root / 'NSA' / 'A.php'}",
            "range": {
                "start": {"line": 6, "character": 20},
                "end": {"line": 6, "character": 23},
            },
        },
    )
    client.close()


def test_9():
    # definition on a plain local variable (no class, no function) - PHP
    # has no distinct "declaration" node for a local, so this jumps to
    # its earliest occurrence in the same file/scope.
    root = LSP_TEST_DIR / "test_9"
    path = root / "main.php"
    client = LspClient(path)
    uri = client.did_open(path)

    # `echo $a;` on line 4 (0-based line 3) -> should jump to `$a = 10;`
    # on line 3 (0-based line 2), the earlier of the two occurrences.
    check(
        "definition $a (local var)",
        client.definition(uri, 3, 5),
        {
            "uri": uri,
            "range": {
                "start": {"line": 2, "character": 0},
                "end": {"line": 2, "character": 2},
            },
        },
    )
    client.close()


def test_10():
    # go-to-definition "in class context": $this->prop and $this->method()
    # both resolving within the same class (Greeter, in Greeter.php)
    root = LSP_TEST_DIR / "test_10"
    client = LspClient(root)
    uri = client.did_open(root / "Greeter.php")

    check(
        "definition $this->name (property)",
        client.definition(uri, 13, 34),
        {
            "uri": uri,
            "range": {
                "start": {"line": 4, "character": 18},
                "end": {"line": 4, "character": 23},
            },
        },
    )
    check(
        "definition $this->greet() (method)",
        client.definition(uri, 18, 33),
        {
            "uri": uri,
            "range": {
                "start": {"line": 11, "character": 20},
                "end": {"line": 11, "character": 25},
            },
        },
    )
    client.close()


def test_11():
    # didChange must actually reparse: after editing $a's initializer from
    # an int literal to a string one, hovering the same position should
    # reflect the new type, not the stale on-disk state.
    root = LSP_TEST_DIR / "test_11"
    path = root / "main.php"
    client = LspClient(path)
    uri = client.did_open(path)

    check("hover $a before edit", client.hover(uri, 3, 5), php("$a: int"))

    client.did_change(uri, "<?php\n\n$a = 'hi';\necho $a;\n")

    check("hover $a after edit", client.hover(uri, 3, 5), php("$a: string"))
    client.close()


# ---------------------------------------------------------------------------
# More definition cases: a plain function call, a parameter usage, a
# non-$this object property, a forward reference, and a same-file object
# method call.
# ---------------------------------------------------------------------------


def test_13():
    # plain top-level function call -> its definition (not a method call,
    # not covered by any earlier test)
    root = LSP_TEST_DIR / "test_13"
    path = root / "main.php"
    client = LspClient(path)
    uri = client.did_open(path)

    check(
        "definition greet()",
        client.definition(uri, 7, 5),
        {
            "uri": uri,
            "range": {
                "start": {"line": 2, "character": 9},
                "end": {"line": 2, "character": 14},
            },
        },
    )
    client.close()


def test_14():
    # a parameter *usage* inside the body -> jumps back to the parameter
    # declaration in the signature (PHP has no separate "declaration" node
    # for locals, but params are their own kind - test_9 only covered a
    # plain local)
    root = LSP_TEST_DIR / "test_14"
    path = root / "main.php"
    client = LspClient(path)
    uri = client.did_open(path)

    check(
        "definition $a (param usage)",
        client.definition(uri, 4, 11),
        {
            "uri": uri,
            "range": {
                "start": {"line": 2, "character": 17},
                "end": {"line": 2, "character": 19},
            },
        },
    )
    client.close()


def test_15():
    # $obj->prop where obj is a regular variable, not $this (test_10 only
    # covered $this->prop); and clicking the property declaration itself,
    # which should resolve to itself
    root = LSP_TEST_DIR / "test_15"
    path = root / "main.php"
    client = LspClient(path)
    uri = client.did_open(path)

    check(
        "definition $p->x",
        client.definition(uri, 9, 15),
        {
            "uri": uri,
            "range": {
                "start": {"line": 4, "character": 15},
                "end": {"line": 4, "character": 17},
            },
        },
    )
    check(
        "definition of the property declaration itself",
        client.definition(uri, 4, 15),
        {
            "uri": uri,
            "range": {
                "start": {"line": 4, "character": 15},
                "end": {"line": 4, "character": 17},
            },
        },
    )
    client.close()


def test_16():
    # forward reference: the call appears before the function is declared
    # in the file - definition lookup is a name search, not position-based,
    # so this should resolve exactly like a backward reference would
    root = LSP_TEST_DIR / "test_16"
    path = root / "main.php"
    client = LspClient(path)
    uri = client.did_open(path)

    check(
        "definition double() (forward reference)",
        client.definition(uri, 2, 5),
        {
            "uri": uri,
            "range": {
                "start": {"line": 4, "character": 9},
                "end": {"line": 4, "character": 15},
            },
        },
    )
    client.close()


def test_17():
    # same-file (not cross-file, unlike test_8) method call through a
    # regular object variable - combines VAR_OBJ class resolution with
    # method lookup
    root = LSP_TEST_DIR / "test_17"
    path = root / "main.php"
    client = LspClient(path)
    uri = client.did_open(path)

    check(
        "definition $c->increment()",
        client.definition(uri, 13, 15),
        {
            "uri": uri,
            "range": {
                "start": {"line": 4, "character": 20},
                "end": {"line": 4, "character": 29},
            },
        },
    )
    client.close()


# ---------------------------------------------------------------------------
# References: the inverse of definition - given a symbol, every location
# that refers to it, not just the one it resolves to.
# ---------------------------------------------------------------------------


def test_23():
    # a plain function called from two places; requested from the
    # definition itself, with and without includeDeclaration
    root = LSP_TEST_DIR / "test_23"
    path = root / "main.php"
    client = LspClient(path)
    uri = client.did_open(path)

    def_loc = {
        "uri": uri,
        "range": {
            "start": {"line": 2, "character": 9},
            "end": {"line": 2, "character": 15},
        },
    }
    call1 = {
        "uri": uri,
        "range": {
            "start": {"line": 7, "character": 5},
            "end": {"line": 7, "character": 11},
        },
    }
    call2 = {
        "uri": uri,
        "range": {
            "start": {"line": 8, "character": 5},
            "end": {"line": 8, "character": 11},
        },
    }

    check(
        "references square() with declaration",
        client.references(uri, 2, 9, include_declaration=True),
        [def_loc, call1, call2],
    )
    check(
        "references square() without declaration",
        client.references(uri, 2, 9, include_declaration=False),
        [call1, call2],
    )
    client.close()


def test_24():
    # a property referenced both via $this (inside the class) and via an
    # external object variable (outside it) - both kinds should show up
    # for a single references request
    root = LSP_TEST_DIR / "test_24"
    path = root / "main.php"
    client = LspClient(path)
    uri = client.did_open(path)

    check(
        "references Point::$x (property + $this-> + $p->)",
        client.references(uri, 4, 15, include_declaration=True),
        [
            {
                "uri": uri,
                "range": {
                    "start": {"line": 4, "character": 15},
                    "end": {"line": 4, "character": 17},
                },
            },
            {
                "uri": uri,
                "range": {
                    "start": {"line": 8, "character": 15},
                    "end": {"line": 8, "character": 16},
                },
            },
            {
                "uri": uri,
                "range": {
                    "start": {"line": 14, "character": 15},
                    "end": {"line": 14, "character": 16},
                },
            },
        ],
    )
    client.close()


def test_25():
    # a local variable, requested from a usage (not the declaration) -
    # includeDeclaration only toggles the declaration itself, every other
    # occurrence always shows up regardless of the flag
    root = LSP_TEST_DIR / "test_25"
    path = root / "main.php"
    client = LspClient(path)
    uri = client.did_open(path)

    decl = {
        "uri": uri,
        "range": {
            "start": {"line": 4, "character": 4},
            "end": {"line": 4, "character": 8},
        },
    }
    echo_use = {
        "uri": uri,
        "range": {
            "start": {"line": 5, "character": 9},
            "end": {"line": 5, "character": 13},
        },
    }
    return_use = {
        "uri": uri,
        "range": {
            "start": {"line": 6, "character": 11},
            "end": {"line": 6, "character": 15},
        },
    }

    check(
        "references $sum from a usage, with declaration",
        client.references(uri, 5, 9, include_declaration=True),
        [decl, echo_use, return_use],
    )
    check(
        "references $sum from a usage, without declaration",
        client.references(uri, 5, 9, include_declaration=False),
        [echo_use, return_use],
    )
    client.close()


# ---------------------------------------------------------------------------
# More references cases: cross-file, empty results, querying from a call
# site instead of the definition, class/function/scope isolation, params,
# reassignment ordering, and same-line multiple occurrences.
# ---------------------------------------------------------------------------


def test_26():
    # cross-file: a method defined in Logger.php, called from A.php and
    # B.php - requested from the definition. File enumeration order isn't
    # guaranteed, so compare sorted.
    root = LSP_TEST_DIR / "test_26"
    client = LspClient(root)
    logger_uri = client.did_open(root / "Logger.php")
    a_uri = f"file://{root / 'A.php'}"
    b_uri = f"file://{root / 'B.php'}"

    expected = [
        {
            "uri": logger_uri,
            "range": {
                "start": {"line": 4, "character": 20},
                "end": {"line": 4, "character": 23},
            },
        },
        {
            "uri": a_uri,
            "range": {
                "start": {"line": 4, "character": 8},
                "end": {"line": 4, "character": 11},
            },
        },
        {
            "uri": b_uri,
            "range": {
                "start": {"line": 4, "character": 8},
                "end": {"line": 4, "character": 11},
            },
        },
    ]
    check(
        "references Logger::log() across files",
        sort_locs(client.references(logger_uri, 4, 20, include_declaration=True)),
        sort_locs(expected),
    )
    client.close()


def test_27():
    # a method with no other usages anywhere: includeDeclaration is the
    # only thing separating an empty result from a single self-reference
    root = LSP_TEST_DIR / "test_27"
    path = root / "main.php"
    client = LspClient(path)
    uri = client.did_open(path)

    check(
        "references unused() without declaration",
        client.references(uri, 4, 20, include_declaration=False),
        [],
    )
    check(
        "references unused() with declaration",
        client.references(uri, 4, 20, include_declaration=True),
        [
            {
                "uri": uri,
                "range": {
                    "start": {"line": 4, "character": 20},
                    "end": {"line": 4, "character": 26},
                },
            }
        ],
    )
    client.close()


def test_28():
    # requested from a call site instead of the definition - should
    # resolve to the same definition and the same full reference set as
    # requesting from the definition itself
    root = LSP_TEST_DIR / "test_28"
    path = root / "main.php"
    client = LspClient(path)
    uri = client.did_open(path)

    check(
        "references double() from a call site",
        client.references(uri, 7, 5, include_declaration=True),
        [
            {
                "uri": uri,
                "range": {
                    "start": {"line": 2, "character": 9},
                    "end": {"line": 2, "character": 15},
                },
            },
            {
                "uri": uri,
                "range": {
                    "start": {"line": 7, "character": 5},
                    "end": {"line": 7, "character": 11},
                },
            },
            {
                "uri": uri,
                "range": {
                    "start": {"line": 8, "character": 5},
                    "end": {"line": 8, "character": 11},
                },
            },
        ],
    )
    client.close()


def test_29():
    # two classes each with a same-named method: references on Cat::speak()
    # must not pick up Dog::speak()'s definition or call
    root = LSP_TEST_DIR / "test_29"
    path = root / "main.php"
    client = LspClient(path)
    uri = client.did_open(path)

    check(
        "references Cat::speak() (not Dog::speak())",
        client.references(uri, 4, 20, include_declaration=True),
        [
            {
                "uri": uri,
                "range": {
                    "start": {"line": 4, "character": 20},
                    "end": {"line": 4, "character": 25},
                },
            },
            {
                "uri": uri,
                "range": {
                    "start": {"line": 22, "character": 10},
                    "end": {"line": 22, "character": 15},
                },
            },
        ],
    )
    client.close()


def test_30():
    # two functions each with a same-named local $x: references on
    # first()'s $x must not pick up second()'s
    root = LSP_TEST_DIR / "test_30"
    path = root / "main.php"
    client = LspClient(path)
    uri = client.did_open(path)

    check(
        "references first()'s $x (not second()'s)",
        client.references(uri, 4, 4, include_declaration=True),
        [
            {
                "uri": uri,
                "range": {
                    "start": {"line": 4, "character": 4},
                    "end": {"line": 4, "character": 6},
                },
            },
            {
                "uri": uri,
                "range": {
                    "start": {"line": 5, "character": 11},
                    "end": {"line": 5, "character": 13},
                },
            },
        ],
    )
    client.close()


def test_31():
    # a parameter used three times in the body - requested from the
    # parameter declaration in the signature
    root = LSP_TEST_DIR / "test_31"
    path = root / "main.php"
    client = LspClient(path)
    uri = client.did_open(path)

    check(
        "references $s (param, 3 uses)",
        client.references(uri, 2, 23, include_declaration=True),
        [
            {
                "uri": uri,
                "range": {
                    "start": {"line": 2, "character": 23},
                    "end": {"line": 2, "character": 25},
                },
            },
            {
                "uri": uri,
                "range": {
                    "start": {"line": 4, "character": 9},
                    "end": {"line": 4, "character": 11},
                },
            },
            {
                "uri": uri,
                "range": {
                    "start": {"line": 5, "character": 9},
                    "end": {"line": 5, "character": 11},
                },
            },
            {
                "uri": uri,
                "range": {
                    "start": {"line": 6, "character": 11},
                    "end": {"line": 6, "character": 13},
                },
            },
        ],
    )
    client.close()


def test_32():
    # a local variable inside a class method, reassigned once (so it
    # appears twice on the same line: once read, once written)
    root = LSP_TEST_DIR / "test_32"
    path = root / "main.php"
    client = LspClient(path)
    uri = client.did_open(path)

    check(
        "references $result inside Worker::run()",
        client.references(uri, 6, 8, include_declaration=True),
        [
            {
                "uri": uri,
                "range": {
                    "start": {"line": 6, "character": 8},
                    "end": {"line": 6, "character": 15},
                },
            },
            {
                "uri": uri,
                "range": {
                    "start": {"line": 7, "character": 8},
                    "end": {"line": 7, "character": 15},
                },
            },
            {
                "uri": uri,
                "range": {
                    "start": {"line": 7, "character": 18},
                    "end": {"line": 7, "character": 25},
                },
            },
            {
                "uri": uri,
                "range": {
                    "start": {"line": 8, "character": 15},
                    "end": {"line": 8, "character": 22},
                },
            },
        ],
    )
    client.close()


def test_33():
    # a property used exactly once via $this-> - the minimal non-empty
    # reference set, and includeDeclaration's effect on a single-use case
    root = LSP_TEST_DIR / "test_33"
    path = root / "main.php"
    client = LspClient(path)
    uri = client.did_open(path)

    this_use = {
        "uri": uri,
        "range": {
            "start": {"line": 8, "character": 22},
            "end": {"line": 8, "character": 27},
        },
    }
    check(
        "references Once::$value without declaration",
        client.references(uri, 4, 15, include_declaration=False),
        [this_use],
    )
    check(
        "references Once::$value with declaration",
        client.references(uri, 4, 15, include_declaration=True),
        [
            {
                "uri": uri,
                "range": {
                    "start": {"line": 4, "character": 15},
                    "end": {"line": 4, "character": 21},
                },
            },
            this_use,
        ],
    )
    client.close()


def test_34():
    # two calls to the same function on the same line - checks that byte
    # offsets/columns stay distinct within a single line
    root = LSP_TEST_DIR / "test_34"
    path = root / "main.php"
    client = LspClient(path)
    uri = client.did_open(path)

    check(
        "references inc() (two calls, same line)",
        client.references(uri, 2, 9, include_declaration=True),
        [
            {
                "uri": uri,
                "range": {
                    "start": {"line": 2, "character": 9},
                    "end": {"line": 2, "character": 12},
                },
            },
            {
                "uri": uri,
                "range": {
                    "start": {"line": 7, "character": 5},
                    "end": {"line": 7, "character": 8},
                },
            },
            {
                "uri": uri,
                "range": {
                    "start": {"line": 7, "character": 14},
                    "end": {"line": 7, "character": 17},
                },
            },
        ],
    )
    client.close()


def test_35():
    # a global function and a class method share a name: references on the
    # global format() must not pick up Formatter::format()'s definition or
    # its $f->format() call
    root = LSP_TEST_DIR / "test_35"
    path = root / "main.php"
    client = LspClient(path)
    uri = client.did_open(path)

    check(
        "references global format() (not Formatter::format())",
        client.references(uri, 2, 9, include_declaration=True),
        [
            {
                "uri": uri,
                "range": {
                    "start": {"line": 2, "character": 9},
                    "end": {"line": 2, "character": 15},
                },
            },
            {
                "uri": uri,
                "range": {
                    "start": {"line": 17, "character": 4},
                    "end": {"line": 17, "character": 10},
                },
            },
        ],
    )
    client.close()


def test_40():
    # PHPDoc in hover: shown for a declaration and for a call site (the block
    # lives on the declaration either way), absent when there is none, and not
    # picked up from an ordinary /* */ comment
    add = func(
        "add",
        "function add(): int",
        "Adds two numbers.",
        "@param int $a",
        "@return int",
    )
    hover_cases(
        40,
        [
            (8, 9, add),
            (35, 7, add),
            (14, 9, func("plain", "function plain(): int")),
            (19, 9, func("undocumented", "function undocumented(): int")),
            (29, 20, func("Calc::double", "public function double(): int",
                          "Doubles a value.")),
        ],
    )


def test_41():
    # parameter types resolved through the file's use statements: an import, an
    # "as" alias, a reserved name that must stay bare, and a same-namespace
    # class that only resolves because the index declares it
    hover_cases(
        41,
        [
            (18, 31, php("$a: App\\NSA\\A")),
            (18, 43, php("$b: App\\NSA\\A")),
            (18, 51, php("$n: int")),
            (18, 59, php("$self: App\\Foo")),
            # the same type reached through a usage rather than the declaration
            (20, 12, php("$a: App\\NSA\\A")),
        ],
    )


def test_42():
    # the object of an access typed by a call into another file: nothing in
    # main.php says what $e is, so $c has to be typed from its parameter,
    # Container::engine() found in Container.php, and its return type read
    # off that declaration - all of it at query time, since the indexer sees
    # one file at a time
    root = LSP_TEST_DIR / "test_42"
    client = LspClient(root)
    uri = client.did_open(root / "main.php")
    container_uri = f"file://{root / 'Container.php'}"

    check("hover $e (typed by a cross-file call)", client.hover(uri, 4, 4), php("$e: Engine"))
    check(
        "hover $c->engine() (cross-file method)",
        client.hover(uri, 4, 13),
        func("Container::engine", "public function engine(): Engine"),
    )
    check(
        "hover $e->power (property of the returned class)",
        client.hover(uri, 6, 15),
        php("$power: int"),
    )
    check(
        "definition $e->power (cross-file)",
        client.definition(uri, 6, 15),
        {
            "uri": container_uri,
            "range": {
                "start": {"line": 4, "character": 15},
                "end": {"line": 4, "character": 21},
            },
        },
    )
    client.close()


def test_43():
    # a class named rather than declared: "new Square()", the ": Square"
    # return type and "implements Shape" are all references to a declaration,
    # the last of them in another file
    root = LSP_TEST_DIR / "test_43"
    client = LspClient(root)
    uri = client.did_open(root / "main.php")
    shape_uri = f"file://{root / 'Shape.php'}"

    square_decl = {
        "uri": uri,
        "range": {
            "start": {"line": 2, "character": 6},
            "end": {"line": 2, "character": 12},
        },
    }
    shape_decl = {
        "uri": shape_uri,
        "range": {
            "start": {"line": 2, "character": 10},
            "end": {"line": 2, "character": 15},
        },
    }
    implements = {
        "uri": uri,
        "range": {
            "start": {"line": 2, "character": 24},
            "end": {"line": 2, "character": 29},
        },
    }
    return_type = {
        "uri": uri,
        "range": {
            "start": {"line": 6, "character": 17},
            "end": {"line": 6, "character": 23},
        },
    }
    created = {
        "uri": uri,
        "range": {
            "start": {"line": 8, "character": 15},
            "end": {"line": 8, "character": 21},
        },
    }

    check("definition new Square()", client.definition(uri, 8, 15), square_decl)
    check("definition ': Square' return type", client.definition(uri, 6, 17), square_decl)
    check("definition implements Shape (cross-file)", client.definition(uri, 2, 24), shape_decl)
    check(
        "references Square (declaration, return type, new)",
        client.references(uri, 2, 6, include_declaration=True),
        [square_decl, return_type, created],
    )
    check(
        "references Shape across files",
        sort_locs(client.references(shape_uri, 2, 10, include_declaration=True)),
        sort_locs([shape_decl, implements]),
    )
    client.close()


TESTS = [
    test_1,
    test_2,
    test_3,
    test_4,
    test_5,
    test_6,
    test_7,
    test_8,
    test_9,
    test_10,
    test_11,
    test_13,
    test_14,
    test_15,
    test_16,
    test_17,
    test_23,
    test_24,
    test_25,
    test_26,
    test_27,
    test_28,
    test_29,
    test_30,
    test_31,
    test_32,
    test_33,
    test_34,
    test_35,
    test_40,
    test_41,
    test_42,
    test_43,
]


def run_lsp_tests():
    for t in TESTS:
        print(_c(f"Running {t.__name__}...", "36;1"))
        t()


def main():
    subprocess.run([sys.executable, "build.py", "build_all"], check=True, cwd=ROOT)

    run_lsp_tests()

    print()
    if failures:
        print(_c(f"{failures} check(s) failed", "31;1"))
        sys.exit(1)
    print(_c("all checks passed", "32;1"))


if __name__ == "__main__":
    main()
