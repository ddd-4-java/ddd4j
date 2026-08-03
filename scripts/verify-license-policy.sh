#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LICENSE_FILE="${DDD4J_REPORT_DIR:-${ROOT_DIR}/target/release-quality}/licenses/THIRD-PARTY.txt"
SELECTIONS_FILE="${ROOT_DIR}/config/license-selections.tsv"
BUILD_TOOL_EXCLUSIONS_FILE="${ROOT_DIR}/config/license-build-tool-exclusions.tsv"
SBOM_FILE="${DDD4J_REPORT_DIR:-${ROOT_DIR}/target/release-quality}/sbom/ddd4j-sbom.json"

cd "${ROOT_DIR}"
if [[ ! -s "${LICENSE_FILE}" ]]; then
  ./scripts/generate-license-report.sh
fi
if [[ ! -s "${SBOM_FILE}" ]]; then
  ./scripts/generate-sbom.sh
fi

# The release policy deliberately has a small allow-list. A dependency not covered by
# it must be reviewed rather than silently becoming part of a published ddd4j JAR.
FORBIDDEN_PATTERN='AFFERO|AGPL|GENERAL PUBLIC LICENSE|\bGPL\b|LGPL|LESSER GENERAL|CDDL|Commercial License|Unknown [Ll]icense'
VIOLATIONS_FILE="${LICENSE_FILE%.txt}-policy-violations.txt"
RAW_VIOLATIONS_FILE="${VIOLATIONS_FILE}.raw"

grep -E "${FORBIDDEN_PATTERN}" "${LICENSE_FILE}" > "${RAW_VIOLATIONS_FILE}" || true
: > "${VIOLATIONS_FILE}"

while IFS= read -r violation; do
  coordinate="$(sed -E 's/.*\(([A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[^ ]+) - .*/\1/' <<< "${violation}")"
  selection=""
  if [[ -f "${SELECTIONS_FILE}" ]]; then
    selection="$(awk -F $'\t' -v coordinate="${coordinate}" '$1 == coordinate { print $2 }' "${SELECTIONS_FILE}")"
  fi

  if [[ -n "${selection}" ]] && grep -Eq "${selection}" <<< "${violation}"; then
    echo "[PASS] Selected compatible license for ${coordinate}: ${selection}"
    continue
  fi

  build_tool_reason=""
  if [[ -f "${BUILD_TOOL_EXCLUSIONS_FILE}" ]]; then
    build_tool_reason="$(awk -F $'\t' -v coordinate="${coordinate}" '$1 == coordinate { print $2 }' "${BUILD_TOOL_EXCLUSIONS_FILE}")"
  fi
  if [[ -n "${build_tool_reason}" ]]; then
    IFS=':' read -r group_id artifact_id version <<< "${coordinate}"
    component_ref="pkg:maven/${group_id//.//}/${artifact_id}@${version}"
    if ! grep -Fq "${component_ref}" "${SBOM_FILE}"; then
      echo "[PASS] Excluded build-tool-only component absent from SBOM: ${coordinate} (${build_tool_reason})"
      continue
    fi
  fi

  echo "${violation}" >> "${VIOLATIONS_FILE}"
done < "${RAW_VIOLATIONS_FILE}"

if [[ -s "${VIOLATIONS_FILE}" ]]; then
  echo "[FAIL] License policy violations were found:" >&2
  cat "${VIOLATIONS_FILE}" >&2
  echo "[FAIL] Allowed licenses: Apache-2.0, MIT, BSD, EPL-2.0 and ISC." >&2
  exit 1
fi

echo "[PASS] No blocked license family was found in ${LICENSE_FILE}."
