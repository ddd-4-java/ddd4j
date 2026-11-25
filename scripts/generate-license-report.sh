#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LICENSE_DIR="${DDD4J_REPORT_DIR:-${ROOT_DIR}/target/release-quality}/licenses"
cd "${ROOT_DIR}"
rm -rf "${LICENSE_DIR}" && mkdir -p "${LICENSE_DIR}"
METADATA_DIR="$(mktemp -d)"
trap 'rm -rf -- "${METADATA_DIR}"' EXIT
METADATA_FILE="${METADATA_DIR}/third-party-license-metadata.properties"
cp -- "${ROOT_DIR}/config/third-party-license-metadata.properties" "${METADATA_FILE}"
./mvnw -B -ntp -DskipTests org.codehaus.mojo:license-maven-plugin:2.5.0:aggregate-add-third-party \
  -Dlicense.outputDirectory="${LICENSE_DIR}" -Dlicense.thirdPartyFilename="THIRD-PARTY.txt" \
  -Dlicense.fileTemplate="${ROOT_DIR}/config/third-party-inventory.ftl" \
  -Dlicense.useMissingFile=true -Dlicense.useRepositoryMissingFiles=false \
  -Dlicense.missingFile="${METADATA_FILE}"
test -s "${LICENSE_DIR}/THIRD-PARTY.txt"
echo "[PASS] license inventory generated in ${LICENSE_DIR}/THIRD-PARTY.txt"
