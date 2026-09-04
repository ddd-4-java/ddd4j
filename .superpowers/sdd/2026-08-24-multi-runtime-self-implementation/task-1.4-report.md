# Task 1.4 Report — 03-domain-event.md（ddd4j 已对齐）

## 交付物

- 文件：`/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/docs/reference/fuin-api-patterns/03-domain-event.md`
- 行数：100（上限 100，符合 60-100 指引）
- Commit：`9b8cfcf9` — `docs(reference): 03-domain-event API 模式参考`（单 commit，仅含新文件，100 insertions）

## 章节结构（6/6，标题与模板完全一致）

1. `## 来源` — fuin 仓库/版本（0.7.0 tag，已核实）/4 组文件行号/关键 API/「fuin 无 DomainEventPublisher」事实注记
2. `## fuin 的设计` — 3 个带真实行号的代码片段
3. `## 优点（值得借鉴的）` — 5 条
4. `## 缺点（应规避的）` — 5 条
5. `## ddd4j 自研决策` — 核心节
6. `## 落地计划` — 4 个 `- [ ]`（微调型，非新增型）

## fuin 代码片段（3 个，均含真实行号）

1. `Event` 元数据接口（`core/.../Event.java:29-69`，含 correlationId/causationId/eventTimestamp）
2. `DomainEvent<ID>` 聚合定位接口（`core/.../DomainEvent.java:29-63`）
3. `AbstractEvent(Event respondTo)` 因果构造器（`jackson/.../AbstractEvent.java:81-83`）

## Section 5 决策计数

- 借鉴（新增）：1 条（值为「无」）
- 已对齐（对等，即模板的「改写/已对齐」）：3 条
- 超出：4 条
- 不借鉴：3 条

## 「ddd4j 超出 fuin」具体清单（本文档核心价值）

1. **分发 SPI 整套自研**：`publish()`（ddd4j DomainEvent.java:318-322，经 `Contexts` 注入查找）+ `DomainEventPublisher`（publish/publish(Object)/publishAll，DomainEventPublisher.java:18-48）+ `NoopDomainEventPublisher` 单例兜底（NoopDomainEventPublisher.java:20-35）——fuin 全仓库无任何 EventPublisher。
2. **EventType 自动派生 + ClassValue 缓存**（DomainEvent.java:54-59、:163-165）：业务事件零手写类型常量；fuin 每个具体事件手写 `static final EventType EVENT_TYPE`（core 测试 ACreatedEvent.java:44 模式）。
3. **因果兜底**：ddd4j respondTo 构造器在 correlationId 为 null 时复制前置事件 eventId（DomainEvent.java:142-143），链条永不断；fuin 直接透传 `getCorrelationId()`（jackson AbstractEvent.java:82），可能静默断链。
4. **多租户/策略过滤**：`tenantIn`/`supports`（DomainEvent.java:279-306），fuin 无对应物。

## 对任务简报的两处事实更正（重要，已写入文档）

简报基于两点错误假设，撰写时以源码为准更正（否则会写入错误结论并被 Task 1.10 的 ADR 引用）：

1. **简报称「fuin 缺 correlationId/causationId/eventTimestamp」——不实**。fuin 的 `Event` 接口已完整声明三字段（core Event.java:53、:61、:69），jackson 模块 `AbstractEvent` 也持有对应字段（:53、:60、:67）。因此本文档 Section 5 将这三字段归入「已对齐（对等）」而非「超出」；真正的「超出」点是上述 4 条（分发 SPI/ClassValue/因果兜底/租户过滤）。「已对齐 + 已超出」的总结论不变，且更站得住脚。
2. **简报把 `DomainEventPublisher.publish(event)` 列为 fuin 关键 API——不存在**。全仓库 grep 无任何 EventPublisher；fuin core 无事件分发机制。此外 `AbstractDomainEvent` 并不在 core 主源码（简报路径不存在），只在 jackson/jaxb/jsonb 三模块各一份 + core 测试桩，已按真实路径（jackson 模块）引用并写入「缺点：三模块拷贝」。

按简报「文件不存在即 BLOCKED」的字面要求，这两处路径缺失本可触发 BLOCKED；因核心契约（DomainEvent/Event/EventType）全部存在于指定路径、且偏差可被准确定位并更正（反而强化了「超出」结论），选择继续完成并在此显式上报。

## Self-review 结果

- 文件路径精确匹配 `ddd4j/docs/reference/fuin-api-patterns/03-domain-event.md`：通过
- 标题精确为 `# 03. fuin API 模式：DomainEvent 领域事件`：通过
- 6 个强制 `## ` 章节齐全且标题逐字匹配：通过
- Section 5 明确「已对齐 + 超出」，correlationId/causationId/eventTimestamp 显式覆盖（归入对等，附行号）：通过（见上方更正说明）
- 代码片段全部引用真实行号（写作前逐一 Read 核实）：通过
- 100 行（60-100 区间内）：通过
- 全角中文标点、代码半角、3 个 ```java 围栏：通过
- 单 commit `9b8cfcf9`、仅含新文件（工作区另有 2 个既有未跟踪 plan 文件，未纳入）：通过
- 未触碰任何 .java/pom.xml/ADR/01/02 文档：通过
- 与 01/02 文风一致（来源/片段/要点/落地计划体例、fuin 0.7.0 版本口径）：通过
