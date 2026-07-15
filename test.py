#!/usr/bin/env python

from pathlib import Path
import subprocess

if __name__ == "__main__":
    script_dir = Path(__file__).resolve().parent
    test_dir = script_dir / "tests"
    binary = script_dir / "target" / "main"
    tests = sorted(test_dir.glob("test_*.php"))
    exs = sorted(test_dir.glob("test_*_ex.txt"))
    subprocess.run(["make"], check=True, cwd=script_dir)
    for test, ex in zip(tests, exs):
        result = subprocess.run([binary, '--scan-file', test], capture_output=True, text=True).stdout
        expected = ex.read_text()
        if result == expected:
            print(f"{test}: OK")
        else:
            print(f"{test}: ERROR")
            print("Expected:")
            print(expected)
            print("Actual:")
            print(result)
            print("--------")
