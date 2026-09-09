#!/usr/bin/env python3
"""Verify or normalize Maven 4 internal parent declarations."""

import argparse
import re
import xml.etree.ElementTree as ET
from pathlib import Path


PARENT_BLOCK = re.compile(r"<parent>.*?</parent>", re.DOTALL)


def child_text(element, name, namespace):
    value = element.findtext("m:" + name, default="", namespaces=namespace)
    return value.strip()


def violations(root):
    errors = []
    for pom in sorted(root.rglob("pom.xml")):
        if "target" in pom.parts or ".superpowers" in pom.parts:
            continue
        document = ET.parse(pom).getroot()
        namespace = {"m": document.tag.split("}")[0][1:]}
        if document.findtext("m:modelVersion", namespaces=namespace) != "4.1.0":
            continue
        parent = document.find("m:parent", namespace)
        if parent is None:
            continue
        relative = child_text(parent, "relativePath", namespace)
        group_id = child_text(parent, "groupId", namespace)
        artifact_id = child_text(parent, "artifactId", namespace)
        version = child_text(parent, "version", namespace)
        if relative or not group_id or not artifact_id or not version or "${" in version:
            errors.append(pom.relative_to(root))
    return errors


def normalize(pom, revision):
    source = pom.read_text(encoding="utf-8")
    if "<modelVersion>4.1.0</modelVersion>" not in source:
        return False
    match = PARENT_BLOCK.search(source)
    if match is None:
        return False
    block = match.group(0)
    relative_match = re.search(r"<relativePath>\s*([^<]+)\s*</relativePath>", block)
    if relative_match is None:
        return False
    parent_pom = (pom.parent / relative_match.group(1).strip()).resolve()
    parent_document = ET.parse(parent_pom).getroot()
    namespace = {"m": parent_document.tag.split("}")[0][1:]}
    artifact_id = child_text(parent_document, "artifactId", namespace)
    indent = re.search(r"\n(\s*)<relativePath>", block).group(1)
    updated = "<parent>\n"
    updated += "%s<groupId>io.ddd4j</groupId>\n" % indent
    updated += "%s<artifactId>%s</artifactId>\n" % (indent, artifact_id)
    updated += "%s<version>%s</version>\n" % (indent, revision)
    updated += re.match(r"\s*", block[block.rfind("\n") + 1:]).group(0) + "</parent>"
    if updated == block:
        return False
    pom.write_text(source[:match.start()] + updated + source[match.end():], encoding="utf-8")
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", type=Path, nargs="?", default=Path("."))
    parser.add_argument("--fix", action="store_true")
    args = parser.parse_args()
    root = args.root.resolve()
    root_document = ET.parse(root / "pom.xml").getroot()
    root_namespace = {"m": root_document.tag.split("}")[0][1:]}
    revision = child_text(root_document.find("m:properties", root_namespace), "revision", root_namespace)
    if args.fix:
        for pom in sorted(root.rglob("pom.xml")):
            if "target" not in pom.parts and ".superpowers" not in pom.parts:
                normalize(pom, revision)
    errors = violations(root)
    if errors:
        for pom in errors:
            print("Maven 4 internal parent must use a fixed GAV without relativePath: %s" % pom)
        raise SystemExit(1)
    print("PASS: Maven 4 parent declarations use fixed GAV lookup")


if __name__ == "__main__":
    main()
