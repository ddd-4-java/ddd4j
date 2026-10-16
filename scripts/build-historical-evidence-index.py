#!/usr/bin/env python3
"""Index the preserved Superpowers records from three immutable Git lines."""

import argparse
import hashlib
import json
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


LINES = ("1.0.x", "2.0.x", "3.0.x")
SOURCE_DIRS = (".superpowers", "docs/superpowers", "docs/reference/fuin-api-patterns",
               "docs/ECOSYSTEM-ARCHITECTURE.md")
INDEX_DOCUMENT = "docs/superpowers/historical-evidence-index.md"
ACTIVE_PLAN = "docs/superpowers/plans/2026-10-10-three-line-product-convergence.md"
INDEX_PATH = "verification/three-line-parity/historical-evidence-index.json"


def git(root, *args):
    return subprocess.check_output(["git", "-C", str(root), *args])


def indexed_paths(root, line):
    pom = ET.fromstring((root / "pom.xml").read_bytes())
    revision = pom.findtext("{*}properties/{*}revision")
    if not revision or not revision.startswith(line + "."):
        raise ValueError(f"{line}: root Maven revision does not match the requested line")
    paths = git(root, "ls-files", "-z", "--", *SOURCE_DIRS).split(b"\0")
    return [path.decode("utf-8") for path in sorted(path for path in paths if path)
            if path.decode("utf-8") not in (INDEX_DOCUMENT, ACTIVE_PLAN)
            and path.endswith(b".md")]


def entries_for_line(root, line):
    if git(root, "rev-parse", "--is-shallow-repository").strip() != b"false":
        raise ValueError(f"{line}: full Git history is required for source dates")
    status = git(root, "status", "--porcelain", "--untracked-files=no", "--", *SOURCE_DIRS)
    if status:
        raise ValueError(f"{line}: indexed documents have uncommitted changes")

    entries = []
    for path in indexed_paths(root, line):
        source = git(root, "log", "-1", "--format=%H%x09%aI", "--", path).decode().strip()
        if not source:
            raise ValueError(f"{line}: no source commit for {path}")
        commit, modified_at = source.split("\t", 1)
        content = git(root, "show", f"HEAD:{path}")
        entries.append({
            "line": line,
            "path": path,
            "sourceCommit": commit,
            "sourceTime": modified_at,
            "sha256": hashlib.sha256(content).hexdigest(),
        })
    return entries


def check_content(roots, output):
    if not output.is_file():
        return 1
    raw = output.read_bytes()
    if any(not (root / INDEX_PATH).is_file()
           or (root / INDEX_PATH).read_bytes() != raw for root in roots):
        return 1
    document = json.loads(raw)
    if document.get("schemaVersion") != 1 or document.get("scope") != list(SOURCE_DIRS):
        return 1
    expected = [(entry["line"], entry["path"], entry["sha256"])
                for entry in document["entries"]]
    actual = []
    for line, root in zip(LINES, roots):
        for path in indexed_paths(root, line):
            content = git(root, "show", f"HEAD:{path}")
            actual.append((line, path, hashlib.sha256(content).hexdigest()))
    if expected != actual:
        return 1
    print(f"historical evidence content verified: {len(actual)} records")
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--roots", nargs=3, type=Path, required=True, metavar="ROOT")
    parser.add_argument("--output", type=Path, required=True)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check", action="store_true")
    mode.add_argument("--check-content", action="store_true")
    args = parser.parse_args()
    roots = [root.resolve() for root in args.roots]
    if args.check_content:
        result = check_content(roots, args.output)
        if result:
            print(f"historical evidence content is stale: {args.output}", file=sys.stderr)
        return result

    entries = []
    for line, root in zip(LINES, roots):
        entries.extend(entries_for_line(root, line))
    document = {"schemaVersion": 1, "scope": list(SOURCE_DIRS),
                "entries": entries}
    rendered = json.dumps(document, ensure_ascii=False, indent=2) + "\n"
    if args.check:
        if not args.output.is_file() or args.output.read_text(encoding="utf-8") != rendered:
            print(f"historical evidence index is stale: {args.output}", file=sys.stderr)
            return 1
        print(f"historical evidence index verified: {len(entries)} records")
        return 0
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(rendered, encoding="utf-8")
    print(f"historical evidence index written: {len(entries)} records")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, subprocess.CalledProcessError, ValueError) as error:
        print(error, file=sys.stderr)
        raise SystemExit(2)
