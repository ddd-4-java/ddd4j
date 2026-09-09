#!/usr/bin/env python3
"""Verify authoritative platform versions from a generated effective POM."""

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


EXPECTED = {
    ("org.springframework.security", "spring-security-core"): "7.1.0",
    ("org.testcontainers", "testcontainers-localstack"): "2.0.5",
    ("io.github.easy4j", "zxing-extension"): "3.0.x.20260630-SNAPSHOT",
    ("global.namespace.truelicense", "truelicense-v1"): "4.1.4",
}

FORBIDDEN = {
    ("de.schlichtherle.truelicense", "truelicense-core"),
    ("de.schlichtherle.truelicense", "truelicense-xml"),
}

FORBIDDEN_ECOSYSTEM_COORDINATES = {
    ("com.baomidou", "mybatis-plus-spring-boot4-starter"),
    ("org.dromara.mica-mqtt", "mica-mqtt-client-spring-boot-starter"),
    ("io.github.resilience4j", "resilience4j-spring-boot2"),
    ("io.github.resilience4j", "resilience4j-spring-cloud2"),
}


def managed_versions(path):
    root = ET.parse(path).getroot()
    namespace = {"m": root.tag.split("}")[0][1:]}
    result = {}
    for dependency in root.findall("m:dependencyManagement/m:dependencies/m:dependency", namespace):
        group = dependency.findtext("m:groupId", default="", namespaces=namespace)
        artifact = dependency.findtext("m:artifactId", default="", namespaces=namespace)
        version = dependency.findtext("m:version", default="", namespaces=namespace)
        result[(group, artifact)] = version
    return result


def verify(path, expected, forbidden):
    managed = managed_versions(path)
    errors = []
    for coordinate, expected_version in sorted(expected.items()):
        actual = managed.get(coordinate)
        name = ":".join(coordinate)
        if actual is None:
            errors.append(f"{name} is missing")
        elif actual != expected_version:
            errors.append(f"{name} expected {expected_version} but was {actual}")
    for coordinate in sorted(forbidden):
        if coordinate in managed:
            errors.append(f"forbidden coordinate {':'.join(coordinate)} is managed")
    return errors


def verify_source_authority(path):
    managed = managed_versions(path)
    return [
        f"platform BOM must not manage ecosystem coordinate {':'.join(coordinate)}"
        for coordinate in sorted(FORBIDDEN_ECOSYSTEM_COORDINATES & managed.keys())
    ]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("effective_pom", type=Path)
    parser.add_argument("--source-pom", type=Path)
    args = parser.parse_args()
    errors = verify(args.effective_pom, EXPECTED, FORBIDDEN)
    if args.source_pom:
        errors.extend(verify_source_authority(args.source_pom))
    if errors:
        print("\n".join(errors))
        return 1
    print(f"PASS: {len(EXPECTED)} platform versions and {len(FORBIDDEN)} forbidden coordinates")
    return 0


if __name__ == "__main__":
    sys.exit(main())
