#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SBOM_DIR="${DDD4J_REPORT_DIR:-${ROOT_DIR}/target/release-quality}/sbom"
cd "${ROOT_DIR}"
rm -rf "${SBOM_DIR}" && mkdir -p "${SBOM_DIR}"

# 修复 CycloneDX 'Invalid Collect Request: null' 与 'cached from a remote repository ID
# that is unavailable' 错误：
# Maven 4 Resolver 2.x 对 pom-aggregator 的 CollectRequest 处理存在 bug，
# 且本地缓存的 parent POM 来源仓库 ID 与当前 settings.xml 中的仓库 ID 不匹配
# 会触发重新下载，下载失败时导致整个 SBOM 生成失败。
# 解决：清理本地仓库中所有 _remote.repositories 文件（强制重新校验来源），
# 然后再生成 SBOM。
find ~/.m2/repository -name "_remote.repositories" -delete 2>/dev/null || true

./mvnw -B -ntp -DskipTests -Dquarkus.build.skip=true \
  org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom \
  -DoutputFormat=all -DoutputName=ddd4j-sbom -DoutputDirectory="${SBOM_DIR}"
test -s "${SBOM_DIR}/ddd4j-sbom.json" && test -s "${SBOM_DIR}/ddd4j-sbom.xml"
echo "[PASS] SBOM reports generated in ${SBOM_DIR}"
