#!/usr/bin/env python3

import argparse
import json
import os
import shutil
import subprocess
from pathlib import Path

JNI_FLAGS = ["-O2", "-fPIC", "-Wall", "-Wextra"]


class Paths:
    def __init__(self, root: Path):
        self.root = root
        self.target = root / "target"
        self.headers = self.target / "headers"
        self.classes = self.target / "classes"
        self.dependency = self.target / "dependency"
        self.native = self.target / "native"
        self.objects = self.native / "obj"
        self.library = self.native / "libtsjni.so"
        self.compile_commands = root / "compile_commands.json"
        self._tree_sitter = root / ".old-project" / "tree-sitter"
        self._tree_sitter_php = root / ".old-project" / "tree-sitter-php"

    def classpath(self) -> str:
        """Our classes plus the jars mvn package copied next to them. The
        trailing /* is expanded by the JVM itself, not the shell."""
        return os.pathsep.join([str(self.classes), f"{self.dependency}/*"])

    def _require(self, path: Path, hint: str | None = None):
        if path.exists():
            return path
        raise RuntimeError(f"missing {path}" + (f" ({hint})" if hint else ""))

    def pom(self):
        return self._require(self.root / "pom.xml", "not a Maven project")

    def jni_sources(self):
        native = self.root / "src/main/c"
        return [
            self._require(native / "tsjni.c"),
            self._require(native / "tsjni_node.c"),
            self._require(native / "tsjni_tree.c"),
            self._require(native / "tsjni_parser.c"),
        ]

    def jni_include(self):
        return self._require(self.root / "src/main/c/tsjni.h")

    def jni_header(self):
        return self._require(
            self.headers / "dev_bluepitaya_phpmagik_ts_Node.h",
            "run ./build.py build_java first",
        )

    def jdk(self):
        if "JAVA_HOME" not in os.environ:
            raise RuntimeError("JAVA_HOME must be set")
        jdk = Path(os.environ["JAVA_HOME"])
        self._require(jdk / "include/jni.h", "JAVA_HOME must point at a JDK, not a JRE")
        return jdk

    def vendor_sources(self):
        return [
            self._require(self._tree_sitter / "lib/src/lib.c", "no c-project checkout"),
            self._require(self._tree_sitter_php / "php_only/src/parser.c"),
            self._require(self._tree_sitter_php / "php_only/src/scanner.c"),
        ]

    def include_dirs(self):
        jdk = self.jdk()
        return [
            jdk / "include",
            jdk / "include/linux",
            self.jni_header().parent,
            self._require(self._tree_sitter / "lib/include"),
            self._require(self._tree_sitter_php / "php_only/src"),
        ]


class Runner:
    def __init__(self, paths: Paths):
        self.paths = paths

    def run(self, *command: str | Path, cwd: Path | None = None):
        printable = " ".join(str(part) for part in command)
        print(f"+ {printable}")
        subprocess.run([str(part) for part in command], cwd=cwd, check=True)

    def compile_object(
        self,
        source: Path,
        flags: list[str],
        include_flags: list[str],
        dependencies: list[Path],
    ):
        obj = self.paths.objects / (source.stem + ".o")
        newest = max(path.stat().st_mtime for path in [source, *dependencies])
        if obj.is_file() and obj.stat().st_mtime >= newest:
            print(f"  up to date: {obj.name}")
            return obj
        self.run("cc", *flags, *include_flags, "-c", source, "-o", obj)
        return obj

    def build_java(self):
        # javac -h will not create it
        self.paths.headers.mkdir(parents=True, exist_ok=True)
        self.run("mvn", "package", cwd=self.paths.pom().parent)

    def build_native(self):
        self.paths.objects.mkdir(parents=True, exist_ok=True)
        include_flags = [f"-I{path}" for path in self.paths.include_dirs()]
        dependencies = [self.paths.jni_include(), self.paths.jni_header()]
        objects = [
            self.compile_object(source, ["-O2", "-fPIC"], include_flags, [])
            for source in self.paths.vendor_sources()
        ]
        objects += [
            self.compile_object(source, JNI_FLAGS, include_flags, dependencies)
            for source in self.paths.jni_sources()
        ]
        self.run("cc", "-shared", "-o", self.paths.library, *objects)
        print(f"built {self.paths.library}")

    def compile_commands(self):
        """Compilation database for clangd; without it the editor cannot find
        jni.h, tree_sitter/api.h or the javac-generated headers."""
        include_flags = [f"-I{path}" for path in self.paths.include_dirs()]
        entries = [
            {
                "directory": str(self.paths.root),
                "file": str(source),
                "arguments": [
                    "cc",
                    *JNI_FLAGS,
                    *include_flags,
                    "-c",
                    str(source),
                    "-o",
                    str(self.paths.objects / (source.stem + ".o")),
                ],
            }
            for source in self.paths.jni_sources()
        ]
        self.paths.compile_commands.write_text(json.dumps(entries, indent=2) + "\n")
        print(f"wrote {self.paths.compile_commands}")

    def clean(self):
        shutil.rmtree(self.paths.target, ignore_errors=True)
        print(f"removed {self.paths.target}")

    def build_all(self):
        self.build_java()
        self.build_native()

    def serve(self, path: str):
        self.build_all()
        self.run(
            "java",
            f"-Djava.library.path={self.paths.native}",
            "-cp",
            self.paths.classpath(),
            "dev.bluepitaya.phpmagik.Main",
            "--path",
            path,
        )


def main():
    runner = Runner(Paths(Path(__file__).resolve().parent))
    cmds = {
        "clean": runner.clean,
        "build_java": runner.build_java,
        "build_native": runner.build_native,
        "build_all": runner.build_all,
        "compile_commands": runner.compile_commands,
        "serve": runner.serve,
    }
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=list(cmds))
    parser.add_argument(
        "path",
        nargs="?",
        help="project root to index; required by serve",
    )
    args = parser.parse_args()
    if args.command == "serve":
        if args.path is None:
            parser.error("serve needs a path to index")
        runner.serve(args.path)
    else:
        cmds[args.command]()


if __name__ == "__main__":
    main()
