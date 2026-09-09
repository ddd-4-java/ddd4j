#!/usr/bin/env python3
"""解析 Maven 4 BOM import 冲突并执行坐标与版本精确匹配。"""

import argparse
import csv
import re
import sys
import xml.etree.ElementTree as ET
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
                     row["ignored_version"]): row
            for row in rows
        }


def managed_versions(path):
    root = ET.parse(path).getroot()
    namespace = {"m": root.tag.split("}")[0][1:]}
    return {
        (
            dependency.findtext("m:groupId", default="", namespaces=namespace),
            dependency.findtext("m:artifactId", default="", namespaces=namespace),
        ): dependency.findtext("m:version", default="", namespaces=namespace)
        for dependency in root.findall(
            "m:dependencyManagement/m:dependencies/m:dependency", namespace
        )
    }


def verify(log_path, allowlist_path, effective_pom=None):
    conflicts = parse_conflicts(log_path)
    rows = load_allowlist(allowlist_path)
    allowed = set(rows)
    errors = [f"unlisted BOM conflict: {conflict}" for conflict in sorted(conflicts - allowed)]
    errors.extend(f"stale allowlist entry: {conflict}" for conflict in sorted(allowed - conflicts))
    if effective_pom:
        effective = managed_versions(effective_pom)
        for conflict in sorted(conflicts & allowed):
            row = rows[conflict]
            actual = effective.get((conflict.group_id, conflict.artifact_id))
            if row["final_version"] != actual:
                errors.append(
                    f"{conflict.group_id}:{conflict.artifact_id} final version "
                    f"{row['final_version']} does not match effective {actual}"
                )
            if row["authority"] != "ddd4j-dependencies":
                errors.append(f"{conflict.group_id}:{conflict.artifact_id} authority must be ddd4j-dependencies")
            if not row["reason"].strip():
                errors.append(f"{conflict.group_id}:{conflict.artifact_id} reason must not be empty")
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    parser.add_argument("--allowlist", type=Path, required=True)
    parser.add_argument("--effective-pom", type=Path)
    args = parser.parse_args()
    errors = verify(args.log, args.allowlist, args.effective_pom)
    if errors:
        print("\n".join(errors))
        return 1
    print(f"PASS: {len(parse_conflicts(args.log))} exact BOM conflict tuples")
    return 0


if __name__ == "__main__":
    sys.exit(main())
