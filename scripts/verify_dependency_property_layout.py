#!/usr/bin/env python3
"""验证三条版本线的依赖版本属性命名、分组和自然字母顺序。"""

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

SECTION_MARKERS = (
    ("global", "<!-- Global Properties -->"),
    ("third_party", "<!-- Third-Party Dependencies -->"),
    ("maven", "<!-- Maven Dependencies -->"),
)

MAVEN_PROPERTIES = {
    "central-publishing-maven-plugin.version",
    "commons-attributes-plugin.version",
    "quarkus-failsafe-plugin.version",
    "quarkus-surefire-plugin.version",
}


def natural_key(value):
    """按大小写不敏感的自然字母顺序生成比较键。"""
    return tuple(int(part) if part.isdigit() else part.casefold()
                 for part in re.split(r"(\d+)", value))


def expected_section(name):
    """返回属性应归属的固定分区。"""
    if not name.endswith(".version"):
        return "global"
    if name.startswith("maven-") or name in MAVEN_PROPERTIES:
        return "maven"
    return "third_party"


def property_names(path):
    """读取 POM properties 的属性名并拒绝重复定义。"""
    root = ET.parse(path).getroot()
    ns = {"m": root.tag.split("}")[0][1:]}
    properties = root.find("m:properties", ns)
    if properties is None:
        raise ValueError(f"{path}: missing properties")
    names = [element.tag.split("}")[-1] for element in properties]
    duplicates = sorted({name for name in names if names.count(name) > 1}, key=natural_key)
    if duplicates:
        raise ValueError(f"{path}: duplicate properties: {', '.join(duplicates)}")
    return names


def section_names(path):
    """按三个固定注释分区读取属性名。"""
    text = path.read_text()
    block = re.search(r"<properties>(.*?)</properties>", text, re.DOTALL)
    if block is None:
        raise ValueError(f"{path}: missing properties block")
    body = block.group(1)
    positions = []
    for section, marker in SECTION_MARKERS:
        matches = list(re.finditer(re.escape(marker), body))
        if len(matches) != 1:
            raise ValueError(f"{path}: expected one {marker}, found {len(matches)}")
        positions.append((section, matches[0].start(), matches[0].end()))
    if [position[1] for position in positions] != sorted(position[1] for position in positions):
        raise ValueError(f"{path}: property sections are out of order")
    result = {}
    for index, (section, _, start) in enumerate(positions):
        end = positions[index + 1][1] if index + 1 < len(positions) else len(body)
        result[section] = re.findall(r"<([A-Za-z_][\w.:-]*)>[^<]*</\1>", body[start:end])
    return result


def verify(path):
    """验证单个 POM，并返回所有错误。"""
    errors = []
    try:
        names = property_names(path)
        sections = section_names(path)
    except (ET.ParseError, ValueError) as error:
        return [str(error)]
    prefixed = sorted((name for name in names if name.startswith("easy4j-")), key=natural_key)
    if prefixed:
        errors.append(f"{path}: easy4j-prefixed properties remain: {', '.join(prefixed)}")
    legacy = sorted((name for name in names if name.startswith("hiwepy-")), key=natural_key)
    if legacy:
        errors.append(f"{path}: conflicting hiwepy properties remain: {', '.join(legacy)}")
    alignment = sorted((name for name in names if name.startswith("alignment.")), key=natural_key)
    if alignment:
        errors.append(f"{path}: internal alignment properties remain: {', '.join(alignment)}")
    flattened = [name for section, _ in SECTION_MARKERS for name in sections[section]]
    if set(flattened) != set(names) or len(flattened) != len(names):
        errors.append(f"{path}: section membership does not cover properties exactly once")
    for section, _ in SECTION_MARKERS:
        actual = sections[section]
        misplaced = [name for name in actual if expected_section(name) != section]
        if misplaced:
            errors.append(f"{path}: misplaced in {section}: {', '.join(misplaced)}")
        expected = sorted(actual, key=natural_key)
        if actual != expected:
            first = next(index for index, pair in enumerate(zip(actual, expected)) if pair[0] != pair[1])
            errors.append(f"{path}: {section} is not naturally sorted at {actual[first]} (expected {expected[first]})")
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("poms", nargs="+", type=Path)
    args = parser.parse_args()
    errors = [error for path in args.poms for error in verify(path)]
    if errors:
        print("\n".join(errors))
        return 1
    print(f"PASS: {len(args.poms)} dependency property blocks")
    return 0


if __name__ == "__main__":
    sys.exit(main())
