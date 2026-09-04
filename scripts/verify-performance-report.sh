#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORT_PATH="${DDD4J_PERFORMANCE_REPORT:-}"
OUTPUT_DIR="${DDD4J_REPORT_DIR:-${ROOT_DIR}/target/release-quality}/performance"

if [[ -z "${REPORT_PATH}" || ! -f "${REPORT_PATH}" ]]; then
    echo "[FAIL] DDD4J_PERFORMANCE_REPORT must point to a fixed-runner JSON performance report." >&2
    exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
    echo "[FAIL] jq is required to validate the performance report." >&2
    exit 1
fi

cd "${ROOT_DIR}"
head_commit="$(git rev-parse HEAD)"
required_scenarios='["idempotency-lease", "mq-outbox-dispatch", "web-request-contract"]'

if ! jq -e --arg commit "${head_commit}" --argjson required "${required_scenarios}" '
    .schemaVersion == "1.0"
    and .reportKind == "ddd4j-performance"
    and .commit == $commit
    and (.baseline.version | type == "string" and length > 0 and endswith("SNAPSHOT") | not)
    and (.baseline.commit | test("^[0-9a-f]{40}$"))
    and (.environment.runnerId | type == "string" and length > 0)
    and (.environment.javaVersion | type == "string" and startswith("17"))
    and (.generator.command | type == "string" and length > 0)
    and (.rawOutputSha256 | test("^[0-9a-f]{64}$"))
    and ([.scenarios[].name] | sort) == ($required | sort)
    and all(.scenarios[];
        (.p95Millis | type == "number" and . > 0)
        and (.throughputPerSecond | type == "number" and . > 0)
        and (.rssMegabytes | type == "number" and . > 0)
        and (.baseline.p95Millis | type == "number" and . > 0)
        and (.baseline.throughputPerSecond | type == "number" and . > 0)
        and (.baseline.rssMegabytes | type == "number" and . > 0)
        and (.p95Millis <= (.baseline.p95Millis * 1.2))
        and (.throughputPerSecond >= (.baseline.throughputPerSecond * 0.8))
        and (.rssMegabytes <= (.baseline.rssMegabytes * 1.2))
    )
' "${REPORT_PATH}" >/dev/null; then
    echo "[FAIL] Performance evidence is incomplete, targets another commit, or exceeds the 20% regression threshold." >&2
    exit 1
fi

mkdir -p "${OUTPUT_DIR}"
cp "${REPORT_PATH}" "${OUTPUT_DIR}/performance-report.json"
echo "[PASS] Performance evidence matches ${head_commit}; report copied to ${OUTPUT_DIR}."
