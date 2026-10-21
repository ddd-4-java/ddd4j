#!/usr/bin/env python3
"""验证 ddd4j 三条发布线的非例外模块、生产对象与公开 JVM API。"""

import argparse
import csv
import hashlib
import json
import os
import pathlib
import re
import sqlite3
import subprocess
import sys
import xml.etree.ElementTree as ET


VERSIONS = ("1", "2", "3")
JDK_VERSIONS = ("1.8", "17", "21")
GENERATED_CLASS = re.compile(r"\$(?:\d+|Definition(?:\$.*)?|Reference|Exec|InitializeInterceptor)$")
JAVA_OPERATORS = tuple(sorted((
    ">>>=", "<<=", ">>=", ">>>", "<<", ">>", "::", "->", "++", "--",
    "==", "!=", "<=", ">=", "&&", "||", "+=", "-=", "*=", "/=", "%=",
    "&=", "|=", "^=",
), key=len, reverse=True))
PLATFORM_IMPORT_NAMESPACES = tuple((old, new) for old, new in (
    ("javax.persistence.", "jakarta.persistence."),
    ("javax.validation.", "jakarta.validation."),
    ("javax.servlet.", "jakarta.servlet."),
    ("javax.ws.rs.", "jakarta.ws.rs."),
    ("javax.annotation.", "jakarta.annotation."),
    ("javax.inject.", "jakarta.inject."),
    ("javax.jms.", "jakarta.jms."),
    ("javax.json.bind.", "jakarta.json.bind."),
    ("javax.enterprise.", "jakarta.enterprise."),
    ("javax.interceptor.", "jakarta.interceptor."),
    ("javax.transaction.", "jakarta.transaction."),
    ("com.fasterxml.jackson.databind.", "tools.jackson.databind."),
    ("com.fasterxml.jackson.core.", "tools.jackson.core."),
))
CDI_PLATFORM_DESCRIPTORS = {
    "ddd4j-runtime/ddd4j-runtime-quarkus/src/main/resources/META-INF/beans.xml": ("4.0", "4_0"),
    "ddd4j-samples/ddd4j-sample-helidon-cqrs/src/main/resources/META-INF/beans.xml": ("3.0", "3_0"),
}
HELIDON_CDI_SERVICE_PREFIX = (
    "ddd4j-runtime/ddd4j-runtime-helidon/src/main/resources/META-INF/services/"
)
HELIDON_CDI_SERVICE_NAMES = (
    HELIDON_CDI_SERVICE_PREFIX + "javax.enterprise.inject.spi.Extension",
    HELIDON_CDI_SERVICE_PREFIX + "jakarta.enterprise.inject.spi.Extension",
)
HELIDON_CDI_PROVIDER = b"io.ddd4j.helidon.Ddd4jHelidonExtension\n"


def run(*command: str) -> str:
    return subprocess.check_output(command, text=True)


def excluded(path: str) -> bool:
    lowered = path.lower()
    return (
        path.startswith("ddd4j-samples/")
        or "quarkus" in lowered
        or "panache" in lowered
        or "helidon" in lowered
    )


def tracked_files(root: pathlib.Path) -> set[str]:
    return set(run(
        "git", "-C", str(root), "ls-files", "--cached", "--others", "--exclude-standard"
    ).splitlines())


def inventory_files(root: pathlib.Path) -> dict[str, str]:
    """读取当前工作树的跟踪及新增文件，避免将旧索引或旧 target 作为输入。"""
    listed = subprocess.check_output(
        ["git", "-C", str(root), "ls-files", "-z", "--cached", "--others", "--exclude-standard"]
    )
    result = {}
    for raw_path in set(listed.split(b"\0")) - {b""}:
        path = os.fsdecode(raw_path)
        if path.startswith((".git/", ".codegraph/", "target/")) or "/target/" in path:
            continue
        file = root / path
        if file.is_file():
            result[path] = hashlib.sha256(file.read_bytes()).hexdigest()
    return result


def inventory_category(path: str) -> str:
    if path.endswith("/pom.xml") or path == "pom.xml":
        return "module"
    if path.endswith(".java") and "/src/main/java/" in path:
        return "production"
    if path.endswith(".java") and "/src/test/java/" in path:
        return "test"
    if path in {
        "docs/ECOSYSTEM-ARCHITECTURE.md",
        "docs/RELEASE-NOTES-3.0.x-2026-09.md",
        "docs/architecture/cloud-agents-migration-evidence.md",
        "docs/architecture/cloud-exception-contract-audit.md",
        "docs/architecture/cloud-tenant-context-contract-audit.md",
        "docs/architecture/shiro-http-login-audit.md",
        "docs/architecture/shiro-real-provider-audit.md",
        "docs/migrations/truelicense4-framework-consumer-audit.md",
    }:
        return "historical-document"
    if path.endswith(".md") and (path.startswith(".superpowers/") or path.startswith("docs/superpowers/")):
        return "historical-document"
    if path.endswith(".md"):
        return "product-document"
    if path.endswith((".xml", ".properties", ".yml", ".yaml", ".json")):
        return "configuration"
    return "other"


def indexed_javadoc_history_matches(path: str, roots: list[pathlib.Path],
                                    values: list[str | None]) -> bool:
    """仅将逐文件锁定且可从统一索引定位的旧验证记录归为历史。"""
    progress = path == "docs/verification/javadoc-fill-progress-20261009.md"
    report = re.fullmatch(r"docs/verification/javadoc-fill-report-(?:0[1-9]|1[0-8])\.md", path)
    batch = re.fullmatch(r"docs/verification/javadoc-fill-batches/batch-(?:0[1-9]|1[0-8])\.txt", path)
    raw_gaps = path == "docs/verification/javadoc-gaps-raw.txt"
    redline = path == "docs/verification/redline-cleanup-evidence-20261009.txt"
    if not (progress or report or batch or raw_gaps or redline):
        return False
    line_index = 0 if redline else 1 if progress else 2
    if len(values) != 3 or any(value is None if index == line_index else value is not None
                               for index, value in enumerate(values)):
        return False
    index_path = pathlib.Path("docs/superpowers/historical-evidence-index.md")
    try:
        indexes = [(root / index_path).read_bytes() for root in roots]
        if indexes[0] != indexes[1] or indexes[1] != indexes[2]:
            return False
        index_text = indexes[0].decode("utf-8")
    except (OSError, UnicodeError):
        return False
    line = ("1.0.x", "2.0.x", "3.0.x")[line_index]
    expected = (rf"^\| `{re.escape(pathlib.Path(path).name)}` \| {line}；[^|]+ \| "
                rf"\[原始文件\]\(https://github\.com/ddd-4-java/ddd4j/blob/[0-9a-f]{{40}}/"
                rf"{re.escape(path)}\) \| `{values[line_index]}` \|$")
    return re.search(expected, index_text, re.MULTILINE) is not None


def pom_topology_signature(file: pathlib.Path) -> tuple[str, str, str, frozenset[str]] | None:
    """只比较 Maven 模块身份与聚合拓扑；依赖和插件留给各线严格门禁。"""
    try:
        project = ET.parse(file).getroot()
    except (ET.ParseError, OSError):
        return None

    def child(parent: ET.Element | None, name: str) -> ET.Element | None:
        if parent is None:
            return None
        return next((node for node in parent if node.tag.rsplit("}", 1)[-1] == name), None)

    def value(parent: ET.Element | None, name: str) -> str | None:
        node = child(parent, name)
        return node.text.strip() if node is not None and node.text else None

    parent = child(project, "parent")
    group = value(project, "groupId") or value(parent, "groupId")
    artifact = value(project, "artifactId")
    packaging = value(project, "packaging") or "jar"
    if not group or not artifact:
        return None
    declared = []
    for container_name, item_name in (("modules", "module"), ("subprojects", "subproject")):
        container = child(project, container_name)
        if container is not None:
            names = [node.text.strip() if node.text else "" for node in container
                     if node.tag.rsplit("}", 1)[-1] == item_name]
            if not names or any(not name for name in names) or len(set(names)) != len(names):
                return None
            declared.append(frozenset(names))
    if len(set(declared)) > 1:
        return None
    return group, artifact, packaging, declared[0] if declared else frozenset()


def platform_config_matches(path: str, roots: list[pathlib.Path]) -> bool:
    signatures = [pom_topology_signature(root / path) for root in roots]
    return signatures[0] is not None and signatures[0] == signatures[1] == signatures[2]


def cdi_descriptor_platform_match(path: str, roots: list[pathlib.Path]) -> bool:
    """只认可两处已知 CDI 描述符的 Java EE 到 Jakarta EE schema 迁移。"""
    if path not in CDI_PLATFORM_DESCRIPTORS:
        return False
    version, suffix = CDI_PLATFORM_DESCRIPTORS[path]
    legacy_namespace = "http://xmlns.jcp.org/xml/ns/javaee"
    modern_namespace = "https://jakarta.ee/xml/ns/jakartaee"
    schema_attribute = "{http://www.w3.org/2001/XMLSchema-instance}schemaLocation"
    legacy_attributes = {"bean-discovery-mode": "annotated", "version": "2.0"}
    if "helidon-cqrs" in path:
        legacy_attributes[schema_attribute] = (
            f"{legacy_namespace} {legacy_namespace}/beans_2_0.xsd")
    modern_attributes = {
        "bean-discovery-mode": "annotated",
        "version": version,
        schema_attribute: f"{modern_namespace} {modern_namespace}/beans_{suffix}.xsd",
    }
    for index, root in enumerate(roots):
        try:
            document = ET.parse(root / path).getroot()
        except (OSError, ET.ParseError):
            return False
        namespace = legacy_namespace if index == 0 else modern_namespace
        attributes = legacy_attributes if index == 0 else modern_attributes
        actual = dict(document.attrib)
        if schema_attribute in actual:
            actual[schema_attribute] = " ".join(actual[schema_attribute].split())
        if (document.tag != f"{{{namespace}}}beans" or actual != attributes
                or len(document) != 0 or (document.text or "").strip()):
            return False
    return True


def helidon_cdi_service_platform_match(path: str, roots: list[pathlib.Path]) -> bool:
    """只认可同一 Helidon CDI 扩展从 javax 到 jakarta 的 SPI 文件名迁移。"""
    if path not in HELIDON_CDI_SERVICE_NAMES:
        return False
    legacy, modern = HELIDON_CDI_SERVICE_NAMES
    for index, root in enumerate(roots):
        expected = legacy if index == 0 else modern
        unexpected = modern if index == 0 else legacy
        try:
            if (root / expected).read_bytes() != HELIDON_CDI_PROVIDER:
                return False
        except OSError:
            return False
        if (root / unexpected).exists():
            return False
    return True


def validate_platform_dependency_config_files(
        mappings: list[dict], snapshots: list[dict[str, str]]) -> dict[str, dict]:
    """只允许逐线哈希锁定的依赖治理配置差异，内容变化须重新审查。"""
    license_governance = {"config/license-selections.tsv",
                          "config/license-build-tool-exclusions.tsv"}
    validated = {}
    for item in mappings:
        path = item.get("path")
        hashes = item.get("sha256ByJdk")
        if (not isinstance(path, str)
                or not (path.startswith("config/dependencies/")
                        or path in license_governance)
                or pathlib.PurePosixPath(path).suffix not in (".json", ".csv", ".tsv")
                or path in validated
                or not isinstance(hashes, dict)
                or set(hashes) != {"8", "17", "21"}
                or not item.get("evidence")):
            raise ValueError(f"invalid platform dependency config mapping: {path}")
        expected = [hashes[jdk] for jdk in ("8", "17", "21")]
        if (all(value is None for value in expected)
                or len(set(expected)) == 1
                or any(value is not None and not re.fullmatch(r"[0-9a-f]{64}", value)
                       for value in expected)):
            raise ValueError(f"invalid platform dependency config hashes: {path}")
        actual = [snapshot.get(path) for snapshot in snapshots]
        if actual != expected:
            raise ValueError(f"stale platform dependency config mapping: {path}")
        validated[path] = item
    return validated


def validate_platform_build_config_files(
        mappings: list[dict], snapshots: list[dict[str, str]]) -> dict[str, dict]:
    """只豁免精确锁定的逐线构建配置与框架资源。"""
    allowed = {".mvn/jvm.config", ".mvn/maven.config",
               ".mvn/wrapper/maven-wrapper.properties",
               ".mvn/wrapper/MavenWrapperDownloader.java",
               "config/apache-2.0-header.txt",
               "ddd4j-data/ddd4j-data-event-store-jpa/src/test/resources/META-INF/persistence.xml",
               "ddd4j-data/ddd4j-data-event-store-panache/src/main/resources/META-INF/beans.xml",
               "ddd4j-samples/ddd4j-sample-helidon/src/main/resources/META-INF/beans.xml"}
    validated = {}
    for item in mappings:
        path = item.get("path")
        hashes = item.get("sha256ByJdk")
        if (path not in allowed or path in validated
                or not isinstance(hashes, dict)
                or set(hashes) != {"8", "17", "21"}
                or not item.get("evidence")):
            raise ValueError(f"invalid platform build config mapping: {path}")
        expected = [hashes[jdk] for jdk in ("8", "17", "21")]
        if (all(value is None for value in expected)
                or len(set(expected)) == 1
                or any(value is not None and not re.fullmatch(r"[0-9a-f]{64}", value)
                       for value in expected)):
            raise ValueError(f"invalid platform build config hashes: {path}")
        if [snapshot.get(path) for snapshot in snapshots] != expected:
            raise ValueError(f"stale platform build config mapping: {path}")
        validated[path] = item
    return validated


def validate_platform_tool_scripts(
        mappings: list[dict], snapshots: list[dict[str, str]]) -> dict[str, dict]:
    """仅分类已锁定的逐线构建脚本和依赖策略测试。"""
    allowed = {
        "scripts/dependencies/tests/test_uniform_contract.py",
        "scripts/test-maven-wrapper-jdk-compatibility.sh",
        "scripts/test-parent-consumer-version-isolation.sh",
        "scripts/verify-java8-dependency-baseline.sh",
        "scripts/verify-java8-source-compatibility.sh",
        "scripts/test_bom_import_conflicts.py",
        "scripts/test_maven4_model_contract.py",
        "scripts/test_platform_version_contract.py",
        "scripts/test_web_api_version_suite.py",
        "scripts/verify_bom_import_conflicts.py",
        "scripts/verify_maven4_model_contract.py",
        "scripts/verify_maven4_parent_contract.py",
        "scripts/verify_platform_version_contract.py",
    }
    jdk8_only = {
        "scripts/test-maven-wrapper-jdk-compatibility.sh",
        "scripts/test-parent-consumer-version-isolation.sh",
        "scripts/verify-java8-dependency-baseline.sh",
        "scripts/verify-java8-source-compatibility.sh",
    }
    validated = {}
    for item in mappings:
        path = item.get("path")
        hashes = item.get("sha256ByJdk")
        if (path not in allowed or path in validated
                or not isinstance(hashes, dict)
                or set(hashes) != {"8", "17", "21"}
                or not item.get("evidence")):
            raise ValueError(f"invalid platform tool script: {path}")
        expected = [hashes[jdk] for jdk in ("8", "17", "21")]
        if path == "scripts/dependencies/tests/test_uniform_contract.py":
            required = (True, True, True)
        else:
            required_index = 0 if path in jdk8_only else 2
            required = tuple(index == required_index for index in range(3))
        if (any((value is not None) != present
                for value, present in zip(expected, required))
                or any(value is not None and not re.fullmatch(r"[0-9a-f]{64}", value)
                       for value in expected)):
            raise ValueError(f"invalid platform tool script hashes: {path}")
        if [snapshot.get(path) for snapshot in snapshots] != expected:
            raise ValueError(f"stale platform tool script: {path}")
        validated[path] = item
    return validated


def validate_platform_workflow_files(mappings: list[dict], roots: list[pathlib.Path],
                                     snapshots: list[dict[str, str]]) -> dict[str, dict]:
    """锁定三线工作流，并核对各线 JDK 与发布坐标标记。"""
    allowed = {".github/workflows/deploy.yml", ".github/workflows/release-candidate.yml",
               ".github/workflows/verify.yml"}
    validated = {}
    for item in mappings:
        path = item.get("path")
        hashes = item.get("sha256ByJdk")
        if (path not in allowed or path in validated
                or not isinstance(hashes, dict) or set(hashes) != {"8", "17", "21"}
                or not item.get("evidence")
                or any(not isinstance(hashes[jdk], str)
                       or not re.fullmatch(r"[0-9a-f]{64}", hashes[jdk])
                       for jdk in ("8", "17", "21"))
                or len(set(hashes.values())) == 1):
            raise ValueError(f"invalid platform workflow mapping: {path}")
        if [snapshot.get(path) for snapshot in snapshots] != [hashes[jdk] for jdk in ("8", "17", "21")]:
            raise ValueError(f"stale platform workflow mapping: {path}")
        for root, (jdk, line) in zip(roots, ((8, "1.0.x"), (17, "2.0.x"), (21, "3.0.x"))):
            source = (root / path).read_text(encoding="utf-8")
            markers = [f"java-version: '{jdk}'"]
            if path.endswith("/deploy.yml"):
                markers.extend((f"name: Set up JDK {jdk}",
                                f"spring-extension/{line}.20260630-SNAPSHOT"))
            elif path.endswith("/release-candidate.yml"):
                markers.extend((f"name: Java {jdk} release candidate evidence",
                                f"Published non-SNAPSHOT {line} API baseline version"))
            else:
                markers.extend((f"name: Java {jdk} unit and contract",
                                f"spring-extension/{line}.20260630-SNAPSHOT"))
            if any(marker not in source for marker in markers):
                raise ValueError(f"wrong line markers in platform workflow: {path} JDK {jdk}")
        validated[path] = item
    return validated


def java_lexical_tokens(source: str) -> tuple[str, ...] | None:
    """仅剔除 Java 注释与格式；无法安全处理的语法保持 OPEN。"""
    # Unicode escape 在 Java 词法分析前展开，可能使注释边界发生变化。
    if "\\u" in source or '"""' in source:
        return None
    tokens = []
    index = 0
    length = len(source)
    while index < length:
        char = source[index]
        if char.isspace():
            index += 1
            continue
        if source.startswith("//", index):
            ending = source.find("\n", index + 2)
            index = length if ending < 0 else ending + 1
            continue
        if source.startswith("/*", index):
            ending = source.find("*/", index + 2)
            if ending < 0:
                return None
            index = ending + 2
            continue
        if char in ('"', "'"):
            start = index
            index += 1
            while index < length:
                if source[index] == "\\":
                    index += 2
                elif source[index] == char:
                    index += 1
                    break
                else:
                    index += 1
            else:
                return None
            tokens.append(source[start:index])
            continue
        if char.isalpha() or char in "_$":
            start = index
            index += 1
            while index < length and (source[index].isalnum() or source[index] in "_$"):
                index += 1
            tokens.append(source[start:index])
            continue
        if char.isdigit():
            start = index
            index += 1
            while index < length and (source[index].isalnum() or source[index] in "_."):
                index += 1
            tokens.append(source[start:index])
            continue
        operator = next((item for item in JAVA_OPERATORS if source.startswith(item, index)), None)
        if operator:
            tokens.append(operator)
            index += len(operator)
            continue
        tokens.append(char)
        index += 1
    return tuple(tokens)


def java_files_lexically_equal(path: str, roots: list[pathlib.Path]) -> bool:
    tokens = [java_tokens_with_normalized_imports((root / path).read_text(encoding="utf-8"))
              for root in roots]
    return tokens[0] is not None and tokens[0] == tokens[1] == tokens[2]


def java_platform_imports_only_match(path: str, roots: list[pathlib.Path],
                                     type_mappings: list[dict]) -> bool:
    """只映射已确认的平台导入命名空间；类型正文和其余导入逐 token 相同。"""
    sources = [(root / path).read_text(encoding="utf-8") for root in roots]
    dropwizard_imports = []
    for mapping in type_mappings:
        types = mapping["types"]
        old, modern = types["1.0.x"], types["2.0.x"]
        if (old.startswith("io.dropwizard.") and old != modern
                and modern == types["3.0.x"]):
            dropwizard_imports.append((old + ";", modern + ";"))

    def normalize(source: str) -> str:
        for old, new in (*PLATFORM_IMPORT_NAMESPACES, *dropwizard_imports):
            source = re.sub(r"(?m)^([ \t]*import[ \t]+(?:static[ \t]+)?)" + re.escape(old),
                            lambda match: match.group(1) + new, source)
        return source

    original = [java_tokens_with_normalized_imports(source) for source in sources]
    if any(tokens is None for tokens in original) or original[0] == original[1] == original[2]:
        return False
    mapped = [java_tokens_with_normalized_imports(normalize(source)) for source in sources]
    return mapped[0] is not None and mapped[0] == mapped[1] == mapped[2]


def java_serial_field_adaptation_matches(path: str, roots: list[pathlib.Path]) -> bool:
    """仅忽略 JDK 17/21 对 serialVersionUID 的 SOURCE 注解及对应导入。"""
    tokens = [java_tokens_with_normalized_imports((root / path).read_text(encoding="utf-8"))
              for root in roots]
    if any(item is None for item in tokens):
        return False
    serial_import = ("import", "java", ".", "io", ".", "Serial", ";")
    old = tokens[0]
    if any(old[index:index + len(serial_import)] == serial_import
           for index in range(len(old))) or any(old[index:index + 2] == ("@", "Serial")
                                               for index in range(len(old))):
        return False

    for modern in tokens[1:]:
        import_starts = [index for index in range(len(modern))
                         if modern[index:index + len(serial_import)] == serial_import]
        if len(import_starts) != 1:
            return False
        start = import_starts[0]
        remaining = list(modern[:start] + modern[start + len(serial_import):])
        annotations = 0
        index = 0
        while index < len(remaining):
            if remaining[index:index + 2] != ["@", "Serial"]:
                index += 1
                continue
            field_start = index + 2
            if field_start < len(remaining) and remaining[field_start] in ("private", "protected", "public"):
                field_start += 1
            field = remaining[field_start:field_start + 5]
            if field != ["static", "final", "long", "serialVersionUID", "="]:
                return False
            del remaining[index:index + 2]
            annotations += 1
        if annotations == 0 or tuple(remaining) != old:
            return False
    return True


def java_tokens_with_normalized_imports(source: str) -> tuple[str, ...] | None:
    """排序导入，去除正文未引用及唯一通配包已覆盖的普通显式导入。"""
    tokens = java_lexical_tokens(source)
    if tokens is None or "import" not in tokens:
        return tokens

    def identifier(token: str) -> bool:
        return bool(token) and (token[0].isalpha() or token[0] in "_$") and all(
            char.isalnum() or char in "_$" for char in token[1:])

    start = tokens.index("import")
    index = start
    imports: list[tuple[str, ...]] = []
    while index < len(tokens) and tokens[index] == "import":
        end = index + 1
        while end < len(tokens) and tokens[end] != ";":
            end += 1
        if end == len(tokens):
            return None
        statement = tokens[index:end + 1]
        body = statement[1:-1]
        if body and body[0] == "static":
            body = body[1:]
        if len(body) < 3 or not identifier(body[0]):
            return None
        position = 1
        while position < len(body):
            if body[position] != "." or position + 1 == len(body):
                return None
            next_token = body[position + 1]
            if next_token == "*":
                if position + 2 != len(body):
                    return None
            elif not identifier(next_token):
                return None
            position += 2
        imports.append(statement)
        index = end + 1
    wildcard_packages = [statement[1:-3] for statement in imports
                         if statement[1] != "static" and statement[-2] == "*"]
    static_wildcard = any(statement[1] == "static" and statement[-2] == "*"
                          for statement in imports)
    static_wildcard_owners = {statement[1:-3] for statement in imports
                              if statement[1] == "static" and statement[-2] == "*"}
    if len(static_wildcard_owners) == 1:
        imports = [statement for statement in imports
                   if not (statement[1] == "static" and statement[-2] != "*"
                           and statement[1:-3] in static_wildcard_owners)]
    if len(wildcard_packages) == 1 and not static_wildcard:
        imports = [statement for statement in imports
                   if statement[1] == "static" or statement[-2] == "*"
                   or statement[1:-3] != wildcard_packages[0]]
    body = tokens[index:]
    imports = [statement for statement in imports
               if statement[1] == "static" or statement[-2] == "*"
               or statement[-2] in body]
    ordered = tuple(token for statement in sorted(imports) for token in statement)
    return tokens[:start] + ordered + tokens[index:]


def task_for_difference(path: str, category: str) -> str:
    """按能力边界路由差异；任务号是待处理归属，不表示差异已解释。"""
    if category in ("historical-document", "product-document"):
        return "T09"
    if path.startswith(".github/"):
        return "T10"
    if path.startswith(("scripts/", "verification/three-line-parity/", "config/parity/")):
        return "T01"
    if path.startswith("config/dependencies/"):
        return "T03"
    if path.startswith("ddd4j-samples/"):
        return "T08"
    if category == "module":
        return "T03"
    if path.startswith("ddd4j-mq/"):
        return "T07"
    if path.startswith("ddd4j-data/"):
        return "T05"
    if path.startswith(("ddd4j-web/", "ddd4j-auth/")):
        return "T06"
    if path.startswith("ddd4j-runtime/"):
        return "T08"
    if path.startswith("ddd4j-core/") and "/cqrs/" in path:
        return "T05"
    if path.startswith(("ddd4j-core/", "ddd4j-annotation/", "ddd4j-kit/", "ddd4j-cache/")):
        return "T04"
    return "T00"


def source_api(root: pathlib.Path, paths: dict[str, str], output: pathlib.Path) -> dict[str, str]:
    """使用 JDK Compiler Tree API 从当前源码提取显式方法，不消费 CodeGraph 缓存。"""
    java_home = os.environ.get("DDD4J_JDK21_HOME")
    if not java_home:
        java_home = run("/usr/libexec/java_home", "-v", "21").strip()
    jdk = pathlib.Path(java_home)
    scanner = pathlib.Path(__file__).resolve().parents[1] / "verification/three-line-parity/ThreeLineSourceApiScanner.java"
    classes = output / "scanner-classes"
    classes.mkdir(parents=True, exist_ok=True)
    subprocess.run([str(jdk / "bin/javac"), "-d", str(classes), str(scanner)], check=True,
                   capture_output=True, text=True)
    source_paths = sorted(path for path in paths if path.endswith(".java") and "/src/main/java/" in path)
    if not source_paths:
        return {}
    completed = subprocess.run([str(jdk / "bin/java"), "-cp", str(classes),
                                "ThreeLineSourceApiScanner", str(root)],
                               input="\n".join(source_paths) + "\n", capture_output=True,
                               text=True, check=True)
    declarations = {}
    for line in completed.stdout.splitlines():
        path, symbol, value = line.split("\t", 2)
        declarations[path + "#" + symbol] = value
    return declarations


def validate_platform_api_mappings(mappings: list[dict], roots: list[pathlib.Path]) -> list[dict]:
    """平台独有公开成员必须使用完整 JVM 签名及三线共同测试精确登记。"""
    symbols = set()
    entries = set()
    for item in mappings:
        symbol = item.get("sourceSymbol", "")
        entry = item.get("compiledEntry", "")
        evidence = pathlib.PurePosixPath(item.get("evidence", ""))
        parts = symbol.split("#", 2)
        if len(parts) != 3 or "/src/main/java/" not in parts[0]:
            raise ValueError(f"invalid platform API source symbol: {symbol}")
        module = parts[0].split("/src/main/java/", 1)[0]
        owner = parts[1].replace(".", "/")
        method = parts[2].split("(", 1)[0]
        entry_parts = entry.split("|")
        bridge = item.get("compilerBridgeEntry", "")
        bridge_parts = bridge.split("|") if bridge else []
        if (not entry.startswith(f"{module}|{owner}|{method}|")
                or len(entry_parts) != 8
                or any("*" in part for index, part in enumerate(entry_parts) if index != 4)
                or symbol in symbols or entry in entries or not item.get("reason")):
            raise ValueError(f"invalid or duplicate platform API member: {symbol}")
        if bridge and (not bridge.startswith(f"{module}|{owner}|{method}|")
                       or len(bridge_parts) != 8
                       or any("*" in part for index, part in enumerate(bridge_parts) if index != 4)
                       or bridge in entries or bridge == entry):
            raise ValueError(f"invalid compiler bridge API member: {symbol}")
        if (evidence.is_absolute() or ".." in evidence.parts
                or "/src/test/java/" not in evidence.as_posix()
                or not all((root / evidence).is_file() for root in roots)):
            raise ValueError(f"platform API evidence missing in a line: {evidence}")
        symbols.add(symbol)
        entries.add(entry)
        if bridge:
            entries.add(bridge)
    return mappings


def validate_platform_files_only_on_jdk8(items: list[dict],
                                         roots: list[pathlib.Path]) -> dict[str, dict]:
    """只允许已锁定源码及三线行为证据的 JDK 8 平台入口。"""
    validated = {}
    for item in items:
        path = pathlib.PurePosixPath(item.get("path", ""))
        counterpart = pathlib.PurePosixPath(item.get("counterpart", ""))
        evidence = pathlib.PurePosixPath(item.get("evidence", ""))
        path_text = path.as_posix()
        digest = item.get("sha256", "")
        if (path.is_absolute() or ".." in path.parts
                or "/src/main/java/" not in path_text or path.suffix != ".java"
                or path_text in validated or len(digest) != 64
                or any(char not in "0123456789abcdef" for char in digest)
                or counterpart.is_absolute() or ".." in counterpart.parts
                or "/src/main/java/" not in counterpart.as_posix()
                or counterpart.suffix != ".java"
                or evidence.is_absolute() or ".." in evidence.parts
                or "/src/test/java/" not in evidence.as_posix()
                or not item.get("reason")):
            raise ValueError(f"invalid JDK 8-only platform file: {path_text}")
        if (not (roots[0] / path).is_file()
                or hashlib.sha256((roots[0] / path).read_bytes()).hexdigest() != digest
                or any((root / path).exists() for root in roots[1:])
                or not all((root / counterpart).is_file() for root in roots)
                or not all((root / evidence).is_file() for root in roots)):
            raise ValueError(f"stale JDK 8-only platform file evidence: {path_text}")
        validated[path_text] = item
    return validated


def validate_platform_resources_only_on_jdk8(items: list[dict],
                                             roots: list[pathlib.Path]) -> dict[str, dict]:
    """锁定低线配置资源，并核对高线注解内联了相同的默认值。"""
    validated = {}
    for item in items:
        path = pathlib.PurePosixPath(item.get("path", ""))
        counterpart = pathlib.PurePosixPath(item.get("counterpart", ""))
        evidence = pathlib.PurePosixPath(item.get("evidence", ""))
        path_text = path.as_posix()
        digest = item.get("sha256", "")
        property_name = item.get("property", "")
        default = item.get("default", "")
        if (path.is_absolute() or ".." in path.parts
                or "/src/main/resources/" not in path_text
                or path.suffix != ".properties" or path_text in validated
                or len(digest) != 64
                or any(char not in "0123456789abcdef" for char in digest)
                or counterpart.is_absolute() or ".." in counterpart.parts
                or "/src/main/java/" not in counterpart.as_posix()
                or counterpart.suffix != ".java"
                or evidence.is_absolute() or ".." in evidence.parts
                or "/src/test/java/" not in evidence.as_posix()
                or not property_name or "\n" in property_name
                or not default or "\n" in default or not item.get("reason")):
            raise ValueError(f"invalid JDK 8-only platform resource: {path_text}")
        old_annotation = f'@Scheduled(cron = "{{{property_name}}}")'
        new_annotation = f'@Scheduled(cron = "${{{property_name}:{default}}}")'
        if (not (roots[0] / path).is_file()
                or hashlib.sha256((roots[0] / path).read_bytes()).hexdigest() != digest
                or (roots[0] / path).read_text().strip() != f"{property_name}={default}"
                or any((root / path).exists() for root in roots[1:])
                or not all((root / counterpart).is_file() for root in roots)
                or old_annotation not in (roots[0] / counterpart).read_text()
                or any(new_annotation not in (root / counterpart).read_text()
                       for root in roots[1:])
                or not all((root / evidence).is_file() for root in roots)):
            raise ValueError(f"stale JDK 8-only platform resource evidence: {path_text}")
        validated[path_text] = item
    return validated


def validate_platform_cdi_resources_only_on_jdk8(items: list[dict],
                                                 roots: list[pathlib.Path]) -> dict[str, dict]:
    """只登记有三线 Arc 作用域测试的低线 Java EE CDI 描述符。"""
    validated = {}
    for item in items:
        path = pathlib.PurePosixPath(item.get("path", ""))
        evidence = pathlib.PurePosixPath(item.get("evidence", ""))
        source = path.as_posix()
        test = evidence.as_posix()
        digest = item.get("sha256", "")
        test_digest = item.get("testSha256", "")
        if (path.is_absolute() or ".." in path.parts
                or not source.endswith("/src/main/resources/META-INF/beans.xml")
                or source in validated
                or evidence.is_absolute() or ".." in evidence.parts
                or "/src/test/java/" not in test or evidence.suffix != ".java"
                or not all(isinstance(value, str) and re.fullmatch(r"[0-9a-f]{64}", value)
                           for value in (digest, test_digest))
                or not item.get("reason")):
            raise ValueError(f"invalid JDK 8-only CDI descriptor: {source}")
        descriptor = roots[0] / path
        tests = [root / evidence for root in roots]
        if (not descriptor.is_file()
                or hashlib.sha256(descriptor.read_bytes()).hexdigest() != digest
                or any((root / path).exists() for root in roots[1:])
                or not all(file.is_file()
                           and hashlib.sha256(file.read_bytes()).hexdigest() == test_digest
                           for file in tests)):
            raise ValueError(f"stale JDK 8-only CDI descriptor evidence: {source}")
        contents = tests[0].read_text(encoding="utf-8")
        assertion = item.get("assertion", "scope")
        if assertion == "scope":
            markers = ("@QuarkusTest", "Arc.container()", "ApplicationScoped")
        elif assertion == "available":
            markers = ("@QuarkusTest", "Arc.container()", "isAvailable()")
            http_path = pathlib.PurePosixPath(item.get("httpEvidence", ""))
            http_hashes = item.get("httpTestSha256")
            module = source.split("/src/main/resources/", 1)[0]
            if (http_path.is_absolute() or ".." in http_path.parts
                    or not http_path.as_posix().startswith(module + "/src/test/java/")
                    or http_path.suffix != ".java"
                    or not isinstance(http_hashes, list) or len(http_hashes) != 3
                    or not all(isinstance(digest, str)
                               and re.fullmatch(r"[0-9a-f]{64}", digest)
                               for digest in http_hashes)):
                raise ValueError(f"invalid CDI HTTP contract evidence: {source}")
            for root, digest in zip(roots, http_hashes):
                http_file = root / http_path
                if (not http_file.is_file()
                        or hashlib.sha256(http_file.read_bytes()).hexdigest() != digest):
                    raise ValueError(f"stale CDI HTTP contract evidence: {source}")
                http_content = http_file.read_text(encoding="utf-8")
                if ("@QuarkusTest" not in http_content
                        or "extends AbstractWebContractTest" not in http_content):
                    raise ValueError(f"missing CDI HTTP contract: {source}")
        else:
            raise ValueError(f"unsupported CDI assertion: {source}")
        if not all(marker in contents for marker in markers):
            raise ValueError(f"missing CDI {assertion} assertion: {test}")
        beans = ET.fromstring(descriptor.read_bytes())
        if (beans.tag != "{http://xmlns.jcp.org/xml/ns/javaee}beans"
                or beans.attrib != {"bean-discovery-mode": "annotated", "version": "2.0"}
                or list(beans) or (beans.text or "").strip()):
            raise ValueError(f"unsupported JDK 8-only CDI descriptor: {source}")
        validated[source] = item
    return validated


def validate_platform_tests_only_on_jdk8(items: list[dict],
                                         roots: list[pathlib.Path]) -> dict[str, dict]:
    """锁定低线专用测试及三线对应容器契约，避免用宽泛排除掩盖测试缺失。"""
    def valid_sha256(value: object) -> bool:
        return (isinstance(value, str) and len(value) == 64
                and all(char in "0123456789abcdef" for char in value))

    validated = {}
    for item in items:
        path = pathlib.PurePosixPath(item.get("path", ""))
        counterpart = pathlib.PurePosixPath(item.get("counterpart", ""))
        path_text = path.as_posix()
        digest = item.get("sha256", "")
        counterpart_digests = item.get("counterpartSha256", [])
        if (path.is_absolute() or ".." in path.parts
                or "/src/test/java/" not in path_text or path.suffix != ".java"
                or path_text in validated or not valid_sha256(digest)
                or counterpart.is_absolute() or ".." in counterpart.parts
                or "/src/test/java/" not in counterpart.as_posix()
                or counterpart.suffix != ".java"
                or not isinstance(counterpart_digests, list)
                or len(counterpart_digests) != len(roots)
                or not all(valid_sha256(value) for value in counterpart_digests)
                or not item.get("reason")):
            raise ValueError(f"invalid JDK 8-only platform test: {path_text}")
        if (not (roots[0] / path).is_file()
                or hashlib.sha256((roots[0] / path).read_bytes()).hexdigest() != digest
                or any((root / path).exists() for root in roots[1:])
                or any(not (root / counterpart).is_file()
                       or hashlib.sha256((root / counterpart).read_bytes()).hexdigest() != expected
                       for root, expected in zip(roots, counterpart_digests))):
            raise ValueError(f"stale JDK 8-only platform test: {path_text}")
        validated[path_text] = item
    return validated


def validate_platform_test_namespace_mappings(
        mappings: list[dict], roots: list[pathlib.Path]) -> dict[str, dict]:
    """仅接受已列明架构规则中精确的 Java EE/Jakarta EE 命名空间替换。"""
    allowed = {
        "ddd4j-data/ddd4j-data-cqrs-helidon/src/test/java/"
        "io/ddd4j/data/cqrs/helidon/arch/CqrsHelidonModuleIndependenceTest.java",
        "ddd4j-data/ddd4j-data-projection-helidon/src/test/java/"
        "io/ddd4j/data/projection/helidon/arch/ProjectionHelidonModuleIndependenceTest.java",
        "ddd4j-data/ddd4j-data-cqrs-quarkus/src/test/java/"
        "io/ddd4j/data/cqrs/quarkus/arch/CqrsQuarkusModuleIndependenceTest.java",
    }
    validated = {}
    for item in mappings:
        path = item.get("path")
        hashes = item.get("sha256ByJdk")
        if (path not in allowed or path in validated
                or not isinstance(hashes, dict)
                or set(hashes) != {"8", "17", "21"}
                or not all(isinstance(value, str) and re.fullmatch(r"[0-9a-f]{64}", value)
                           for value in hashes.values())
                or not item.get("evidence")):
            raise ValueError(f"invalid platform test namespace mapping: {path}")
        contents = []
        for jdk, root in zip(("8", "17", "21"), roots):
            source = root / path
            if (not source.is_file()
                    or hashlib.sha256(source.read_bytes()).hexdigest() != hashes[jdk]):
                raise ValueError(f"stale platform test namespace mapping: {path}")
            contents.append(source.read_text(encoding="utf-8"))
        quarkus = path.endswith("/CqrsQuarkusModuleIndependenceTest.java")
        if quarkus and any(
                '"jakarta.persistence.."' not in content
                or '"javax.persistence.."' not in content
                for content in contents):
            raise ValueError(f"platform test namespace contract differs: {path}")
        source = contents[0]
        if quarkus:
            source = source.replace('"javax.persistence.."', '"__LEGACY_PERSISTENCE__"')
        normalized = (source.replace("Javax", "Jakarta")
                      .replace("javax", "jakarta")
                      .replace("@since 1.0.x", "@since 2.0.x"))
        if quarkus:
            normalized = normalized.replace('"__LEGACY_PERSISTENCE__"', '"javax.persistence.."')
        if ("@ArchTest" not in contents[0] or '"javax.."' not in contents[0]
                or '"jakarta.."' not in contents[1]
                or normalized != contents[1] or contents[1] != contents[2]):
            raise ValueError(f"platform test namespace contract differs: {path}")
        validated[path] = item
    return validated


def compiled_api_gate(roots: list[pathlib.Path], output: pathlib.Path,
                      policy: dict[str, object], source_rows: list[dict[str, str]],
                      module: str | None = None) -> dict[str, object]:
    """独立验证编译 API；源码清单差异不应遮蔽独立的构建阻塞或 API 漂移。"""
    if any(not (root / "mvnw").is_file() for root in roots):
        return {"status": "BLOCKED", "reason": "Maven Wrapper is missing in at least one line"}
    results = compile_roots(roots, output, module)
    if any(result["exit"] != 0 for result in results):
        return {"status": "BLOCKED", "reason": "clean compile failed", "compiles": results}
    mappings = policy.get("typeMappings", [])
    bridge_sets = [set() for _ in roots]
    class_sets = [({identity: path for identity, path in class_files(root).items()
                    if identity.startswith(module + "|")} if module else class_files(root))
                  for root in roots]
    if module and any(not classes for classes in class_sets):
        return {"status": "BLOCKED", "reason": f"no compiled classes for {module}",
                "compiles": results}
    api_sets = [javap_api(classes, mappings, version, bridge_entries=bridges)
                for classes, version, bridges in zip(class_sets, ("1.0.x", "2.0.x", "3.0.x"), bridge_sets)]
    differences = {f"{left}-{right}": pair_diff(api_sets[left - 1], api_sets[right - 1])
                   for left, right in ((1, 2), (2, 3), (1, 3))}
    source_by_symbol = {row["path"]: row for row in source_rows if row["category"] == "api"}
    adapted = []
    adapted_only_on_jdk8 = []
    adapted_bridges = []
    stale = []
    for item in policy.get("platformApiMembersMissingOnJdk8", []):
        if module and not item["sourceSymbol"].startswith(module + "/"):
            continue
        entry = item["compiledEntry"]
        bridge = item.get("compilerBridgeEntry")
        source = source_by_symbol.get(item["sourceSymbol"])
        explicit_matches = (entry not in api_sets[0] and entry in api_sets[1] and entry in api_sets[2]
                            and source and source["jdk8"] == "MISSING"
                            and source["jdk17"] != "MISSING"
                            and source["jdk17"] == source["jdk21"])
        bridge_matches = (not bridge or (bridge not in api_sets[0]
                                         and bridge in bridge_sets[1] and bridge in bridge_sets[2]))
        if explicit_matches and bridge_matches:
            adapted.append(item)
            if bridge:
                adapted_bridges.append(bridge)
        else:
            stale.append(bridge if explicit_matches and bridge else entry)
    for item in policy.get("platformApiMembersOnlyOnJdk8", []):
        if module and not item["sourceSymbol"].startswith(module + "/"):
            continue
        entry = item["compiledEntry"]
        source = source_by_symbol.get(item["sourceSymbol"])
        if (entry in api_sets[0] and entry not in api_sets[1] and entry not in api_sets[2]
                and source and source["jdk8"] == item.get("sourceDeclaration")
                and source["jdk17"] == source["jdk21"] == "MISSING"):
            adapted_only_on_jdk8.append(item)
        else:
            stale.append(entry)
    accepted_high = {item["compiledEntry"] for item in adapted} | set(adapted_bridges)
    accepted_low = {item["compiledEntry"] for item in adapted_only_on_jdk8}
    unresolved = {
        pair: {"only_left": [entry for entry in diff["only_left"]
                              if not (pair in ("1-2", "1-3") and entry in accepted_low)],
               "only_right": [entry for entry in diff["only_right"]
                              if not (pair in ("1-2", "1-3") and entry in accepted_high)]}
        for pair, diff in differences.items()
    }
    passed = not stale and all(not pair["only_left"] and not pair["only_right"]
                               for pair in unresolved.values())
    result = {"status": "PASS" if passed else "FAIL", "compiles": results,
              "apiCounts": [len(api) for api in api_sets], "pairs": differences,
              "platformAdaptations": adapted + adapted_only_on_jdk8,
              "platformCompilerBridges": adapted_bridges,
              "stalePlatformApiMappings": stale,
              "unresolved": unresolved}
    if module:
        result["module"] = module
        result["scope"] = "module-compiled-api-only"
        result["fullConvergence"] = "NOT_RUN"
        result["roots"] = [str(root) for root in roots]
        result["sourceApiDifferenceCount"] = len(source_rows)
    (output / "compiled-api.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n",
                                                encoding="utf-8")
    return {"status": result["status"], "artifact": "compiled-api.json", "compiles": results}


def classify_compiled_source_api(rows: list[dict[str, str]], roots: list[pathlib.Path],
                                 output: pathlib.Path, compiled: dict[str, object]) -> None:
    """仅在三线干净编译后，将源码声明差异中已由 class API 证明等价的成员单列。"""
    if compiled.get("artifact") != "compiled-api.json":
        return
    result = json.loads((output / "compiled-api.json").read_text(encoding="utf-8"))
    if any(item["exit"] != 0 for item in result["compiles"]):
        return

    def source_owner(identity: str) -> str:
        module, binary = identity.split("|", 1)
        return module + "|" + binary.replace("/", ".").replace("$", ".")

    present = [{source_owner(identity) for identity in class_files(root)} for root in roots]
    divergent = set()
    for pair in result["pairs"].values():
        for side in ("only_left", "only_right"):
            for member in pair[side]:
                divergent.add(source_owner("|".join(member.split("|", 2)[:2])))

    for row in rows:
        if row["category"] != "api" or row["status"] != "OPEN":
            continue
        parts = row["path"].split("#", 2)
        if len(parts) != 3 or "/src/main/java/" not in parts[0]:
            continue
        owner = parts[0].split("/src/main/java/", 1)[0] + "|" + parts[1]
        if owner not in divergent and all(owner in classes for classes in present):
            row["status"] = "COMPILED_API_EQUIVALENT"
            row["evidence"] = "compiled-api.json: class exists in all lines with equal public JVM API"
    for item in result.get("platformAdaptations", []):
        for row in rows:
            if (row["category"] == "api" and row["path"] == item["sourceSymbol"]
                    and row["status"] in ("OPEN", "PLATFORM_API_PENDING_COMPILE")):
                row["status"] = "PLATFORM_API_ADAPTED"
                row["evidence"] = item["evidence"]


def classify_declared_platform_api(rows: list[dict[str, str]], policy: dict[str, object]) -> None:
    """精确源码形态已登记时暂缓 OPEN；只有编译门禁能确认平台适配。"""
    missing = {item["sourceSymbol"]: item for item in
               policy.get("platformApiMembersMissingOnJdk8", [])}
    only_jdk8 = {item["sourceSymbol"]: item for item in
                 policy.get("platformApiMembersOnlyOnJdk8", [])}
    for row in rows:
        if row["category"] != "api" or row["status"] != "OPEN":
            continue
        absent = missing.get(row["path"])
        present = only_jdk8.get(row["path"])
        if ((absent and row["jdk8"] == "MISSING" and row["jdk17"] != "MISSING"
             and row["jdk17"] == row["jdk21"])
                or (present and row["jdk8"] == present.get("sourceDeclaration")
                    and row["jdk17"] == row["jdk21"] == "MISSING")):
            row["status"] = "PLATFORM_API_PENDING_COMPILE"
            row["evidence"] = (absent or present)["evidence"]


def inventory_gate(roots: list[pathlib.Path], output: pathlib.Path, policy_path: pathlib.Path,
                   phase: str, fail_on_open: bool = False) -> int:
    policy = json.loads(policy_path.read_text(encoding="utf-8"))
    if policy.get("schemaVersion") != 1:
        raise ValueError("unsupported three-line policy schema")
    validate_type_mappings(policy.get("typeMappings", []), roots)
    validate_platform_api_mappings(
        policy.get("platformApiMembersMissingOnJdk8", [])
        + policy.get("platformApiMembersOnlyOnJdk8", []), roots)
    platform = {item["path"]: item for item in policy.get("platformItemsMissingOnJdk8", [])}
    platform_only_jdk8 = validate_platform_files_only_on_jdk8(
        policy.get("platformFilesOnlyOnJdk8", []), roots)
    platform_resources_only_jdk8 = validate_platform_resources_only_on_jdk8(
        policy.get("platformResourcesOnlyOnJdk8", []), roots)
    platform_cdi_resources_only_jdk8 = validate_platform_cdi_resources_only_on_jdk8(
        policy.get("platformCdiResourcesOnlyOnJdk8", []), roots)
    platform_tests_only_jdk8 = validate_platform_tests_only_on_jdk8(
        policy.get("platformTestsOnlyOnJdk8", []), roots)
    platform_content = validate_platform_content_mappings(
        policy.get("platformContentMappings", []), roots)
    platform_syntax = validate_platform_syntax_mappings(
        policy.get("platformSyntaxMappings", []), roots)
    platform_test_syntax = validate_platform_test_syntax_mappings(
        policy.get("platformTestSyntaxMappings", []), roots, platform_syntax)
    platform_test_helpers = validate_platform_test_helper_mappings(
        policy.get("platformTestHelperMappings", []), roots)
    platform_test_namespace = validate_platform_test_namespace_mappings(
        policy.get("platformTestNamespaceMappings", []), roots)
    platform_test_evidence = {}
    for source, mapping in platform_syntax.items():
        if mapping.get("testSha256ByJdk"):
            platform_test_evidence.setdefault(mapping["evidence"], []).append((source, mapping))
    historical_sections = validate_historical_document_sections(
        policy.get("historicalDocumentSections", []), roots)
    snapshots = [inventory_files(root) for root in roots]
    dependency_configs = validate_platform_dependency_config_files(
        policy.get("platformDependencyConfigFiles", []), snapshots)
    build_configs = validate_platform_build_config_files(
        policy.get("platformBuildConfigFiles", []), snapshots)
    tool_scripts = validate_platform_tool_scripts(
        policy.get("platformToolScripts", []), snapshots)
    workflow_configs = validate_platform_workflow_files(
        policy.get("platformWorkflowFiles", []), roots, snapshots)
    rows = []
    for path in sorted(set().union(*(snapshot.keys() for snapshot in snapshots))):
        values = [snapshot.get(path) for snapshot in snapshots]
        if values[0] == values[1] == values[2]:
            continue
        category = inventory_category(path)
        status = "OPEN"
        evidence = ""
        if category == "historical-document":
            status = "HISTORICAL"
        elif (category in ("product-document", "other")
              and indexed_javadoc_history_matches(path, roots, values)):
            status = "HISTORICAL"
            evidence = "docs/superpowers/historical-evidence-index.md"
        elif (category == "product-document" and path in historical_sections
              and all(value is not None for value in values)
              and historical_document_section_matches(path, roots, historical_sections[path])):
            status = "HISTORICAL_SECTION"
            evidence = historical_sections[path]["evidence"]
        elif (category == "module" and all(value is not None for value in values)
              and platform_config_matches(path, roots)):
            status = "PLATFORM_CONFIG"
            evidence = ("current POMs: groupId/artifactId, packaging and reactor child set match; "
                        "per-line dependency/plugin correctness remains in T03 strict gate")
        elif path in dependency_configs:
            status = "PLATFORM_CONFIG"
            evidence = dependency_configs[path]["evidence"]
        elif path in build_configs:
            status = "PLATFORM_CONFIG"
            evidence = build_configs[path]["evidence"]
        elif path in tool_scripts:
            status = "PLATFORM_CONFIG"
            evidence = tool_scripts[path]["evidence"]
        elif path in workflow_configs:
            status = "PLATFORM_CONFIG"
            evidence = workflow_configs[path]["evidence"]
        elif (category == "configuration" and all(value is not None for value in values)
              and cdi_descriptor_platform_match(path, roots)):
            status = "PLATFORM_ADAPTED"
            evidence = "current CDI beans.xml: annotated discovery, empty body and exact Java EE/Jakarta EE schema"
        elif helidon_cdi_service_platform_match(path, roots):
            status = "PLATFORM_ADAPTED"
            evidence = "current Helidon CDI SPI: exact provider and javax/Jakarta filename transition"
        elif path in platform and values[0] is None and values[1] is not None and values[2] is not None:
            item = platform[path]
            if item.get("decision") == "unsupported" and item.get("evidence"):
                status = "PLATFORM_UNSUPPORTED"
                evidence = item["evidence"]
        elif path in platform_only_jdk8 and values[0] is not None and values[1] is None and values[2] is None:
            status = "PLATFORM_ADAPTED"
            evidence = platform_only_jdk8[path]["evidence"]
        elif (path in platform_resources_only_jdk8 and values[0] is not None
              and values[1] is None and values[2] is None):
            status = "PLATFORM_ADAPTED"
            evidence = platform_resources_only_jdk8[path]["evidence"]
        elif (path in platform_cdi_resources_only_jdk8 and values[0] is not None
              and values[1] is None and values[2] is None):
            status = "PLATFORM_ADAPTED"
            evidence = platform_cdi_resources_only_jdk8[path]["evidence"]
        elif path in platform_tests_only_jdk8 and values[0] is not None and values[1] is None and values[2] is None:
            status = "PLATFORM_ADAPTED"
            evidence = platform_tests_only_jdk8[path]["counterpart"]
        elif (path in platform_content and all(value is not None for value in values)
              and platform_content_matches(path, roots, platform_content[path])):
            status = "PLATFORM_ADAPTED"
            evidence = platform_content[path]["evidence"]
        elif (path in platform_syntax and all(value is not None for value in values)
              and platform_syntax_matches(path, roots, platform_syntax[path])):
            status = "PLATFORM_ADAPTED"
            evidence = platform_syntax[path]["evidence"]
        elif (category == "test" and path in platform_test_evidence
              and all(value is not None for value in values)
              and all(platform_syntax_matches(source, roots, mapping)
                      for source, mapping in platform_test_evidence[path])):
            status = "PLATFORM_ADAPTED"
            evidence = "; ".join(source for source, _ in platform_test_evidence[path])
        elif (category == "test" and path in platform_test_syntax
              and all(value is not None for value in values)):
            status = "PLATFORM_ADAPTED"
            evidence = platform_test_syntax[path]["productionPath"]
        elif (category == "test" and path in platform_test_helpers
              and all(value is not None for value in values)):
            status = "PLATFORM_ADAPTED"
            evidence = platform_test_helpers[path]["contractPath"]
        elif (category == "test" and path in platform_test_namespace
              and all(value is not None for value in values)):
            status = "PLATFORM_ADAPTED"
            evidence = platform_test_namespace[path]["evidence"]
        elif (category in ("production", "test") and all(value is not None for value in values)
              and java_serial_field_adaptation_matches(path, roots)):
            status = "PLATFORM_ADAPTED"
            evidence = "JDK 17/21 java.io.Serial annotation on unchanged serialVersionUID field"
        elif (category in ("production", "test") and all(value is not None for value in values)
              and java_files_lexically_equal(path, roots)):
            status = "LEXICALLY_EQUIVALENT"
            evidence = "current Java source: identical tokens after removing comments, whitespace and import order"
        elif (category in ("production", "test") and all(value is not None for value in values)
              and java_platform_imports_only_match(path, roots, policy.get("typeMappings", []))):
            status = "PLATFORM_ADAPTED"
            evidence = ("current Java source: only approved javax/Jakarta, Jackson, or exact "
                        "Dropwizard type imports differ; "
                        "remaining tokens match; compiled API and behavior require separate gates")
        reason = "missing" if any(value is None for value in values) else "content"
        stable_id = hashlib.sha256((category + "\0" + path + "\0" + reason).encode()).hexdigest()[:16]
        rows.append({"id": stable_id, "path": path, "category": category,
                     "jdk8": values[0] or "MISSING", "jdk17": values[1] or "MISSING",
                     "jdk21": values[2] or "MISSING", "reason": reason,
                     "status": status, "task": task_for_difference(path, category), "evidence": evidence})
    output.mkdir(parents=True, exist_ok=True)
    apis = [source_api(root, snapshot, output) for root, snapshot in zip(roots, snapshots)]
    for symbol in sorted(set().union(*(api.keys() for api in apis))):
        values = [api.get(symbol) for api in apis]
        if values[0] == values[1] == values[2]:
            continue
        reason = "missing" if any(value is None for value in values) else "signature"
        rows.append({"id": hashlib.sha256(("api\0" + symbol).encode()).hexdigest()[:16],
                     "path": symbol, "category": "api", "jdk8": values[0] or "MISSING",
                     "jdk17": values[1] or "MISSING", "jdk21": values[2] or "MISSING",
                     "reason": reason, "status": "OPEN", "task": task_for_difference(symbol, "api"),
                     "evidence": ""})
    classify_declared_platform_api(rows, policy)
    rows.sort(key=lambda row: (row["path"], row["category"]))
    compiled = (compiled_api_gate(roots, output, policy, rows) if phase == "verify"
                else {"status": "NOT_RUN", "reason": "inventory phase"})
    if phase == "verify":
        classify_compiled_source_api(rows, roots, output, compiled)
    counts = {status: sum(row["status"] == status for row in rows)
              for status in ("OPEN", "PLATFORM_UNSUPPORTED", "PLATFORM_ADAPTED", "PLATFORM_API_ADAPTED",
                             "PLATFORM_API_PENDING_COMPILE",
                             "PLATFORM_CONFIG", "HISTORICAL", "HISTORICAL_SECTION",
                             "COMPILED_API_EQUIVALENT", "LEXICALLY_EQUIVALENT")}
    report = {"schemaVersion": 1, "phase": phase, "roots": [str(root) for root in roots],
              "policySha256": hashlib.sha256(policy_path.read_bytes()).hexdigest(),
              "fileCounts": [len(snapshot) for snapshot in snapshots], "compiledApi": compiled,
              "differences": rows}
    (output / "inventory.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n",
                                            encoding="utf-8")
    with (output / "differences.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=("id", "path", "category", "jdk8", "jdk17",
                                                    "jdk21", "reason", "status", "task", "evidence"))
        writer.writeheader()
        writer.writerows(rows)
    (output / "summary.md").write_text(
        "# 三线当前文件差异\n\n" + "\n".join(f"- {name}: {count}" for name, count in counts.items())
        + "\n- compiled API: " + compiled["status"] + "\n\n"
        + "PLATFORM_API_PENDING_COMPILE 仅代表当前源码声明符合精确登记；必须完成编译 API 门禁才能验收。\n"
        + "PLATFORM_CONFIG 仅证明所列配置的精确适配：POM 依赖由 T03 严格门禁验证，工作流由 lint 和实际 CI 验证。\n"
        + "文件内容差异仍须进一步判定语义与公开 API；本清单不代表行为验证。\n",
        encoding="utf-8")
    print(f"phase={phase} differences={len(rows)} open={counts['OPEN']} "
          f"compiled_api={compiled['status']} output={output}")
    return 1 if ((fail_on_open and counts["OPEN"])
                 or (phase == "verify" and (counts["OPEN"] or counts["PLATFORM_API_PENDING_COMPILE"]
                                            or compiled["status"] != "PASS"))) else 0


def module_dirs(paths: set[str]) -> set[str]:
    modules = set()
    for path in paths:
        candidate = pathlib.PurePosixPath(path)
        if candidate.name != "pom.xml" or str(candidate.parent) == "." or excluded(path):
            continue
        modules.add(str(candidate.parent))
    return modules


def production_sources(paths: set[str]) -> set[str]:
    return {
        path
        for path in paths
        if "/src/main/java/" in path and path.endswith(".java") and not excluded(path)
    }


def normalize_descriptor(value: str, mappings=(), line: str = "") -> str:
    """只归一化政策逐项列明的第三方 JVM 类型，不按包前缀放行。"""
    seen = set()
    for mapping in mappings:
        identifier = mapping.get("id", "")
        types = mapping.get("types", {})
        aliases = mapping.get("aliases", {})
        if (not re.fullmatch(r"[a-z][a-z0-9-]*", identifier)
                or set(types) != {"1.0.x", "2.0.x", "3.0.x"}
                or not isinstance(aliases, dict)
                or not set(aliases).issubset(types)
                or not mapping.get("reason") or not mapping.get("evidence")):
            raise ValueError(f"invalid exact type mapping: {identifier}")
        for candidates in aliases.values():
            if not isinstance(candidates, list) or not candidates:
                raise ValueError(f"invalid exact type aliases: {identifier}")
        for java_type in [*types.values(), *(name for names in aliases.values() for name in names)]:
            if not isinstance(java_type, str):
                raise ValueError(f"non-exact Java type in mapping {identifier}: {java_type}")
            if not re.fullmatch(r"[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)+", java_type):
                raise ValueError(f"non-exact Java type in mapping {identifier}: {java_type}")
            if java_type.startswith("io.ddd4j."):
                raise ValueError(f"own API type cannot be mapped: {java_type}")
        if not line or line not in types:
            raise ValueError(f"mapping {identifier} requires a version line")
        for java_type in [types[line], *aliases.get(line, [])]:
            source = "L" + java_type.replace(".", "/")
            if source in seen:
                raise ValueError(f"duplicate mapped type for {line}: {source}")
            seen.add(source)
            target = "LDDD4J_MAPPED/" + identifier
            value = re.sub(re.escape(source) + r"(?=[;<])", target, value)
    return value


def validate_type_mappings(mappings, roots: list[pathlib.Path]) -> None:
    """在源码清单阶段即拒绝无证据、重复或过宽映射。"""
    identifiers = set()
    for mapping in mappings:
        identifier = mapping.get("id", "")
        if identifier in identifiers:
            raise ValueError(f"duplicate type mapping id: {identifier}")
        identifiers.add(identifier)
        evidence = pathlib.PurePosixPath(mapping.get("evidence", ""))
        if evidence.is_absolute() or ".." in evidence.parts:
            raise ValueError(f"invalid type mapping evidence path: {evidence}")
        if not all((root / evidence).is_file() for root in roots):
            raise ValueError(f"type mapping evidence missing in a line: {evidence}")
        if mapping.get("aliases"):
            alias_evidence = pathlib.PurePosixPath(mapping.get("aliasEvidence", ""))
            if alias_evidence.is_absolute() or ".." in alias_evidence.parts:
                raise ValueError(f"invalid type mapping alias evidence path: {alias_evidence}")
            if not all((root / alias_evidence).is_file() for root in roots):
                raise ValueError(f"type mapping alias evidence missing in a line: {alias_evidence}")
    for version in ("1.0.x", "2.0.x", "3.0.x"):
        normalize_descriptor("", mappings, version)


def validate_platform_content_mappings(mappings, roots: list[pathlib.Path]) -> dict[str, dict]:
    """只接受有三线文件与测试证据的逐文件精确平台适配。"""
    by_path = {}
    for mapping in mappings:
        path = pathlib.PurePosixPath(mapping.get("path", ""))
        evidence = pathlib.PurePosixPath(mapping.get("evidence", ""))
        source = path.as_posix()
        if (path.is_absolute() or ".." in path.parts or "*" in source
                or "/src/main/java/" not in source and "/src/test/java/" not in source
                or source in by_path):
            raise ValueError(f"invalid or duplicate platform content path: {source}")
        if (evidence.is_absolute() or ".." in evidence.parts
                or "/src/test/java/" not in evidence.as_posix()
                or not all((root / path).is_file() and (root / evidence).is_file() for root in roots)):
            raise ValueError(f"platform content mapping lacks three-line source or test: {source}")
        replacements = mapping.get("replacements", [])
        if not replacements or not mapping.get("reason"):
            raise ValueError(f"platform content mapping lacks replacements or reason: {source}")
        target_lines = mapping.get("targetLines", ["2.0.x", "3.0.x"])
        if target_lines not in (["2.0.x", "3.0.x"], ["2.0.x"], ["3.0.x"]):
            raise ValueError(f"invalid platform content target lines: {source}")
        originals = set()
        jackson_mapper_imports = {
            ("import com.fasterxml.jackson.databind.JsonNode;",
             "import tools.jackson.databind.JsonNode;"),
            ("import com.fasterxml.jackson.databind.ObjectMapper;",
             "import tools.jackson.databind.ObjectMapper;"),
            ("import com.fasterxml.jackson.databind.json.JsonMapper;",
             "import tools.jackson.databind.json.JsonMapper;"),
        }
        exact_test_adaptations = {
            ("ddd4j-data/ddd4j-data-event-store-jdbi/src/test/java/io/ddd4j/data/event/store/jdbi/MysqlEventStoreContainerSupport.java",
             "import org.testcontainers.containers.MySQLContainer;",
             "import org.testcontainers.mysql.MySQLContainer;", ("3.0.x",)),
            ("ddd4j-data/ddd4j-data-event-store-jdbi/src/test/java/io/ddd4j/data/event/store/jdbi/MysqlEventStoreContainerSupport.java",
             "static final MySQLContainer<?> MYSQL = new MySQLContainer<>(",
             "static final MySQLContainer MYSQL = new MySQLContainer(", ("3.0.x",)),
            ("ddd4j-web/ddd4j-web-webflux/src/test/java/io/ddd4j/web/webflux/ApiVersionWebFilterTest.java",
             "import com.fasterxml.jackson.databind.ObjectMapper;",
             "import tools.jackson.databind.json.JsonMapper;", ("3.0.x",)),
            ("ddd4j-web/ddd4j-web-webflux/src/test/java/io/ddd4j/web/webflux/ApiVersionWebFilterTest.java",
             "new GlobalErrorWebExceptionHandler(new GlobalErrorAttributes(), new ObjectMapper(),",
             "new GlobalErrorWebExceptionHandler(new GlobalErrorAttributes(), JsonMapper.builder().build(),", ("3.0.x",)),
            ("ddd4j-data/ddd4j-data-projection-jpa/src/test/java/io/ddd4j/data/projection/jpa/TestApp.java",
             "import org.springframework.boot.autoconfigure.domain.EntityScan;",
             "import org.springframework.boot.persistence.autoconfigure.EntityScan;", ("3.0.x",)),
            ("ddd4j-mq/ddd4j-mq-rabbitmq/src/test/java/io/ddd4j/mq/rabbitmq/RabbitMQContainerIntegrationTest.java",
             "import org.testcontainers.rabbitmq.RabbitMQContainer;",
             "import org.testcontainers.containers.RabbitMQContainer;", ("2.0.x",)),
            ("ddd4j-core/src/test/java/io/ddd4j/core/cqrs/readmodel/ProjectionRunInfoValueContractTest.java",
             "import com.fasterxml.jackson.databind.ObjectMapper;",
             "import tools.jackson.databind.json.JsonMapper;", ("3.0.x",)),
            ("ddd4j-core/src/test/java/io/ddd4j/core/cqrs/readmodel/ProjectionRunInfoValueContractTest.java",
             "ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();",
             "JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();", ("3.0.x",)),
            ("ddd4j-data/ddd4j-data-projection-jpa/src/test/java/io/ddd4j/data/projection/jpa/arch/ProjectionJpaModuleIndependenceTest.java",
             '                            "javax..",\n', "", ("2.0.x", "3.0.x")),
            ("ddd4j-runtime/ddd4j-runtime-helidon/src/test/java/io/ddd4j/helidon/HelidonServerBootTest.java",
             "server.cdiContainer().select(RuntimeReadinessRegistry.class).get()",
             "jakarta.enterprise.inject.spi.CDI.current().select(RuntimeReadinessRegistry.class).get()",
             ("2.0.x", "3.0.x")),
            ("ddd4j-runtime/ddd4j-runtime-spring/src/test/java/io/ddd4j/spring/cqrs/SpringJpaProjectionPositionTestApp.java",
             "import org.springframework.boot.autoconfigure.domain.EntityScan;",
             "import org.springframework.boot.persistence.autoconfigure.EntityScan;",
             ("3.0.x",)),
            ("ddd4j-data/ddd4j-data-projection-panache/src/test/java/io/ddd4j/data/projection/panache/arch/ProjectionPanacheModuleIndependenceTest.java",
             '                            "javax..",',
             '                            "jakarta..",',
             ("2.0.x", "3.0.x")),
            ("ddd4j-data/ddd4j-data-projection-quarkus/src/test/java/io/ddd4j/data/projection/quarkus/arch/ProjectionQuarkusModuleIndependenceTest.java",
             '                            "javax..",',
             '                            "jakarta..",',
             ("2.0.x", "3.0.x")),
        }
        java8_map_calls = (
            ('Java8Maps.of("name", "alice", "count", 2)',
             'Map.of("name", "alice", "count", 2)'),
            ('Java8Maps.of(null, "value")', 'Map.of(null, "value")'),
            ('Java8Maps.of("key", null)', 'Map.of("key", null)'),
            ('Java8Maps.of("name", "alice")', 'Map.of("name", "alice")'),
            ('void rejectsDuplicateKeysAndOddArguments() {\n'
             '        assertThrows(IllegalArgumentException.class, () -> Java8Maps.of("key", 1, "key", 2));\n'
             '        assertThrows(IllegalArgumentException.class, () -> Java8Maps.of("key"));\n'
             '    }',
             'void rejectsDuplicateKeys() {\n'
             '        assertThrows(IllegalArgumentException.class, () -> Map.of("key", 1, "key", 2));\n'
             '    }'),
        )
        for sample in ("satoken/rbac/web", "shiro/rbac/controller"):
            family = sample.split("/", 1)[0]
            contract = (f"ddd4j-samples/ddd4j-sample-javalin-{family}/src/test/java/"
                        f"io/ddd4j/sample/javalin/{sample}/Java8MapsContractTest.java")
            exact_test_adaptations.update((contract, before, after, ("2.0.x", "3.0.x"))
                                          for before, after in java8_map_calls)
        for replacement in replacements:
            before, after = replacement.get("from", ""), replacement.get("to", "")
            exact_test_adaptation = (source, before, after, tuple(target_lines)) in exact_test_adaptations
            javax_to_jakarta = (
                before.startswith("import javax.") and before.endswith(";")
                and after.startswith("import jakarta.") and after.endswith(";")
                and before[len("import javax."):] == after[len("import jakarta."):]
            )
            pulsar_broker_image = (
                source in {
                    "ddd4j-mq/ddd4j-mq-pulsar/src/test/java/io/ddd4j/mq/pulsar/PulsarContainerIntegrationTest.java",
                    "ddd4j-mq/ddd4j-mq-tdmq/src/test/java/io/ddd4j/mq/tdmq/TdmqPulsarProtocolIntegrationTest.java",
                }
                and (before, after) == ("apachepulsar/pulsar:2.10.6", "apachepulsar/pulsar:4.2.4")
            )
            dropwizard_configuration_import = (
                source == "ddd4j-samples/ddd4j-sample-dropwizard/src/main/java/io/ddd4j/sample/dropwizard/DropwizardOrderConfiguration.java"
                and (before, after) == ("import io.dropwizard.Configuration;", "import io.dropwizard.core.Configuration;")
            )
            if (not (javax_to_jakarta or (before, after) in jackson_mapper_imports
                     or pulsar_broker_image or dropwizard_configuration_import
                     or exact_test_adaptation)
                    or "*" in before or "*" in after
                    or (("\n" in before or "\n" in after) and not exact_test_adaptation)
                    or (target_lines == ["3.0.x"] and (before, after) not in jackson_mapper_imports
                        and not exact_test_adaptation)
                    or before in originals):
                raise ValueError(f"non-exact platform content replacement: {source}")
            originals.add(before)
        by_path[source] = mapping
    return by_path


def platform_content_matches(path: str, roots: list[pathlib.Path], mapping: dict) -> bool:
    contents = [(root / path).read_text(encoding="utf-8") for root in roots]
    normalized = contents[0]
    for replacement in mapping["replacements"]:
        before = replacement["from"]
        if normalized.count(before) != 1:
            return False
        normalized = normalized.replace(before, replacement["to"])
    target_lines = mapping.get("targetLines", ["2.0.x", "3.0.x"])
    return all(contents[index] == (normalized if version in target_lines else contents[0])
               for index, version in ((1, "2.0.x"), (2, "3.0.x")))


def validate_historical_document_sections(mappings, roots: list[pathlib.Path]) -> dict[str, dict]:
    """只允许逐文件、逐线锁定的历史任务段落不同，产品正文仍逐字比较。"""
    by_path = {}
    versions = ("1.0.x", "2.0.x", "3.0.x")
    index_path = "docs/superpowers/historical-evidence-index.md"
    for mapping in mappings:
        path = mapping.get("path", "")
        hashes = mapping.get("sectionSha256", {})
        if (not isinstance(path, str)
                or not path.startswith("docs/reference/fuin-api-patterns/")
                or not path.endswith(".md") or ".." in pathlib.PurePosixPath(path).parts
                or "*" in path or path in by_path
                or mapping.get("marker") != "## 落地计划\n"
                or mapping.get("evidence") != index_path
                or not mapping.get("reason")
                or not isinstance(hashes, dict) or set(hashes) != set(versions)
                or not all(isinstance(value, str)
                           and re.fullmatch(r"[0-9a-f]{64}", value)
                           for value in hashes.values())):
            raise ValueError(f"invalid historical document section mapping: {path}")
        if not all((root / path).is_file() and (root / index_path).is_file()
                   for root in roots):
            raise ValueError(f"historical document or index missing on a line: {path}")
        for root in roots:
            index = (root / index_path).read_text(encoding="utf-8")
            if path not in index or not all(value in index for value in hashes.values()):
                raise ValueError(f"historical document index lacks path or hashes: {path}")
        by_path[path] = mapping
    return by_path


def historical_document_section_matches(path: str, roots: list[pathlib.Path], mapping: dict) -> bool:
    marker = mapping["marker"]
    sections = []
    for root in roots:
        content = (root / path).read_text(encoding="utf-8")
        if content.count(marker) != 1:
            return False
        sections.append(content.split(marker, 1))
    if len({body for body, _ in sections}) != 1:
        return False
    return all(section and hashlib.sha256(section.encode("utf-8")).hexdigest()
               == mapping["sectionSha256"][version]
               for (_, section), version in zip(sections, ("1.0.x", "2.0.x", "3.0.x")))


def validate_platform_test_helper_mappings(mappings, roots: list[pathlib.Path]) -> dict[str, dict]:
    """只接受哈希固定、同模块三线 HTTP 契约方法一致的测试装配器。"""
    validated = {}
    for mapping in mappings:
        path = pathlib.PurePosixPath(mapping.get("path", ""))
        contract = pathlib.PurePosixPath(mapping.get("contractPath", ""))
        source = path.as_posix()
        contract_path = contract.as_posix()
        hashes = mapping.get("sourceSha256")
        contract_hashes = mapping.get("contractSha256")
        methods = mapping.get("testMethods")
        if (path.is_absolute() or ".." in path.parts
                or path.name != "TestSupport.java" or "/src/test/java/" not in source
                or source in validated or contract.is_absolute() or ".." in contract.parts
                or contract.suffix != ".java" or "/src/test/java/" not in contract_path
                or contract_path == source
                or source.split("/src/test/java/", 1)[0]
                   != contract_path.split("/src/test/java/", 1)[0]
                or not isinstance(methods, list) or not methods
                or len(set(methods)) != len(methods)
                or not all(isinstance(method, str)
                           and re.fullmatch(r"(?:[^\W\d]|\$)[\w$]*", method)
                           for method in methods)
                or not mapping.get("reason")):
            raise ValueError(f"invalid platform test helper mapping: {source}")
        for expected in (hashes, contract_hashes):
            if (not isinstance(expected, list) or len(expected) != len(roots)
                    or not all(isinstance(value, str)
                               and re.fullmatch(r"[0-9a-f]{64}", value)
                               for value in expected)):
                raise ValueError(f"invalid platform test helper hashes: {source}")
        for index, root in enumerate(roots):
            helper_file, contract_file = root / path, root / contract
            if (not helper_file.is_file() or not contract_file.is_file()
                    or hashlib.sha256(helper_file.read_bytes()).hexdigest() != hashes[index]
                    or hashlib.sha256(contract_file.read_bytes()).hexdigest()
                    != contract_hashes[index]):
                raise ValueError(f"stale platform test helper mapping: {source}")
            contract_text = contract_file.read_text(encoding="utf-8")
            actual_methods = re.findall(r"(?m)^\s*void\s+((?:[^\W\d]|\$)[\w$]*)\s*\(",
                                        contract_text)
            if "@Test" not in contract_text or actual_methods != methods:
                raise ValueError(f"platform test helper contract differs: {source}")
        validated[source] = mapping
    return validated


def validate_platform_test_syntax_mappings(mappings, roots: list[pathlib.Path],
                                           platform_syntax: dict[str, dict] | None = None) -> dict[str, dict]:
    """只登记生产源码相同或已锁定语法适配、测试方法一致的逐线依赖 API 适配。"""
    validated = {}
    for mapping in mappings:
        path = pathlib.PurePosixPath(mapping.get("path", ""))
        production = pathlib.PurePosixPath(mapping.get("productionPath", ""))
        source = path.as_posix()
        hashes = mapping.get("sourceSha256")
        production_hashes = mapping.get("productionSha256")
        methods = mapping.get("testMethods")
        contract = pathlib.PurePosixPath(mapping.get("contractPath", ""))
        contract_hash = mapping.get("contractSha256")
        contract_hashes = mapping.get("contractSha256ByJdk")
        inherited = bool(mapping.get("contractPath"))
        if (path.is_absolute() or ".." in path.parts or "/src/test/java/" not in source
                or path.suffix != ".java" or source in validated
                or production.is_absolute() or ".." in production.parts
                or "/src/main/java/" not in production.as_posix()
                or production.suffix != ".java"
                or not isinstance(methods, list) or not methods
                or len(set(methods)) != len(methods)
                or not all(isinstance(method, str) and re.fullmatch(r"(?:[^\W\d]|\$)[\w$]*", method)
                           for method in methods)
                or not mapping.get("reason")):
            raise ValueError(f"invalid platform test syntax mapping: {source}")
        for expected in (hashes, production_hashes):
            if (not isinstance(expected, list) or len(expected) != len(roots)
                    or not all(isinstance(value, str) and re.fullmatch(r"[0-9a-f]{64}", value)
                               for value in expected)):
                raise ValueError(f"invalid platform test hashes: {source}")
        if inherited:
            scalar_hash = (isinstance(contract_hash, str)
                           and re.fullmatch(r"[0-9a-f]{64}", contract_hash)
                           and contract_hashes is None)
            line_hashes = (contract_hash is None and isinstance(contract_hashes, dict)
                           and set(contract_hashes) == {"8", "17", "21"}
                           and all(isinstance(value, str)
                                   and re.fullmatch(r"[0-9a-f]{64}", value)
                                   for value in contract_hashes.values()))
            if (contract.is_absolute() or ".." in contract.parts
                    or "/src/main/java/" not in contract.as_posix()
                    or contract.suffix != ".java"
                    or not (scalar_hash or line_hashes)):
                raise ValueError(f"invalid inherited contract mapping: {source}")
        elif contract_hash is not None or contract_hashes is not None:
            raise ValueError(f"inherited contract hash without path: {source}")
        for index, root in enumerate(roots):
            test_file, production_file = root / path, root / production
            if (not test_file.is_file() or not production_file.is_file()
                    or hashlib.sha256(test_file.read_bytes()).hexdigest() != hashes[index]
                    or hashlib.sha256(production_file.read_bytes()).hexdigest()
                    != production_hashes[index]):
                raise ValueError(f"stale platform test syntax mapping: {source}")
            method_file = test_file
            if inherited:
                method_file = root / contract
                expected_contract_hash = (contract_hashes[str((8, 17, 21)[index])]
                                          if contract_hashes else contract_hash)
                if (not method_file.is_file()
                        or hashlib.sha256(method_file.read_bytes()).hexdigest()
                        != expected_contract_hash):
                    raise ValueError(f"stale inherited contract mapping: {source}")
                adapter_contents = test_file.read_text(encoding="utf-8")
                cqrs_contract = (contract.name == "AbstractCqrsRuntimeContractTest.java"
                                 and "extends AbstractCqrsRuntimeContractTest" in adapter_contents
                                 and "createContract(" in adapter_contents)
                web_contract = (contract.name == "AbstractWebContractTest.java"
                                and "extends AbstractWebContractTest" in adapter_contents
                                and "client()" in adapter_contents)
                if (not (cqrs_contract or web_contract)
                        or "@Test" not in method_file.read_text(encoding="utf-8")):
                    raise ValueError(f"missing inherited test contract: {source}")
            actual_methods = re.findall(r"(?m)^\s*void\s+((?:[^\W\d]|\$)[\w$]*)\s*\(",
                                        method_file.read_text(encoding="utf-8"))
            if actual_methods != methods:
                raise ValueError(f"platform test methods differ: {source}")
        if not java_files_lexically_equal(production.as_posix(), roots):
            locked = (platform_syntax or {}).get(production.as_posix())
            if locked is None or not platform_syntax_matches(production.as_posix(), roots, locked):
                raise ValueError(f"platform test production logic differs: {source}")
        validated[source] = mapping
    return validated


def validate_platform_syntax_mappings(mappings, roots: list[pathlib.Path]) -> dict[str, dict]:
    """只接受逐文件锁定源码和三线契约测试的语法适配声明。"""
    by_path = {}
    for mapping in mappings:
        path = pathlib.PurePosixPath(mapping.get("path", ""))
        evidence = pathlib.PurePosixPath(mapping.get("evidence", ""))
        source = path.as_posix()
        test = evidence.as_posix()
        hashes = mapping.get("sourceSha256")
        test_hash = mapping.get("testSha256")
        test_hashes = mapping.get("testSha256ByJdk")
        additional_evidence = mapping.get("additionalEvidence", [])
        valid_test_hash = (isinstance(test_hash, str)
                           and re.fullmatch(r"[0-9a-f]{64}", test_hash)
                           and test_hashes is None)
        valid_line_hashes = (test_hash is None and isinstance(test_hashes, dict)
                             and set(test_hashes) == {"8", "17", "21"}
                             and all(isinstance(value, str)
                                     and re.fullmatch(r"[0-9a-f]{64}", value)
                                     for value in test_hashes.values()))
        if (path.is_absolute() or ".." in path.parts or "*" in source
                or "/src/main/java/" not in source or source in by_path
                or evidence.is_absolute() or ".." in evidence.parts or "*" in test
                or "/src/test/java/" not in test
                or not all((root / path).is_file() and (root / evidence).is_file() for root in roots)
                or not isinstance(hashes, list) or len(hashes) != 3
                or not all(isinstance(value, str) and re.fullmatch(r"[0-9a-f]{64}", value)
                           for value in hashes)
                or not (valid_test_hash or valid_line_hashes)
                or not mapping.get("reason")):
            raise ValueError(f"invalid platform syntax mapping: {source}")
        if not isinstance(additional_evidence, list):
            raise ValueError(f"invalid additional platform evidence: {source}")
        seen_evidence = {test}
        for item in additional_evidence:
            if not isinstance(item, dict):
                raise ValueError(f"invalid additional platform evidence: {source}")
            extra = pathlib.PurePosixPath(item.get("path", ""))
            extra_path = extra.as_posix()
            extra_hash = item.get("testSha256")
            if (extra.is_absolute() or ".." in extra.parts or "*" in extra_path
                    or "/src/test/java/" not in extra_path or extra_path in seen_evidence
                    or not isinstance(extra_hash, str)
                    or not re.fullmatch(r"[0-9a-f]{64}", extra_hash)
                    or not all((root / extra).is_file() for root in roots)):
                raise ValueError(f"invalid additional platform evidence: {extra_path}")
            seen_evidence.add(extra_path)
        by_path[source] = mapping
    return by_path


def platform_syntax_matches(path: str, roots: list[pathlib.Path], mapping: dict) -> bool:
    expected = mapping["sourceSha256"]
    evidence = mapping["evidence"]
    test_hashes = mapping.get("testSha256ByJdk")
    return (all(hashlib.sha256((root / path).read_bytes()).hexdigest() == expected[index]
                for index, root in enumerate(roots))
            and all(hashlib.sha256((root / evidence).read_bytes()).hexdigest()
                    == (test_hashes[str(jdk)] if test_hashes else mapping["testSha256"])
                    for root, jdk in zip(roots, (8, 17, 21)))
            and all(hashlib.sha256((root / item["path"]).read_bytes()).hexdigest()
                    == item["testSha256"]
                    for item in mapping.get("additionalEvidence", []) for root in roots))


def class_files(root: pathlib.Path) -> dict[str, pathlib.Path]:
    result = {}
    for file in root.glob("**/target/classes/**/*.class"):
        relative = file.relative_to(root).as_posix()
        module, class_name = relative.split("/target/classes/", 1)
        class_name = class_name.removesuffix(".class")
        if GENERATED_CLASS.search(class_name):
            continue
        result[f"{module}|{class_name}"] = file
    return result


def javap_api(classes: dict[str, pathlib.Path], mappings=(), version_line: str = "",
              bridge_entries: set[str] | None = None) -> set[str]:
    result = set()
    identities_by_binary_name = {}
    for identity in classes:
        binary_name = identity.partition("|")[2].replace("/", ".")
        identities_by_binary_name.setdefault(binary_name, []).append(identity)

    entries = sorted(classes.items())
    for offset in range(0, len(entries), 100):
        chunk = entries[offset:offset + 100]
        output = run("javap", "-protected", "-s", "-v", *[str(file) for _, file in chunk])
        declaration = ""
        identity = ""
        pending = ""
        parameter_names = []
        reading_parameters = False
        reading_exceptions = False
        generic_signature = ""
        declared_exceptions = []
        constant_value = ""
        pending_is_bridge = False

        def finish_method() -> None:
            nonlocal pending, parameter_names, reading_parameters, reading_exceptions
            nonlocal generic_signature, declared_exceptions, constant_value, pending_is_bridge
            if pending:
                names = parameter_names if any(parameter_names) else []
                if pending.endswith("|equals|(Ljava/lang/Object;)Z"):
                    # Object.equals 的参数名不参与 Java 覆写契约，保留其他方法的参数名检查。
                    names = []
                entry = (pending + "|generic=" + generic_signature
                         + "|throws=" + ",".join(sorted(declared_exceptions))
                         + "|constant=" + constant_value
                         + "|params=" + ",".join(names))
                result.add(entry)
                if pending_is_bridge and bridge_entries is not None:
                    bridge_entries.add(entry)
            pending = ""
            parameter_names = []
            reading_parameters = False
            reading_exceptions = False
            generic_signature = ""
            declared_exceptions = []
            constant_value = ""
            pending_is_bridge = False

        for raw_line in output.splitlines() + [""]:
            line = raw_line.strip()
            if reading_parameters:
                if not line:
                    reading_parameters = False
                    continue
                indentation = len(raw_line) - len(raw_line.lstrip())
                if indentation <= 4:
                    reading_parameters = False
                else:
                    if line.startswith("Name"):
                        continue
                    parameter_names.append("" if line.startswith("<no name>") else line.split()[0])
                    continue
            if reading_exceptions:
                if line.startswith("throws "):
                    for exception in line.removeprefix("throws ").split(", "):
                        descriptor = "L" + exception.replace(".", "/") + ";"
                        declared_exceptions.append(normalize_descriptor(descriptor, mappings, version_line))
                    continue
                reading_exceptions = False
            if not line or line in {"{", "}"} or line.startswith("Compiled from"):
                continue
            header = (re.search(r"(?:class|interface|enum|record)\s+([\w.$]+)", line)
                      if raw_line == raw_line.lstrip() else None)
            if header:
                finish_method()
                candidates = identities_by_binary_name.get(header.group(1), [])
                identity = candidates[0] if line.startswith("public ") and len(candidates) == 1 else ""
                continue
            if line.startswith(("public ", "protected ")) and line.endswith(";"):
                finish_method()
                declaration = re.sub(r"\s+", " ", line)
                continue
            if line.startswith("descriptor:"):
                if not identity:
                    continue
                descriptor = normalize_descriptor(line.partition(":")[2].strip(), mappings, version_line)
                if not declaration or " lambda$" in declaration or " access$" in declaration:
                    continue
                if "(" in declaration:
                    owner = identity.partition("|")[2].rsplit("/", 1)[-1]
                    prefix = declaration.partition("(")[0]
                    method_name = prefix.split()[-1]
                    if method_name == owner or method_name.endswith("." + owner):
                        method_name = "<init>"
                    pending = f"{identity}|{method_name}|{descriptor}"
                else:
                    field_name = declaration.removesuffix(";").split()[-1]
                    pending = f"{identity}|field:{field_name}|{descriptor}"
                declaration = ""
                continue
            if line == "MethodParameters:" and pending:
                reading_parameters = True
            if line.startswith("flags:") and pending:
                pending_is_bridge = "ACC_BRIDGE" in line and "ACC_SYNTHETIC" in line
            if line == "Exceptions:" and pending:
                reading_exceptions = True
            if line.startswith("Signature:") and pending and "//" in line:
                generic_signature = normalize_descriptor(line.partition("//")[2].strip(),
                                                         mappings, version_line)
            if line.startswith("ConstantValue:") and pending:
                constant_value = line.partition(":")[2].strip()
        finish_method()
    return result


def compile_roots(roots: list[pathlib.Path], output: pathlib.Path,
                  module: str | None = None) -> list[dict[str, object]]:
    results = []
    for index, (root, jdk_version) in enumerate(zip(roots, JDK_VERSIONS), 1):
        major = (8, 17, 21)[index - 1]
        configured = os.environ.get(f"DDD4J_JDK{major}_HOME")
        jdk = pathlib.Path(configured or run("/usr/libexec/java_home", "-v", jdk_version).strip())
        executable = root / "mvnw"
        command = [str(executable), "-B", "-ntp"]
        if module:
            command.extend(("-pl", module, "-am"))
        command.extend(("clean", "compile"))
        environment = os.environ.copy()
        environment["JAVA_HOME"] = str(jdk)
        environment["PATH"] = str(jdk / "bin") + os.pathsep + environment["PATH"]
        completed = subprocess.run(command, cwd=root, env=environment, text=True, capture_output=True)
        log = output / f"compile-line-{index}.log"
        log.write_text(completed.stdout + completed.stderr, encoding="utf-8")
        results.append({"line": index, "exit": completed.returncode, "log": log.name})
    return results


def module_compiled_api_gate(roots: list[pathlib.Path], output: pathlib.Path,
                             policy_path: pathlib.Path, module: str) -> int:
    """只编译指定模块及其 reactor 依赖，报告该模块的公开 JVM API 差异。"""
    policy = json.loads(policy_path.read_text(encoding="utf-8"))
    if policy.get("schemaVersion") != 1:
        raise ValueError("unsupported three-line policy schema")
    validate_type_mappings(policy.get("typeMappings", []), roots)
    platform_api = (policy.get("platformApiMembersMissingOnJdk8", [])
                    + policy.get("platformApiMembersOnlyOnJdk8", []))
    validate_platform_api_mappings(
        [item for item in platform_api if item["sourceSymbol"].startswith(module + "/")], roots)
    output.mkdir(parents=True, exist_ok=True)
    snapshots = [{path: digest for path, digest in inventory_files(root).items()
                  if path.startswith(module + "/")}
                 for root in roots]
    apis = [source_api(root, snapshot, output) for root, snapshot in zip(roots, snapshots)]
    rows = []
    for symbol in sorted(set().union(*(api.keys() for api in apis))):
        values = [api.get(symbol) for api in apis]
        if values[0] == values[1] == values[2]:
            continue
        rows.append({"category": "api", "path": symbol, "jdk8": values[0] or "MISSING",
                     "jdk17": values[1] or "MISSING", "jdk21": values[2] or "MISSING"})
    compiled = compiled_api_gate(roots, output, policy, rows, module)
    print(f"module={module} compiled_api={compiled['status']} "
          f"source_api_differences={len(rows)} output={output}")
    return 0 if compiled["status"] == "PASS" else 1


def codegraph_signatures(root: pathlib.Path, tracked: set[str]) -> set[str]:
    database = root / ".codegraph" / "codegraph.db"
    connection = sqlite3.connect(f"file:{database}?mode=ro", uri=True)
    rows = connection.execute(
        "select file_path, qualified_name, name, signature, return_type, visibility "
        "from nodes where kind = 'method'"
    )
    signatures = set()
    for file_path, qualified_name, name, signature, return_type, visibility in rows:
        if file_path not in tracked or excluded(file_path) or "/src/main/java/" not in file_path:
            continue
        if visibility == "private" or "$anon@" in qualified_name:
            continue
        compact = re.sub(r"\s+", " ", signature or "").strip()
        signatures.add(f"{file_path}|{qualified_name}|{name}|{compact}|{return_type or ''}|{visibility or ''}")
    connection.close()
    return signatures


def pair_diff(left: set[str], right: set[str]) -> dict[str, list[str]]:
    return {"only_left": sorted(left - right), "only_right": sorted(right - left)}


def codegraph_conflicts(left: set[str], right: set[str]) -> list[dict[str, object]]:
    """比较两边都被 CodeGraph 识别出的显式方法，包含源码参数名。"""
    def indexed(entries: set[str]) -> dict[str, set[str]]:
        values = {}
        for entry in entries:
            parts = entry.split("|", 5)
            values.setdefault("|".join(parts[:3]), set()).add("|".join(parts[3:]))
        return values

    left_index = indexed(left)
    right_index = indexed(right)
    return [
        {"symbol": key, "left": sorted(left_index[key]), "right": sorted(right_index[key])}
        for key in sorted(left_index.keys() & right_index.keys())
        if left_index[key] != right_index[key]
    ]


def allowed_api_difference(entry: str) -> bool:
    http_client_constructors = {
        ("ddd4j-data/ddd4j-data-crypto|io/ddd4j/data/crypto/strategy/FlksecCryptoStrategy",
         "(LJACKSON/databind/ObjectMapper;Ljava/net/http/HttpClient;Ljava/lang/String;Ljava/lang/String;)V"),
        ("ddd4j-data/ddd4j-data-external|io/ddd4j/data/external/geo/GeoBaiduTemplate",
         "(Ljava/net/http/HttpClient;Ljava/lang/String;)V"),
        ("ddd4j-data/ddd4j-data-external|io/ddd4j/data/external/region/BaiduRegionTemplate",
         "(Ljava/lang/String;Ljava/net/http/HttpClient;)V"),
        ("ddd4j-data/ddd4j-data-external|io/ddd4j/data/external/region/BaiduRegionTemplate",
         "(Ljava/lang/String;Ljava/net/http/HttpClient;Lio/ddd4j/data/external/region/RegionCache;)V"),
        ("ddd4j-data/ddd4j-data-external|io/ddd4j/data/external/region/PconlineRegionTemplate",
         "(Ljava/net/http/HttpClient;)V"),
        ("ddd4j-data/ddd4j-data-external|io/ddd4j/data/external/region/PconlineRegionTemplate",
         "(Ljava/net/http/HttpClient;Lio/ddd4j/data/external/region/RegionCache;)V"),
        ("ddd4j-data/ddd4j-data-external|io/ddd4j/data/external/weather/WeatherTemplate",
         "(Ljava/net/http/HttpClient;)V"),
    }
    parts = entry.split("|")
    identity = "|".join(parts[:2])
    descriptor = parts[3] if len(parts) > 3 and parts[2] == "<init>" else ""
    if (identity, descriptor) in http_client_constructors:
        return True
    return (
        entry.startswith("ddd4j-web/ddd4j-web-micronaut|io/ddd4j/web/micronaut/Ddd4jMicronautContext|")
        or entry.startswith("ddd4j-web/ddd4j-web-micronaut|io/ddd4j/web/micronaut/Ddd4jMicronautWebFilter|")
    )


def allowed_class_difference(entry: str) -> bool:
    """仅放行 JDK/框架版本适配产生、且不属于公开 API 的私有辅助类。"""
    return entry.endswith((
        "$Internals",
        "$StoredEventRowMapper",
        "Ddd4jMicronautWebFilter$ContextPropagationFactory",
    ))


def allowed_codegraph_conflict(conflict: dict[str, object]) -> bool:
    symbol = conflict["symbol"]
    left_values = set(conflict["left"])
    right_values = set(conflict["right"])
    differences = left_values ^ right_values
    http_client_types = (
        "FlksecCryptoStrategy", "GeoBaiduTemplate", "BaiduRegionTemplate",
        "PconlineRegionTemplate", "WeatherTemplate",
    )
    return (
        (symbol.endswith("|" + symbol.split("::")[-1].split("|")[0])
         and any(type_name in symbol for type_name in http_client_types)
         and differences and all("HttpClient" in value for value in differences))
        or ("ddd4j-web/ddd4j-web-javalin/" in symbol and "|configure" in symbol
            and any("Javalin" in value for value in differences)
            and all("Javalin" in value for value in differences))
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--roots", nargs=3, required=True, type=pathlib.Path)
    parser.add_argument("--output", required=True, type=pathlib.Path)
    parser.add_argument("--compile", action="store_true",
                        help="兼容入口：执行当前源码清单与三线 clean compile/API 验证")
    parser.add_argument("--policy", type=pathlib.Path)
    parser.add_argument("--phase", choices=("inventory", "verify"))
    parser.add_argument("--module", help="仅在 verify 阶段 clean compile 指定模块及其依赖并比较该模块 API")
    parser.add_argument("--fail-on-open", action="store_true",
                        help="盘点后保留证据，并在存在 OPEN 差异时以失败退出")
    args = parser.parse_args()
    roots = [supplied_root.resolve() for supplied_root in args.roots]
    policy = args.policy or roots[-1] / "config/parity/three-line-policy.json"
    if not policy.is_file():
        parser.error(f"missing three-line policy: {policy}")
    phase = args.phase or ("verify" if args.compile else "inventory")
    if args.module:
        module = pathlib.PurePosixPath(args.module)
        if (phase != "verify" or module.is_absolute() or ".." in module.parts
                or "." in module.parts or "*" in args.module
                or module.as_posix() != args.module
                or not all((root / module / "pom.xml").is_file() for root in roots)):
            parser.error(f"invalid module for scoped verification: {args.module}")
        return module_compiled_api_gate(roots, args.output, policy, args.module)
    return inventory_gate(roots, args.output, policy, phase, args.fail_on_open)


if __name__ == "__main__":
    sys.exit(main())
