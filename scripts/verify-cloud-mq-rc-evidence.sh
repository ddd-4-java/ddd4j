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


if ! command -v python3 >/dev/null 2>&1; then
  echo "[FAIL] python3 is required to validate the cloud MQ report." >&2
  exit 1
fi

candidate_report="$(mktemp)"
trap 'rm -f -- "${candidate_report}"' EXIT
if ! cp -- "${REPORT_PATH}" "${candidate_report}" 2>/dev/null; then
  echo "[FAIL] Could not read the cloud MQ report." >&2
  exit 1
fi

# Validate and archive the same bytes. Reject ambiguous JSON before jq can discard a duplicate member.
if ! python3 - "${candidate_report}" >/dev/null 2>&1 <<'PYTHON'
import json
import sys
from urllib.parse import parse_qsl, urlsplit


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate JSON member")
        result[key] = value
    return result


def reject_constant(_):
    raise ValueError("non-JSON number")


credential_parameters = {
    "accesskey", "access_key", "access_token", "client_secret", "password",
    "refresh_token", "secret", "secret_access_key", "secret_key", "session_token", "token",
}
with open(sys.argv[1], encoding="utf-8-sig") as report_file:
    report = json.load(report_file, object_pairs_hook=unique_object, parse_constant=reject_constant)
if not isinstance(report, dict):
    raise ValueError("expected one JSON report object")
for execution in report.get("executions", []):
    uri = urlsplit(execution["evidenceUri"])
    if "@" in uri.netloc:
        raise ValueError("URI contains user information")
    for parameters in (uri.query, uri.fragment):
        if any(key.casefold().replace("-", "_") in credential_parameters
               for key, _ in parse_qsl(parameters, keep_blank_values=True)):
            raise ValueError("URI contains a credential parameter")
PYTHON
then
  echo "[FAIL] Cloud MQ evidence is malformed or may expose credentials." >&2
  exit 1
fi

cd "${ROOT_DIR}"
head_commit="$(git rev-parse HEAD)"
required_checks='["ack", "consume", "message-id", "publish", "retry"]'

if ! jq -e --arg commit "${head_commit}" --argjson required "${required_checks}" '
    def hostname:
        type == "string"
        and (sub("\\.\\z"; "") |
            length <= 253
            and (split(".") | all(.[]; test("\\A[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\z"))));

    .schemaVersion == "1.0"
    and .reportKind == "ddd4j-cloud-mq-rc"
    and .commit == $commit
    and (.generatedAt | type == "string" and length > 0)
    and ([.executions[].broker] | unique | sort) == ["ons", "tdmq"]
    and all(.executions[];
        (.result == "passed")
        and (.region | type == "string" and length > 0)
        and (.endpointHost | hostname)
        and (.clientVersion | type == "string" and length > 0)
        and (.rawLogSha256 | test("^[0-9a-f]{64}$"))
        and (.evidenceUri | type == "string" and length > 0)
        and ((.checks | sort) == ($required | sort))
    )
    and ([.. | objects | keys[]? | ascii_downcase | select(test("secret|password|access[-_]?key|token"))] | length == 0)
' "${candidate_report}" >/dev/null 2>&1; then
  echo "[FAIL] Cloud MQ evidence is incomplete, contains a failed broker run, or may expose credentials." >&2
  exit 1
fi

mkdir -p "${OUTPUT_DIR}"
cp -- "${candidate_report}" "${OUTPUT_DIR}/cloud-mq-rc-report.json"
echo "[PASS] ONS and TDMQ cloud release-candidate evidence matches ${head_commit}."
