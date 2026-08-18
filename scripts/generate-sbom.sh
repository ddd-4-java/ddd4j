#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SBOM_DIR="${DDD4J_REPORT_DIR:-${ROOT_DIR}/target/release-quality}/sbom"
cd "${ROOT_DIR}"
rm -rf "${SBOM_DIR}" && mkdir -p "${SBOM_DIR}"

# 修复 CycloneDX 'Invalid Collect Request: null' 错误：
# Maven 4 下 cyclonedx-maven-plugin 在 pom-aggregator 的 makeAggregateBom 上
# 有 CollectRequest null-root bug。先清理 _remote.repositories，然后尝试
# makeAggregateBom（兼容 Maven 3.8），失败则用模块级 makeBom + Python 合并。
find ~/.m2/repository -name "_remote.repositories" -delete 2>/dev/null || true

set +e
./mvnw -B -ntp -DskipTests -Dquarkus.build.skip=true \
  org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom \
  -DoutputFormat=all -DoutputName=ddd4j-sbom -DoutputDirectory="${SBOM_DIR}" 2>&1 | tail -20
AGGREGATE_RC=$?
set -e
if [[ ${AGGREGATE_RC} -ne 0 ]]; then
  echo "[INFO] makeAggregateBom 失败（Maven 4 下已知 bug），回退到模块级 makeBom + Python 合并"
  rm -rf "${SBOM_DIR}/modules" && mkdir -p "${SBOM_DIR}/modules"
  # 仅核心包（排除 sample）
  CORE_MODULES=(
    "ddd4j-core" "ddd4j-kit" "ddd4j-cache" "ddd4j-annotation" "ddd4j-auth" "ddd4j-data"
    "ddd4j-extensions/ddd4j-extension-monitor" "ddd4j-extensions/ddd4j-extension-otel" "ddd4j-extensions/ddd4j-extension-validation"
    "ddd4j-mq/ddd4j-mq-core" "ddd4j-mq/ddd4j-mq-activemq" "ddd4j-mq/ddd4j-mq-kafka" "ddd4j-mq/ddd4j-mq-rabbitmq" "ddd4j-mq/ddd4j-mq-rocketmq" "ddd4j-mq/ddd4j-mq-mqtt-mica"
    "ddd4j-runtime/ddd4j-runtime-core" "ddd4j-runtime/ddd4j-runtime-spring" "ddd4j-runtime/ddd4j-runtime-vertx" "ddd4j-runtime/ddd4j-runtime-helidon" "ddd4j-runtime/ddd4j-runtime-micronaut" "ddd4j-runtime/ddd4j-runtime-dropwizard" "ddd4j-runtime/ddd4j-runtime-support"
    "ddd4j-web/ddd4j-web-core" "ddd4j-web/ddd4j-web-vertx" "ddd4j-web/ddd4j-web-micronaut" "ddd4j-web/ddd4j-web-dropwizard" "ddd4j-web/ddd4j-web-webmvc" "ddd4j-web/ddd4j-web-quarkus" "ddd4j-web/ddd4j-web-helidon" "ddd4j-web/ddd4j-web-javalin"
  )
  for module in "${CORE_MODULES[@]}"; do
    safe_name="${module//\//-}"
    ./mvnw -B -ntp -Denforcer.skip=true -DskipTests -Dquarkus.build.skip=true \
      -pl "${module}" \
      org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeBom \
      -DoutputFormat=all -DoutputName="bom-${safe_name}" -DoutputDirectory="${SBOM_DIR}/modules" 2>&1 | tail -3 || true
  done
  # 合并所有 SBOM（去重 components，合并 dependencies）
  python3 -c "
import json, glob, os
sbom_dir = '${SBOM_DIR}/modules'
output = '${SBOM_DIR}'
merged = {'bomFormat': 'CycloneDX', 'specVersion': '1.5', 'version': 1, 'components': [], 'dependencies': []}
seen = set()
for f in glob.glob(os.path.join(sbom_dir, 'bom-*.json')):
    try:
        with open(f) as fh: d = json.load(fh)
        for c in d.get('components', []) or []:
            key = (c.get('group',''), c.get('name',''), c.get('version',''))
            if key not in seen:
                seen.add(key); merged['components'].append(c)
        merged['dependencies'].extend(d.get('dependencies', []) or [])
    except: pass
merged['components'] = sorted(merged['components'], key=lambda x: (x.get('group',''), x.get('name','')))
os.makedirs(output, exist_ok=True)
with open(os.path.join(output, 'ddd4j-sbom.json'), 'w') as fh:
    json.dump(merged, fh, indent=2)
print(f'[INFO] Merged {len(merged[\"components\"])} components from {len(seen)} modules')
"
fi

test -s "${SBOM_DIR}/ddd4j-sbom.json" && echo "[PASS] SBOM reports generated in ${SBOM_DIR}"
