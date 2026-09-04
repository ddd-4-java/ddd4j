# Task 1.8 Brief — Write 07-cqrs-projection.md（ddd4j-core 已对齐）

## Task

Write `ddd4j/docs/reference/fuin-api-patterns/07-cqrs-projection.md` — the seventh of 8 fuin API reference documents. Topic: **CQRS read side / projection**（投影位置、视图、调度）。This is the last of the three "ddd4j-core 已对齐" docs (with 1.4 and 1.7) — ddd4j-core already has a complete framework-agnostic projection abstraction (ProjectionService / ProjectionPosition / ProjectionPositionRepository / ProjectionRunner / ProjectionView / ViewManager / ViewScheduler / EventChunk / EventChunkReader), so the doc documents parity + what ddd4j already exceeds, plus the fuin Spring adapter as the reference for stage 7's runtime schedulers.

## Sources to read

**Primary (fuin cqrs-4-java 0.6.0, springboot 模块 — this IS the projection-side module):**
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/cqrs-4-java/springboot/src/main/java/org/fuin/cqrs4j/springboot/view/QryProjectionService.java` (reset/read/updateProjectionPosition)
- `.../view/QryProjectionPosition.java` (JPA entity)
- `.../view/SpringJpaViewManager.java` (~216 lines: configureTasks/onApplicationEvent/createViews/shutdownViews/updateView/readStreamEvents/asTypeNames/handleChunk — the view lifecycle + chunk loop)
- `.../view/` 目录其余文件（列目录后全读，如 JpaView、ViewManager 变体）
- cqrs-4-java/core 模块如有 view/projection 相关类也读（列目录确认）

**ddd4j-core (parity side — KEY):**
- `ddd4j-core/src/main/java/io/ddd4j/core/cqrs/readmodel/` 全目录：ProjectionService、ProjectionPosition、DefaultProjectionPosition、ProjectionPositionRepository、InMemoryProjectionPositionRepository、DefaultProjectionService、EventChunk、EventChunkReader、NoopEventChunkReader、ProjectionRunner、ProjectionView、ViewManager、ViewScheduler、TypedEventDispatcher、TypedEventHandler、TypedEvent
- 注意 ProjectionRunner 的 runOnce 流程（读位置→拉块→业务投影→推进位置）与 fuin SpringJpaViewManager 的 handleChunk 循环逐段对照

**Sibling docs**: 03（已对齐模板）、06（最新、含 40+ file:line 密度标准）。不修改 01-06。

## Mandatory structure (6 sections, exact `## ` titles)

`## 来源` / `## fuin 的设计` / `## 优点（值得借鉴的）` / `## 缺点（应规避的）` / `## ddd4j 自研决策` / `## 落地计划`

Content requirements:
- Section 5 headline「已对齐 + 超出」，对照清单逐项可 file:line 核验，候选角度（先逐条对源码验证再写，不成立就删）：
  - 对齐: 投影位置三方法（reset/read/update）；cron + chunkSize 视图配置；ViewManager 生命周期（start/stop/triggerOnce）
  - ddd4j 超出/改写: (a) ddd4j-core 的整套投影抽象是**纯 Java、框架无关**（ViewScheduler SPI 留给各运行时实现），fuin 的 ViewManager 直接写死 Spring（@Scheduled/SchedulingConfigurer/ContextRefreshedEvent）；(b) ProjectionRunner 把「读位置→拉块→投影→推进」沉淀为可复用纯类，fuin 把该循环内联在 SpringJpaViewManager 的 216 行里；(c) fuin QryProjectionService 三方法与 ddd4j ProjectionService 签名逐一对齐（Task 0.2 已清理 fuin 注释引用）；(d) fuin JPA 实体直接当聚合用 vs ddd4j 仓储分离
  - 不借鉴: fuin 的 Spring 专用调度器实现（阶段 7 按运行时各写）；fuin 的 String eventType 类型路由（ddd4j 用 Class/TypedEvent）
- 缺点须引真实行号（如 SpringJpaViewManager 中 Spring 注解行、asTypeNames 反射字符串行）
- 落地计划: 微调型——指向阶段 7（4 持久化 + 7 调度器，按 ddd4j-core ViewScheduler SPI 实现）与 ADR（如 0003）
- 60-100 lines；全角中文标点；title 精确为 `# 07. fuin API 模式：CQRS 投影读侧`

## Commit

```bash
cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j
git add docs/reference/fuin-api-patterns/07-cqrs-projection.md
git commit -m "docs(reference): 07-cqrs-projection API 模式参考（ddd4j-core 已对齐）"
```

## Precedent (Tasks 1.3-1.7)

Brief 可能有误——信源码不信 brief，corrections 写进报告（DONE_WITH_CONCERNS）。每条对照 file:line 双侧可查。已知相关事实（仍需自行核行号）：Task 1.7 评审确认 quarkus/springboot 模块为投影侧 + EventstoreConfig；官方示例 REST 直调聚合绕过命令侧。

## Report

Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-1.8-report.md`（行数、6 节、snippet 数 + 行引用、对齐/超出/不借鉴 计数、brief corrections、self-review）。Reply ≤15 lines: Status / commit / one-line summary / concerns / report path.
