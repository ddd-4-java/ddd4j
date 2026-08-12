# ddd4j-data 模块优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 ddd4j-data 通用层中错误混入的 Spring Boot auto-config 代码迁移到 `ddd4j-boot-data`，实现三层范式（纯 Java SPI → Spring 桥接 → Spring Boot auto-config），对齐 ddd4j-mq 的标准分层。

**Architecture:** 以 ddd4j-mq 为标准范本：`ddd4j-data-mybatis`（纯 Java SPI）→ `ddd4j-data-spring`（Spring 桥接）→ `ddd4j-boot-data`（Spring Boot auto-config）。核心问题：data 模块当前第一层和第二层混在一起，且混入了本该在第三层的 Spring Boot auto-config。

**Tech Stack:**
- Java 17、Maven 多模块
- MyBatis-Plus、Spring Framework 6.x

**Related Design Doc:** `docs/superpowers/specs/2026-06-29-ddd4j-architecture-overview-design.md`

---

## 全局约定

- **三层范式**：纯 Java SPI（零框架依赖）→ Spring 桥接（@Configuration）→ Spring Boot auto-config（AutoConfiguration.imports）
- **对照范本**：`ddd4j-mq`（`-core` 纯 Java + `-spring` Spring 桥接）→ `ddd4j-boot-mq`（Spring Boot auto-config）

---

## 实施阶段总览

| Stage | 目标 | 预期 Task 数 |
|-------|------|-------------|
| 1 | 审计 ddd4j-data 各子模块的 Spring Boot auto-config 现状 | 1 |
| 2 | 将错误放置的 auto-config 迁移到 ddd4j-boot-data | 3 |
| 3 | ddd4j-data-spring 桥接层清理 | 1 |

---

## Stage 1 — 审计现状

### Task 1.1：逐项审计 Spring Boot auto-config 配置

- [x] **Step 1:** 审计 `ddd4j-data-mybatis`：metadata 文件指向 crypto 旧包名（错误）
- [x] **Step 2:** 审计 `ddd4j-data-crypto`：metadata 对但缺 imports，auto-config 无法被发现
- [x] **Step 3:** 审计 `ddd4j-data-external`：配置正确但位置错（应在 boot 层）
- [x] **Step 4:** 审计 `ddd4j-data-logs`：metadata + imports 都是旧包名（全错）

---

## Stage 2 — 迁移 auto-config 到 boot 层

### Task 2.1：迁移 external auto-config

- [ ] **Step 1:** 将 `ExternalAutoConfiguration` 从 `ddd4j-data-external` 迁移到 `ddd4j-boot-data` (未实现：ddd4j-boot 为外部仓库，当前 feature/3.0.x 分支未含此迁移)

### Task 2.2：修复 crypto auto-config

- [ ] **Step 1:** 修复 `ddd4j-data-crypto` 的 imports 文件，使其可被发现 (未实现：需同步 boot 层)

### Task 2.3：修复 logs auto-config

- [ ] **Step 1:** 修复 `ddd4j-data-logs` 的 metadata 和 imports 包名 (未实现：旧包名引用)

---

## Stage 3 — ddd4j-data-spring 桥接层

### Task 3.1：Spring 桥接清理

- [ ] **Step 1:** 创建 `ddd4j-data-spring` 模块，保留 `RepositoryBeanPostProcessor` 等 Spring 桥接代码 (未实现：当前 feature/3.0.x 分支不存在 ddd4j-data-spring 子模块)
- [ ] **Step 2:** 确保不含 `spring-boot-autoconfigure` 依赖 (未实现：依赖 Step 1)

<!-- 日期依据：文档内声明"最后更新：2026-07-01"，首次审计日期为 2026-06-29 -->
