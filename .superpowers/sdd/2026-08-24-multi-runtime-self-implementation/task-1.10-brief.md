# Task 1.10 Brief — Write ADR template + ADR-0001~0005

## Task

Create `ddd4j/docs/adr/` with 6 files: the ADR template + 5 architecture decision records. This closes 阶段 1's decision-record deliverable; ADR-0006 comes later (plan Task 2.4).

**Numbering correction (plan collision)**: the plan lists both `0001-template.md` and `0001-no-fork-strategy.md`. Resolve by naming the template **`0000-template.md`** so ADRs stay 0001-0005 (0006 reserved for Task 2.4). Record this as a brief correction in your report.

## Files to create

1. `docs/adr/0000-template.md`
2. `docs/adr/0001-no-fork-strategy.md`
3. `docs/adr/0002-core-zero-deps.md`
4. `docs/adr/0003-multi-runtime-strategy.md`
5. `docs/adr/0004-command-bus-design.md`
6. `docs/adr/0005-event-store-spi.md`

## Template structure (0000-template.md)

5 sections, exactly: `## Status` / `## Context` / `## Decision` / `## Consequences` / `## Alternatives Considered`. Template uses placeholder text (e.g. `（Accepted / Proposed / Superseded by ADR-XXXX）`) and one usage note at top: ADR 编号递增、文件名 `NNNN-kebab-case.md`、一旦 Accepted 只有通过新 ADR 才能推翻。

## ADR content requirements

Each ADR: 60-120 lines, full-width Chinese punctuation, Title as H1 `# ADR-000N: <标题>`, all 5 sections. **These are decisions now grounded in the completed reference series 01-08** — cite the supporting doc(s) by relative path (e.g. `../reference/fuin-api-patterns/05-event-store.md`).

**ADR-0001 不 fork fuin，reference-only 策略**（计划已给出正文骨架，遵循并补强）:
- Context: fuin 三仓库（ddd-4-java 0.7.0 / cqrs-4-java 0.6.0 / ddd-cqrs-4-java-example 0.5.0）均为 LGPL-3.0；fork 需 LICENSE 跟随+版权保留+修改标注
- Decision: 不 fork；「高精度参考 + 完全自研」；ddd4j 全 Apache-2.0；fuin 仅作 API 形态参考（docs/reference/fuin-api-patterns/）
- Consequences: 正面（Apache-2.0 无许可证风险 / API 100% 自控 / 不受 fuin 的 Spring 5-Java8 锁定）；负面（自研工作量 56-84 天 / 失去 fuin 的多序列化与框架适配）
- Alternatives: fork+改名（LGPL 跟随，已否决）；fuin 作为可选 ddd4j-data-fuin 模块（已否决）
- 补强：引用 01-08 系列作为 reference-only 的落地证据（8 篇、全部 file:line 核验）

**ADR-0002 ddd4j-core 零外部依赖**:
- Context: 跨 8 运行时的核心契约必须框架无关；现存 CoreIndependenceTest（6 条规则）
- Decision: ddd4j-core 仅允许 jackson-databind/annotations、commons-lang3、transmittable-thread-local；ArchUnit 守护；Task 2.5 强化 5 条新规则（含与现存 no_spring_in_core 去重——引用 08 篇 deferred 发现）
- Consequences / Alternatives（如允许 SLF4J 进 core 的方案）

**ADR-0003 跨 8 运行时适配策略**（计划正文已给出，遵循）:
- 8 运行时 × 持久化 × 调度矩阵表（Spring WebMVC/WebFlux、Quarkus、Micronaut、Helidon、Javalin、Vert.x、Dropwizard）
- 每能力 1 SPI + ≥4 持久化实现 + 7 调度器；工作量 56-84 天
- Alternatives: 只 Spring（否决）/ Spring+Quarkus（否决）

**ADR-0004 CommandBus 设计**:
- Context: 引用 06 篇结论——fuin CommandExecutor「有接口、无生态」（全生态 0 实现）、三参泛型、EventType 字符串路由、6 个受检异常
- Decision: ddd4j-core `CommandBus`/`DefaultCommandBus`（Map<Class,CommandExecutor> + 重复检测）为唯一入口；阶段 6 各运行时适配器**继承 DefaultCommandBus**（Spring 加 @Transactional；Quarkus CDI Instance 收集；Javalin/Vertx/Dropwizard 手动注册）；不引入 MultiCommandExecutor 层级
- Consequences / Alternatives（fuin 式三参执行器； MediatR 风格管道）

**ADR-0005 EventStore SPI 设计**:
- Context: 引用 05 篇结论——esc-api 0.9.0 同步/异步双轨全量复制（≈20 签名×2）、软删无墓碑、EJB 时代遗留；ddd4j-core EventChunkReader 已有读侧抽象
- Decision: 自研 4 方法 SPI（append/read×2/readAll + AggregateVersionConflictException）+ 全局 position + StoredEvent 完整元数据（eventId/correlationId/causationId）；异步走独立 `AsyncEventStore`（Reactor 单轨，阶段 5 Task 5.4）；删除语义=墓碑领域事件走统一追加路径（不提供存储级 deleteStream）；序列化 Jackson（多态 @class）
- Consequences / Alternatives（直接集成 esc-api；EventStoreDB/KurrentDB 商用存储）

## Commit

```bash
cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j
git add docs/adr/
git commit -m "docs(adr): ADR 模板 + ADR-0001~0005（参考系列 01-08 证据落地）"
```

## Verification before commit

- `ls docs/adr/` → exactly 6 files
- Every ADR has all 5 sections in order; numbering 0000-0005; no TBD/占位符 in the 5 ADRs (template's placeholders are fine)
- Cross-references to ../reference/fuin-api-patterns/NN-*.md point to files that exist

## Out of scope

不修改 docs/reference/、不写 ADR-0006（Task 2.4）、不碰 .java/pom

## Report

Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-1.10-report.md`（文件清单+行数、各 ADR 5 节确认、引用核对、brief corrections、self-review）。Reply ≤15 lines.
