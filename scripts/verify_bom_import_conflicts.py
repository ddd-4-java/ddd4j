#!/usr/bin/env python3
"""解析 Maven 4 BOM import 冲突并执行坐标与版本精确匹配。"""

import argparse
import csv
import re
import sys
from dataclasses import dataclass
from pathlib import Path


WARNING_PATTERN = re.compile(
    r"Ignored POM import for: "
    r"(?P<group>[^:]+):(?P<artifact>[^:]+):(?P<type>[^:]+):(?P<ignored>[^@ ]+)@\S+ "
    r"as already imported "
    r"(?P=group):(?P=artifact):(?P=type):(?P<current>[^@ ]+)@\S+"
)


@dataclass(frozen=True, order=True)
class Conflict:
    group_id: str
    artifact_id: str
    current_version: str
    ignored_version: str


def parse_conflicts(path):
    conflicts = set()
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        match = WARNING_PATTERN.search(line)
        if match:
            conflicts.add(Conflict(
                match.group("group"),
                match.group("artifact"),
                match.group("current"),
                match.group("ignored"),
            ))
    return conflicts


def load_allowlist(path):
    with Path(path).open(encoding="utf-8", newline="") as source:
        rows = csv.DictReader(source, delimiter="\t")
        return {
            Conflict(row["group_id"], row["artifact_id"], row["current_version"],
                     row["ignored_version"])
            for row in rows
        }


def verify(log_path, allowlist_path):
    conflicts = parse_conflicts(log_path)
    allowed = load_allowlist(allowlist_path)
    errors = [f"unlisted BOM conflict: {conflict}" for conflict in sorted(conflicts - allowed)]
    errors.extend(f"stale allowlist entry: {conflict}" for conflict in sorted(allowed - conflicts))
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    parser.add_argument("--allowlist", type=Path, required=True)
    args = parser.parse_args()
    errors = verify(args.log, args.allowlist)
    if errors:
        print("\n".join(errors))
        return 1
    print(f"PASS: {len(parse_conflicts(args.log))} exact BOM conflict tuples")
    return 0


if __name__ == "__main__":
    sys.exit(main())
