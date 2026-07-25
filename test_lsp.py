#!/usr/bin/env python
import json
import subprocess
from pathlib import Path
import argparse

BINARY = Path(__file__).resolve().parent / "target" / "main"
TESTS = {
    1: {
        "steps": [
            ("textDocument/hover", "Foo.php", {"line": 14, "character": 18}, "get"),
        ],
    },
}


def lsp_msg(obj):
    body = json.dumps(obj)
    return f"Content-Length: {len(body)}\r\n\r\n{body}"


def lsp_request(id, method, params=None):
    return lsp_msg(
        {"jsonrpc": "2.0", "id": id, "method": method, "params": params or {}}
    )


def lsp_notify(method, params=None):
    return lsp_msg({"jsonrpc": "2.0", "method": method, "params": params or {}})


def lsp_method_did_open(uri, text):
    return (
        "textDocument/didOpen",
        {"textDocument": {"uri": uri, "languageId": "php", "version": 1, "text": text}},
    )


class Session:
    def __init__(self, root: Path):
        self._id = 0
        self._proc = subprocess.Popen(
            [BINARY, "--path", str(root)],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            text=True,
        )
        self.send("initialize")
        self.notify("initialized")

    def _next_id(self):
        self._id += 1
        return self._id

    def notify(self, method, params=None):
        msg = lsp_notify(method, params)
        if not self._proc.stdin:
            raise Exception
        self._proc.stdin.write(msg)
        self._proc.stdin.flush()

    def send(self, method, params=None):
        msg = lsp_request(self._next_id(), method, params)
        if not self._proc.stdin:
            raise Exception
        self._proc.stdin.write(msg)
        self._proc.stdin.flush()
        header = ""
        if not self._proc.stdout:
            raise Exception
        while not header.endswith("\r\n\r\n"):
            header += self._proc.stdout.read(1)
        length = int(header.split(":")[1])
        body = self._proc.stdout.read(length)
        return json.loads(body)

    def close(self):
        self.send("shutdown")
        self.notify("exit")
        self._proc.wait()


def check(name, results, expected):
    for i, (r, e) in enumerate(zip(results, expected)):
        result = r.get("result")
        if result == e:
            print(f"{name}[{i}]: OK")
        else:
            print(f"{name}[{i}]: ERROR")
            print(f"  expected: {json.dumps(e)}")
            print(f"  actual:   {json.dumps(result)}")


# --- tests ---


def run_test(n):
    t = TESTS[n]
    root = Path(__file__).resolve().parent / "lsp-tests" / f"test_{n}"
    s = Session(root)

    opened = set()
    results = []
    expected = []
    for method, file, pos, exp in t["steps"]:
        uri = f"file://{root / file}"
        if file not in opened:
            s.notify(*lsp_method_did_open(uri, (root / file).read_text()))
            opened.add(file)
        results.append(
            s.send(
                method,
                {
                    "textDocument": {"uri": uri},
                    "position": pos,
                },
            )
        )
        expected.append(exp)
    s.close()
    check(f"test_{n}", results, expected)


if __name__ == "__main__":
    subprocess.run(["make"], check=True, cwd=Path(__file__).resolve().parent)
    parser = argparse.ArgumentParser()
    parser.add_argument("test", type=int)
    args = parser.parse_args()
    run_test(args.test)
