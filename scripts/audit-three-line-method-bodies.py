#!/usr/bin/env python3
"""Compare method bodies from the three current source trees without an index."""

import argparse
import base64
import hashlib
import json
import os
import re
import subprocess
import tempfile
from pathlib import Path


SCANNER = Path(__file__).resolve().parent.parent / "verification/three-line-parity/ThreeLineMethodBodyScanner.java"


def lexical(source):
    """Discard comments and insignificant whitespace, preserving string values."""
    tokens = []
    position = 0
    while position < len(source):
        char = source[position]
        if char.isspace():
            position += 1
            continue
        if source.startswith("//", position):
            newline = source.find("\n", position + 2)
            position = len(source) if newline < 0 else newline + 1
            continue
        if source.startswith("/*", position):
            end = source.find("*/", position + 2)
            if end < 0:
                raise ValueError("unterminated block comment")
            position = end + 2
            continue
        if source.startswith('"""', position):
            end = source.find('"""', position + 3)
            if end < 0:
                raise ValueError("unterminated text block")
            tokens.append(source[position:end + 3])
            position = end + 3
            continue
        if char in ('"', "'"):
            end = position + 1
            while end < len(source):
                if source[end] == "\\":
                    end += 2
                elif source[end] == char:
                    end += 1
                    break
                else:
                    end += 1
            else:
                raise ValueError("unterminated quoted literal")
            tokens.append(source[position:end])
            position = end
            continue
        match = re.match(r"[A-Za-z_$][A-Za-z0-9_$]*|\d+(?:\.\d+)?(?:[A-Za-z]+)?|.", source[position:], re.S)
        tokens.append(match.group(0))
        position += len(match.group(0))
    return " ".join(tokens)


def source_files(root):
    """Include tracked and newly added production sources, but not build outputs."""
    paths = []
    excluded = {".git", ".codegraph", ".idea", "target", "build", "node_modules"}
    for directory, children, files in os.walk(root):
        children[:] = [child for child in children if child not in excluded]
        current = Path(directory)
        if current.parts[-3:] == ("src", "main", "java"):
            for nested, _, names in os.walk(current):
                paths.extend(
                    (Path(nested) / name).relative_to(root).as_posix()
                    for name in names if name.endswith(".java")
                )
            children[:] = []
    return sorted(paths)


def source_fingerprint(root):
    digest = hashlib.sha256()
    for relative in source_files(root):
        digest.update(relative.encode("utf-8"))
        digest.update(b"\0")
        digest.update((root / relative).read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def git_head(root):
    result = subprocess.run(["git", "-C", str(root), "rev-parse", "HEAD"],
                            capture_output=True, text=True)
    return result.stdout.strip() if result.returncode == 0 else None


def snapshot(root, java, classpath):
    data = {}
    files = source_files(root)
    for offset in range(0, len(files), 64):
        batch = files[offset:offset + 64]
        result = subprocess.run(
            [java, "-cp", str(classpath), "ThreeLineMethodBodyScanner", str(root)],
            input="\n".join(batch) + "\n", capture_output=True, text=True, check=True,
        )
        for line in result.stdout.splitlines():
            path, symbol, start, end, encoded = line.split("\t", 4)
            source = base64.b64decode(encoded).decode("utf-8")
            key = path + "|" + symbol
            data.setdefault(key, []).append({
                "start": int(start), "end": int(end), "normalized": lexical(source), "source": source,
            })
    return data


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--roots", nargs=3, required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--parser-jdk", type=Path, help="JDK 21+ used only to parse all three source lines")
    args = parser.parse_args()
    roots = {str(index): root.resolve() for index, root in enumerate(args.roots, 1)}
    java = str(args.parser_jdk / "bin/java") if args.parser_jdk else "java"
    javac = str(args.parser_jdk / "bin/javac") if args.parser_jdk else "javac"
    before = {key: source_fingerprint(root) for key, root in roots.items()}

    with tempfile.TemporaryDirectory(prefix="ddd4j-method-audit-") as temporary:
        subprocess.run([javac, "-encoding", "UTF-8", "-d", temporary, str(SCANNER)], check=True)
        snapshots = {key: snapshot(root, java, temporary) for key, root in roots.items()}
    after = {key: source_fingerprint(root) for key, root in roots.items()}

    report = {
        "schemaVersion": 2,
        "roots": {key: str(root) for key, root in roots.items()},
        "gitHeads": {key: git_head(root) for key, root in roots.items()},
        "scriptSha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "scannerSha256": hashlib.sha256(SCANNER.read_bytes()).hexdigest(),
        "scope": "Named Java method and constructor bodies in src/main/java; excludes anonymous-class methods, initializers, and semantic equivalence decisions",
        "inputFingerprints": before,
        "inputStable": before == after,
        "snapshots": {
            key: {"methods": sum(len(values) for values in data.values()), "keys": len(data)}
            for key, data in snapshots.items()
        },
        "pairs": {},
    }
    for left, right in (("1", "2"), ("2", "3"), ("1", "3")):
        first, second = snapshots[left], snapshots[right]
        common = first.keys() & second.keys()
        mismatches = []
        ambiguous = []
        for key in sorted(common):
            left_values, right_values = first[key], second[key]
            if len(left_values) != 1 or len(right_values) != 1:
                ambiguous.append({"key": key, "left_count": len(left_values), "right_count": len(right_values)})
                continue
            if left_values[0]["normalized"] != right_values[0]["normalized"]:
                mismatches.append({
                    "key": key,
                    "left": {name: left_values[0][name] for name in ("start", "end", "source")},
                    "right": {name: right_values[0][name] for name in ("start", "end", "source")},
                })
        report["pairs"][left + "-" + right] = {
            "common_keys": len(common), "only_left": len(first.keys() - second.keys()),
            "only_right": len(second.keys() - first.keys()),
            "only_left_keys": sorted(first.keys() - second.keys()),
            "only_right_keys": sorted(second.keys() - first.keys()),
            "ambiguous": ambiguous, "body_mismatches": mismatches,
        }
        print(f"{left}-{right}: common={len(common)} only={len(first.keys() - second.keys())}/"
              f"{len(second.keys() - first.keys())} ambiguous={len(ambiguous)} "
              f"body_mismatches={len(mismatches)}")
    args.output.mkdir(parents=True, exist_ok=True)
    output = args.output / "method-body-report.json"
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(output)
    if not report["inputStable"]:
        raise SystemExit("source input changed during audit")


if __name__ == "__main__":
    main()
