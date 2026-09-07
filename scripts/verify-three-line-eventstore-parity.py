#!/usr/bin/env python3
"""在 JDK8/17/21 上运行同一组 EventStore 行为断言并比较结果。"""

import argparse
import json
import os
import pathlib
import subprocess
import sys
import time
import xml.etree.ElementTree as element_tree


MODULES = ",".join((
    "ddd4j-data/ddd4j-data-event-store-jdbi",
    "ddd4j-data/ddd4j-data-event-store-jpa",
    "ddd4j-data/ddd4j-data-event-store-r2dbc",
))
SHARED_TESTS = (
    "JdbiEventStoreTest#appendMustNotMutateInputAggregateVersion"
    "+readAllLimitMustBePositive+persistedTimestampMustComeFromEvent,"
    "JpaEventStoreTest#appendMustNotMutateInputAggregateVersion"
    "+readAllLimitMustBePositive+persistedTimestampMustComeFromEvent,"
    "R2dbcAsyncEventStoreTest#readAllLimitMustBePositive+persistedTimestampMustComeFromEvent"
)
EXPECTED = {
    ("io.ddd4j.data.event.store.jdbi.JdbiEventStoreTest", "appendMustNotMutateInputAggregateVersion"),
    ("io.ddd4j.data.event.store.jdbi.JdbiEventStoreTest", "readAllLimitMustBePositive"),
    ("io.ddd4j.data.event.store.jdbi.JdbiEventStoreTest", "persistedTimestampMustComeFromEvent"),
    ("io.ddd4j.data.event.store.jpa.JpaEventStoreTest", "appendMustNotMutateInputAggregateVersion"),
    ("io.ddd4j.data.event.store.jpa.JpaEventStoreTest", "readAllLimitMustBePositive"),
    ("io.ddd4j.data.event.store.jpa.JpaEventStoreTest", "persistedTimestampMustComeFromEvent"),
    ("io.ddd4j.data.event.store.r2dbc.R2dbcAsyncEventStoreTest", "readAllLimitMustBePositive"),
    ("io.ddd4j.data.event.store.r2dbc.R2dbcAsyncEventStoreTest", "persistedTimestampMustComeFromEvent"),
}
REPORTS = (
    ("ddd4j-data/ddd4j-data-event-store-jdbi", "io.ddd4j.data.event.store.jdbi.JdbiEventStoreTest"),
    ("ddd4j-data/ddd4j-data-event-store-jpa", "io.ddd4j.data.event.store.jpa.JpaEventStoreTest"),
    ("ddd4j-data/ddd4j-data-event-store-r2dbc", "io.ddd4j.data.event.store.r2dbc.R2dbcAsyncEventStoreTest"),
)


def java_home(version: str) -> pathlib.Path:
    value = subprocess.check_output(["/usr/libexec/java_home", "-v", version], text=True).strip()
    return pathlib.Path(value)


def observed_tests(root: pathlib.Path, suffix: str,
                   started_ns: int) -> tuple[set[tuple[str, str]], list[str]]:
    observed = set()
    invalid_reports = []
    for module, class_name in REPORTS:
        relative = f"{module}/target/surefire-reports/TEST-{class_name}-{suffix}.xml"
        report = root / relative
        if not report.is_file():
            invalid_reports.append(f"missing:{relative}")
            continue
        if report.stat().st_mtime_ns < started_ns:
            invalid_reports.append(f"stale:{relative}")
            continue
        document = element_tree.parse(report)
        suite = document.getroot()
        if any(int(suite.attrib.get(key, "0")) != 0 for key in ("failures", "errors", "skipped")):
            invalid_reports.append(f"non-green:{relative}")
            continue
        for case in document.iter("testcase"):
            actual_class = case.attrib.get("classname", "")
            marker = f"({suffix})"
            if actual_class.endswith(marker):
                actual_class = actual_class[:-len(marker)]
            key = (actual_class, case.attrib.get("name", ""))
            failed = any(child.tag in {"failure", "error", "skipped"} for child in case)
            if key in EXPECTED and not failed:
                observed.add(key)
    return observed, invalid_reports


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--roots", nargs=3, required=True, type=pathlib.Path)
    parser.add_argument("--output", required=True, type=pathlib.Path)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)

    results = []
    for index, (supplied_root, jdk_version) in enumerate(zip(args.roots, ("1.8", "17", "21")), 1):
        root = supplied_root.resolve()
        jdk = java_home(jdk_version)
        executable = root / "mvnw" if index == 3 else pathlib.Path("mvn")
        command = [
            str(executable), "-B", "-ntp", "-Pparity-verification",
            "-pl", MODULES, "-am", f"-Dtest={SHARED_TESTS}",
            "-Dsurefire.failIfNoSpecifiedTests=false", "-Denforcer.skip=true",
        ]
        suffix = f"ddd4j-parity-line-{index}-{time.time_ns()}"
        command.extend([f"-Dsurefire.reportNameSuffix={suffix}", "test"])
        environment = os.environ.copy()
        environment["JAVA_HOME"] = str(jdk)
        environment["PATH"] = str(jdk / "bin") + os.pathsep + environment["PATH"]
        started_ns = time.time_ns()
        completed = subprocess.run(command, cwd=root, env=environment, text=True, capture_output=True)
        log = args.output / f"line-{index}.log"
        log.write_text(completed.stdout + completed.stderr, encoding="utf-8")
        observed, invalid_reports = observed_tests(root, suffix, started_ns)
        results.append({
            "line": index,
            "root": str(root),
            "jdk": str(jdk),
            "head": subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip(),
            "exit": completed.returncode,
            "observed": sorted([list(item) for item in observed]),
            "missing": sorted([list(item) for item in EXPECTED - observed]),
            "invalid_reports": invalid_reports,
            "surefire_report_suffix": suffix,
            "contract_outcome": [
                {"class": class_name, "method": method_name, "outcome": "PASS"}
                for class_name, method_name in sorted(observed)
            ],
            "log": str(log),
        })

    outcome_vectors = [result["contract_outcome"] for result in results]
    outputs_equal = outcome_vectors[0] == outcome_vectors[1] == outcome_vectors[2]
    passed = all(
        result["exit"] == 0 and not result["missing"] and not result["invalid_reports"]
        for result in results
    ) and outputs_equal
    output = {
        "passed": passed,
        "evidence_kind": "fresh same-input executable assertions; PASS/FAIL outcomes compared across lines",
        "shared_assertions": len(EXPECTED),
        "outcomes_equal": outputs_equal,
        "runs": results,
    }
    destination = args.output / "results.json"
    destination.write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")
    for result in results:
        print(f"line={result['line']} exit={result['exit']} observed={len(result['observed'])} "
              f"missing={len(result['missing'])} invalid_reports={len(result['invalid_reports'])} "
              f"log={result['log']}")
    print(f"passed={passed} outcomes_equal={outputs_equal} report={destination}")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
