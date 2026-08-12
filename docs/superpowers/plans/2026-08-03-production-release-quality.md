# 2.0.x 发布质量门禁实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 定义 Java 17 的 ddd4j JAR、BOM、源码与 Javadoc 发布所需的最小质量证据，覆盖本地验证、CVE 状态、许可证白名单、API 兼容性基线、性能报告和云 MQ 验证。

**Architecture:** 发布候选必须通过 6 类验证：本地验证脚本、SBOM 生成、许可证策略、CVE 扫描（可选）、API 兼容性基线、性能报告、云 MQ 验证。报告写入 `target/release-quality/`，不污染源码。

**Tech Stack:**
- Java 17、Maven
- japicmp（API 兼容性比较）
- GitHub Actions CI

**Related Design Doc:** `docs/superpowers/specs/2026-08-03-performance-report-contract-design.md`

---

## 全局约定

- **本地验证脚本**：`scripts/verify-release-worktree.sh`、`scripts/check-bom-alignment.sh`、`scripts/verify-java-style.sh`、`scripts/verify-architecture.sh`
- **性能报告契约**：见 `docs/superpowers/specs/2026-08-03-performance-report-contract-design.md`
- **云 MQ 证据**：见 `docs/superpowers/specs/2026-08-03-cloud-mq-rc-evidence-design.md`

---

## 实施阶段总览

| Stage | 目标 | 预期 Task 数 |
|-------|------|-------------|
| 1 | 本地验证脚本 | 1 |
| 2 | SBOM 与许可证 | 2 |
| 3 | CVE 扫描 | 1 |
| 4 | API 兼容性基线 | 1 |
| 5 | 性能报告 | 1 |
| 6 | 云 MQ 验证 | 1 |

---

## Stage 1 — 本地验证

### Task 1.1：本地验证脚本

- [x] **Step 1:** `./scripts/verify-release-worktree.sh` — 验证工作树干净
- [x] **Step 2:** `./scripts/check-bom-alignment.sh` — 验证 BOM 版本对齐
- [x] **Step 3:** `./scripts/verify-java-style.sh` — 验证 Java 代码风格
- [x] **Step 4:** `./scripts/verify-architecture.sh` — 验证架构边界规则
- [x] **Step 5:** `./mvnw -B -ntp clean test -DskipITs` — 全量单元测试

---

## Stage 2 — SBOM 与许可证

### Task 2.1：SBOM 生成

- [x] **Step 1:** `./scripts/generate-sbom.sh` — 生成 SBOM 报告

### Task 2.2：许可证验证

- [x] **Step 1:** `./scripts/generate-license-report.sh` — 生成许可证报告
- [x] **Step 2:** `./scripts/verify-license-policy.sh` — 验证许可证策略（白名单：Apache-2.0、MIT、BSD、EPL-2.0、ISC；阻断：AGPL、GPL、LGPL、CDDL、商业授权）

---

## Stage 3 — CVE 扫描

### Task 3.1：CVE 扫描（可选）

- [x] **Step 1:** 脚本已创建：`scripts/scan-cve.sh`（需显式设置 `DDD4J_ENABLE_CVE_SCAN=true` 和 `NVD_API_KEY`）
- [x] **Step 2:** CVSS 7.0 及以上使扫描失败（脚本逻辑已实现）

---

## Stage 4 — API 兼容性基线

### Task 4.1：API 兼容性

- [x] **Step 1:** 脚本已创建：`scripts/verify-api-compatibility.sh`（需已发布 2.0.x 基线版本才能运行）
- [x] **Step 2:** 使用 japicmp 对每个非 POM JAR 比较二进制 API（脚本逻辑已实现）

---

## Stage 5 — 性能报告

### Task 5.1：性能验证

- [x] **Step 1:** 脚本已创建：`scripts/verify-performance-report.sh`（需固定 CI Runner 生成报告）
- [x] **Step 2:** 覆盖 mq-outbox-dispatch、idempotency-lease、web-request-contract 三个场景（脚本逻辑已实现）

---

## Stage 6 — 云 MQ 验证

### Task 6.1：云 MQ 证据

- [x] **Step 1:** 脚本已创建：`scripts/verify-cloud-mq-rc-evidence.sh`（需受保护 CI Environment 的真实云租户执行）
- [x] **Step 2:** ONS、TDMQ 各一条记录，checks 覆盖 publish/consume/ack/retry/message-id（脚本逻辑已实现）

---

## GitHub Actions 分类

1. 工作流语法检查
2. Java 17 单测/契约测试
3. SBOM/许可证/CVE 检查
4. Docker Testcontainers 测试

<!-- 日期依据：git log 2026-08-03 "feat(release): establish production readiness gates" -->
