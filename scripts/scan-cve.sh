#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CVE_DIR="${DDD4J_REPORT_DIR:-${ROOT_DIR}/target/release-quality}/cve"
cd "${ROOT_DIR}"
if [[ "${DDD4J_ENABLE_CVE_SCAN:-false}" != "true" ]]; then
  echo "[SKIP] CVE scan is disabled"
  exit 0
fi
BRANCH="${DDD4J_BRANCH:-${GITHUB_HEAD_REF:-${GITHUB_REF_NAME:-}}}"
if [[ -z "${BRANCH}" ]]; then
  echo "[FAIL] DDD4J_BRANCH or a GitHub branch name is required" >&2
  exit 1
fi
THRESHOLD="$(PYTHONPATH="${ROOT_DIR}/scripts" python3 - "${BRANCH}" <<'PY'
import json
import sys

from dependencies.check_dependencies import resolve_policy

with open('.github/dependency-policy.json', encoding='utf-8') as source:
    policy = resolve_policy(json.load(source), sys.argv[1])
print(policy['cvss_threshold'])
PY
)"
rm -rf "${CVE_DIR}" && mkdir -p "${CVE_DIR}"
python3 scripts/dependencies/scan_vulnerabilities.py \
  --root "${ROOT_DIR}" --output "${CVE_DIR}" \
  --line "${BRANCH}" --threshold "${THRESHOLD}"
echo "[PASS] Keyless Trivy reports generated in ${CVE_DIR}; CVSS ${THRESHOLD} or higher blocks the build."
