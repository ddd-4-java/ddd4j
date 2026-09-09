#!/usr/bin/env python3
"""Verify the ddd4j-owned Maven 4 effective-model boundary."""

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


DDD4J_WARNING = re.compile(r"effective model for 'io\.ddd4j:")


def has_imported_bom(source_pom):
    root = ET.parse(source_pom).getroot()
    namespace_uri = root.tag.split("}")[0][1:] if "}" in root.tag else ""
    prefix = "{%s}" % namespace_uri if namespace_uri else ""
    for dependency in root.iter(prefix + "dependency"):
        dependency_type = dependency.findtext(prefix + "type", default="").strip()
        scope = dependency.findtext(prefix + "scope", default="").strip()
        if dependency_type == "pom" and scope == "import":
            return True
    return False


def verify(log_path, source_pom):
    errors = []
    log = Path(log_path).read_text(encoding="utf-8")
    if DDD4J_WARNING.search(log):
        errors.append("ddd4j effective-model warning")
    if has_imported_bom(source_pom):
        errors.append("platform dependencyManagement still imports a BOM")
    if "BUILD SUCCESS" not in log:
        errors.append("Maven validation did not finish successfully")
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    parser.add_argument("--source-pom", type=Path, required=True)
    args = parser.parse_args()
    errors = verify(args.log, args.source_pom)
    if errors:
        print("\n".join(errors))
        return 1
    print("PASS: ddd4j Maven 4 model has no owned warnings or imported BOMs")
    return 0


if __name__ == "__main__":
    sys.exit(main())
