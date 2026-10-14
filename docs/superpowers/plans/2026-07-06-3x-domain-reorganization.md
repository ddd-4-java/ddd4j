# 3.0.0 核心包重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 消除早期 MVC 脚手架遗留的 `domain` 大杂烩，让
DDD、CQRS、技术契约、技术事件在命名上直接体现架构意图。重构策略：硬切换 + 全工作区引用同步迁移，不保留新的旧包兼容壳。

**Architecture:** 当前 `io.ddd4j.core/` 下存在 4 包关系混乱：`contract/`（老契约层）、`domain/`（新轻量 DDD）、`ddd/`（fuinorg
包装层）、`cqrs/`（框架无关投影抽象）。需要重新组织为清晰的分层。

**Tech Stack:**

- Java 17、Maven 多模块

---

## 全局约定

- **重构策略**：硬切换，不保留旧包兼容壳
- **10 大关系混乱点**需全部解决

---

## 实施阶段总览

| Stage | 目标                          | 预期 Task 数 |
|-------|-------------------------------|--------------|
| 1     | 诊断当前包结构问题            | 1            |
| 2     | 重新组织 io.ddd4j.core 包结构 | 3            |
| 3     | 同步迁移全工作区引用          | 1            |

---

## Stage 1 — 问题诊断

### Task 1.1：诊断当前包结构

- [x] **Step 1:** 确认 10 大关系混乱点：
    1. 聚合根基类出现在 3 个位置：`contract/Model` / `domain/AggregateRoot` / `ddd/aggregate/DddAggregateRoot`
    2. 领域事件出现在 4 个位置：`contract/DomainEvent` / `contract/MQEvent` / `ddd/event/DddDomainEvent` /
       `cqrs/projection/TypedEvent`
    3. 仓储接口出现在 3 个位置：`contract/BaseRepository` / `contract/Repository` / `domain/DomainRepository`
    4. 视图抽象出现在 2 个位置：`ddd/query/DddView` / `cqrs/projection/ProjectionView`
    5. `contract` 包名不准（含 Model/Query 通用契约 + R/IR/ServiceException 响应）
    6. `domain` 与 `ddd` 混淆（仅靠 Ddd 前缀区分）
    7. `cqrs` 仅有一层 `projection`（职责分裂）
    8. `cqrs` 命名有歧义（应包含 Command/Query/Projection 三部分）
    9. `cqrs.projection` 职责分裂（View / TypedEvent / Dispatcher 混在一起）
    10. `contract` 兼做"响应模型"（R/IR/ResultCode 是 HTTP 响应）

---

## Stage 2 — 重新组织包结构

### Task 2.1：DDD 包重组

- [x] **Step 1:** 统一聚合根基类到 `io.ddd4j.core.ddd.model.AggregateRoot`
- [x] **Step 2:** 统一领域事件到 `io.ddd4j.core.ddd.event.DomainEvent`
- [x] **Step 3:** 统一仓储接口到 `io.ddd4j.core.ddd.repository.Repository`

### Task 2.2：CQRS 包重组

- [x] **Step 1:** 命令相关：`io.ddd4j.core.cqrs.command.*`
- [x] **Step 2:** 查询相关：`io.ddd4j.core.cqrs.query.*`
- [x] **Step 3:** 读模型相关：`io.ddd4j.core.cqrs.readmodel.*`

### Task 2.3：响应模型分离

- [x] **Step 1:** `R`/`IR`/`ResultCode` 等 HTTP 响应模型从 `contract` 包移出
- [x] **Step 2:** `ServiceException` 等异常类独立到 `io.ddd4j.core.exception.*`

---

## Stage 3 — 全工作区引用同步

### Task 3.1：同步迁移引用

- [x] **Step 1:** 更新所有模块的 import 引用
- [x] **Step 2:** 更新测试代码
- [x] **Step 3:** 更新文档引用

<!-- 日期依据：git log 首次出现 2026-07-06 "refactor(mq): 重构消息队列事件发布相关类"，文档内容涉及 3.0.0 核心包重构 -->


## 2026-10-10：同步 1.x / 2.x API 与工具位置调整

来源：2.x `4762b230b` 的 API/工具迁移，1.x `fafcbbde0` 中 Page 空迭代修复。
规格沿用本文“响应模型分离、硬切换、引用同步”约定；3.x 的 JDK、Jackson 和父 POM 保持本线配置。

| 原位置/类型 | 目标位置/类型 |
|---|---|
| core.ApiCode、ApiCodeValue、api.ResultCode | core.api.ApiCode |
| core.CustomApiCode | core.api.CustomApiCode |
| core.ApiRestResponse | core.api.R |
| core.HttpStatus | core.constant.HttpStatus |
| core.util.LambdaKit / MappingKit / SFunction | kit.lang 同名类型 |

保留当前工作区对 R 的进一步优化：自定义码构建集中到 of，精简重复 success/error 重载，
success(String) 保留传入消息；调用方适配最新方法。

验收行为：统一错误码目录及自定义码构建 R；保留共享码值的首条查找规则、未知/null 查询行为；
异常响应使用 `code/msg/data/error`，校验异常与 2.x 一致使用 400；Page 的空/null records
可安全使用增强 for、spliterator 和 stream。更新调用方与测试，移除旧类型；不复制其他版本线的依赖配置。

- [x] 同步 API、工具位置与当前仓库调用方。
- [x] 验证错误码查找、统一响应、自定义码、空分页迭代及受影响回归。
- [x] 记录 Maven 检查的实际结果及未通过范围；CI 以本次提交触发后状态为准。

当前验证：API/Page/Jackson 3 序列化及自定义成功消息 43 项通过；真实 JDK 21 下核心 Maven 回归 368 项通过。
`clean` 后旧根包 API 与 core.util 工具 class 均不存在。为通过实际编译，补回 JsonKit 的
Jackson 3 imports 与 ProjectionStatusTest 的 assertThat import。受影响链路的 MQ、Web Core、
Spring runtime 测试补回缺失断言 imports，DefaultWebMvcConfigurer 补回 Jackson 3 imports；
Ddd4jWebMvcContractTest 恢复错位的 record 声明。`./mvnw -pl ddd4j-web/ddd4j-web-webmvc -am test` 最终 BUILD SUCCESS，824 项测试全部通过（无跳过），
含核心、MQ、Web Core、Spring runtime 与 WebMVC 契约/异常响应测试。
全仓 Maven 回归运行到独立 EventStore Docker 镜像拉取后停止，未取得全量通过证据。
Python 45 项中 44 项通过，Maven4 模型扫描因历史验证目录中的 Quarkus 旧 POM 失败；未改写该验收规则。
