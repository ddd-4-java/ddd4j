# ddd4j 2.0.x 重构迁移实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 ddd4j 从"Spring 强耦合的单一仓库"重构为"纯 Java 公共底座 + 多运行时绑定层"，核心契约不绑定容器，由 Spring、Quarkus、Guice、Micronaut、Vert.x、Helidon、Dropwizard 运行时模块负责接线。

**Architecture:** 平铺式纯 Java 公共底座 + 多运行时绑定。`ddd4j-core` 持有 DDD/CQRS/Auth/Cache/MQ 等核心契约，`ddd4j-runtime-*` 负责容器绑定，`ddd4j-web`、`ddd4j-data`、`ddd4j-mq`、`ddd4j-auth`、`ddd4j-cache` 按能力聚合。Boot 自动装配留在外部 `ddd4j-boot` 仓库。

**Tech Stack:**
- Java 17、Maven 多模块、JUnit 5
- fuinorg ddd-4-java + cqrs-4-java + esc-api
- Spring Framework 6.x / Quarkus CDI / Guice / Micronaut / Vert.x / Helidon / Dropwizard

**Related Design Doc:** `docs/superpowers/specs/2026-06-29-ddd4j-architecture-overview-design.md`

---

## 全局约定

- **Java 版本**：Java 17（全模块基线）
- **groupId**：`io.ddd4j`。版本走 `${revision}`（flatten-maven-plugin）。
- **提交约定**：英文 conventional commits（feat/fix/docs/test/refactor）
- **测试惯例**：JUnit 5 + Mockito，断言用 `Assertions.*`
- **包命名**：`io.ddd4j.{module}.*`（如 `io.ddd4j.core.*`、`io.ddd4j.kit.lang.*`）

---

## 实施阶段总览

| Stage | 目标 | 预期 Task 数 |
|-------|------|-------------|
| 1 | 模块合并/删除（core-api 合并到 core、清理空壳模块） | 3 |
| 2 | ddd4j-mq-core 迁出 Spring 集成代码到 ddd4j-mq-spring | 1 |
| 3 | Auth 注解双地址清理 | 1 |
| 4 | ddd4j-ddd-rules ArchUnit 增强（Clean + COLA） | 2 |
| 5 | 14 个工具类收编到 ddd4j-kit | 1 |
| 6 | 包路径统一（io.ddd4j.core.api.* → io.ddd4j.core.*） | 1 |
| 7 | 七 Runtime + 八 Web 契约与共享 Order 生产接线 | 2 |
| 8 | PostgreSQL / Redis / Kafka Testcontainers Docker 验证 | 1 |

---

## Stage 1 — 模块合并/删除

### Task 1.1：合并 ddd4j-core-api 到 ddd4j-core

- [x] **Step 1:** 将 `ddd4j-core-api` 的 28 个 Java 文件合并到 `ddd4j-core`，统一 package 为 `io.ddd4j.core.*`
- [x] **Step 2:** 删除 `ddd4j-core-api` 模块
- [x] **Step 3:** 更新根 `pom.xml` 的 modules 列表，移除 `ddd4j-core-api`

### Task 1.2：清理旧空壳模块

- [x] **Step 1:** 删除 `ddd4j-{data,mq,auth}-{quarkus,javalin}` 等 0 java 文件的空壳模块
- [x] **Step 2:** 保留 `ddd4j-web-*` 的 Quarkus/Javalin 实现（按新结构）

### Task 1.3：清理 Auth 注解双地址

- [x] **Step 1:** 删除 `ddd4j-annotation/auth/{BaseAuth,Inside}.java`（旧位置重复）
- [x] **Step 2:** 统一到 `io.ddd4j.auth.annotation.*`

---

## Stage 2 — ddd4j-mq-core 迁出 Spring 集成

### Task 2.1：迁出 Spring 集成代码

- [x] **Step 1:** `mq-core/pom.xml` 移除 `spring-context` / `spring-messaging`（compile scope）
- [x] **Step 2:** 4 个文件从 `io.ddd4j.mq.{config,registry}` 迁到 `io.ddd4j.mq.spring.{config,registry}`
- [x] **Step 3:** 监听器调用链统一到纯 Java `MQMessage` / `MQConsumerContext` / `MessageAcknowledgment`
- [x] **Step 4:** 创建 `ddd4j-mq-spring` 模块（Spring 桥接：@Configuration/BeanPostProcessor）

---

## Stage 3 — ddd4j-ddd-rules ArchUnit 增强

### Task 3.1：迁移并增强规则

- [x] **Step 1:** `DDDLayerRules` 从 `ddd4j-core/test` 迁到 `ddd4j-ddd-rules-clean/main`
- [x] **Step 2:** 改名 `CleanDDDLayerRules` + 新增 `ColaDDDLayerRules`
- [x] **Step 3:** `CleanArchitectureTest` / `ColaArchitectureTest` 抽象基类（`@AnalyzeClasses` + `@ArchTest`）

### Task 3.2：Checker 实现

- [x] **Step 1:** `CleanArchitectureChecker` / `ColaArchitectureChecker` 用 ArchUnit 强化
- [x] **Step 2:** 业务项目使用：写空 `extends CleanArchitectureTest {}` 即可自动应用所有规则

---

## Stage 4 — 14 个工具类收编到 ddd4j-kit

### Task 4.1：工具类迁移

- [x] **Step 1:** 迁移 P0 工具类：`JsonKit`（24 处使用）到 `io.ddd4j.kit.lang.JsonKit`
- [x] **Step 2:** 迁移 P1 工具类：`JacksonKit`（合并 toType）、`HttpStatus`（纯常量）
- [x] **Step 3:** 迁移 P2 工具类：`Arith`→`ArithKit`、`DateUtils`→`DateKit`、`Functions`→`FunctionKit`、`RankUtil`→`RankKit`、`AppUtils`→`AppKit`、`GraphUtil`→`GraphKit`、`LotteryUtils`→`LotteryKit`
- [x] **Step 4:** 删除 P3 工具类：`CookieUtils`（0 外部使用）、`RandomString`（0 外部使用）
- [x] **Step 5:** 保留在 ddd4j-core 的 5 个工具类：`HttpStatus`、`ExceptionKit`、`MappingKit`、`TransmittableThreadLocal`、`FastdfsUtils`

---

## Stage 5 — 包路径统一

### Task 5.1：统一包路径

- [x] **Step 1:** `io.ddd4j.core.api.*` → `io.ddd4j.core.*`（core-api 合并到 core，`.api.` 二级包删除）
- [x] **Step 2:** `io.ddd4j.core.util.HttpStatus` → `io.ddd4j.core.HttpStatus`
- [x] **Step 3:** `io.ddd4j.core.util.JsonKit` → `io.ddd4j.kit.lang.JsonKit`
- [x] **Step 4:** `io.ddd4j.mq.config.Ddd4jMQPropertiesConfiguration` → `io.ddd4j.mq.spring.config.*`
- [x] **Step 5:** `io.ddd4j.annotation.auth.*` → `io.ddd4j.auth.annotation.*`

---

## Stage 6 — 多运行时与 Web 契约

### Task 6.1：七类运行时接入

- [x] **Step 1:** `ddd4j-runtime-spring`：SpringDomainEventPublisher 通过 ApplicationEventPublisher.publishEvent
- [x] **Step 2:** `ddd4j-runtime-quarkus`：CdiDomainEventPublisher 通过 CDI Event.fire
- [x] **Step 3:** `ddd4j-runtime-guice`：GuiceDomainEventPublisher 通过 Guava EventBus.post + DddAnnotationModule
- [x] **Step 4:** `ddd4j-runtime-micronaut`：MicronautDomainEventPublisher
- [x] **Step 5:** `ddd4j-runtime-vertx`：VertxDomainEventPublisher
- [x] **Step 6:** `ddd4j-runtime-helidon`：Ddd4jHelidonExtension
- [x] **Step 7:** `ddd4j-runtime-dropwizard`：DropwizardDomainEventPublisher + Ddd4jBundle

### Task 6.2：八类 Web 适配器契约

- [x] **Step 1:** `ddd4j-web-webmvc` / `ddd4j-web-webflux` / `ddd4j-web-javalin` / `ddd4j-web-quarkus`
- [x] **Step 2:** `ddd4j-web-micronaut` / `ddd4j-web-vertx` / `ddd4j-web-helidon` / `ddd4j-web-dropwizard`
- [x] **Step 3:** `ddd4j-web-testkit` 共享响应、鉴权、租户/Trace、幂等和上下文清理契约

---

## Stage 7 — Docker 验证与 CI

### Task 7.1：Testcontainers 验证

- [x] **Step 1:** 本机 Docker Desktop 通过 PostgreSQL、Redis、Kafka Testcontainers 订单 Outbox 闭环
- [x] **Step 2:** GitHub Actions 配置 BOM、Java 规范、全量单元/契约测试，Docker Job 执行 Testcontainers

---

## 发版门槛

- `io.ddd4j:ddd4j-parent` → `2.0.x`
- 破坏性变更清单、运行时/Web 契约和 Java 17 单元测试均已收口
- 发出 `2.0.x-RELEASE` 前，必须在 Docker 可用 CI 环境确认 PostgreSQL、Redis、Kafka 的订单 Outbox Testcontainers Job 通过

<!-- 日期依据：文档内声明"初始重构时间：2026-06-27" -->
