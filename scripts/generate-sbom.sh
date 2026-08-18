#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SBOM_DIR="${DDD4J_REPORT_DIR:-${ROOT_DIR}/target/release-quality}/sbom"
cd "${ROOT_DIR}"
rm -rf "${SBOM_DIR}" && mkdir -p "${SBOM_DIR}"
# 修复 CycloneDX 'Invalid Collect Request: null' 错误：
# cyclonedx-maven-plugin 2.9.1 与 Maven 4 Resolver 2.x 在 pom-aggregator 上有
# CollectRequest null-root bug。降级到 2.7.0（Maven 4 兼容的最后一个版本）。
./mvnw -B -ntp -DskipTests -Dquarkus.build.skip=true \
  org.cyclonedx:cyclonedx-maven-plugin:2.7.0:makeAggregateBom \
  -DoutputFormat=all -DoutputName=ddd4j-sbom -DoutputDirectory="${SBOM_DIR}"
test -s "${SBOM_DIR}/ddd4j-sbom.json" && test -s "${SBOM_DIR}/ddd4j-sbom.xml"
echo "[PASS] SBOM reports generated in ${SBOM_DIR}"
