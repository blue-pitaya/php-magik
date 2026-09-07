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
"""

import json
import subprocess
import sys
from pathlib import Path
from typing import IO

ROOT = Path(__file__).resolve().parent
BINARY = ROOT / "target" / "main"
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
            [str(BINARY), "--path", str(root)],
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
            header += self.stdout.read(1)
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

    def definition(self, uri: str, line: int, character: int):
        result = self.request(
            "textDocument/definition",
            {
                "textDocument": {"uri": uri},
                "position": {"line": line, "character": character},
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
            (2, 9, php("function zero(): int")),
            (7, 9, php("function zerof(): float")),
            (12, 9, php("function empty_string(): string")),
        ],
    )


def test_2():
    # return type inferred through +, *, / on literals
    hover_cases(
        2,
        [
            (2, 9, php("function add(): int")),
            (7, 9, php("function add2(): int")),
            (12, 9, php("function add3(): float")),
        ],
    )


def test_3():
    # declared param types, and reads resolving to them
    hover_cases(
        3,
        [
            (2, 9, php("function add(): int")),
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
            (2, 9, php("function add(): int")),
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
            (6, 20, php("function print(): int")),
            (13, 20, php("function bar(): string")),
            (21, 21, php("function ok(): float")),
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
            (12, 20, php("function print(): int")),
            (21, 20, php("function xd(): string")),
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
    # object-typed properties and $this->engine->prop chains (the second
    # hop, ->power/->fuel, isn't indexed - known gap)
    hover_cases(
        7,
        [
            (13, 20, php("function __construct()")),
            (18, 20, php("function describe(): string")),
            (4, 15, php("$power: int")),
            (6, 18, php("$fuel: string")),
            (11, 19, php("$engine: Engine")),
            (13, 39, php("$engine: Engine")),
            (15, 8, php("$this")),
            (15, 15, php("$engine: Engine")),
            (15, 24, php("$engine: Engine")),
            (20, 8, php("$total")),
            (20, 17, php("$this")),
            (20, 24, php("$engine: Engine")),
            (22, 15, php("$this")),
            (22, 22, php("$engine: Engine")),
            (22, 39, php("$total")),
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
        php("function get(): string"),
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
]


def run_lsp_tests():
    for t in TESTS:
        print(_c(f"Running {t.__name__}...", "36;1"))
        t()


def main():
    subprocess.run(["make"], check=True, cwd=ROOT)

    run_lsp_tests()

    print()
    if failures:
        print(_c(f"{failures} check(s) failed", "31;1"))
        sys.exit(1)
    print(_c("all checks passed", "32;1"))


if __name__ == "__main__":
    main()
