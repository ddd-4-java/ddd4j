#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORT_PATH="${DDD4J_PERFORMANCE_REPORT:-}"
OUTPUT_DIR="${DDD4J_REPORT_DIR:-${ROOT_DIR}/target/release-quality}/performance"

cd "${ROOT_DIR}"
head_commit="$(git rev-parse HEAD)"

# Parse and compare the same report bytes that will be archived.
if ! python3 - "${REPORT_PATH}" "${OUTPUT_DIR}/performance-report.json" "${head_commit}" <<'PYTHON'
from fractions import Fraction
import json
from pathlib import Path
import re
import sys
import tempfile


def unique_object(pairs):
    result = {}
    for name, value in pairs:
        if name in result:
            raise ValueError("duplicate JSON key")
        result[name] = value
    return result


def reject_constant(_):
    raise ValueError("invalid JSON constant")


def nonblank(text):
    return isinstance(text, str) and bool(text.strip())


def iterations(value, minimum):
    return type(value) in (int, Fraction) and value % 1 == 0 and value >= minimum


def valid_metrics(scenario):
    for key, baseline_limit, maximum in (
        ("p95Millis", 6, True),
        ("throughputPerSecond", 4, False),
        ("rssMegabytes", 6, True),
    ):
        value, baseline = scenario[key], scenario["baseline"][key]
        if any(type(number) not in (int, Fraction) or number <= 0 for number in (value, baseline)):
            return False
        observed, limit = value * 5, baseline * baseline_limit
        regressed = observed > limit if maximum else observed < limit
        if regressed:
            return False
    return True


def validate(data, head):
    report = json.loads(data, parse_float=Fraction, parse_constant=reject_constant,
                        object_pairs_hook=unique_object)
    baseline = report["baseline"]
    environment = report["environment"]
    scenarios = report["scenarios"]
    java17 = (r"17(?:(?:\.0)*\.[1-9][0-9]*)*"
              r"(?:(?:-[a-zA-Z0-9]+)?\+(?:0|[1-9][0-9]*)(?:-[-a-zA-Z0-9.]+)?"
              r"|-[a-zA-Z0-9]+(?:-[-a-zA-Z0-9.]+)?|\+-[-a-zA-Z0-9.]+)?")
    return (
        report["schemaVersion"] == "1.0"
        and report["reportKind"] == "ddd4j-performance"
        and report["commit"] == head
        and nonblank(baseline["version"])
        and not baseline["version"].strip().upper().endswith("SNAPSHOT")
        and re.fullmatch(r"[0-9a-f]{40}", baseline["commit"]) is not None
        and nonblank(environment["runnerId"])
        and re.fullmatch(java17, environment["javaVersion"]) is not None
        and iterations(environment["warmupIterations"], 0)
        and iterations(environment["measurementIterations"], 1)
        and nonblank(report["generator"]["command"])
        and re.fullmatch(r"[0-9a-f]{64}", report["rawOutputSha256"]) is not None
        and isinstance(scenarios, list)
        and sorted(s["name"] for s in scenarios)
            == ["idempotency-lease", "mq-outbox-dispatch", "web-request-contract"]
        and all(valid_metrics(s) for s in scenarios)
    )


source, output = map(Path, sys.argv[1:3])
try:
    try:
        if source.is_dir():
            reports = [path for path in source.rglob("*.json") if path.is_file()]
            if len(reports) != 1:
                raise ValueError("performance artifact must contain exactly one JSON report")
            source = reports[0]
        data = source.read_bytes()
    finally:
        output.unlink(missing_ok=True)
    if not validate(data, sys.argv[3]):
        sys.exit(1)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=output.parent, delete=False) as temp:
        staged = Path(temp.name)
    try:
        staged.write_bytes(data)
        staged.replace(output)
    finally:
        staged.unlink(missing_ok=True)
except (OSError, KeyError, TypeError, ValueError, OverflowError, RecursionError):
    sys.exit(1)
PYTHON
then
  echo "[FAIL] Performance evidence is incomplete, targets another commit, or exceeds the 20% regression threshold." >&2
  exit 1
fi

echo "[PASS] Performance evidence matches ${head_commit}; report copied to ${OUTPUT_DIR}."
