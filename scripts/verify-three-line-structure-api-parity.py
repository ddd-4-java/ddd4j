#!/usr/bin/env python3
"""验证 ddd4j 三条发布线的非例外模块、生产对象与公开 JVM API。"""

import argparse
import json
import os
import pathlib
import re
import sqlite3
import subprocess
import sys


VERSIONS = ("1", "2", "3")
JDK_VERSIONS = ("1.8", "17", "21")
GENERATED_CLASS = re.compile(r"\$(?:\d+|Definition(?:\$.*)?|Reference|Exec|InitializeInterceptor)$")


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


def normalize_descriptor(value: str) -> str:
    value = re.sub(r"L(?:com/fasterxml|tools)/jackson/[^;]+;", "LJACKSON;", value)
    replacements = {
        "Ljavax/validation/": "LVALIDATION/",
        "Ljakarta/validation/": "LVALIDATION/",
        "Ljavax/persistence/": "LPERSISTENCE/",
        "Ljakarta/persistence/": "LPERSISTENCE/",
        "Ljavax/servlet/": "LSERVLET/",
        "Ljakarta/servlet/": "LSERVLET/",
        "Ljavax/ws/rs/": "LJAXRS/",
        "Ljakarta/ws/rs/": "LJAXRS/",
        "Ljavax/inject/": "LINJECT/",
        "Ljakarta/inject/": "LINJECT/",
        "Ljavax/annotation/": "LANNOTATION/",
        "Ljakarta/annotation/": "LANNOTATION/",
        "Ljavax/jms/": "LJMS/",
        "Ljakarta/jms/": "LJMS/",
    }
    for source, target in replacements.items():
        value = value.replace(source, target)
    value = re.sub(r"Lio/dropwizard/(?:core/)?", "LDROPWIZARD/", value)
    value = re.sub(r"Lio/javalin/(?:config/)?Javalin(?:Config)?;", "LJAVALIN;", value)
    value = re.sub(r"Lorg/springframework/(?:biz|extension)/", "LSPRING_EXTENSION/", value)
    return value


def class_files(root: pathlib.Path) -> dict[str, pathlib.Path]:
    result = {}
    for file in root.glob("**/target/classes/**/*.class"):
        relative = file.relative_to(root).as_posix()
        if excluded(relative):
            continue
        module, class_name = relative.split("/target/classes/", 1)
        class_name = class_name.removesuffix(".class")
        if GENERATED_CLASS.search(class_name):
            continue
        result[f"{module}|{class_name}"] = file
    return result


def javap_api(classes: dict[str, pathlib.Path]) -> set[str]:
    result = set()
    identities_by_binary_name = {}
    for identity in classes:
        binary_name = identity.partition("|")[2].replace("/", ".")
        identities_by_binary_name.setdefault(binary_name, []).append(identity)

    entries = sorted(classes.items())
    for offset in range(0, len(entries), 100):
        chunk = entries[offset:offset + 100]
        output = run("javap", "-public", "-s", "-v", *[str(file) for _, file in chunk])
        declaration = ""
        identity = ""
        pending = ""
        parameter_names = []
        reading_parameters = False

        def finish_method() -> None:
            nonlocal pending, parameter_names, reading_parameters
            if pending:
                result.add(pending + "|params=" + ",".join(parameter_names))
            pending = ""
            parameter_names = []
            reading_parameters = False

        for raw_line in output.splitlines() + [""]:
            line = raw_line.strip()
            if reading_parameters:
                if not line:
                    finish_method()
                    continue
                indentation = len(raw_line) - len(raw_line.lstrip())
                if indentation <= 4:
                    finish_method()
                else:
                    if line.startswith("Name"):
                        continue
                    parameter_names.append(line.split()[0])
                    continue
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
                declaration = re.sub(r"\s+", " ", line) if "(" in line else ""
                continue
            if line.startswith("descriptor:"):
                if not identity:
                    continue
                descriptor = normalize_descriptor(line.partition(":")[2].strip())
                if "(" not in declaration or " lambda$" in declaration or " access$" in declaration:
                    continue
                owner = identity.partition("|")[2].rsplit("/", 1)[-1]
                prefix = declaration.partition("(")[0]
                method_name = prefix.split()[-1]
                if method_name == owner or method_name.endswith("." + owner):
                    method_name = "<init>"
                pending = f"{identity}|{method_name}|{descriptor}"
                declaration = ""
                continue
            if line == "MethodParameters:" and pending:
                reading_parameters = True
        finish_method()
    return result


def compile_roots(roots: list[pathlib.Path], output: pathlib.Path) -> list[dict[str, object]]:
    results = []
    for index, (root, jdk_version) in enumerate(zip(roots, JDK_VERSIONS), 1):
        jdk = pathlib.Path(run("/usr/libexec/java_home", "-v", jdk_version).strip())
        executable = root / "mvnw" if index == 3 else pathlib.Path("mvn")
        command = [str(executable), "-B", "-ntp", "-DskipTests", "-Denforcer.skip=true",
                   "-Dmaven.compiler.parameters=true", "clean", "compile"]
        environment = os.environ.copy()
        environment["JAVA_HOME"] = str(jdk)
        environment["PATH"] = str(jdk / "bin") + os.pathsep + environment["PATH"]
        completed = subprocess.run(command, cwd=root, env=environment, text=True, capture_output=True)
        log = output / f"compile-line-{index}.log"
        log.write_text(completed.stdout + completed.stderr, encoding="utf-8")
        results.append({"line": index, "exit": completed.returncode, "log": str(log)})
    return results


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
        compact = compact.replace("com.fasterxml.jackson", "JACKSON").replace("tools.jackson", "JACKSON")
        compact = compact.replace("javax.", "DEPENDENCY.").replace("jakarta.", "DEPENDENCY.")
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
         "(LJACKSON;Ljava/net/http/HttpClient;Ljava/lang/String;Ljava/lang/String;)V"),
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
    """仅放行由 JDK8 语法降级产生、且不属于公开 API 的私有辅助类。"""
    return entry.endswith(("$Internals", "$StoredEventRowMapper"))


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
                        help="先在对应 JDK 上 clean compile，禁止消费陈旧 class")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)

    roots = [supplied_root.resolve() for supplied_root in args.roots]
    compile_results = compile_roots(roots, args.output) if args.compile else []
    compile_failed = any(result["exit"] != 0 for result in compile_results)

    snapshots = {}
    for version, root in zip(VERSIONS, roots):
        tracked = tracked_files(root)
        classes = class_files(root)
        snapshots[version] = {
            "root": str(root),
            "head": run("git", "-C", str(root), "rev-parse", "HEAD").strip(),
            "modules": module_dirs(tracked),
            "sources": production_sources(tracked),
            "classes": set(classes),
            "javap_api": javap_api(classes),
            "codegraph_api": codegraph_signatures(root, tracked),
        }

    pairs = {}
    failed = compile_failed or not args.compile
    for left, right in (("1", "2"), ("2", "3"), ("1", "3")):
        pair = {}
        for key in ("modules", "sources", "classes", "javap_api", "codegraph_api"):
            difference = pair_diff(snapshots[left][key], snapshots[right][key])
            if key == "javap_api":
                difference["violations_left"] = [
                    entry for entry in difference["only_left"] if not allowed_api_difference(entry)
                ]
                difference["violations_right"] = [
                    entry for entry in difference["only_right"] if not allowed_api_difference(entry)
                ]
                difference["allowed_left"] = [
                    entry for entry in difference["only_left"] if allowed_api_difference(entry)
                ]
                difference["allowed_right"] = [
                    entry for entry in difference["only_right"] if allowed_api_difference(entry)
                ]
            elif key == "classes":
                difference["violations_left"] = [
                    entry for entry in difference["only_left"] if not allowed_class_difference(entry)
                ]
                difference["violations_right"] = [
                    entry for entry in difference["only_right"] if not allowed_class_difference(entry)
                ]
                difference["allowed_left"] = [
                    entry for entry in difference["only_left"] if allowed_class_difference(entry)
                ]
                difference["allowed_right"] = [
                    entry for entry in difference["only_right"] if allowed_class_difference(entry)
                ]
            elif key == "codegraph_api":
                difference["common_symbol_conflicts"] = codegraph_conflicts(
                    snapshots[left][key], snapshots[right][key]
                )
                difference["violations"] = [
                    conflict for conflict in difference["common_symbol_conflicts"]
                    if not allowed_codegraph_conflict(conflict)
                ]
                difference["allowed"] = [
                    conflict for conflict in difference["common_symbol_conflicts"]
                    if allowed_codegraph_conflict(conflict)
                ]
            pair[key] = difference
            if key in ("modules", "sources"):
                failed = failed or bool(difference["only_left"] or difference["only_right"])
            elif key == "classes":
                failed = failed or bool(difference["violations_left"] or difference["violations_right"])
            elif key == "javap_api":
                failed = failed or bool(difference["violations_left"] or difference["violations_right"])
            elif key == "codegraph_api":
                failed = failed or bool(difference["violations"])
        pairs[f"{left}-{right}"] = pair

    serializable = {
        "passed": not failed,
        "gate_scope": ["module tree", "production source paths", "compiled class topology",
                       "public JVM method descriptors and MethodParameters"],
        "codegraph_role": "common explicit source symbols and parameter names are gated; record implicit methods absent from CodeGraph are proven by javap",
        "compile_results": compile_results,
        "snapshots": {
            version: {
                "root": snapshot["root"],
                "head": snapshot["head"],
                "module_count": len(snapshot["modules"]),
                "source_count": len(snapshot["sources"]),
                "class_count": len(snapshot["classes"]),
                "public_api_count": len(snapshot["javap_api"]),
                "codegraph_api_count": len(snapshot["codegraph_api"]),
            }
            for version, snapshot in snapshots.items()
        },
        "pairs": pairs,
    }
    destination = args.output / "results.json"
    destination.write_text(json.dumps(serializable, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"passed={not failed} report={destination}")
    for pair_name, pair in pairs.items():
        summary = ", ".join(
            f"{key}:{len(value['only_left'])}/{len(value['only_right'])}"
            for key, value in pair.items()
        )
        print(f"{pair_name} {summary}")
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
