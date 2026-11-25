#!/usr/bin/env python3
"""三线收敛总入口：固定输入，运行已有门禁，并对缺失证据失败关闭。"""

import argparse
import hashlib
import json
import os
import re
import runpy
import shutil
import subprocess
import sys
import tempfile
import time
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
EXPECTED = ((8, "1.0.x"), (17, "2.0.x"), (21, "3.0.x"))
SCENARIO_CASE_RENAMES = {
    "MQ-MQTT-UNSUPPORTED-NACK": {
        "shouldTreatMessageIdAsPayloadConcernAndRejectNativeNack":
            "shouldRejectUnsupportedOperationsWithoutAcknowledgingAndAllowAck",
    },
}
VERIFY_CHECKS = ("structure", "product-docs", "historical-index", "values", "effective-8", "effective-17",
                 "effective-21", "dependency-catalog", "dependencies", "reactor-8", "reactor-17", "reactor-21",
                 "capacity-8", "capacity-17", "capacity-21",
                 "redis-native-8", "redis-native-17", "redis-native-21",
                 "scenario-matrix", "scenario-execution",
                 "quarkus-durable-sample-8", "mybatisplus-mysql-8")
INVENTORY_CHECKS = ("structure", "product-docs", "historical-index", "values", "dependencies", "reactor",
                    "scenario-matrix")


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def git_input(root):
    commit = subprocess.check_output(
        ["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip()
    names = subprocess.check_output(
        ["git", "-C", str(root), "ls-files", "-z", "--cached", "--others", "--exclude-standard"])
    digest = hashlib.sha256()
    modules = []
    for raw in sorted(set(names.split(b"\0"))):
        if not raw:
            continue
        relative = os.fsdecode(raw)
        path = root / relative
        digest.update(raw + b"\0")
        if path.is_symlink():
            content = os.readlink(path).encode()
        elif path.is_file():
            content = path.read_bytes()
        else:
            content = b"<missing>"
        digest.update(hashlib.sha256(content).digest())
        if relative.endswith("/pom.xml") or relative == "pom.xml":
            modules.append(relative)
    return {"sha": commit, "treeFingerprint": digest.hexdigest(), "modules": modules}


def jdk_info(path, major):
    java = path / "bin/java"
    javac = path / "bin/javac"
    if not java.is_file() or not javac.is_file():
        raise ValueError(f"JDK {major} missing java or javac: {path}")
    result = subprocess.run([str(java), "-version"], capture_output=True, text=True)
    version = (result.stderr or result.stdout).splitlines()[0]
    marker = '"1.8.' if major == 8 else f'"{major}.'
    if result.returncode or marker not in version:
        raise ValueError(f"expected JDK {major} at {path}, got {version}")
    return {"path": str(path), "version": version}


def value_probe_classpaths(repository, majors=(8, 17, 21)):
    """Use the same pinned and validated JDK 8 compatible JAR on each line."""
    bootstrap = runpy.run_path(str(SCRIPT_DIR / "bootstrap-three-line-classpath.py"))
    jar = bootstrap["cached_artifact"](repository)
    for _ in majors:
        yield str(jar)


def combined_status(checks, phase, stable):
    if not stable or any(item["status"] == "FAIL" for item in checks.values()):
        return "FAIL"
    required = INVENTORY_CHECKS if phase == "inventory" else VERIFY_CHECKS
    if any(name not in checks for name in required):
        return "BLOCKED"
    if any(item["status"] not in ("PASS", "BLOCKED", "NOT_RUN") for item in checks.values()):
        return "FAIL"
    if phase == "inventory":
        return "INVENTORY" if (checks["structure"]["status"] == "PASS"
                               and checks["product-docs"]["status"] == "PASS") else "BLOCKED"
    if any(checks[name]["status"] != "PASS" for name in required):
        return "BLOCKED"
    return "PASS"


def input_stable(before, after, policy_before, policy_after):
    return before == after and policy_before == policy_after


def reactors_can_run(checks):
    """Run fresh reactor evidence once all three lines can generate effective POMs."""
    return all(checks.get(f"effective-{major}", {}).get("status") == "PASS"
               for major, _ in EXPECTED)


def explicit_scenario_commands(version, redis_url):
    capacity = ["./mvnw", "-q", "-pl", "ddd4j-core", "-am",
                "-Dtest=EventStoreAppendCapacityBaselineIT",
                "-Dsurefire.failIfNoSpecifiedTests=false",
                f"-Dddd4j.baseline.branch=feature/{version}", "test"]
    redis = (["./mvnw", "-q", "-pl", "ddd4j-mq/ddd4j-mq-redis-stream", "-am",
              "-Dtest=RedisStreamNativeCausalityIT",
              "-Dsurefire.failIfNoSpecifiedTests=false",
              f"-Dddd4j.redis.native.url={redis_url}", "test"] if redis_url else None)
    return capacity, redis


def valid_case_names(names):
    """Accept one method name or a nonempty set of distinct method names."""
    if isinstance(names, str):
        names = [names]
    return (isinstance(names, list) and bool(names)
            and all(isinstance(name, str) and name.strip() for name in names)
            and len(set(names)) == len(names))


def validate_scenarios(data, roots=None):
    scenarios = data["scenarios"]
    if not isinstance(scenarios, list) or not scenarios:
        raise ValueError("scenario list is empty or invalid")
    versions = {version for _, version in EXPECTED}
    seen = set()
    pending = []
    for row in scenarios:
        scenario_id = row["id"]
        if not isinstance(scenario_id, str) or not scenario_id.strip() or scenario_id in seen:
            raise ValueError(f"invalid or duplicate scenario ID: {scenario_id}")
        seen.add(scenario_id)
        lines = row["lines"]
        if not isinstance(lines, dict) or set(lines) != versions:
            raise ValueError(f"invalid version lines for {scenario_id}")
        case_names = row.get("caseNames", {})
        if not isinstance(case_names, dict) or (
                case_names and (set(case_names) != versions or
                                any(not valid_case_names(names) for names in case_names.values()))):
            raise ValueError(f"invalid case names for {scenario_id}")
        for index, (_, version) in enumerate(EXPECTED):
            mapping = lines[version]
            status = mapping["status"]
            label = f"{scenario_id} in {version}"
            if status == "supported":
                tests = mapping["tests"]
                if not isinstance(tests, list) or not valid_case_names(tests):
                    raise ValueError(f"invalid mapped tests for {label}")
                if roots is not None and not all((roots[index] / file).is_file() for file in tests):
                    raise ValueError(f"missing mapped test for {label}")
                if case_names and len(tests) != 1:
                    raise ValueError(f"case name requires one test file for {label}")
            elif status in ("unsupported", "pending"):
                reason = mapping.get("reason")
                if not isinstance(reason, str) or not reason.strip():
                    raise ValueError(f"missing {status} reason for {label}")
                if status == "pending":
                    pending.append(f"{scenario_id}:{version}")
            else:
                raise ValueError(f"invalid status for {label}")
    return scenarios, pending


def scenario_regressions(previous, current):
    rank = {"unsupported": 0, "pending": 1, "supported": 2}
    current_by_id = {row["id"]: row for row in current}
    regressions = []
    for old_row in previous:
        scenario_id = old_row["id"]
        new_row = current_by_id.get(scenario_id)
        if new_row is None:
            regressions.append(f"{scenario_id} removed")
            continue
        for _, version in EXPECTED:
            old = old_row["lines"][version]
            new = new_row["lines"][version]
            label = f"{scenario_id}:{version}"
            if rank[new["status"]] < rank[old["status"]]:
                regressions.append(f"{label} {old['status']} -> {new['status']}")
            elif old["status"] == "supported":
                old_names = old_row.get("caseNames", {}).get(version, [])
                new_names = new_row.get("caseNames", {}).get(version, [])
                old_names = {old_names} if isinstance(old_names, str) else set(old_names)
                new_names = {new_names} if isinstance(new_names, str) else set(new_names)
                # A consolidated test suite can retain coverage through individually required cases.
                if max(len(new["tests"]), len(new_names)) < max(len(old["tests"]), len(old_names)):
                    regressions.append(f"{label} required test coverage reduced")
                if len(new_names) < len(old_names):
                    regressions.append(f"{label} required named cases reduced")
                missing_names = old_names - new_names
                renames = SCENARIO_CASE_RENAMES.get(scenario_id, {})
                # A successor already required by the baseline cannot cover two former cases.
                missing_names = {name for name in missing_names
                                 if renames.get(name) not in new_names - old_names}
                if missing_names:
                    regressions.append(f"{label} required named cases removed: "
                                       + ", ".join(sorted(missing_names)))
    return regressions


def scenario_matrix(stage, roots, baseline=None):
    if baseline is not None:
        try:
            data = json.loads(baseline)
            if type(data["schemaVersion"]) is not int or data["schemaVersion"] != 1:
                raise ValueError("unsupported schemaVersion")
            previous, _ = validate_scenarios(data)
        except (KeyError, TypeError, ValueError) as error:
            return {"status": "FAIL", "reason": f"invalid scenario baseline: {error}"}

    paths = [root / "verification/three-line-parity/scenario-matrix.json" for root in roots]
    missing = [str(path) for path in paths if not path.is_file()]
    if missing:
        return {"status": "FAIL", "reason": "T09 scenario matrix is missing",
                "missing": missing}
    contents = [path.read_bytes() for path in paths]
    hashes = [hashlib.sha256(content).hexdigest() for content in contents]
    if len(set(hashes)) != 1:
        return {"status": "FAIL", "reason": "scenario matrix differs across version lines",
                "sha256ByLine": dict(zip((version for _, version in EXPECTED), hashes))}
    try:
        scenarios, pending = validate_scenarios(json.loads(contents[2]), roots)
    except (KeyError, TypeError, ValueError) as error:
        return {"status": "FAIL", "reason": str(error), "source": str(paths[2])}
    if baseline is not None:
        regressions = scenario_regressions(previous, scenarios)
        if regressions:
            return {"status": "FAIL", "reason": "scenario matrix regressed against baseline",
                    "regressions": regressions, "pending": pending}
    artifact = stage / "scenario-matrix.json"
    artifact.write_bytes(contents[2])
    return {"status": "BLOCKED" if pending else "PASS", "pending": pending,
            "scenarios": len(scenarios), "artifact": artifact.name,
            "artifactSha256": hashes[2]}


def baseline_result(stage, roots, path, content):
    if content is None:
        return {"status": "FAIL", "baseline": str(path),
                "reason": f"scenario baseline unavailable: {path}"}
    artifact = stage / "scenario-baseline.json"
    artifact.write_bytes(content)
    return {**scenario_matrix(stage, roots, content),
            "baseline": str(path), "baselineArtifact": artifact.name,
            "baselineSha256": hashlib.sha256(content).hexdigest()}


def read_scenario_baseline(path):
    try:
        return path.read_bytes()
    except OSError:
        return None


def publish_report(stage, destination, report):
    (stage / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n",
                                       encoding="utf-8")
    shutil.copytree(stage, destination)
    print(f"three-line={report['status']} phase={report['phase']} runId={report['runId']} "
          f"report={destination / 'report.json'}")
    return 0 if report["status"] in ("PASS", "INVENTORY") else 1


def test_suite_results(suite):
    """Read Maven test evidence and reconcile its summary with its reported cases."""
    xml = ET.parse(suite).getroot()
    suite_class = suite.stem.removeprefix("TEST-")
    namespace, _, element = xml.tag.rpartition("}")
    if element != "testsuite" or xml.get("name") != suite_class:
        raise ValueError("test suite name does not match the report filename")
    prefix = namespace + "}" if namespace else ""
    # Jupiter can report an enclosing class for nested tests. ArchUnit uses simple names.
    class_names = {suite_class}
    while "$" in suite_class:
        suite_class = suite_class.rsplit("$", 1)[0]
        class_names.add(suite_class)
    class_names |= {name.rsplit(".", 1)[-1] for name in class_names}
    cases = xml.findall(prefix + "testcase")
    observed = {"tests": len(cases), "failures": 0, "errors": 0, "skipped": 0}
    outcomes = {"failure": "failures", "error": "errors", "skipped": "skipped"}
    passed_cases = set()
    for case in cases:
        name = case.get("name")
        if not name or case.get("classname") not in class_names:
            raise ValueError("testcase name or class does not match the report suite")
        results = [outcomes[tag] for child in case
                   if (tag := child.tag.removeprefix(prefix)) in outcomes]
        if len(results) > 1:
            raise ValueError(f"testcase {name} has multiple final outcomes")
        if results:
            observed[results[0]] += 1
        else:
            passed_cases.add(name)
    declared = {key: int(xml.get(key, "0")) for key in observed}
    if declared != observed:
        raise ValueError(f"test counters do not match testcase outcomes: {declared} != {observed}")
    return observed, passed_cases


def scenario_test_reports(root, scenarios, version, started_ns, target="target"):
    """Match every supported scenario test to a fresh successful Surefire/Failsafe XML."""
    root = root.resolve()
    issues = []
    records = []
    executed = 0
    counted_reports = set()
    for row in scenarios:
        mapping = row["lines"][version]
        if mapping["status"] != "supported":
            continue
        for source in mapping["tests"]:
            module, separator, java_path = source.partition("/src/test/java/")
            if not separator or not module or not java_path.endswith(".java"):
                issues.append(f"{row['id']}: invalid Java test path {source}")
                continue
            directory = (root / module).resolve()
            if not directory.is_relative_to(root):
                issues.append(f"{row['id']}: test module escapes root {source}")
                continue
            class_name = java_path[:-5].replace("/", ".")
            prefix = "TEST-" + class_name
            suites = []
            for folder in ("surefire-reports", "failsafe-reports"):
                for suite in (directory / target / folder).glob(prefix + "*.xml"):
                    if suite.name == prefix + ".xml" or suite.name.startswith(prefix + "$"):
                        suites.append(suite)
            if not suites:
                issues.append(f"{row['id']}: missing executed XML for {source}")
                continue
            counts = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
            artifacts = {}
            source_reports = set()
            executed_cases = set()
            valid = True
            for suite in suites:
                if suite.stat().st_mtime_ns < started_ns:
                    issues.append(f"{row['id']}: stale XML {suite}")
                    valid = False
                    continue
                try:
                    values, passed_cases = test_suite_results(suite)
                    digest = sha256(suite)
                except (OSError, ValueError, ET.ParseError) as error:
                    issues.append(f"{row['id']}: invalid XML {suite}: {error}")
                    valid = False
                    continue
                artifacts[str(suite.relative_to(root))] = digest
                report_id = (directory, suite.name, digest)
                if report_id in source_reports:
                    continue
                source_reports.add(report_id)
                for key, value in values.items():
                    counts[key] += value
                executed_cases.update(passed_cases)
                if report_id not in counted_reports:
                    executed += values["tests"] - values["skipped"]
                    counted_reports.add(report_id)
            ran = counts["tests"] - counts["skipped"]
            if valid and (ran <= 0 or counts["failures"] or counts["errors"]
                          or counts["skipped"]):
                issues.append(f"{row['id']}: unsuccessful test report for {source}: {counts}")
            required_cases = row.get("caseNames", {}).get(version, [])
            if isinstance(required_cases, str):
                required_cases = [required_cases]
            for required_case in required_cases:
                if valid and required_case not in executed_cases:
                    issues.append(f"{row['id']}: missing executed test case {required_case} for {source}")
            records.append({"scenario": row["id"], "source": source,
                            "counts": counts, "artifacts": artifacts,
                            "requiredCases": required_cases})
    return {"status": "FAIL" if issues else "PASS", "version": version,
            "mappedTests": len(records), "uniqueReports": len(counted_reports),
            "executedTests": executed,
            "issues": issues, "tests": records}


def execute(name, command, stage, checks, cwd=None, env=None, required_artifacts=()):
    log = stage / (name + ".log")
    with log.open("w", encoding="utf-8") as stream:
        result = subprocess.run(command, cwd=cwd, env=env, stdout=stream, stderr=subprocess.STDOUT)
    artifacts = {}
    missing = []
    invalid = []
    for relative in required_artifacts:
        artifact = stage / relative
        if not artifact.is_file() or artifact.stat().st_size == 0:
            missing.append(relative)
            continue
        if artifact.suffix == ".json":
            try:
                json.loads(artifact.read_text(encoding="utf-8"))
            except (UnicodeError, json.JSONDecodeError):
                invalid.append(relative)
                continue
        artifacts[relative] = sha256(artifact)
    passed = result.returncode == 0 and not missing and not invalid
    checks[name] = {"status": "PASS" if passed else "FAIL", "exitCode": result.returncode,
                    "log": log.name, "logSha256": sha256(log), "artifacts": artifacts,
                    "missingArtifacts": missing, "invalidArtifacts": invalid}
    return passed


def verify_quarkus_sample_durable(root, stage, checks, environment):
    name = "quarkus-durable-sample-8"
    module = "ddd4j-samples/ddd4j-sample-quarkus-cqrs"
    test = "io.ddd4j.sample.quarkus.cqrs.NativeSharedCqrsTest"
    command = ["./mvnw", "-q", "-pl", module, "-am", "-Pdurable-order", f"-Dtest={test}",
               "-Dsurefire.failIfNoSpecifiedTests=false", "test"]
    started_ns = time.time_ns()
    if not execute(name, command, stage, checks, cwd=root, env=environment):
        return
    source = f"{module}/src/test/java/{test.replace('.', '/')}.java"
    scenario = {"id": "S03-POSTGRESQL-DURABLE",
                "lines": {"1.0.x": {"status": "supported", "tests": [source]}},
                "caseNames": {"1.0.x": "durableSourceProjectionFailureRetryAndSparsePosition"}}
    collect_explicit_scenario_report(root, stage, checks, name, scenario, started_ns,
                                     expected_tests=1, target="target/durable-order")


def verify_mybatisplus_mysql_8(root, stage, checks, environment):
    name = "mybatisplus-mysql-8"
    module = "ddd4j-data/ddd4j-data-mybatisplus"
    test = "io.ddd4j.data.mybatis.repository.MybatisAggregateRepositoryMySQLTest"
    command = ["./mvnw", "-q", "-pl", module, "-am", f"-Dtest={test}",
               "-Dsurefire.failIfNoSpecifiedTests=false", "test"]
    source = f"{module}/src/test/java/{test.replace('.', '/')}.java"
    methods = [
        "saveInsertsNewAggregate", "updateByIdUpdatesExistingOnly",
        "updateByIdDoesNotInsertWhenAbsent", "insertOrUpdateBehavesAsUpsert",
        "deleteByIdRemovesRow", "originalModelAndQueryUsagePersistsWithoutCqrsInfrastructure",
        "originalQueriesConvertAndFillPersistedModels", "originalQueriesScopeRowsToCurrentTenant",
    ]
    scenario = {"id": "MYBATISPLUS-MYSQL-NATIVE",
                "lines": {"1.0.x": {"status": "supported", "tests": [source]}},
                "caseNames": {"1.0.x": methods}}
    started_ns = time.time_ns()
    execute(name, command, stage, checks, cwd=root, env=environment)
    collect_explicit_scenario_report(root, stage, checks, name, scenario, started_ns,
                                     expected_tests=len(methods))


def collect_explicit_scenario_report(root, stage, checks, name, scenario, started_ns,
                                     *, expected_tests, target="target"):
    version = "1.0.x"
    reports = scenario_test_reports(root, [scenario], version, started_ns,
                                    target=target)
    checks[name]["testReports"] = reports
    for record in reports["tests"]:
        for relative in record["artifacts"]:
            destination = stage / f"{name}-{Path(relative).name}"
            shutil.copy2(root / relative, destination)
            checks[name]["artifacts"][destination.name] = sha256(destination)
    if reports["status"] != "PASS":
        checks[name]["status"] = "FAIL"
    elif reports["executedTests"] != expected_tests:
        checks[name].update(status="FAIL", reason=f"expected exactly {expected_tests} executed tests")


def default_reactor_modules(root):
    """Follow only default POM modules/subprojects, not inactive profile declarations."""
    root = root.resolve()
    pending = [root]
    visited = set()
    while pending:
        module = pending.pop()
        pom = module / "pom.xml"
        if pom in visited:
            continue
        if not pom.is_file():
            raise ValueError(f"missing reactor POM: {pom}")
        visited.add(pom)
        project = ET.parse(pom).getroot()
        for group in project:
            if group.tag.rsplit("}", 1)[-1] not in ("modules", "subprojects"):
                continue
            for child in group:
                if child.tag.rsplit("}", 1)[-1] in ("module", "subproject") and child.text:
                    candidate = (module / child.text.strip()).resolve()
                    if not candidate.is_relative_to(root.resolve()):
                        raise ValueError(f"reactor module escapes root: {candidate}")
                    pending.append(candidate)
    return sorted(path.parent for path in visited)


def reactor_reports(root, started_ns):
    """Require fresh Surefire XML from every default-reactor module with test sources."""
    root = root.resolve()
    try:
        modules = default_reactor_modules(root)
    except (OSError, ValueError, ET.ParseError) as error:
        return {"status": "FAIL", "reason": str(error), "modules": []}
    records = []
    issues = []
    total_executed = 0
    for module in modules:
        source = module / "src/test/java"
        candidates = [path for path in source.rglob("*.java")
                      if path.stem.startswith("Test") or path.stem.endswith(("Test", "Tests", "TestCase"))]
        if not candidates:
            continue
        relative = str(module.relative_to(root))
        suites = list((module / "target/surefire-reports").glob("TEST-*.xml"))
        if not suites:
            issues.append(f"{relative}: missing Surefire XML")
            continue
        counts = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
        artifacts = {}
        for suite in suites:
            if suite.stat().st_mtime_ns < started_ns:
                issues.append(f"{relative}: stale Surefire XML {suite.name}")
                continue
            try:
                values, _ = test_suite_results(suite)
            except (OSError, ValueError, ET.ParseError) as error:
                issues.append(f"{relative}: invalid Surefire XML {suite.name}: {error}")
                continue
            for name, value in values.items():
                counts[name] += value
            artifacts[suite.name] = sha256(suite)
        executed = counts["tests"] - counts["skipped"]
        total_executed += executed
        if executed <= 0 or counts["failures"] or counts["errors"] or counts["skipped"]:
            issues.append(f"{relative}: tests={counts['tests']} failures={counts['failures']} "
                          f"errors={counts['errors']} skipped={counts['skipped']}")
        records.append({"module": relative, "sourceCandidates": len(candidates),
                        "counts": counts, "artifacts": artifacts})
    if not records:
        issues.append("default reactor has no modules with executed test reports")
    return {"status": "FAIL" if issues else "PASS", "reactorModules": len(modules),
            "testedModules": len(records), "executedTests": total_executed,
            "issues": issues, "modules": records}


def reactor_javadocs(root, started_ns, log):
    """Require fresh Javadoc attachments and reject errors hidden by failOnError=false."""
    root = root.resolve()
    try:
        modules = default_reactor_modules(root)
        content = log.read_text(encoding="utf-8", errors="replace")
    except (OSError, ValueError, ET.ParseError) as error:
        return {"status": "FAIL", "reason": str(error), "issues": []}
    issues = []
    suppressed_errors = len(re.findall(
        r"(?m)^\[ERROR\] MavenReportException: Error while generating Javadoc:", content))
    if suppressed_errors:
        issues.append(f"Maven reported {suppressed_errors} Javadoc generation errors")
    artifacts = {}
    documented_modules = 0
    for module in modules:
        if not any((module / "src/main/java").rglob("*.java")):
            continue
        documented_modules += 1
        relative = str(module.relative_to(root))
        jars = list((module / "target").glob("*-javadoc.jar"))
        if not jars:
            issues.append(f"{relative}: missing Javadoc JAR")
            continue
        fresh = [jar for jar in jars if jar.stat().st_mtime_ns >= started_ns]
        if not fresh:
            issues.append(f"{relative}: stale Javadoc JAR")
            continue
        artifacts[relative] = {jar.name: sha256(jar) for jar in fresh}
    if not documented_modules:
        issues.append("default reactor has no modules with production Java sources")
    return {"status": "FAIL" if issues else "PASS",
            "documentedModules": documented_modules,
            "suppressedErrors": suppressed_errors,
            "issues": issues, "artifacts": artifacts}


def validate_checkout(root, version, expected_sha=None):
    """Accept a named local release branch or an explicitly pinned CI checkout."""
    actual_sha = subprocess.check_output(
        ["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip()
    project = ET.parse(root / "pom.xml").getroot()
    namespace = project.tag.partition("}")[0] + "}" if "}" in project.tag else ""
    properties = project.find(namespace + "properties")
    revision = properties.findtext(namespace + "revision", default="") if properties is not None else ""
    if not revision.startswith(version + "."):
        raise ValueError(f"wrong release line in pom.xml: {root}: {revision}")
    if expected_sha is not None:
        if not re.fullmatch(r"[0-9a-f]{40}", expected_sha) or actual_sha != expected_sha:
            raise ValueError(f"checkout SHA differs from pinned {version} input: {root}")
    elif subprocess.check_output(
            ["git", "-C", str(root), "branch", "--show-current"],
            text=True).strip() != "feature/" + version:
        raise ValueError(f"wrong release branch: {root}")
    return actual_sha


def run(args):
    baseline_path = getattr(args, "scenario_baseline", None)
    baseline = None
    if baseline_path is not None:
        baseline_path = baseline_path.absolute()
        baseline = read_scenario_baseline(baseline_path)
    roots = [path.resolve() for path in args.roots]
    jdks = [path.resolve() for path in args.jdks]
    policy = args.policy.resolve()
    output = args.output.resolve()
    if len(set(roots)) != 3 or not policy.is_file():
        raise ValueError("three distinct roots and a real policy file are required")
    before = []
    jdk_versions = []
    for index, root in enumerate(roots):
        if not (root / "pom.xml").is_file() or not (root / "mvnw").is_file():
            raise ValueError(f"missing pom.xml or Maven Wrapper: {root}")
        expected_sha = args.expected_shas[index] if args.expected_shas else None
        validate_checkout(root, EXPECTED[index][1], expected_sha)
        before.append(git_input(root))
        jdk_versions.append(jdk_info(jdks[index], EXPECTED[index][0]))
    policy_before = sha256(policy)
    run_id = uuid.uuid4().hex
    output.mkdir(parents=True, exist_ok=True)
    destination = output / run_id

    def finish_report(stage, checks):
        after = [git_input(root) for root in roots]
        policy_after = sha256(policy)
        stable = input_stable(before, after, policy_before, policy_after)
        if baseline_path is not None:
            baseline_after = read_scenario_baseline(baseline_path)
            matrix_check = checks["scenario-matrix"]
            matrix_check["baselineAfterSha256"] = (
                hashlib.sha256(baseline_after).hexdigest() if baseline_after is not None else None)
            if baseline != baseline_after:
                stable = False
                matrix_check.update(status="FAIL", reason="scenario baseline changed during verification")
        report = {"schemaVersion": 1, "runId": run_id, "phase": args.phase,
                  "status": combined_status(checks, args.phase, stable),
                  "policy": str(policy), "policySha256": policy_before,
                  "policyAfterSha256": policy_after,
                  "roots": [{"path": str(root), "before": old, "after": new,
                             "jdk": jdk, "wrapper": str(root / "mvnw")}
                            for root, old, new, jdk in zip(roots, before, after, jdk_versions)],
                  "inputStable": stable, "expectedShas": args.expected_shas,
                  "checks": checks}
        return publish_report(stage, destination, report)

    with tempfile.TemporaryDirectory(prefix="ddd4j-three-line-") as temporary:
        stage = Path(temporary)
        checks = {}
        if baseline_path is not None:
            checks["scenario-matrix"] = baseline_result(stage, roots, baseline_path, baseline)
            if checks["scenario-matrix"]["status"] == "FAIL":
                return finish_report(stage, checks)
        common = ["--roots", *map(str, roots)]
        structure = SCRIPT_DIR / "verify-three-line-structure-api-parity.py"
        structure_environment = os.environ.copy()
        for (major, _), jdk in zip(EXPECTED, jdks):
            structure_environment[f"DDD4J_JDK{major}_HOME"] = str(jdk)
        docs = SCRIPT_DIR / "verify-three-line-product-docs.py"
        execute("structure", [sys.executable, str(structure), *common,
                              "--policy", str(policy), "--phase", args.phase,
                              "--output", str(stage / "structure")], stage, checks,
                env=structure_environment,
                required_artifacts=("structure/inventory.json", "structure/differences.csv")
                + (("structure/compiled-api.json",) if args.phase == "verify" else ()))
        execute("product-docs", [sys.executable, str(docs), *common,
                                  "--output", str(stage / "product-docs")], stage, checks,
                required_artifacts=("product-docs/product-docs.json",))
        historical_index = stage / "historical-index.json"
        source_index = roots[2] / "verification/three-line-parity/historical-evidence-index.json"
        if source_index.is_file():
            shutil.copy2(source_index, historical_index)
        execute("historical-index",
                [sys.executable, str(SCRIPT_DIR / "build-historical-evidence-index.py"),
                 *common, "--output", str(historical_index), "--check-content"],
                stage, checks, required_artifacts=("historical-index.json",))
        if args.phase == "verify":
            values = SCRIPT_DIR / "verify-three-line-value-parity.py"
            classpaths = list(value_probe_classpaths(Path.home() / ".m2/repository"))
            execute("values", [sys.executable, str(values), *common,
                               "--jdks", *map(str, jdks),
                               "--classpaths", *classpaths,
                               "--output", str(stage / "values")], stage, checks,
                    required_artifacts=("values/results.json",))
            effective = []
            for index, (root, jdk) in enumerate(zip(roots, jdks)):
                artifact = stage / f"effective-{EXPECTED[index][0]}.xml"
                environment = os.environ.copy()
                environment["JAVA_HOME"] = str(jdk)
                success = execute(
                    f"effective-{EXPECTED[index][0]}",
                    ["./mvnw", "-q", "-pl", "ddd4j-dependencies",
                     "help:effective-pom", f"-Doutput={artifact}"],
                    stage, checks, cwd=root, env=environment,
                    required_artifacts=(artifact.name,))
                if success:
                    effective.append(str(artifact))
            if len(effective) == 3:
                catalog = SCRIPT_DIR / "bootstrap_dependency_catalog.py"
                execute("dependency-catalog", [sys.executable, str(catalog), *common,
                         "--effective", *effective, "--policy",
                         str(roots[2] / "config/dependencies/alignment-policy.json"),
                         "--output", str(stage / "dependency-catalog")],
                        stage, checks,
                        required_artifacts=("dependency-catalog/downloads.json",))
                dependency = SCRIPT_DIR / "verify_dependency_alignment.py"
                execute("dependencies", [sys.executable, str(dependency), *common,
                         "--effective", *effective, "--policy",
                         str(roots[2] / "config/dependencies/alignment-policy.json"),
                         "--strict-artifacts", "--output", str(stage / "dependencies")],
                        stage, checks, required_artifacts=("dependencies/result.json",
                                                        "dependencies/components.csv",
                                                        "dependencies/module-usage.json"))
            else:
                checks["dependency-catalog"] = {"status": "BLOCKED", "reason": "effective POM missing"}
                checks["dependencies"] = {"status": "BLOCKED", "reason": "effective POM missing"}
            reactor_starts = {}
            if reactors_can_run(checks):
                reactor_command = ["./mvnw", "-q", "clean", "verify"]
                # The Sa-Token Redis tests use unauthenticated DB 0 at 127.0.0.1.
                local_redis = re.fullmatch(r"redis://127\.0\.0\.1:([0-9]{1,5})(?:/0?)?",
                                           args.redis_native_url or "")
                redis_port = int(local_redis[1]) if local_redis else 0
                if 0 < redis_port <= 65535:
                    reactor_command.append(f"-Dddd4j.test.redis.port={redis_port}")
                for index, (root, jdk) in enumerate(zip(roots, jdks)):
                    environment = os.environ.copy()
                    environment["JAVA_HOME"] = str(jdk)
                    name = f"reactor-{EXPECTED[index][0]}"
                    started_ns = time.time_ns()
                    reactor_starts[EXPECTED[index][1]] = started_ns
                    execute(name, reactor_command, stage, checks, cwd=root, env=environment)
                    reports = reactor_reports(root, started_ns)
                    artifact = stage / f"{name}-reports.json"
                    artifact.write_text(json.dumps(reports, ensure_ascii=False, indent=2) + "\n",
                                        encoding="utf-8")
                    checks[name]["artifacts"][artifact.name] = sha256(artifact)
                    checks[name]["testReports"] = {"status": reports["status"],
                                                    "testedModules": reports.get("testedModules", 0),
                                                    "executedTests": reports.get("executedTests", 0),
                                                    "issues": reports.get("issues", [])}
                    javadocs = reactor_javadocs(root, started_ns, stage / f"{name}.log")
                    javadoc_artifact = stage / f"{name}-javadocs.json"
                    javadoc_artifact.write_text(
                        json.dumps(javadocs, ensure_ascii=False, indent=2) + "\n",
                        encoding="utf-8")
                    checks[name]["artifacts"][javadoc_artifact.name] = sha256(javadoc_artifact)
                    checks[name]["javadocs"] = {
                        "status": javadocs["status"],
                        "documentedModules": javadocs.get("documentedModules", 0),
                        "suppressedErrors": javadocs.get("suppressedErrors", 0),
                        "issues": javadocs.get("issues", [])}
                    if reports["status"] != "PASS" or javadocs["status"] != "PASS":
                        checks[name]["status"] = "FAIL"
                    capacity_name = f"capacity-{EXPECTED[index][0]}"
                    redis_name = f"redis-native-{EXPECTED[index][0]}"
                    if checks[name]["status"] == "PASS":
                        capacity_command, redis_command = explicit_scenario_commands(
                            EXPECTED[index][1], args.redis_native_url)
                        capacity_started_ns = time.time_ns()
                        if execute(capacity_name, capacity_command, stage, checks,
                                   cwd=root, env=environment):
                            source = (root / "ddd4j-core/target/three-line-parity/capacity/"
                                      "eventstore-append-baseline.json")
                            try:
                                capacity = json.loads(source.read_text(encoding="utf-8"))
                                version = capacity["environment"]["javaVersion"]
                                expected = ("1.8." if EXPECTED[index][0] == 8
                                            else str(EXPECTED[index][0]) + ".")
                                if (source.stat().st_mtime_ns < capacity_started_ns
                                        or capacity["branch"] != "feature/" + EXPECTED[index][1]
                                        or not version.startswith(expected)
                                        or len(capacity["metrics"]) != 3
                                        or any(metric["sampleCount"] != 2000
                                               for metric in capacity["metrics"])):
                                    raise ValueError("capacity report is stale or from another line")
                                artifact = stage / f"{capacity_name}.json"
                                shutil.copy2(source, artifact)
                                checks[capacity_name]["artifacts"][artifact.name] = sha256(artifact)
                            except (OSError, KeyError, ValueError, json.JSONDecodeError) as error:
                                checks[capacity_name]["status"] = "FAIL"
                                checks[capacity_name]["reason"] = str(error)
                        if redis_command:
                            execute(redis_name, redis_command, stage, checks,
                                    cwd=root, env=environment)
                        else:
                            checks[redis_name] = {"status": "BLOCKED",
                                                  "reason": "isolated Redis URL was not supplied"}
                    else:
                        for explicit_name in (capacity_name, redis_name):
                            checks[explicit_name] = {"status": "BLOCKED",
                                                     "reason": "line reactor failed"}
            else:
                for major, _ in EXPECTED:
                    checks[f"reactor-{major}"] = {"status": "BLOCKED",
                                                   "reason": "effective POM gate failed"}
                    checks[f"capacity-{major}"] = {"status": "BLOCKED",
                                                    "reason": "line reactor did not run"}
                    checks[f"redis-native-{major}"] = {"status": "BLOCKED",
                                                        "reason": "line reactor did not run"}
            if baseline_path is None:
                checks["scenario-matrix"] = scenario_matrix(stage, roots)
            if "artifact" in checks["scenario-matrix"] and len(reactor_starts) == 3:
                scenarios = json.loads((stage / "scenario-matrix.json").read_text(
                    encoding="utf-8"))["scenarios"]
                results = [scenario_test_reports(root, scenarios, version,
                                                 reactor_starts[version])
                           for root, (_, version) in zip(roots, EXPECTED)]
                artifact = stage / "scenario-execution.json"
                artifact.write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n",
                                    encoding="utf-8")
                checks["scenario-execution"] = {
                    "status": "FAIL" if any(result["status"] == "FAIL"
                                            for result in results) else "PASS",
                    "lines": [{"version": result["version"],
                               "status": result["status"],
                               "mappedTests": result["mappedTests"],
                               "uniqueReports": result["uniqueReports"],
                               "executedTests": result["executedTests"],
                               "issues": result["issues"]} for result in results],
                    "artifact": artifact.name, "artifactSha256": sha256(artifact)}
            else:
                checks["scenario-execution"] = {
                    "status": "BLOCKED", "reason": "scenario matrix or reactor evidence missing"}
            if "1.0.x" in reactor_starts:
                jdk8_environment = {**os.environ, "JAVA_HOME": str(jdks[0])}
                verify_quarkus_sample_durable(roots[0], stage, checks, jdk8_environment)
                verify_mybatisplus_mysql_8(roots[0], stage, checks, jdk8_environment)
            else:
                for name in ("quarkus-durable-sample-8", "mybatisplus-mysql-8"):
                    checks[name] = {"status": "BLOCKED", "reason": "Java 8 reactor did not run"}
        else:
            for name in ("values", "dependencies", "reactor", "scenario-matrix"):
                checks.setdefault(name, {"status": "NOT_RUN", "reason": "inventory phase"})
        return finish_report(stage, checks)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--roots", nargs=3, type=Path, required=True)
    parser.add_argument("--jdks", nargs=3, type=Path, required=True)
    parser.add_argument("--expected-shas", nargs=3, metavar=("SHA8", "SHA17", "SHA21"),
                        help="fixed Git commit IDs for detached CI checkouts")
    parser.add_argument("--policy", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--phase", choices=("inventory", "verify"), required=True)
    parser.add_argument("--scenario-baseline", type=Path,
                        help="immutable scenario matrix used to detect coverage regressions")
    parser.add_argument("--redis-native-url",
                        help="isolated Redis URL for native Stream scenarios and loopback reactor tests")
    args = parser.parse_args()
    try:
        return run(args)
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f"BLOCKED: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
