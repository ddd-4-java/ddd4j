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
# 解决：先在当前 settings.xml 下执行一次 dependency:resolve 把所有 parent POM
# 重新解析到本地仓库（强制覆盖旧的 repository ID 记录），然后再生成 SBOM。
./mvnw -B -ntp -Denforcer.skip=true -DskipTests -Dquarkus.build.skip=true \
  -pl '!ddd4j-samples/ddd4j-sample-order-domain,!ddd4j-samples/ddd4j-sample-order-application,!ddd4j-samples/ddd4j-sample-order-local,!ddd4j-samples/ddd4j-sample-order-testkit,!ddd4j-samples/ddd4j-sample-order-jdbc,!ddd4j-samples/ddd4j-sample-order-kafka,!ddd4j-samples/ddd4j-sample-order-redis,!ddd4j-samples/ddd4j-sample-quarkus,!ddd4j-samples/ddd4j-sample-javalin,!ddd4j-samples/ddd4j-sample-micronaut,!ddd4j-samples/ddd4j-sample-vertx,!ddd4j-samples/ddd4j-sample-helidon,!ddd4j-samples/ddd4j-sample-dropwizard,!ddd4j-samples/ddd4j-sample-quarkus-cqrs,!ddd4j-samples/ddd4j-sample-javalin-cqrs,!ddd4j-samples/ddd4j-sample-quarkus-satoken,!ddd4j-samples/ddd4j-sample-quarkus-shiro,!ddd4j-samples/ddd4j-sample-javalin-satoken,!ddd4j-samples/ddd4j-sample-javalin-shiro' \
  dependency:resolve dependency:resolve-plugins 2>&1 | tail -20 || true

./mvnw -B -ntp -DskipTests -Dquarkus.build.skip=true \
  org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom \
  -DoutputFormat=all -DoutputName=ddd4j-sbom -DoutputDirectory="${SBOM_DIR}"
test -s "${SBOM_DIR}/ddd4j-sbom.json" && test -s "${SBOM_DIR}/ddd4j-sbom.xml"
echo "[PASS] SBOM reports generated in ${SBOM_DIR}"
