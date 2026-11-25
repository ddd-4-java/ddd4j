#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORT_PATH="${DDD4J_CLOUD_MQ_RC_REPORT:-}"
OUTPUT_DIR="${DDD4J_REPORT_DIR:-${ROOT_DIR}/target/release-quality}/cloud-mq-rc"

if [[ "${OUTPUT_DIR}" != /* ]]; then
  OUTPUT_DIR="${ROOT_DIR}/${OUTPUT_DIR}"
fi
archived_report="${OUTPUT_DIR}/cloud-mq-rc-report.json"

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
# Preserve a single snapshot for both validation and the archive.
if ! python3 - "${REPORT_PATH}" "${candidate_report}" >/dev/null 2>&1 <<'PYTHON'
import hashlib
import json
from pathlib import Path
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
if not sys.argv[1]:
    raise ValueError("missing report path")
artifact = Path(sys.argv[1])
source = artifact
if artifact.is_dir():
    reports = [path for path in artifact.rglob("*.json") if path.is_file()]
    if len(reports) != 1:
        raise ValueError("cloud MQ artifact must contain exactly one JSON report")
    source = reports[0]
raw_report = source.read_bytes()
report = json.loads(raw_report.decode("utf-8-sig"), object_pairs_hook=unique_object,
                    parse_constant=reject_constant)
if not isinstance(report, dict):
    raise ValueError("expected one JSON report object")
executions = report.get("executions", [])
for execution in executions:
    uri = urlsplit(execution["evidenceUri"])
    if "@" in uri.netloc:
        raise ValueError("URI contains user information")
    for parameters in (uri.query, uri.fragment):
        if any(key.casefold().replace("-", "_") in credential_parameters
               for key, _ in parse_qsl(parameters, keep_blank_values=True)):
            raise ValueError("URI contains a credential parameter")

if artifact.is_dir():
    broker_logs = {broker: list(artifact.rglob(f"{broker}.raw.log")) for broker in ("ons", "tdmq")}
    if any(broker_logs.values()):
        for broker, logs in broker_logs.items():
            matches = [execution for execution in executions if execution["broker"] == broker]
            if len(logs) != 1 or len(matches) != 1:
                raise ValueError("expected one log and one record for each broker")
            digest = hashlib.sha256(logs[0].read_bytes()).hexdigest()
            if digest != matches[0]["rawLogSha256"]:
                raise ValueError("broker log checksum differs from report")
Path(sys.argv[2]).write_bytes(raw_report)
PYTHON
then
  rm -f -- "${archived_report}"
  echo "[FAIL] Cloud MQ evidence requires one readable JSON report, matching attached logs and no credential fields." >&2
  exit 1
fi
rm -f -- "${archived_report}"

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
if ! cp -- "${candidate_report}" "${archived_report}"; then
  rm -f -- "${archived_report}"
  exit 1
fi
echo "[PASS] ONS and TDMQ cloud release-candidate evidence matches ${head_commit}."
