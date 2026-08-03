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
```

报告写入 `target/release-quality/`，不会污染源码。发布候选须额外执行：

```bash
DDD4J_REQUIRE_CLEAN_WORKTREE=true ./scripts/verify-release-worktree.sh
```

## CVE 状态

`scan-cve.sh` 只有在显式设置 `DDD4J_ENABLE_CVE_SCAN=true` 且提供 `NVD_API_KEY` 时运行。当前它产出并归档报告，但尚未设定 CVSS 阈值或豁免流程，不能宣称已具备高危/Critical 阻断能力。

GitHub Actions 分为 Java 17 单测、SBOM/许可证报告、Docker Testcontainers 三类任务。CVE 扫描依赖仓库 Secret `NVD_API_KEY`；正式发布前仍需冻结 API 二进制兼容基线并评审 CVSS 阈值。
