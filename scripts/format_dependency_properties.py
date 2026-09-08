#!/usr/bin/env python3
"""统一 ddd4j-dependencies 属性命名，并按固定三段自然排序。"""

import argparse
import re
import textwrap
import xml.etree.ElementTree as ET
from pathlib import Path

from verify_dependency_property_layout import SECTION_MARKERS, expected_section, natural_key

GENERIC_COMMENTS = {
    "<!-- 核心配置 -->",
    "<!-- Global Properties -->",
    "<!-- Third-Party Dependencies -->",
    "<!-- Maven Dependencies -->",
    "<!-- Maven Plugin versions -->",
    "<!-- Dependency versions (Spring Boot 2.7.x compatible) -->",
}


def rename_easy4j_properties(text):
    """移除 easy4j- 前缀，并删除与新坐标冲突的 hiwepy 旧声明。"""
    root = ET.fromstring(text)
    namespace = {"m": root.tag.split("}")[0][1:]}
    properties = root.find("m:properties", namespace)
    names = [element.tag.split("}")[-1] for element in properties]
    changes = []
    for alignment in sorted((name for name in names if name.startswith("alignment.")), key=natural_key):
        references = len(re.findall(r"\$\{" + re.escape(alignment) + r"\}", text))
        if alignment in {"alignment.hitool-crypto.version", "alignment.hitool-mail.version"}:
            text = text.replace("${" + alignment + "}", "${hitool.version}")
        else:
            text, removed = remove_dependencies_using_property(text, alignment)
            if removed != references:
                raise ValueError(f"not all alignment references belong to removable dependencies: {alignment}")
        text, count = re.subn(r"^[ \t]*<" + re.escape(alignment) + r">[^<]*</" +
                              re.escape(alignment) + r">[ \t]*\n", "", text,
                              count=1, flags=re.MULTILINE)
        if count != 1:
            raise ValueError(f"cannot remove alignment property: {alignment}")
        names.remove(alignment)
        changes.append((alignment, "use-current-version" if references else "removed"))
    for legacy in sorted((name for name in names if name.startswith("hiwepy-")), key=natural_key):
        text, removed = remove_dependencies_using_property(text, legacy, "com.github.hiwepy")
        if not removed:
            raise ValueError(f"legacy property has no dependency to remove: {legacy}")
        text, count = re.subn(r"^[ \t]*<" + re.escape(legacy) + r">[^<]*</" +
                              re.escape(legacy) + r">[ \t]*\n", "", text,
                              count=1, flags=re.MULTILINE)
        if count != 1:
            raise ValueError(f"cannot remove legacy property: {legacy}")
        names.remove(legacy)
        changes.append((legacy, "removed-with-legacy-dependencies"))
    prefixed = sorted((name for name in names if name.startswith("easy4j-")), key=natural_key)
    for old_name in prefixed:
        target = old_name[len("easy4j-"):]
        if target in names:
            references = len(re.findall(r"\$\{" + re.escape(target) + r"\}", text))
            if references:
                text, removed = remove_dependencies_using_property(text, target, "com.github.hiwepy")
                if removed != references:
                    raise ValueError(f"not all colliding references belong to removable legacy dependencies: {target}")
            text, count = re.subn(r"^[ \t]*<" + re.escape(target) + r">[^<]*</" +
                                  re.escape(target) + r">[ \t]*\n", "", text,
                                  count=1, flags=re.MULTILINE)
            if count != 1:
                raise ValueError(f"cannot remove colliding property: {target}")
            names.remove(target)
            changes.append((target, "removed-with-legacy-dependencies" if references else "removed-unused"))
        text = re.sub(r"(<\/?)" + re.escape(old_name) + r"(>)",
                      lambda match: match.group(1) + target + match.group(2), text)
        text = text.replace("${" + old_name + "}", "${" + target + "}")
        names[names.index(old_name)] = target
        changes.append((old_name, target))
    return text, changes


def remove_dependencies_using_property(text, property_name, required_group=None):
    """删除使用指定属性的依赖及其紧邻说明。"""
    lines = text.splitlines(keepends=True)
    ranges = []
    index = 0
    while index < len(lines):
        if lines[index].strip() != "<dependency>":
            index += 1
            continue
        end = index + 1
        while end < len(lines) and lines[end].strip() != "</dependency>":
            end += 1
        if end == len(lines):
            raise ValueError("unterminated dependency element")
        block = "".join(lines[index:end + 1])
        matching_group = required_group is None or f"<groupId>{required_group}</groupId>" in block
        matching_version = "<version>${" + property_name + "}</version>" in block
        if matching_group and matching_version:
            start = index
            previous = index - 1
            while previous >= 0 and not lines[previous].strip():
                previous -= 1
            if previous >= 0 and lines[previous].strip().endswith("-->"):
                comment_end = previous
                while previous >= 0 and "<!--" not in lines[previous]:
                    previous -= 1
                if previous >= 0:
                    start = previous
                    while start > 0 and not lines[start - 1].strip():
                        start -= 1
            ranges.append((start, end + 1))
        index = end + 1
    removed = set()
    for start, end in ranges:
        removed.update(range(start, end))
    return "".join(line for number, line in enumerate(lines) if number not in removed), len(ranges)


def normalize_comment(comment):
    """规范注释缩进，同时保留多行兼容性说明。"""
    lines = [line.strip() for line in comment.strip().splitlines()]
    return "\n".join("        " + line for line in lines)


def comment_describes(name, comment):
    """判断位于两个属性之间的注释是否描述前一个属性。"""
    subject = name.removesuffix(".version")
    tokens = [token for token in re.split(r"[-_.]+", subject.casefold()) if token]
    content = comment.casefold()
    return bool(tokens) and all(token in content for token in tokens)


def format_properties(text):
    """保留属性相关注释，将全部属性重排到三个固定分区。"""
    block_match = re.search(r"(?P<open>^[ \t]*<properties>[ \t]*$)\r?\n"
                            r"(?P<body>.*?)"
                            r"(?P<close>^[ \t]*</properties>[ \t]*$)(?:\r?\n)*",
                            text, re.MULTILINE | re.DOTALL)
    if block_match is None:
        raise ValueError("missing properties block")
    body = block_match.group("body")
    root = ET.fromstring(text)
    namespace = {"m": root.tag.split("}")[0][1:]}
    properties = root.find("m:properties", namespace)
    names = [element.tag.split("}")[-1] for element in properties]
    entries = {name: {"before": [], "after": [], "element": ""} for name in names}
    previous_end = 0
    previous_name = None
    for name in names:
        element_match = re.search(r"^[ \t]*<" + re.escape(name) + r">[^<]*</" +
                                  re.escape(name) + r">(?:[ \t]*<!--.*?-->)?[ \t]*$",
                                  body, re.MULTILINE)
        if element_match is None:
            raise ValueError(f"property is not a simple text element: {name}")
        prefix = body[previous_end:element_match.start()]
        comments = [comment for comment in re.findall(r"<!--.*?-->", prefix, re.DOTALL)
                    if textwrap.dedent(comment).strip() not in GENERIC_COMMENTS]
        element = "        " + element_match.group(0).strip()
        for comment in comments:
            normalized = normalize_comment(comment)
            if previous_name and comment_describes(previous_name, comment):
                entries[previous_name]["after"].append(normalized)
            else:
                entries[name]["before"].append(normalized)
        entries[name]["element"] = element
        previous_end = element_match.end()
        previous_name = name
    trailing_comments = [comment for comment in re.findall(r"<!--.*?-->", body[previous_end:], re.DOTALL)
                         if textwrap.dedent(comment).strip() not in GENERIC_COMMENTS]
    if previous_name:
        entries[previous_name]["after"].extend(normalize_comment(comment) for comment in trailing_comments)
    sections = {section: [] for section, _ in SECTION_MARKERS}
    for name in names:
        sections[expected_section(name)].append(name)
    lines = []
    for section, marker in SECTION_MARKERS:
        if lines:
            lines.append("")
        lines.append("        " + marker)
        for name in sorted(sections[section], key=natural_key):
            entry = entries[name]
            lines.extend(entry["before"])
            lines.append(entry["element"])
            lines.extend(entry["after"])
    replacement = block_match.group("open") + "\n" + "\n".join(lines) + "\n    </properties>\n\n"
    return text[:block_match.start()] + replacement + text[block_match.end():]


def format_pom(path):
    """原地规范一个依赖 POM，返回属性重命名台账。"""
    text, changes = rename_easy4j_properties(path.read_text())
    formatted = format_properties(text)
    ET.fromstring(formatted)
    path.write_text(formatted)
    return changes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("poms", nargs="+", type=Path)
    args = parser.parse_args()
    for path in args.poms:
        changes = format_pom(path)
        print(f"{path}: {len(changes)} rename/collision actions")


if __name__ == "__main__":
    main()
