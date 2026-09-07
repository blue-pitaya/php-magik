#!/usr/bin/env python
"""Unified test suite.

Builds the server once, then runs the LSP tests. Each test gets its own
LspClient (spawns the server, does the initialize handshake) to open
file(s) and issue the requests it's actually testing.

Fixtures live under lsp-tests/test_N/. Single-file cases are just
test_N/main.php; test_8 is the one exception with multiple files
(Foo.php + NSA/A.php + NSB/B.php), since it's specifically testing
cross-file indexing and go-to-definition.

The `test_hover_*` tests replace the old tests/test_N.php + test_N_ex.txt
CLI-dump fixtures: instead of diffing a printed index dump, they hover at
the start of each identifier the dump used to describe and check that the
resolved signature/type still matches.
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


def check(name, actual, expected):
    global failures
    if actual == expected:
        print(f"{name}: OK")
    else:
        failures += 1
        print(f"{name}: ERROR")
        print(f"  expected: {expected!r}")
        print(f"  actual:   {actual!r}")


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

    def close(self):
        self.request("shutdown")
        self.notify("exit")
        try:
            self.proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            self.proc.kill()
            self.proc.wait()


# ---------------------------------------------------------------------------
# lsp-tests/test_8: hover + definition across files (namespaces, classes)
# ---------------------------------------------------------------------------


def test_hover_method_call():
    root = LSP_TEST_DIR / "test_8"
    client = LspClient(root)
    uri = client.did_open(root / "Foo.php")

    value = client.hover(uri, 14, 18)
    check(
        "lsp:hover $a->get()",
        value,
        php("function get(): string"),
    )
    client.close()


def test_definition_method_call():
    root = LSP_TEST_DIR / "test_8"
    client = LspClient(root)
    uri = client.did_open(root / "Foo.php")

    result = client.request(
        "textDocument/definition",
        {"textDocument": {"uri": uri}, "position": {"line": 14, "character": 18}},
    )
    check(
        "lsp:definition $a->get()",
        result.get("result"),
        {
            "uri": f"file://{root / 'NSA' / 'A.php'}",
            "range": {
                "start": {"line": 6, "character": 20},
                "end": {"line": 6, "character": 23},
            },
        },
    )
    client.close()


# ---------------------------------------------------------------------------
# lsp-tests/test_N/main.php: hover at the start of every identifier the old
# tests/test_N_ex.txt dump described, checking the same signature/type.
# ---------------------------------------------------------------------------


def run_hover_cases(n: int, cases):
    path = LSP_TEST_DIR / f"test_{n}" / "main.php"
    client = LspClient(path)
    uri = client.did_open(path)
    for line, character, expected in cases:
        value = client.hover(uri, line, character)
        check(f"hover:test_{n}@{line}:{character}", value, expected)
    client.close()


def test_hover_literal_return_types():
    # test_1/main.php: return type inferred from a literal return value
    run_hover_cases(
        1,
        [
            (2, 9, php("function zero(): int")),
            (7, 9, php("function zerof(): float")),
            (12, 9, php("function empty_string(): string")),
        ],
    )


def test_hover_arithmetic_return_types():
    # test_2/main.php: return type inferred through +, *, / on literals
    run_hover_cases(
        2,
        [
            (2, 9, php("function add(): int")),
            (7, 9, php("function add2(): int")),
            (12, 9, php("function add3(): float")),
        ],
    )


def test_hover_params():
    # test_3/main.php: declared param types, and reads resolving to them
    run_hover_cases(
        3,
        [
            (2, 9, php("function add(): int")),
            (2, 17, php("$a: int")),
            (2, 25, php("$b: int")),
            (4, 11, php("$a: int")),
            (4, 16, php("$b: int")),
        ],
    )


def test_hover_local_vars():
    # test_4/main.php: types propagated through local var assignments
    run_hover_cases(
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


def test_hover_methods():
    # test_5/main.php: methods across two classes in one namespace
    run_hover_cases(
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


def test_hover_properties():
    # test_6/main.php: typed properties, $this->prop, and bare $this
    run_hover_cases(
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


def test_hover_chained_properties():
    # test_7/main.php: object-typed properties and $this->engine->prop
    # chains (the second hop, ->power/->fuel, isn't indexed - known gap)
    run_hover_cases(
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


LSP_TESTS = [
    test_hover_method_call,
    test_definition_method_call,
    test_hover_literal_return_types,
    test_hover_arithmetic_return_types,
    test_hover_params,
    test_hover_local_vars,
    test_hover_methods,
    test_hover_properties,
    test_hover_chained_properties,
]


def run_lsp_tests():
    for t in LSP_TESTS:
        t()


def main():
    subprocess.run(["make"], check=True, cwd=ROOT)

    run_lsp_tests()

    if failures:
        print(f"\n{failures} check(s) failed")
        sys.exit(1)
    print("\nall checks passed")


if __name__ == "__main__":
    main()
