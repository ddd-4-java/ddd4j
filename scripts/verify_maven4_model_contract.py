#!/usr/bin/env python3
"""Verify the ddd4j-owned Maven 4 effective-model boundary."""

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


DDD4J_WARNING = re.compile(r"effective model for 'io\.ddd4j:")
MODEL_WARNING = re.compile(
    r"\[WARNING] (?P<count>\d+) problems? (?:were|was) encountered while building "
    r"the effective model for '(?P<coordinate>[^']+)'"
)
QUARKUS_NATIVE_BUILDER = "-Dquarkus.native.builder-image=mandrel"
QUARKUS_JUNIT_RELOCATED = "quarkus-junit5"
ALLOWED_UPSTREAM_MODEL_WARNINGS = {
    "io.smallrye.config:smallrye-config:jar:3.17.2": 13,
    "io.smallrye.config:smallrye-config-core:jar:3.17.2": 13,
    "io.smallrye.config:smallrye-config-common:jar:3.17.2": 13,
    "org.jboss.narayana.jts:narayana-jts-integration:jar:7.3.4.Final": 1,
    "org.apache.pulsar:pulsar-client:jar:4.2.2": 53,
    "org.apache.pulsar:pulsar-client-admin-api:jar:4.2.2": 53,
    "org.apache.pulsar:pulsar-client-api:jar:4.2.2": 53,
    "org.apache.pulsar:bouncy-castle-bc:jar:pkg:4.2.2": 53,
}


def verify_quarkus_config(config_path):
    config = Path(config_path).read_text(encoding="utf-8").splitlines()
    if QUARKUS_NATIVE_BUILDER not in (line.strip() for line in config):
        return ["Quarkus native builder image is not configured"]
    return []


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


def verify_owned_source(root):
    errors = []
    for pom in Path(root).rglob("pom.xml"):
        if "target" in pom.parts:
            continue
        source = pom.read_text(encoding="utf-8")
        if f"<artifactId>{QUARKUS_JUNIT_RELOCATED}</artifactId>" in source:
            errors.append(f"relocated Quarkus test artifact in {pom}")
    parent = Path(root) / "ddd4j-parent/pom.xml"
    if parent.exists():
        root_element = ET.parse(parent).getroot()
        prefix = "{%s}" % root_element.tag.split("}")[0][1:]
        for plugin in root_element.iter(prefix + "plugin"):
            if plugin.findtext(prefix + "artifactId", default="") != "maven-resources-plugin":
                continue
            for execution in plugin.findall(
                    f"{prefix}executions/{prefix}execution"):
                configuration = execution.find(prefix + "configuration")
                if configuration is not None and configuration.find(prefix + "resources") is not None:
                    errors.append("maven-resources-plugin configures read-only execution resources")
    return errors


def verify(log_path, source_pom):
    errors = []
    log = Path(log_path).read_text(encoding="utf-8")
    if DDD4J_WARNING.search(log):
        errors.append("ddd4j effective-model warning")
    for match in MODEL_WARNING.finditer(log):
        coordinate = match.group("coordinate")
        count = int(match.group("count"))
        expected = ALLOWED_UPSTREAM_MODEL_WARNINGS.get(coordinate)
        if expected is None:
            errors.append(f"unexpected upstream effective-model warning: {coordinate}")
        elif count != expected:
            errors.append(
                f"upstream effective-model warning count changed for {coordinate}: "
                f"expected {expected}, got {count}"
            )
    if has_imported_bom(source_pom):
        errors.append("platform dependencyManagement still imports a BOM")
    if "BUILD SUCCESS" not in log:
        errors.append("Maven validation did not finish successfully")
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    parser.add_argument("--source-pom", type=Path, required=True)
    parser.add_argument("--maven-config", type=Path, required=True)
    args = parser.parse_args()
    errors = verify(args.log, args.source_pom)
    errors.extend(verify_quarkus_config(args.maven_config))
    if errors:
        print("\n".join(errors))
        return 1
    print("PASS: ddd4j Maven 4 model has no owned warnings or imported BOMs")
    return 0


if __name__ == "__main__":
    sys.exit(main())
