# Task 1.5 Report — 04-event-sourcing-repository.md

## Status: DONE_WITH_CONCERNS

## Deliverable

- File: `ddd4j/docs/reference/fuin-api-patterns/04-event-sourcing-repository.md`
- Line count: 129（要求 80-150）
- Commit: `79de217d` — `docs(reference): 04-event-sourcing-repository API 模式参考`（单 commit，仅此一个新文件）

## Sections (6/6, exact titles)

1. `## 来源`
2. `## fuin 的设计`
3. `## 优点（值得借鉴的）`
4. `## 缺点（应规避的）`
5. `## ddd4j 自研决策`
6. `## 落地计划`

Title: `# 04. fuin API 模式：EventSourcingRepository 事件溯源仓储`（精确匹配）。

## fuin snippets

3 个带真实行号的代码块：
- 分页回放切片循环 + 异常翻译（EventStoreRepository.java:151-178）
- 乐观锁追加 + 冲突重试 do/while（EventStoreRepository.java:216-236）
- AggregateStreamId.asString 流命名（AggregateStreamId.java:93-95）

另含 20+ 处正文行号引用（:85-120 缓存三态、:222-225 一致性断言、:251-252 add 异常翻译、:285-309 resolveConflicts、:289-292 EventStore issue 1052 补丁、:312-327 硬删流、:398-413 int/long 防线、:426-478 五个模板钩子等），全部对照源文件逐行核实。

## Bullet counts

- 优点（借鉴）: 7
- 缺点（规避）: 7
- 借鉴（新增）: 5
- 改写/已对齐: 4
- 不借鉴: 5
- 落地计划 checkboxes: 7

## Brief corrections surfaced（按 1.3/1.4 先例）

1. **task-1.5-brief.md 为空文件（0 字节）**——brief 未生成。本任务依据：父任务指令 + task-1.4-brief.md 模板（6 段结构、标题、commit 规范）+ 计划文档 `docs/superpowers/plans/2026-08-24-multi-runtime-self-implementation.md:251`（Task 1.5 定义）。产物结构与其余各篇一致。
2. **esc 目录文件清单与预期不符**：`ddd-4-java/esc/.../esc/` 实际仅 3 个文件（`EventStoreRepository.java`、`AggregateStreamId.java`、`package-info.java`）。`EventStore` 接口**不在 ddd-4-java 内**——来自外部依赖 `org.fuin.esc:esc-api:0.9.0`（父 pom `esc.version` 属性，已验证）。文档已如实标注「存储依赖 esc-api」，EventStore SPI 本体留给 Task 1.6（05-event-store.md）。
3. **README 索引（Task 1.2 产物）称「EventStoreRepository 接口」不准确**：它是 abstract class（模板方法模式），实现的是 fuin core 的 `Repository` 接口。文档标题行下已加注说明；README 属 Task 1.2 范围未改动，建议后续修订。

## Self-review

- 路径/标题/6 段精确匹配：通过
- 行号真实性：全部核对（含 ddd4j 侧 `EventSourcingRepository.java:35-66`、`Repository.java:49`）
- 中文全角标点 + 代码半角 + ```java 围栏：通过
- 单 commit 仅新文件：通过（`git show --stat` 验证 1 file changed）
- 两个代码块内有中文省略注释（`/* 聚合版本与存储版本不一致 */`、sliceCount 计算处）标记节选，其余逐字引用源码
- 版本事实核对：ddd-4-java = tag `0.7.0`（git describe）；esc-api = 0.9.0

## Fix Round 1

Commit: `235d5598` — `docs(reference): 04-event-sourcing-repository — correct soft-delete claim + mark abridgments`

### 事实验证（修复依据）

- 本地源码 jar `~/.m2/repository/org/fuin/esc/esc-api/0.9.0/esc-api-0.9.0-sources.jar` 已解包验证：
  - `org/fuin/esc/api/WritableEventStore.java:181`：`void deleteStream(@NotNull StreamId streamId, long expectedVersion, boolean hardDelete)`
  - javadoc 原文：*"hardDelete: TRUE if it should be impossible to recreate the stream. FALSE (soft delete) if appending to it will recreate it. Please note that in this case the version numbers do not start at zero but at where you previously soft deleted the stream from."*
- 调用点 `ddd-4-java/esc/.../EventStoreRepository.java:320`：`getEventStore().deleteStream(streamId, expectedVersion, false)` → **软删除**确认。
- core `Repository.java` throws 统计：`delete`（:175）仅 1 个受检异常；`read(id)`/`add`×2 各 2 个；`read(id,version)`/`update`×2 各 3 个。

### 变更明细（before → after）

1. **缺点 bullet 4（Critical 事实错误）**
   - before: `**delete 是硬删流**：deleteStream（:320）直接物理删除事件流，违背 ES「事件不可变、只追加」原则，历史荡然无存。`
   - after: `**delete 是存储级软删流、删除不走事件**：deleteStream(streamId, expectedVersion, false)（:320）传 hardDelete=false，按 esc-api 语义是**软删除**——流可经追加重建、旧事件留存，并非物理抹除。真正的问题是删除作为存储管理命令执行而非领域事件追加：**不产生墓碑事件**，投影/订阅者无从感知删除发生；且软删后重建流时版本号不归零、接着软删前的版本继续（esc-api javadoc 明示），下游易误读流的历史。`
2. **不借鉴 bullet 2（同一错误）**
   - before: `delete(id, expectedVersion) 硬删流——若 ddd4j 补删除语义，只做软删除（墓碑事件），永不物理删流。`
   - after: `delete(id, expectedVersion) 存储级软删流（:320 传 hardDelete=false，流可重建、版本续接）——删除不走事件追加，无墓碑事件、投影/订阅者不可见；若 ddd4j 补删除语义，应以墓碑领域事件走统一追加路径，而非调用存储删除命令。`
3. **关键 API 行（同一错误的源头表述，联动修正）**
   - before: `delete(id, expectedVersion)（:312-327）：直接删流（硬删除）。`
   - after: `delete(id, expectedVersion)（:312-327）：软删流（:320 传 hardDelete=false，esc-api 语义为流可重建、版本续接，非物理删除）。`
4. **Snippet 1 unmarked abridgments（Important #1）**：原 `// ... readPageSize 与目标版本取小，决定本片条数` 升级为 `// ... omitted：sliceCount 计算（:154-159...）`；新增 `// ... omitted：LOG.debug（:162）`、`// ... omitted：LOG.debug（:164）`。
5. **Snippet 2 unmarked abridgments（Important #1）**：新增 `// ... omitted：LOG.debug（:229-230）`（catch 块内）与 `// ... omitted：catch (StreamDeletedException | StreamNotFoundException) → AggregateNotFoundException（:232-234）`（catch 后独立行）。
6. **Minor #1**：`core Repository 每个方法 throws 2-3 个受检异常` → `core Repository 多数方法 throws 2-3 个受检异常（delete 仅 1 个）`。
7. **Minor #2**：`AggregateStreamId.java:93-95` → `AggregateStreamId.java:92-95，:92 为 @Override`。
8. **落地计划一致性联动**：评估项「限定软删除语义」→「限定墓碑事件式软删除（删除也走事件追加）」——修正后"软删除"单独出现有歧义（fuin 软删正是被批评对象）。

### Diff stats

- 1 file changed, 11 insertions(+), 7 deletions(-)
- 仅 `docs/reference/fuin-api-patterns/04-event-sourcing-repository.md`，单 commit `235d5598`

### Self-review findings

- 两个 bullet（缺点 4 + 不借鉴 2）均已改写：软删语义 + 合法批评（无墓碑事件、投影/订阅者不可见、重建版本续接）三要素齐备 — 通过
- Snippet 1/2 全部节选处（:154-159、:162、:164、:229-230、:232-234）均带显式 `// ... omitted` 标记 — 通过
- Minor #1（delete 仅 1 个受检异常）、Minor #2（:92-95 含 @Override）— 通过
- 其余内容（7 优点、5 借鉴、4 改写、其余不借鉴、落地计划条目数）逐项核对未动 — 通过
- 单 commit、规定的提交信息逐字一致 — 通过
