# 3.0.0 迁移计划：Publisher 静态注册 → 上下文查找

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 消除 `DomainEvent.registerPublisher()` 与 `MQEvent.registerPublisher()` 的静态注册机制，改为由框架适配层在启动期自动注入，业务代码完全无感。

**Architecture:** 旧实现使用进程级单点 `private static volatile DomainEventPublisher eventPublisher`，业务代码被迫在 main 方法中手动注册。新实现改为上下文查找模式，由 `ddd4j-runtime-*` 在启动期自动注入。

**Tech Stack:**
- Java 17、Maven 多模块
- ddd4j-core + ddd4j-runtime-*

---

## 全局约定

- **破坏性变更**：3.0.0 起，`DomainEvent.registerPublisher()` 与 `MQEvent.registerPublisher()` 已删除
- **迁移方向**：静态注册 → 上下文查找（Context Lookup）

---

## 实施阶段总览

| Stage | 目标 | 预期 Task 数 |
|-------|------|-------------|
| 1 | 删除 DomainEvent/MQEvent 的静态注册方法 | 2 |
| 2 | 实现上下文查找机制 | 2 |
| 3 | 更新所有 runtime 模块适配 | 1 |

---

## Stage 1 — 删除静态注册

### Task 1.1：删除 DomainEvent.registerPublisher()

- [x] **Step 1:** 删除 `DomainEvent` 中的 `registerPublisher()` 静态方法和 `eventPublisher` 静态字段
- [x] **Step 2:** 更新所有调用点

### Task 1.2：删除 MQEvent.registerPublisher()

- [x] **Step 1:** 删除 `MQEvent` 中的 `registerPublisher()` 静态方法
- [x] **Step 2:** 更新所有调用点

---

## Stage 2 — 实现上下文查找

### Task 2.1：DomainEvent 上下文查找

- [x] **Step 1:** 实现通过 `Contexts` 查找 `DomainEventPublisher` 的机制（`Contexts.register(SpiKeys.DOMAIN_EVENT_PUBLISHER, ...)`）
- [x] **Step 2:** 确保线程安全和生命周期正确

### Task 2.2：MQEvent 上下文查找

- [x] **Step 1:** 实现通过 `Contexts` 查找 `MQEventPublisher` 的机制（`Contexts.getOrThrow(SpiKeys.MQ_EVENT_PUBLISHER, ...)`）

---

## Stage 3 — Runtime 适配

### Task 3.1：更新所有 runtime 模块

- [x] **Step 1:** 更新 `ddd4j-runtime-spring`、`ddd4j-runtime-quarkus`、`ddd4j-runtime-guice` 等，使其在启动期自动注入 publisher

---

## 旧实现的问题

| # | 问题 | 后果 |
|---|------|------|
| 1 | 生命周期错配 | 业务 `new MQEvent()` 时 publisher 可能未注入 |
| 2 | 进程级单点 | 静态字段，无法支持多租户或测试隔离 |
| 3 | 业务代码侵入 | 业务 main 方法必须知道注册钩子 |
| 4 | 顺序依赖 | 注册必须在 SpringApplication.run() 之前 |
| 5 | 不可测试 | 静态状态难以 mock |

<!-- 日期依据：git log 首次出现 2026-07-02 "docs(migration): 添加 3.0.0 迁移指南和核心包重构报告" -->
