#!/usr/bin/env python3
"""解析 Maven 许可证清单并执行有证据的许可证选择策略。"""

import argparse
import csv
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlparse


ALLOWED_SPDX = {
    "Apache-2.0", "MIT", "MIT-0", "BSD-2-Clause", "BSD-3-Clause", "ISC",
    "MulanPSL-2.0", "UPL-1.0", "W3C", "Bouncy-Castle", "CC0-1.0",
    "Public-Domain", "EPL-1.0", "EPL-2.0", "MPL-1.1", "MPL-2.0",
    "CDDL-1.0", "CDDL-1.1", "EDL-1.0",
}
SELECTION_COLUMNS = (
    "coordinate", "declared_expression", "selected_spdx", "evidence_url",
    "evidence_type", "justification",
)
INVENTORY_LINE = re.compile(
    r"^\s*(?P<prefix>.+?)\s+\((?P<coordinate>[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[^ )]+)"
    r"\s+-\s+[^)]*\)\s*$"
)


@dataclass(frozen=True)
class InventoryEntry:
    coordinate: str
    declared_licenses: tuple[str, ...]
    name: str


@dataclass(frozen=True)
class LicenseSelection:
    coordinate: str
    declared_expression: str
    selected_spdx: str
    evidence_url: str
    evidence_type: str
    justification: str


def normalize_license(value):
    normalized = value.strip().lower().replace("，", ",")
    if "unknown" in normalized or "wtfpl" in normalized or "commercial license" in normalized:
        return None
    if "affero" in normalized or "agpl" in normalized:
        return "AGPL"
    if "lesser general public" in normalized or "lgpl" in normalized:
        return "LGPL"
    if "general public license" in normalized or normalized.startswith("gpl") or re.search(r"\bgpl\b", normalized):
        return "GPL"
    if "mulan" in normalized and ("version 2" in normalized or "2.0" in normalized):
        return "MulanPSL-2.0"
    if normalized == "bsd-3-clause":
        return "BSD-3-Clause"
    if normalized == "bsd-2-clause":
        return "BSD-2-Clause"
    if normalized in {"modified bsd", "bsd license"}:
        return "BSD-3-Clause"
    if "cddl-1.0" in normalized:
        return "CDDL-1.0"
    if "cddl" in normalized or "common development and distribution license" in normalized:
        return "CDDL-1.1"
    if "edl 1.0" in normalized or "eclipse distribution license" in normalized:
        return "EDL-1.0"
    if "epl 1.0" in normalized or "epl-1.0" in normalized:
        return "EPL-1.0"
    if "epl 2.0" in normalized or "epl-2.0" in normalized or "eclipse public license" in normalized:
        return "EPL-2.0"
    if "mpl 1.1" in normalized:
        return "MPL-1.1"
    if "mpl 2.0" in normalized or "mozilla public license 2" in normalized:
        return "MPL-2.0"
    if "universal permissive license" in normalized:
        return "UPL-1.0"
    if "bouncy castle" in normalized:
        return "Bouncy-Castle"
    if normalized == "w3c" or "w3c license" in normalized:
        return "W3C"
    if "cc0" in normalized:
        return "CC0-1.0"
    if "public domain" in normalized:
        return "Public-Domain"
    if normalized == "mit-0":
        return "MIT-0"
    if "apache" in normalized or normalized in {"al 2.0", "apache 2"}:
        return "Apache-2.0"
    if normalized == "mit" or "mit license" in normalized:
        return "MIT"
    if "3-clause bsd" in normalized or "bsd 3-clause" in normalized:
        return "BSD-3-Clause"
    if "2-clause bsd" in normalized or "bsd 2-clause" in normalized:
        return "BSD-2-Clause"
    if normalized == "bsd":
        return "BSD-3-Clause"
    if normalized == "isc" or "isc license" in normalized:
        return "ISC"
    return None


def leading_parenthesized_values(text):
    values = []
    remaining = text.lstrip()
    while remaining.startswith("("):
        depth = 0
        end = None
        for index, character in enumerate(remaining):
            if character == "(":
                depth += 1
            elif character == ")":
                depth -= 1
                if depth == 0:
                    end = index
                    break
        if end is None:
            raise ValueError(f"unbalanced license declaration: {text}")
        values.append(remaining[1:end].strip())
        remaining = remaining[end + 1:].lstrip()
    return tuple(values), remaining


def parse_inventory(path):
    text = Path(path).read_text(encoding="utf-8")
    if not text.strip():
        raise ValueError("license inventory is empty")
    entries = []
    for line in text.splitlines():
        match = INVENTORY_LINE.match(line)
        if not match:
            continue
        prefix = match.group("prefix").strip()
        licenses, prefix = leading_parenthesized_values(prefix)
        if not licenses:
            raise ValueError(f"license declaration is missing: {line.strip()}")
        entries.append(InventoryEntry(match.group("coordinate"), tuple(licenses), prefix.strip()))
    if not entries:
        raise ValueError("license inventory contains no dependency entries")
    return entries


def load_selections(path):
    with Path(path).open(encoding="utf-8", newline="") as source:
        rows = csv.DictReader(
            (line for line in source if not line.startswith("#")), delimiter="\t"
        )
        if tuple(rows.fieldnames or ()) != SELECTION_COLUMNS:
            raise ValueError("license selections must use the six-column schema")
        result = {}
        for row in rows:
            selection = LicenseSelection(**row)
            if selection.coordinate in result:
                raise ValueError(f"duplicate license selection: {selection.coordinate}")
            result[selection.coordinate] = selection
        return result


def load_build_tool_exclusions(path):
    result = {}
    with Path(path).open(encoding="utf-8") as source:
        for line in source:
            if not line.strip() or line.startswith("#"):
                continue
            coordinate, reason = line.rstrip("\n").split("\t", 1)
            result[coordinate] = reason
    return result


def load_sbom_coordinates(path):
    document = json.loads(Path(path).read_text(encoding="utf-8"))
    result = set()
    for component in document.get("components", []):
        purl = component.get("purl", "")
        match = re.match(r"pkg:maven/([^/]+)/([^@]+)@([^?]+)", purl)
        if match:
            result.add(":".join(match.groups()))
    return result


def complete_coordinate(coordinate):
    parts = coordinate.split(":")
    return len(parts) == 3 and all(parts)


def valid_evidence_url(value):
    parsed = urlparse(value)
    if parsed.scheme != "https" or not parsed.netloc:
        return False
    lowered = parsed.path.lower()
    return not any(marker in lowered for marker in ("/blob/main/", "/blob/master/", "/tree/main/", "/tree/master/"))


def verify_selection(entry, selection):
    errors = []
    if not complete_coordinate(selection.coordinate):
        errors.append(f"selection coordinate requires complete version: {selection.coordinate}")
    if selection.selected_spdx not in ALLOWED_SPDX:
        errors.append(f"selected SPDX is not allowed for {selection.coordinate}: {selection.selected_spdx}")
    declared = {normalize_license(value) for value in entry.declared_licenses}
    if selection.selected_spdx not in declared:
        errors.append(f"selected SPDX is not declared for {selection.coordinate}: {selection.selected_spdx}")
    if selection.evidence_type not in {"POM", "LICENSE", "UPSTREAM_SOURCE"}:
        errors.append(f"invalid evidence type for {selection.coordinate}: {selection.evidence_type}")
    if not valid_evidence_url(selection.evidence_url):
        errors.append(f"evidence URL is not immutable HTTPS for {selection.coordinate}")
    if not selection.declared_expression.strip() or not selection.justification.strip():
        errors.append(f"selection evidence fields must not be empty: {selection.coordinate}")
    return errors


def verify_policy(inventory, selections, sbom_coordinates=None, build_tool_exclusions=None):
    errors = []
    sbom_coordinates = sbom_coordinates or set()
    build_tool_exclusions = build_tool_exclusions or {}
    reported = {entry.coordinate for entry in inventory}
    for coordinate in sorted(set(selections) - reported):
        selection = selections[coordinate]
        if not complete_coordinate(selection.coordinate):
            errors.append(f"selection coordinate requires complete version: {selection.coordinate}")
        errors.append(f"stale selection not present in inventory: {coordinate}")
    for entry in inventory:
        normalized = tuple(normalize_license(value) for value in entry.declared_licenses)
        requires_selection = (
            any(value not in ALLOWED_SPDX for value in normalized)
        )
        selection = selections.get(entry.coordinate)
        exclusion_reason = build_tool_exclusions.get(entry.coordinate, "")
        excluded = bool(exclusion_reason) and (
            entry.coordinate not in sbom_coordinates
            or any(marker in exclusion_reason.lower() for marker in ("provided", "optional", "test"))
        )
        if requires_selection and excluded:
            continue
        if requires_selection and selection is None:
            errors.append(f"license requires an evidenced selection: {entry.coordinate}")
        elif selection is not None:
            errors.extend(verify_selection(entry, selection))
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--inventory", type=Path, required=True)
    parser.add_argument("--selections", type=Path, required=True)
    parser.add_argument("--sbom", type=Path, required=True)
    parser.add_argument("--build-tool-exclusions", type=Path, required=True)
    args = parser.parse_args()
    try:
        errors = verify_policy(
            parse_inventory(args.inventory),
            load_selections(args.selections),
            load_sbom_coordinates(args.sbom),
            load_build_tool_exclusions(args.build_tool_exclusions),
        )
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"[FAIL] {error}", file=sys.stderr)
        return 1
    if errors:
        print("\n".join(f"[FAIL] {error}" for error in errors), file=sys.stderr)
        return 1
    print("[PASS] All published dependencies satisfy the license policy.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
