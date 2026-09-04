# Task 1.6 Report — 05-event-store.md

## Deliverable

- `docs/reference/fuin-api-patterns/05-event-store.md` — 138 lines（要求 80-150）
- Commit: `65bb6788` `docs(reference): 05-event-store API 模式参考`（feature/2.0.x，单文件单提交）
- 未改动 01-04 与 README（README 第 13 行已预列 05 条目）

## Structure（6 sections，标题与 brief 逐字一致）

`# 05. fuin API 模式：EventStore 事件存储 SPI` ＋ `## 来源` / `## fuin 的设计` / `## 优点（值得借鉴的）` / `## 缺点（应规避的）` / `## ddd4j 自研决策` / `## 落地计划`。全角标点。

## Sources

- esc-api 0.9.0 sources jar 已存在于 `~/.m2`，解压至 `/tmp/esc-api-src`（61 文件），无需下载。
- 实读：EventStore / WritableEventStore / ReadableEventStore / EventStoreAsync / WritableEventStoreAsync / ReadableEventStoreAsync / EventStoreBasics / StreamId / SimpleStreamId / StreamEventsSlice / ExpectedVersion / CommonEvent / StreamState / SubscribableEventStore（+Async）/ ProjectionAdminEventStore / DelegatingAsyncEventStore / SerDeserializerRegistry / TypeName / 全部异常类（确认均为 RuntimeException 子类）。
- ddd4j 侧：`ddd4j-core/.../readmodel/EventChunkReader.java`、`EventChunk.java`；计划 Task 3.2（EventStore/StoredEvent/AggregateVersionConflictException/EventStoreContractTest）、Task 3.3、Task 5.4（AsyncEventStore + R2dbcEventStore，Reactor）。

## Snippets（file:line，全部出自提取源码，均验证）

1. EventStore.java:23＋EventStoreBasics.java:23-37（组合接口＋open/close）
2. WritableEventStore.java:80-82、:104-105（appendToStream 返回 nextVersion＋无版本重载）
3. WritableEventStore.java:167-171、:181-182（deleteStream 软删语义——Task 1.5 交给的 :181 已复核）
4. ReadableEventStore.java:50-51＋StreamEventsSlice.java:35-41（切片协议）
5. ReadableEventStore.java:147-164（readAllEventsForward＋ChunkEventHandler）
6. 引用不贴码：ExpectedVersion.java:28-32、CommonEvent.java:52、StreamId.java:29-30/:46/:65、SubscribableEventStore.java:55-58、DelegatingAsyncEventStore.java:30-33/:40-41、WrongExpectedVersionException.java:30、EventChunkReader.java:26、EventChunk.java:27-30、计划 Task 3.2 四方法签名。
- 省略一律带 `// ... omitted：…（:行号）` 标记（沿用 04 篇约定）。

## 借鉴/改写/不借鉴计数

- 优点（值得借鉴）：7 条；缺点（应规避）：8 条
- 自研决策：借鉴（新增）4、改写/已对齐 4、不借鉴 5、另加「核实说明」1
- 落地计划：6 个 checkbox（Task 3.2×2、Task 3.3×1、阶段 5×2、Task 1.10/ADR-0005×1）

## Brief corrections（trust source over brief）

1. **GitHub 地址不可从源码核实**：sources jar 内 pom（parent esc-parent 被 strip）无 `<url>`/`<scm>`，仅版权头指向 fuin.org——按 brief 预案标注「外部构件」，未引用 https://github.com/fuinorg/esc-api。
2. **TypeMultiEvent 条件不成立**：brief 说「no TypeMultiEvent meta-events if esc-api has them」——esc-api 0.9.0 全量 grep 0 命中（ddd-4-java 本地快照亦无），已在文档「核实说明」中记录，无需对齐决策。
3. **异常受检性澄清**：esc-api 写侧 throws 声明的全是 RuntimeException 子类（WrongExpectedVersionException.java:30 等）；04 篇批的受检异常噪音来自 ddd-4-java core Repository，非 esc-api——本文档已区分表述，避免误读传播。
4. 接口名与 brief 预期一致（Writable/ReadableEventStore、StreamEventsSlice 均为实名）；另发现 brief 未提的事实：异步侧为完整平行双轨（EventStoreAsync + DelegatingAsyncEventStore 线程池桥接），已写入缺点。

## Self-review

- 行数 138（达标）；标题/节标题逐字比对通过；全角标点抽查通过。
- 所有 file:line 引用逐一 grep 复核（含 SubscribableEventStore.java:55-58、DelegatingAsyncEventStore.java:30-33 等易错处）。
- 必选项核验：来源=外部构件 org.fuin.esc:esc-api:0.9.0 ✓；deleteStream 软删无墓碑/版本续接列为缺点首条 ✓；自研决策对照 Task 3.2（append/read/readAll＋AggregateVersionConflictException＋AsyncEventStore）✓；落地计划链接 3.2/3.3/阶段 5 ✓；未动 01-04 ✓；单提交 ✓。
- 残留风险：`readAllEventsForward`「无全局 position」论断基于接口面（读 API 全部 per-stream、订阅 volatile），esc-api 的实现库（如 esc-jpa-events）可能有 position 列——但本文档只论 API jar，论断限定在「API 无此概念」，成立。

Status: DONE
