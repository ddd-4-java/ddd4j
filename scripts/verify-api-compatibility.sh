#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORT_DIR="${DDD4J_REPORT_DIR:-${ROOT_DIR}/target/release-quality}/api"
BASELINE_VERSION="${DDD4J_API_BASELINE_VERSION:-}"

EXPECTED_LINE="$(sed -nE 's/.*<revision>([123])\.0\.x\..*<\/revision>.*/\1/p' "${ROOT_DIR}/pom.xml" | head -n 1)"
if [[ -z "${EXPECTED_LINE}" ]]; then
  echo "[FAIL] Could not determine this checkout's release line from pom.xml." >&2
  exit 1
fi

if [[ -z "${BASELINE_VERSION}" || "${BASELINE_VERSION}" == *SNAPSHOT || ! "${BASELINE_VERSION}" =~ ^${EXPECTED_LINE}\.0\.x\. ]]; then
  echo "[FAIL] DDD4J_API_BASELINE_VERSION must identify a published non-SNAPSHOT ${EXPECTED_LINE}.0.x baseline." >&2
  exit 1
fi

cd "${ROOT_DIR}"
rm -rf "${REPORT_DIR}"

version_file="$(mktemp "${TMPDIR:-/tmp}/ddd4j-api-version.XXXXXX")"
trap 'rm -f "${version_file}"' EXIT
./mvnw -q -B -N help:evaluate -Dexpression=project.version -Doutput="${version_file}"
CURRENT_VERSION="$(cat "${version_file}")"
if [[ ! "${CURRENT_VERSION}" =~ ^[[:alnum:]][[:alnum:]._-]*$ ]]; then
  echo "[FAIL] Could not resolve the current Maven project version." >&2
  exit 1
fi

# Follow the same default Maven reactor as the deploy workflow.
published_modules="$(python3 - <<'PYTHON'
from functools import lru_cache
from pathlib import Path
import runpy
import sys
from xml.etree import ElementTree as ET

root = Path.cwd()
reactor_modules = runpy.run_path("scripts/verify-three-line-convergence.py")["default_reactor_modules"]

@lru_cache(maxsize=None)
def read_pom(path):
    return ET.parse(path).getroot()

def deploy_skip(pom):
    project = read_pom(pom)
    skip = project.findtext("{*}properties/{*}maven.deploy.skip")
    if skip is not None:
        return skip.strip().lower()
    if project.find("{*}parent") is not None:
        relative = project.findtext("{*}parent/{*}relativePath", "../pom.xml").strip()
        parent = pom.parent / relative
        if relative and parent.is_file():
            return deploy_skip(parent.resolve())
    return "false"

for module in reactor_modules(root):
    pom = module / "pom.xml"
    project = read_pom(pom)
    if project.findtext("{*}packaging", "jar").strip() not in ("jar", "maven-plugin"):
        continue
    skip = deploy_skip(pom)
    if skip == "true":
        continue
    if skip != "false":
        sys.exit(f"[FAIL] Unsupported maven.deploy.skip for release API comparison: {pom}")
    artifact_id = project.findtext("{*}artifactId")
    if not artifact_id or not artifact_id.strip():
        sys.exit(f"[FAIL] Missing artifactId in {pom}")
    print(artifact_id.strip(), module.relative_to(root), sep="\t")
PYTHON
)"

if [[ -z "${published_modules}" ]]; then
  echo "[FAIL] No published reactor JAR modules found for API comparison." >&2
  exit 1
fi

while IFS=$'\t' read -r artifact_id module_dir; do
  baseline_jar="${HOME}/.m2/repository/io/ddd4j/${artifact_id}/${BASELINE_VERSION}/${artifact_id}-${BASELINE_VERSION}.jar"
  if [[ ! -f "${baseline_jar}" ]]; then
    if ! ./mvnw -q -ntp -N org.apache.maven.plugins:maven-dependency-plugin:3.8.1:get \
      -Dartifact="io.ddd4j:${artifact_id}:${BASELINE_VERSION}:jar" -Dtransitive=false; then
      echo "[FAIL] Published baseline JAR is unavailable: io.ddd4j:${artifact_id}:${BASELINE_VERSION}." >&2
      exit 1
    fi
  fi
done <<< "${published_modules}"

echo "[INFO] Comparing published reactor JARs with ${BASELINE_VERSION}."
./mvnw -B -ntp -Papi-compatibility \
  -Dddd4j.api.baseline.version="${BASELINE_VERSION}" \
  -Dddd4j.api.include="io.ddd4j.**" \
  -DskipTests clean verify

# Maven clean also removes the default report directory under target.
mkdir -p "${REPORT_DIR}"
failures=0
while IFS=$'\t' read -r artifact_id module_dir; do
  current_jar="${module_dir}/target/${artifact_id}-${CURRENT_VERSION}.jar"
  report_path="${module_dir}/target/japicmp/verify-public-api-compatibility.xml"
  if [[ ! -f "${current_jar}" ]]; then
    echo "[FAIL] Missing primary JAR for ${artifact_id}: ${current_jar}." >&2
    failures=$((failures + 1))
    continue
  fi
  if ! jar tf "${current_jar}" | grep -E '^io/ddd4j/.+\.class$' >/dev/null; then
    echo "[FAIL] No io.ddd4j classes found in ${current_jar}." >&2
    failures=$((failures + 1))
    continue
  fi
  if [[ ! -s "${report_path}" ]]; then
    echo "[FAIL] JApiCmp did not create a report for ${artifact_id}." >&2
    failures=$((failures + 1))
    continue
  fi
  cp "${report_path}" "${REPORT_DIR}/${artifact_id}.xml"
done <<< "${published_modules}"

if ((failures > 0)); then
  echo "[FAIL] API compatibility failed for ${failures} module(s)." >&2
  exit 1
fi

echo "[PASS] Binary API compatibility passed against ${BASELINE_VERSION}; reports are in ${REPORT_DIR}."
