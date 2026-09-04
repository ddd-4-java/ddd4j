# 3.0.0 核心包重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 消除早期 MVC 脚手架遗留的 `domain` 大杂烩，让 DDD、CQRS、技术契约、技术事件在命名上直接体现架构意图。重构策略：硬切换 + 全工作区引用同步迁移，不保留新的旧包兼容壳。

**Architecture:** 当前 `io.ddd4j.core/` 下存在 4 包关系混乱：`contract/`（老契约层）、`domain/`（新轻量 DDD）、`ddd/`（fuinorg 包装层）、`cqrs/`（框架无关投影抽象）。需要重新组织为清晰的分层。

**Tech Stack:**
- Java 17、Maven 多模块

---

## 全局约定

- **重构策略**：硬切换，不保留旧包兼容壳
- **10 大关系混乱点**需全部解决

---

## 实施阶段总览

| Stage | 目标 | 预期 Task 数 |
|-------|------|-------------|
| 1 | 诊断当前包结构问题 | 1 |
| 2 | 重新组织 io.ddd4j.core 包结构 | 3 |
| 3 | 同步迁移全工作区引用 | 1 |

---

## Stage 1 — 问题诊断

### Task 1.1：诊断当前包结构

- [x] **Step 1:** 确认 10 大关系混乱点：
  1. 聚合根基类出现在 3 个位置：`contract/Model` / `domain/AggregateRoot` / `ddd/aggregate/DddAggregateRoot`
  2. 领域事件出现在 4 个位置：`contract/DomainEvent` / `contract/MQEvent` / `ddd/event/DddDomainEvent` / `cqrs/projection/TypedEvent`
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
