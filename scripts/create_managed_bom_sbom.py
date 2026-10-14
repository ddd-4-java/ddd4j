#!/usr/bin/env python3
"""Create a CycloneDX inventory of every directly managed BOM release."""

import argparse
import json
from pathlib import Path
from urllib.parse import quote

from check_weekly_dependency_versions import managed_dependencies


def create_sbom(pom: Path, line: str) -> dict:
    components = {}
    for row in managed_dependencies(pom):
        group, artifact = row["coordinate"].split(":", 1)
        version = row["current"]
        identity = (group, artifact, version)
        if identity in components:
            continue
        purl = (f"pkg:maven/{quote(group, safe='.')}/{quote(artifact, safe='')}"
                f"@{quote(version, safe='.-_')}")
        components[identity] = {
            "type": "library",
            "group": group,
            "name": artifact,
            "version": version,
            "purl": purl,
            "bom-ref": purl,
        }
    if not components:
        raise ValueError("BOM has no managed dependencies")
    return {
        "bomFormat": "CycloneDX",
        "specVersion": "1.5",
        "version": 1,
        "metadata": {"component": {"type": "application", "name": "ddd4j-managed-bom", "version": line}},
        "components": list(components.values()),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pom", required=True, type=Path)
    parser.add_argument("--line", required=True, choices=("1.0.x", "2.0.x", "3.0.x"))
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    sbom = create_sbom(args.pom, args.line)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(sbom, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"CycloneDX contains {len(sbom['components'])} managed releases")


if __name__ == "__main__":
    main()
