# 05. fuin API 模式：EventStore 事件存储 SPI

> 对应 README 索引第 05 项；只读提炼 fuin 设计，ddd4j 全部自研实现（no code reuse）。注意：fuin 的 `EventStore` **不在 ddd-4-java 中**，而来自外部构件 `org.fuin.esc:esc-api`（04 篇的 EventStoreRepository 即构建其上，Task 1.5 已核实）。

## 来源

- 构件：`org.fuin.esc:esc-api:0.9.0`（Maven Central **外部构件**，非 ddd-4-java 源码；版权头「Copyright (C) 2015 Michael Schnell, http://www.fuin.org/」与 ddd-4-java 同作者。sources jar 内 pom 未含仓库 URL，无法从源码核实 GitHub 地址，故按外部构件标注）
- 提取：`unzip ~/.m2/repository/org/fuin/esc/esc-api/0.9.0/esc-api-0.9.0-sources.jar`（61 个文件，包 `org.fuin.esc.api`）
- 文件（路径均相对 `org/fuin/esc/api/`）：
  - `EventStore.java:23-25`（同步组合接口：Writable＋Readable）
  - `WritableEventStore.java:28-208`（写侧：isSupportsCreateStream/createStream、appendToStream×4、deleteStream×2）
  - `ReadableEventStore.java:26-166`（读侧：切片读×2、单事件、streamExists/streamState、推式全量＋内嵌 ChunkEventHandler）
  - `EventStoreAsync.java:23-25`、`WritableEventStoreAsync.java:29-216`、`ReadableEventStoreAsync.java:28-128`、`SubscribableEventStoreAsync.java`（CompletableFuture 异步双轨）
  - 支撑类型：`StreamId.java:31-75`、`StreamEventsSlice.java:33-108`、`ExpectedVersion.java:26-32`、`CommonEvent.java:27-70`、`StreamState.java`、`SubscribableEventStore.java:28-68`、`ProjectionAdminEventStore.java`
- 关键 API：`appendToStream(streamId, expectedVersion, events)` 返回下一版本；`readEventsForward(streamId, start, count)` 返回切片 `StreamEventsSlice`；`deleteStream(streamId, expectedVersion, hardDelete)` 软删／硬删双语义
- 附注（Task 1.5 核实）：ddd-4-java 本体不含 EventStore 接口（其 esc 模块仅 AggregateStreamId/EventStoreRepository/package-info 三文件）；esc-api jar 内无 `META-INF/services`，未提供 ServiceLoader 发现机制

## fuin 的设计

按「能力」拆接口再组合：`EventStoreBasics`（open/close 生命周期，继承 AutoCloseable）之上分出 `WritableEventStore` 与 `ReadableEventStore`，二者合成 `EventStore`；订阅（SubscribableEventStore）、投影管理（ProjectionAdminEventStore）、异步（EventStoreAsync）各自独立成接口，实现按能力选做。

**1）组合式能力接口（EventStore.java:23、EventStoreBasics.java:23-37）**

```java
public interface EventStore extends WritableEventStore, ReadableEventStore {
}

public interface EventStoreBasics extends AutoCloseable {
    // ... omitted：javadoc（EventStoreBasics.java:25-30）
    EventStoreBasics open();
    void close();
}
```

接口约定「对未 open 的存储调用任意方法会隐式 open」（WritableEventStore.java:25-27）。

**2）写侧——追加返回下一版本，四个重载（WritableEventStore.java:80-82、:104-105）**

```java
long appendToStream(@NotNull StreamId streamId, long expectedVersion, @NotNull CommonEvent... events)
        throws StreamNotFoundException, StreamDeletedException, WrongExpectedVersionException,
        StreamReadOnlyException;

// ... omitted：javadoc 与异常声明（WritableEventStore.java:84-103）
long appendToStream(@NotNull StreamId streamId, @NotNull CommonEvent... events) // 不带版本＝关闭乐观锁
```

另有两个 `List<CommonEvent>` 重载（:130-132、:153-154）。`CommonEvent` 载荷为无类型 `Object`（CommonEvent.java:52），序列化经独立的 `Serializer/DeserializerRegistry`（按 `SerializedDataType`＋`EnhancedMimeType` 注册）完成，不在 EventStore 接口内。

**3）删除——软删／硬删双语义，版本续接（WritableEventStore.java:167-171、:181-182）**

```java
// javadoc：hardDelete 为 FALSE（软删）时追加可重建流，且版本号不归零、
// 接着软删前的版本继续（WritableEventStore.java:167-171）
void deleteStream(@NotNull StreamId streamId, long expectedVersion, boolean hardDelete)
        throws StreamDeletedException, WrongExpectedVersionException, StreamReadOnlyException;
```

流状态三态 `StreamState`：ACTIVE／SOFT_DELETED（可重建）／HARD_DELETED（不可重建）。

**4）读侧——切片返回（ReadableEventStore.java:50-51、StreamEventsSlice.java:35-41）**

```java
StreamEventsSlice readEventsForward(@NotNull StreamId streamId, long start, int count);

// StreamEventsSlice（@Immutable，:32；构造时防御性拷贝 :57-69；返回不可变列表 :87-89）
private final long fromEventNumber;      // 本次起点
private final long nextEventNumber;      // 下一个可读事件号
private final boolean endOfStream;       // 是否到尾
private final List<CommonEvent> events;  // 事件列表
```

另有 `readEventsBackward`（:75-76）、单事件 `readEvent(streamId, eventNumber)`（:98）、`streamExists`（:108）与 `streamState`（:123）。乐观锁哨兵为命名常量：`ExpectedVersion.ANY(-2)`、`NO_OR_EMPTY_STREAM(-1)`（ExpectedVersion.java:28-32）。

**5）推式全量遍历——回调分块（ReadableEventStore.java:147-164）**

```java
void readAllEventsForward(StreamId streamId, long startingAtEventNumber,
                          int chunkSize, ChunkEventHandler handler);

interface ChunkEventHandler {
    void handle(StreamEventsSlice currentSlice);
}
```

注意其「全量」是**单流内**全量，API 无全局 position 概念；跨流订阅靠 `SubscribableEventStore.subscribeToStream`（volatile 订阅，:55-58）与投影管理 `ProjectionAdminEventStore`（投影即只读流，`StreamId.isProjection()` 标记，StreamId.java:46）。

## 优点（值得借鉴的）

- **能力拆分＋组合**：Writable/Readable/Subscribable/ProjectionAdmin 各自独立，`EventStore` 只是前两者组合（EventStore.java:23）——实现只暴露支持的能力，接口即能力清单。
- **切片协议完整**：`readEventsForward(streamId, start, count)` → `StreamEventsSlice`（from/next 事件号＋endOfStream＋拷贝防御的事件列表，:35-41、:57-69），调用方以 `nextEventNumber` 驱动循环（04 篇 EventStoreRepository.java:151-178 即此用法），无需预知事件总数。
- **追加返回下一版本号**：`appendToStream` 返回 nextVersion（:67），使「expectedVersion＋事件数 == nextVersion」一致性断言成为可能（04 篇已采纳该断言）。
- **乐观锁哨兵命名化**：`ExpectedVersion.ANY/NO_OR_EMPTY_STREAM` 用带数值的枚举（:28-32）而非裸魔法数。
- **状态探测轻量**：`streamExists`/`streamState`（ReadableEventStore.java:108、:123）＋三态 `StreamState`，读写前无需试探性读取。
- **推式分块遍历**：`readAllEventsForward` 回调 `ChunkEventHandler`（:147-164），大流遍历内存有界。
- **快照值对象纪律**：`StreamEventsSlice` 不可变、防御性拷贝、返回不可变列表（:32-33、:57-69、:87-89）。

## 缺点（应规避的）

- **deleteStream 软删除不发墓碑事件**：删除是存储管理命令而非事件追加（WritableEventStore.java:181-182）——投影／订阅者无从感知删除发生；软删后重建流版本号不归零、接着软删前继续（:167-171 javadoc 明示，`StreamState.SOFT_DELETED` 佐证），下游易误读流历史。此即 04 篇软删发现的 SPI 层根源。
- **同步／异步双轨全量复制**：`EventStoreAsync` 与 `EventStore` 完全平行（EventStoreAsync.java:23），Writable/Readable 各配一份 CompletableFuture 版（WritableEventStoreAsync.java:46-201 等比复刻同步版 :40-206），约 20 个签名×2，漂移风险高；`DelegatingAsyncEventStore` 用线程池包同步实现，异步名不副实（:30-33、:40-41）。
- **关闭乐观锁的重载**：不带 `expectedVersion` 的 `appendToStream`（:104-105、:153-154）静默跳过并发校验，误用即丢失冲突检测。
- **StreamId 标识陷阱**：javadoc 警告「只能按 asString() 比较」（StreamId.java:29-30），equals 不可靠；`getParameters()/isProjection()` 把投影与参数概念塞进基础标识（:46、:65）。
- **生命周期进 SPI**：`open()/close()`（EventStoreBasics.java:31-37）＋「隐式 open」约定（WritableEventStore.java:25-27）——资源泄漏被静默掩盖；ReadableEventStore.java:126-132 还有 EJB 时代遗留（ejb-jar.xml 配置异常的 CAUTION）。
- **无全局顺序**：读 API 全部按流定位，无全局 position；投影只能靠 volatile 订阅或投影流，断线续传需自管位点。
- **载荷无类型＋序列化全家桶**：`CommonEvent.getData()` 裸 `Object`（CommonEvent.java:52），配 `SerializedDataType/EnhancedMimeType/SerializerRegistry` 一整套注册机制，SPI 表面积大、集成成本高。
- **无发现机制**：jar 内无 `META-INF/services`，实现装配全靠手工。

## ddd4j 自研决策

> **结论：计划 Task 3.2 的四方法 `EventStore`（append/read×2/readAll）＋`AggregateVersionConflictException` 覆盖 esc-api 读写主干的领域语义，而去掉 StreamId/open-close/重载/软删/双轨复制五类负担；切片精神下沉为 ddd4j-core 既有 `EventChunkReader/EventChunk`；全局 `position`（readAll）是 esc-api 没有而投影必需的基石。**

- **借鉴（新增）**：
  - 切片循环协议（起点＋数量→结果带 nextEventNumber 游标）——ddd4j-core `EventChunkReader.read(streamId, fromEventNumber, chunkSize, eventTypes)`（EventChunkReader.java:26）＋`EventChunk(events, nextEventNumber)`（EventChunk.java:27-30，StreamEventsSlice 的精简等价物）已存在，Task 3.2 聚合读的版本区间重载沿用同一游标思想。
  - 追加后一致性断言（expected＋size 对账）——阶段 4 JPA 实现采纳（SPI 返回 void，断言在实现内部做）。
  - 乐观锁异常四字段（aggregateType/aggregateId/expectedVersion/actualVersion）——Task 3.2 `AggregateVersionConflictException` 已对齐（esc-api 全部异常均为 RuntimeException 子类但 throws 显式声明，如 WrongExpectedVersionException.java:30；ddd4j 不写 throws）。
  - 读侧轻量状态探测思想——ddd4j 以「read 返回空列表＋版本字段」等价表达，不单列 exists/state 方法。
- **改写/已对齐**：
  - 流标识：esc-api `StreamId` 接口（含 KeyValue 参数、投影标记）改写为 `(aggregateType, AggregateRootId)` 直接参数对——流命名规则是实现细节，不入核心 SPI。
  - 分页读：`readEventsForward＋StreamEventsSlice` 改写为 `read(type, id, fromVersion, toVersion)` 版本区间＋`List<StoredEvent>` 返回（计划 Task 3.2）；切片遍历归投影侧 EventChunkReader。
  - 异步：esc-api CompletableFuture 双轨改写为 Task 5.4 `AsyncEventStore`（Reactor Mono/Flux，仅 4 方法，`R2dbcEventStore implements AsyncEventStore`）——单轨响应式，不做签名复刻。
  - 载荷类型：`CommonEvent` 裸 Object 改写为 `StoredEvent.payload` 类型化 `DomainEvent<?>`（含 correlation/causation），序列化收敛到 Task 3.3 `EventPayloadSerializer`（Jackson 单策略），不引入 SerializedDataType/MIME registry。
- **不借鉴**：
  - `deleteStream`（含软删/硬删双语义）——删除应走墓碑领域事件的统一追加路径（04 篇结论，ADR-0005 记录）。
  - `open()/close()` 与隐式 open——生命周期交 Spring/Quarkus/Javalin 容器管理（ADR-0003 多运行时）。
  - 不带 expectedVersion 的追加重载——乐观锁不可选关闭。
  - sync/async 接口全量复制与 `DelegatingAsyncEventStore` 线程池伪装。
  - EJB 遗留注释与约定（ReadableEventStore.java:126-132）。
- **核实说明**：brief 猜测 esc-api 可能有 TypeMultiEvent 元事件——提取源码 grep 0 命中，**esc-api 0.9.0 无此类型**，无需对齐决策。

## 落地计划

- [ ] 阶段 3（Task 3.2）：EventStore SPI 定稿四方法（append/read×2/readAll）＋`AggregateVersionConflictException` 四字段＋`StoredEvent`（含全局 position）。
- [ ] 阶段 3（Task 3.2）：`EventStoreContractTest` 三契约用例（追加读回／版本冲突抛异常／readAll 按 position 之后读取）对齐 esc-api 已验证语义。
- [ ] 阶段 3（Task 3.3）：EventPayloadSerializer 不引入 SerializedDataType/EnhancedMimeType 注册体系。
- [ ] 阶段 5：jpa/panache/jdbi/r2dbc 四实现；R2dbcEventStore 走 `AsyncEventStore`（Reactor），其余走同步 `EventStore`。
- [ ] 阶段 5：投影读取统一复用 ddd4j-core `EventChunkReader/EventChunk`（nextEventNumber 游标循环，替代 esc-api 推式 ChunkEventHandler）。
- [ ] Task 1.10：ADR-0005 引用本文档「无 deleteStream（墓碑事件替代）、无 open/close、无双轨异步」三项决策。
