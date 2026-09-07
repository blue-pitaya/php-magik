#!/usr/bin/env python

import json
import subprocess
from pathlib import Path
from typing import IO

ROOT = Path(__file__).resolve().parent
BINARY = ROOT / "target" / "main"
TEST_FILE = ROOT / "lsp-tests" / "test_1" / "Foo.php"


def lsp_message(obj) -> bytes:
    body = json.dumps(obj).encode("utf-8")
    header = f"Content-Length: {len(body)}\r\n\r\n".encode("ascii")
    return header + body


def read_message(stdout: IO[bytes]):
    header = b""
    while not header.endswith(b"\r\n\r\n"):
        header += stdout.read(1)
    length = int(header.split(b":")[1])
    return json.loads(stdout.read(length))


def main():
    subprocess.run(["make"], check=True, cwd=ROOT)

    proc = subprocess.Popen(
        [str(BINARY), "--path", str(TEST_FILE.parent)],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
    )
    if proc.stdin is None or proc.stdout is None:
        raise RuntimeError("subprocess did not open stdin/stdout pipes")
    stdin: IO[bytes] = proc.stdin
    stdout: IO[bytes] = proc.stdout

    # handshake
    stdin.write(
        lsp_message(
            {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "initialize",
                "params": {},
            }
        )
    )
    stdin.flush()
    print("initialize ->", read_message(stdout))

    stdin.write(
        lsp_message(
            {
                "jsonrpc": "2.0",
                "method": "initialized",
                "params": {},
            }
        )
    )
    stdin.flush()

    # open the file
    uri = f"file://{TEST_FILE}"
    stdin.write(
        lsp_message(
            {
                "jsonrpc": "2.0",
                "method": "textDocument/didOpen",
                "params": {
                    "textDocument": {
                        "uri": uri,
                        "languageId": "php",
                        "version": 1,
                        "text": TEST_FILE.read_text(),
                    }
                },
            }
        )
    )
    stdin.flush()

    # hover on `$a->get()`
    stdin.write(
        lsp_message(
            {
                "jsonrpc": "2.0",
                "id": 2,
                "method": "textDocument/hover",
                "params": {
                    "textDocument": {"uri": uri},
                    "position": {"line": 14, "character": 18},
                },
            }
        )
    )
    stdin.flush()
    print("hover ->", read_message(stdout))

    # shutdown
    stdin.write(
        lsp_message(
            {
                "jsonrpc": "2.0",
                "id": 3,
                "method": "shutdown",
                "params": {},
            }
        )
    )
    stdin.flush()
    print("shutdown ->", read_message(stdout))

    stdin.write(
        lsp_message(
            {
                "jsonrpc": "2.0",
                "method": "exit",
                "params": {},
            }
        )
    )
    stdin.flush()
    try:
        proc.wait(timeout=5)
    except subprocess.TimeoutExpired:
        print("server did not exit after 'exit' notification, killing it")
        proc.kill()
        proc.wait()


if __name__ == "__main__":
    main()
