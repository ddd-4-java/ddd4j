#!/usr/bin/env python3
"""Require the three JPA EventStore MySQL container cases to execute in CI."""

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


REPORT = Path("ddd4j-data/ddd4j-data-event-store-jpa/target/surefire-reports/") / (
    "TEST-io.ddd4j.data.event.store.jpa.JpaEventStoreMysqlIT.xml"
)


def verify(root: Path) -> str | None:
    """Return a diagnostic when the MySQL integration report is incomplete."""
    path = root / REPORT
    if not path.is_file():
        return f"missing JPA MySQL test report: {path}"
    try:
        suite = ET.parse(path).getroot()
        if suite.tag != "testsuite":
            raise ValueError("root element is not testsuite")
        counts = {name: int(suite.attrib[name]) for name in
                  ("tests", "skipped", "errors", "failures")}
    except (ET.ParseError, OSError, KeyError, ValueError) as error:
        return f"invalid JPA MySQL test report: {error}"
    if counts["tests"] < 3 or any(counts[name] != 0 for name in
                                    ("skipped", "errors", "failures")):
        return f"JPA MySQL cases were not fully executed: {counts}"
    print(f"PASS: JpaEventStoreMysqlIT ({counts['tests']} tests)")
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    args = parser.parse_args()
    problem = verify(args.root)
    if problem:
        print(f"FAIL: {problem}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
