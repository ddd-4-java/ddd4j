#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SBOM_DIR="${DDD4J_REPORT_DIR:-${ROOT_DIR}/target/release-quality}/sbom"
cd "${ROOT_DIR}"
rm -rf "${SBOM_DIR}" && mkdir -p "${SBOM_DIR}"
./mvnw -B -ntp -DskipTests org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom -DoutputFormat=all -DoutputName=ddd4j-sbom -DoutputDirectory="${SBOM_DIR}"
test -s "${SBOM_DIR}/ddd4j-sbom.json" && test -s "${SBOM_DIR}/ddd4j-sbom.xml"
echo "[PASS] SBOM reports generated in ${SBOM_DIR}"
