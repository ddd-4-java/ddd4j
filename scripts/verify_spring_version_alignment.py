#!/usr/bin/env python3
"""按发布线验证真实 Maven 有效模型，拒绝 Spring 属性和 BOM 优先级漂移。"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET


LINES = {
    "1.0.x": {"jdk": 8, "spring": "5.3.", "security": "5.", "boot": "2.", "data": "2."},
    "2.0.x": {"jdk": 17, "spring": "6.", "security": "6.", "boot": "3.", "data": "3."},
    "3.0.x": {"jdk": 21, "spring": "7.", "security": "7.", "boot": "4.", "data": "4."},
}
COMPANIONS = {
    "amqp": ("2.", "3.", "4."), "batch": ("4.", "5.", "6."),
    "integration": ("5.", "6.", "7."), "kafka": ("2.", "3.", "4."),
    "ldap": ("2.", "3.", "4."), "pulsar": (None, "1.", "2."),
    "restdocs": ("2.", "3.", "4."), "session": ("2.", "3.", "4."),
    "ws": ("3.", "4.", "5."), "hateoas": ("1.", "2.", "3."),
    "retry": ("1.", "2.", "2."),
}
HELP_GOAL = "org.apache.maven.plugins:maven-help-plugin:3.5.2:effective-pom"


def xml(path):
    root = ET.parse(path).getroot()
    for element in root.iter():
        element.tag = element.tag.rsplit("}", 1)[-1]
    return root


def properties(project):
    node = project.find("properties")
    return {e.tag: (e.text or "").strip() for e in node} if node is not None else {}


def spring(group):
    return group == "org.springframework" or group.startswith("org.springframework.")


def identity(dep):
    return (dep.findtext("groupId", ""), dep.findtext("artifactId", ""),
            dep.findtext("type", "jar"), dep.findtext("classifier", ""))


def read_models(paths):
    models = {}
    for path in paths:
        root = xml(path)
        for project in root.findall("project") if root.tag == "projects" else [root]:
            name = project.findtext("artifactId")
            if not name or name in models:
                raise ValueError(f"missing or duplicate effective project: {name}")
            models[name] = project
    return models


def java_major(value):
    return 8 if value == "1.8" else int(value) if value and value.isdigit() else None


def audit_models(models, expected_projects, line):
    policy = LINES[line]
    errors, entries, metadata = [], [], []

    def fail(code, module, actual, expected, **extra):
        errors.append(dict(code=code, module=module, actual=actual, expected=expected, **extra))

    if set(models) != set(expected_projects):
        fail("project-coverage", "reactor", sorted(models), sorted(expected_projects))
    base = models.get("ddd4j-dependencies")
    if base is None:
        fail("missing-version-owner", "ddd4j-dependencies", None, "effective parent model")
        return {"errors": errors, "entries": entries, "metadata": metadata}
    canonical = properties(base)
    shared = {k: v for k, v in canonical.items() if k.startswith("spring") and k.endswith(".version")}
    for key, value in shared.items():
        if not value or "${" in value:
            fail("unresolved-shared-property", "ddd4j-dependencies", value, "resolved version", property=key)
    framework = canonical.get("spring-framework.version", "")
    if not framework.startswith(policy["spring"]) or not re.fullmatch(r"\d+\.\d+\.\d+(?:\.RELEASE)?", framework):
        fail("spring-generation", "ddd4j-dependencies", framework, policy["spring"] + "stable")
    if line == "1.0.x" and framework != "5.3.39":
        fail("spring5-public-final", "ddd4j-dependencies", framework, "5.3.39")
    servlet_key = "javax-servlet.version" if line == "1.0.x" else "jakarta-servlet.version"
    servlet = canonical.get(servlet_key, "")
    prefix = "4." if line == "1.0.x" else "6."
    if not servlet.startswith(prefix) or (line == "3.0.x" and not re.match(r"6\.[1-9]\d*\.", servlet)):
        fail("servlet-baseline", "ddd4j-dependencies", servlet,
             "6.1+ in 6.x" if line == "3.0.x" else prefix + "x")
    central = {identity(d): d.findtext("version", "")
               for d in base.findall("./dependencyManagement/dependencies/dependency")
               if spring(d.findtext("groupId", ""))}
    if not central:
        fail("empty-spring-management", "ddd4j-dependencies", 0, ">0")
    index = int(line[0]) - 1
    for name, project in models.items():
        props = properties(project)
        expected_revision = line + ".20260630-SNAPSHOT"
        if project.findtext("version") != expected_revision or props.get("revision") != expected_revision:
            fail("revision", name, [project.findtext("version"), props.get("revision")], expected_revision)
        if java_major(props.get("java.version")) != policy["jdk"]:
            fail("java-baseline", name, props.get("java.version"), policy["jdk"])
        for key in ("maven.compiler.source", "maven.compiler.target", "maven.compiler.release"):
            if key in props and java_major(props[key]) != policy["jdk"]:
                fail("compiler-baseline", name, props[key], policy["jdk"], property=key)
        if "maven.compiler.release" not in props and not all(
                key in props for key in ("maven.compiler.source", "maven.compiler.target")):
            fail("missing-compiler-baseline", name, None, "release or source+target")
        for plugin in project.findall("./build/plugins/plugin") + project.findall("./build/pluginManagement/plugins/plugin"):
            if plugin.findtext("artifactId") == "maven-compiler-plugin":
                for key in ("release", "source", "target"):
                    value = plugin.findtext("configuration/" + key)
                    if value is not None and java_major(value) != policy["jdk"]:
                        fail("compiler-plugin-baseline", name, value, policy["jdk"], property=key)
        for key, value in shared.items():
            # 根聚合器和内部构件 BOM 不继承依赖父 POM；如自行定义同名属性，仍须一致。
            if name not in ("ddd4j", "ddd4j-bom") or key in props:
                if props.get(key) != value:
                    fail("shared-property", name, props.get(key), value, property=key)
        for scope, xpath in (("managed", "./dependencyManagement/dependencies/dependency"),
                             ("declared", "./dependencies/dependency")):
            for dep in project.findall(xpath):
                group, artifact, kind, classifier = identity(dep)
                version = dep.findtext("version", "")
                if group in ("javax.servlet", "jakarta.servlet") and artifact in ("servlet-api", "javax.servlet-api", "jakarta.servlet-api") and scope == "declared":
                    valid = ((group == "javax.servlet" or group == "jakarta.servlet") and version.startswith("4.")) if line == "1.0.x" else group == "jakarta.servlet" and version.startswith("6.")
                    if line == "3.0.x":
                        valid = valid and bool(re.match(r"6\.[1-9]\d*\.", version))
                    if not valid:
                        fail("declared-servlet", name, group + ":" + version, servlet)
                if not spring(group):
                    continue
                coordinate = group + ":" + artifact
                entries.append(dict(module=name, scope=scope, coordinate=coordinate,
                                    type=kind, classifier=classifier, version=version))
                if not version or "${" in version:
                    fail("unresolved-version", name, version, "resolved version", coordinate=coordinate)
                    continue
                if re.search(r"snapshot|alpha|beta|(?:^|[.-])(?:rc|m)\d+", version, re.IGNORECASE):
                    fail("unstable-spring-version", name, version, "stable release", coordinate=coordinate)
                expected = central.get((group, artifact, kind, classifier))
                if expected is not None and version != expected:
                    fail("dependency-drift", name, version, expected, coordinate=coordinate, scope=scope)
                if artifact.endswith("-bom"):
                    metadata.append(dict(module=name, coordinate=coordinate, version=version))
                    continue
                required = None
                if group == "org.springframework":
                    required = framework
                    if version != required:
                        fail("framework-version", name, version, required, coordinate=coordinate)
                    continue
                if group == "org.springframework.security":
                    required = ("0." if line == "1.0.x" else "1." if line == "2.0.x" else "7.") if artifact == "spring-security-oauth2-authorization-server" else policy["security"]
                elif group == "org.springframework.boot":
                    required = policy["boot"]
                elif group == "org.springframework.data" and artifact in (
                        "spring-data-commons", "spring-data-jpa"):
                    # 同一 Data BOM 中 MongoDB、Neo4j 等产品的主版本不同；用核心产品
                    # 校验代际，其余坐标仍逐项精确比较该线有效管理表，不能统一套主版本。
                    required = policy["data"]
                elif group.startswith("org.springframework."):
                    family = group.removeprefix("org.springframework.")
                    if family in COMPANIONS:
                        required = COMPANIONS[family][index] or "unsupported-on-this-line"
                    elif family == "graphql":
                        required = "1." if line != "3.0.x" else "2."
                if required and not version.startswith(required):
                    fail("companion-generation", name, version, required, coordinate=coordinate)
                if group == "org.springframework.security":
                    key = "spring-authorization-server.version" if artifact == "spring-security-oauth2-authorization-server" and line != "3.0.x" else "spring-security.version"
                    if key in canonical and version != canonical[key]:
                        fail("security-version", name, version, canonical[key], coordinate=coordinate)
    return {"errors": errors, "entries": entries, "metadata": metadata,
            "projects": len(models), "shared_properties": shared,
            "managed_spring_coordinates": len(central)}


def discover_projects(root):
    projects, paths = {}, set()

    def visit(directory):
        path = (directory / "pom.xml").resolve()
        if not path.is_relative_to(root) or path in paths:
            raise ValueError(f"invalid or duplicate reactor path: {path}")
        paths.add(path)
        node = xml(path)
        artifact = node.findtext("artifactId")
        if not artifact or artifact in projects:
            raise ValueError(f"missing or duplicate reactor artifactId: {artifact}")
        projects[artifact] = str(directory.relative_to(root))
        for module in node.findall("./modules/module") + node.findall("./subprojects/subproject"):
            visit((directory / module.text).resolve())
    visit(root)
    return projects, paths


def fingerprint(paths):
    return {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(paths)}


def resolve_matrix(trigger_branch, trigger_sha, lookup):
    if trigger_branch not in {"feature/" + line for line in LINES}:
        raise ValueError("select one of feature/1.0.x, feature/2.0.x, feature/3.0.x")
    include = []
    for line, policy in LINES.items():
        branch = "feature/" + line
        sha = trigger_sha if branch == trigger_branch else lookup(branch)
        if not re.fullmatch(r"[0-9a-f]{40}", sha):
            raise ValueError(f"cannot freeze {branch} to a full commit SHA")
        include.append(dict(line=line, jdk=str(policy["jdk"]), branch=branch, sha=sha))
    return {"include": include}


def run(args):
    root, output = args.root.resolve(), args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    result = {"status": "FAIL", "line": args.line, "errors": []}
    try:
        projects, paths = discover_projects(root)
        before = fingerprint(paths)
        java_home = Path(os.environ.get("JAVA_HOME", ""))
        version = subprocess.run([str(java_home / "bin/java"), "-XshowSettings:properties", "-version"],
                                 capture_output=True, text=True, check=True)
        match = re.search(r"java.specification.version\s*=\s*(\S+)", version.stderr + version.stdout)
        if match is None or java_major(match.group(1)) != LINES[args.line]["jdk"]:
            raise ValueError(f"JAVA_HOME must select JDK {LINES[args.line]['jdk']}")
        env = os.environ.copy()
        env["MAVEN_OPTS"] = (env.get("MAVEN_OPTS", "") + " -Xmx2048m").strip()
        effective = []
        modules = list(projects.values())
        # Maven 4 默认把选中的聚合器展开为全部子项目；-N 与 -pl 组合保持批次精确，
        # 否则首批根项目会导出全仓库，后续批次重复，掩盖覆盖范围或耗尽内存。
        selection_flags = ["-N"] if xml(root / "pom.xml").findtext("modelVersion") == "4.1.0" else []
        for offset in range(0, len(modules), args.batch_size):
            batch = offset // args.batch_size + 1
            target = output / f"effective-{batch}.xml"
            # 不复用旧输出；Maven 失败或少导出项目时必须失败。
            target.unlink(missing_ok=True)
            command = [str(root / "mvnw"), "-B", "-ntp", *args.maven_arg, *selection_flags,
                       "-pl", ",".join(modules[offset:offset + args.batch_size]),
                       HELP_GOAL, "-Doutput=" + str(target)]
            with (output / f"maven-{batch}.log").open("w") as log:
                completed = subprocess.run(command, cwd=root, env=env, stdout=log,
                                           stderr=subprocess.STDOUT, timeout=600)
            if completed.returncode or not target.is_file():
                raise ValueError(f"Maven effective-POM batch {batch} failed; see maven-{batch}.log")
            effective.append(target)
            print(f"exported effective-POM batch {batch}", flush=True)
        result.update(audit_models(read_models(effective), set(projects), args.line))
        after = fingerprint(paths)
        if before != after:
            result["errors"].append({"code": "source-changed", "actual": "POM changed during check"})
        result.update(source_pom_sha256=before, java_specification=match.group(1),
                      effective_pom_files=[p.name for p in effective],
                      checker_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
        result["status"] = "FAIL" if result["errors"] else "PASS"
    except (OSError, ValueError, ET.ParseError, subprocess.SubprocessError) as error:
        result["errors"].append({"code": "incomplete-verification", "message": str(error)})
    (output / "summary.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(f"Spring alignment {args.line}: {result['status']}; {len(result['errors'])} errors; {output / 'summary.json'}")
    for error in result["errors"][:20]:
        print(json.dumps(error, ensure_ascii=False))
    return 0 if result["status"] == "PASS" else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--line", choices=LINES, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--batch-size", type=int, choices=range(1, 26), default=25)
    parser.add_argument("--maven-arg", action="append", default=[])
    return run(parser.parse_args())


if __name__ == "__main__":
    sys.exit(main())
