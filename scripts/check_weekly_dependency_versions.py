#!/usr/bin/env python3
"""Check managed dependency versions against Maven Central release metadata."""

from __future__ import annotations

import argparse
import fnmatch
import json
import re
import sys
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from urllib.parse import quote


PROPERTY = re.compile(r"\$\{([^}]+)}")
PRERELEASE = re.compile(r"(?i)(?:^|[._-])(alpha|beta|rc|cr|m\d+|preview|snapshot|ea\d*)(?:$|[._-])")


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def child_text(element: ET.Element, name: str) -> str | None:
    for child in element:
        if local_name(child.tag) == name:
            return (child.text or "").strip()
    return None


def resolve_version(value: str, properties: dict[str, str]) -> str:
    for _ in range(12):
        updated = PROPERTY.sub(lambda match: properties.get(match.group(1), match.group(0)), value)
        if updated == value:
            break
        value = updated
    if PROPERTY.search(value):
        raise ValueError(f"unresolved version property: {value}")
    return value


def pom_properties(pom: Path, depth: int = 0) -> dict[str, str]:
    if depth > 8:
        raise ValueError("POM parent chain is too deep")
    root = ET.parse(pom).getroot()
    namespace = root.tag.split("}", 1)[0] + "}" if root.tag.startswith("{") else ""
    properties = {}
    parent = root.find(f"{namespace}parent")
    if parent is not None:
        relative = child_text(parent, "relativePath") or "../pom.xml"
        parent_path = (pom.parent / relative).resolve()
        if parent_path.is_dir():
            parent_path /= "pom.xml"
        if parent_path.is_file():
            properties.update(pom_properties(parent_path, depth + 1))
    properties_node = root.find(f"{namespace}properties")
    properties.update({
        local_name(item.tag): (item.text or "").strip()
        for item in (properties_node if properties_node is not None else [])
    })
    return properties


def managed_dependencies(pom: Path) -> list[dict[str, str]]:
    root = ET.parse(pom).getroot()
    namespace = root.tag.split("}", 1)[0] + "}" if root.tag.startswith("{") else ""
    properties = pom_properties(pom)
    dependencies = root.findall(
        f"{namespace}dependencyManagement/{namespace}dependencies/{namespace}dependency"
    )
    result = []
    for dependency in dependencies:
        group = child_text(dependency, "groupId")
        artifact = child_text(dependency, "artifactId")
        raw_version = child_text(dependency, "version")
        if not group or not artifact:
            raise ValueError("dependencyManagement contains a dependency without groupId/artifactId")
        if raw_version is None:
            raise ValueError(f"{group}:{artifact} has no managed version")
        result.append({
            "coordinate": f"{group}:{artifact}",
            "type": child_text(dependency, "type") or "jar",
            "classifier": child_text(dependency, "classifier") or "",
            "current": resolve_version(raw_version, properties),
            "rawVersion": raw_version,
        })
    return result


def matching_rule(rules: list[dict], coordinate: str, current: str) -> dict | None:
    for rule in rules:
        if fnmatch.fnmatchcase(coordinate, rule["pattern"]) and (
            "versionRegex" not in rule or re.fullmatch(rule["versionRegex"], current)
        ):
            return rule
    return None


def latest_stable_version(coordinate: str, repository: str, timeout: float) -> str:
    group, artifact = coordinate.split(":", 1)
    path = "/".join(quote(part, safe="") for part in group.split("."))
    url = f"{repository.rstrip('/')}/{path}/{quote(artifact, safe='')}/maven-metadata.xml"
    request = urllib.request.Request(url, headers={"User-Agent": "ddd4j-weekly-dependency-check/1"})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                data = response.read(2_000_001)
            break
        except urllib.error.HTTPError as error:
            if error.code not in (429, 500, 502, 503, 504) or attempt == 2:
                raise
        except (urllib.error.URLError, TimeoutError):
            if attempt == 2:
                raise
        time.sleep(attempt + 1)
    if len(data) > 2_000_000:
        raise ValueError("Maven metadata is too large")
    root = ET.fromstring(data)
    release = root.findtext("./versioning/release")
    if release and not PRERELEASE.search(release):
        return release
    versions = [node.text for node in root.findall("./versioning/versions/version") if node.text]
    stable = [version for version in versions if not PRERELEASE.search(version)]
    if stable:
        return stable[-1]
    if versions:
        return versions[-1]
    if release:
        return release
    raise ValueError("Maven metadata has no release")


def check(pom: Path, policy: dict, line: str, repository: str, workers: int, timeout: float) -> dict:
    rows = managed_dependencies(pom)
    fixed = policy["fixed"]
    excluded = policy["excluded"]
    seen = set()
    pending = {}
    for row in rows:
        coordinate = row["coordinate"]
        identity = (coordinate, row["type"], row["classifier"])
        if identity in seen:
            raise ValueError(f"duplicate managed coordinate: {identity}")
        seen.add(identity)
        rule = matching_rule(fixed, coordinate, row["current"])
        if rule:
            expected = rule["versions"][line]
            row.update(status="fixed" if row["current"] == expected else "fixed-drift",
                       expected=expected, reason=rule["reason"])
            continue
        rule = matching_rule(excluded, coordinate, row["current"])
        if rule:
            row.update(status="excluded", reason=rule["reason"])
            continue
        pending.setdefault(coordinate, []).append(row)

    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = {
            pool.submit(latest_stable_version, coordinate, repository, timeout): coordinate
            for coordinate in pending
        }
        for future in as_completed(futures):
            matching_rows = pending[futures[future]]
            try:
                latest = future.result()
                for row in matching_rows:
                    row.update(status="current" if row["current"] == latest else "outdated",
                               latest=latest)
            except (urllib.error.URLError, ET.ParseError, ValueError, TimeoutError) as error:
                for row in matching_rows:
                    row.update(status="lookup-error", reason=str(error))

    counts = {}
    for row in rows:
        counts[row["status"]] = counts.get(row["status"], 0) + 1
    return {
        "schemaVersion": 1,
        "line": line,
        "bom": str(pom),
        "repository": repository,
        "status": "PASS" if not any(row["status"] in
                                    ("outdated", "fixed-drift", "lookup-error") for row in rows) else "FAIL",
        "counts": counts,
        "dependencies": rows,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pom", required=True, type=Path)
    parser.add_argument("--policy", required=True, type=Path)
    parser.add_argument("--line", required=True, choices=("1.0.x", "2.0.x", "3.0.x"))
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--workers", type=int, default=12)
    parser.add_argument("--timeout", type=float, default=15.0)
    args = parser.parse_args()
    if args.workers < 1 or args.timeout <= 0:
        parser.error("workers and timeout must be positive")
    policy = json.loads(args.policy.read_text(encoding="utf-8"))
    report = check(args.pom, policy, args.line, policy["repository"], args.workers, args.timeout)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{args.line}: {report['status']} {report['counts']} report={args.output}")
    for row in report["dependencies"]:
        if row["status"] in ("outdated", "fixed-drift", "lookup-error"):
            print(f"  {row['status']}: {row['coordinate']} {row['current']} -> "
                  f"{row.get('latest', row.get('expected', row.get('reason')))}")
    return 0 if report["status"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
