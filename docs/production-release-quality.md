# 2.0.x 发布质量门禁

本页定义 Java 17 的 ddd4j JAR、BOM、源码与 Javadoc 发布所需的最小质量证据，不覆盖镜像、部署平台或外部 `ddd4j-boot`。

## 本地验证

```bash
./scripts/verify-release-worktree.sh
./scripts/check-bom-alignment.sh
./scripts/verify-java-style.sh
./scripts/verify-architecture.sh
./mvnw -B -ntp clean test -DskipITs
./scripts/generate-sbom.sh
./scripts/generate-license-report.sh
./scripts/verify-license-policy.sh
```

报告写入 `target/release-quality/`，不会污染源码。发布候选须额外执行：

```bash
DDD4J_REQUIRE_CLEAN_WORKTREE=true ./scripts/verify-release-worktree.sh
```

已发布 2.0.x 基线可用后，发布候选还必须执行：

```bash
DDD4J_API_BASELINE_VERSION=<published-2.0.x-version> ./scripts/verify-api-compatibility.sh
```

该脚本使用 `japicmp` 对每个非 POM JAR 比较二进制 API；没有已发布基线时必须失败，不能以当前快照或源代码比较替代。性能报告必须符合 [性能报告契约](performance-report-contract.md)，并由固定 CI Runner 归档。

候选提交还必须携带固定 Runner 的性能报告与受保护云环境的 ONS/TDMQ 认证报告：

```bash
DDD4J_PERFORMANCE_REPORT=/secure-artifacts/performance.json ./scripts/verify-performance-report.sh
DDD4J_CLOUD_MQ_RC_REPORT=/secure-artifacts/cloud-mq-rc.json ./scripts/verify-cloud-mq-rc-evidence.sh
```

两份报告的提交 SHA 必须等于候选提交；性能阈值见[性能报告契约](performance-report-contract.md)，云端 MQ 的字段与脱敏要求见[云 MQ 发布候选证据](cloud-mq-rc-evidence.md)。本地 Testcontainers 或协议模拟报告不能替代这两项证据。

## CVE 状态

`scan-cve.sh` 只有在显式设置 `DDD4J_ENABLE_CVE_SCAN=true` 且提供 `NVD_API_KEY` 时运行。CVSS 7.0 及以上会使扫描失败。`NVD_API_KEY` 只能由 CI 的受保护 Secret 注入，禁止写入 POM、脚本或源码。

`verify-license-policy.sh` 以 Apache-2.0、MIT、BSD、EPL-2.0、ISC 为目标发布白名单，并阻断 AGPL、GPL、LGPL、CDDL、商业授权和未知授权。双重许可依赖必须在许可证选择清单中明确选定允许的分支；报告中的其他命中均为发布阻断，不得通过跳过该步骤或添加无期限豁免掩盖。

GitHub Actions 分为工作流语法、Java 17 单测/契约、SBOM/许可证/CVE、Docker Testcontainers 四类任务。正式发布前还必须冻结 API 二进制兼容基线、归档性能基线和完成受保护云环境验证。
