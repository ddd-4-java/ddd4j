#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CVE_DIR="${DDD4J_REPORT_DIR:-${ROOT_DIR}/target/release-quality}/cve"
cd "${ROOT_DIR}"
if [[ "${DDD4J_ENABLE_CVE_SCAN:-false}" != "true" ]]; then echo "[SKIP] CVE scan is disabled"; exit 0; fi
if [[ -z "${NVD_API_KEY:-}" ]]; then echo "[FAIL] NVD_API_KEY is required" >&2; exit 1; fi
rm -rf "${CVE_DIR}" && mkdir -p "${CVE_DIR}"
./mvnw -B -ntp -DskipTests org.owasp:dependency-check-maven:12.2.2:aggregate \
  -Dformat=ALL -DoutputDirectory="${CVE_DIR}" \
  -DfailBuildOnCVSS=7
find "${CVE_DIR}" -maxdepth 1 -type f -name 'dependency-check-report.*' -size +0c -print -quit | grep -q .
echo "[PASS] CVE reports generated in ${CVE_DIR}; CVSS 7.0 or higher blocks the build."
