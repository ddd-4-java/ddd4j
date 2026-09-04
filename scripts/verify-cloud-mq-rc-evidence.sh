#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORT_PATH="${DDD4J_CLOUD_MQ_RC_REPORT:-}"
OUTPUT_DIR="${DDD4J_REPORT_DIR:-${ROOT_DIR}/target/release-quality}/cloud-mq-rc"

if [[ -z "${REPORT_PATH}" || ! -f "${REPORT_PATH}" ]]; then
    echo "[FAIL] DDD4J_CLOUD_MQ_RC_REPORT must point to a cloud MQ release-candidate JSON report." >&2
    exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
    echo "[FAIL] jq is required to validate the cloud MQ report." >&2
    exit 1
fi

cd "${ROOT_DIR}"
head_commit="$(git rev-parse HEAD)"
required_checks='["ack", "consume", "message-id", "publish", "retry"]'

if ! jq -e --arg commit "${head_commit}" --argjson required "${required_checks}" '
    .schemaVersion == "1.0"
    and .reportKind == "ddd4j-cloud-mq-rc"
    and .commit == $commit
    and (.generatedAt | type == "string" and length > 0)
    and ([.executions[].broker] | unique | sort) == ["ons", "tdmq"]
    and all(.executions[];
        (.result == "passed")
        and (.region | type == "string" and length > 0)
        and (.endpointHost | type == "string" and length > 0)
        and (.clientVersion | type == "string" and length > 0)
        and (.rawLogSha256 | test("^[0-9a-f]{64}$"))
        and (.evidenceUri | type == "string" and length > 0)
        and ((.checks | sort) == ($required | sort))
    )
    and ([.. | objects | keys[]? | ascii_downcase | select(test("secret|password|accesskey|token"))] | length == 0)
' "${REPORT_PATH}" >/dev/null; then
    echo "[FAIL] Cloud MQ evidence is incomplete, contains a failed broker run, or may expose credentials." >&2
    exit 1
fi

mkdir -p "${OUTPUT_DIR}"
cp "${REPORT_PATH}" "${OUTPUT_DIR}/cloud-mq-rc-report.json"
echo "[PASS] ONS and TDMQ cloud release-candidate evidence matches ${head_commit}."
