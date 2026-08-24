# 04. fuin API 模式：EventSourcingRepository 事件溯源仓储

> 对应 README 索引第 04 项；只读提炼 fuin 设计，ddd4j 全部自研实现（no code reuse）。注意：fuin 的 `EventStoreRepository` 是**抽象类**（模板方法模式），而非接口——接口形态来自 fuin core 的 `Repository`，实现细节见下文。

## 来源

- 仓库：https://github.com/fuinorg/ddd-4-java
- 版本：0.7.0（本地快照：`workspace-ddd4j-boot/ddd-4-java`，tag `0.7.0`）
- 文件：
  - `esc/src/main/java/org/fuin/ddd4j/esc/EventStoreRepository.java:57-480`（主源，esc 模块仅此一核心类）
  - `esc/src/main/java/org/fuin/ddd4j/esc/AggregateStreamId.java:35-122`（流标识值对象）
  - `core/src/main/java/org/fuin/ddd4j/core/Repository.java`（被实现的仓储契约接口）
- 关键 API：
  - `EventStoreRepository<ID extends AggregateRootId, AGGREGATE extends AggregateRoot<ID>>`（abstract class，实现 core `Repository` 接口，EventStoreRepository.java:57-58）。
  - `read(id)`/`read(id, version)`（:85-120）：缓存感知读取；私有 `read(aggregate, id, targetVersion)`（:141-187）分页回放。
  - `update(aggregate[, metaType, metaData])`（:197-238）：乐观锁追加 + 冲突重试循环。
  - `add(aggregate[, metaType, metaData])`（:241-257）：复用 update，异常翻译为 `AggregateAlreadyExistsException`。
  - `delete(id, expectedVersion)`（:312-327）：直接删流（硬删除）。
  - 模板钩子：`conflictsResolved()`（:426-428，默认 false）、`getMaxTryCount()`（:437-439，默认 3）、`getAggregateCache()`（:448-450，默认 NoCache）、`getReadPageSize()`（:458-460，默认 100）、抽象 `getIdParamName()`（:478）。
  - 存储依赖：`org.fuin.esc:esc-api` 0.9.0（`EventStore`/`CommonEvent`/`StreamEventsSlice`/`ExpectedVersion` 均来自该外部库，ddd-4-java 本体不含 EventStore 接口——那是 05 篇主题）。

## fuin 的设计

模板方法模式：抽象类包揽读写全流程（缓存、分页、乐观锁、重试、异常翻译），子类只覆写钩子；存储操作全部委托外部 esc-api 的 `EventStore`。

**1）分页回放读——切片循环 + 异常翻译（EventStoreRepository.java:151-178）**

```java
int sliceStart = aggregate.getVersion() + 1;
StreamEventsSlice currentSlice;
do {
    // ... readPageSize 与目标版本取小，决定本片条数
    try {
        currentSlice = getEventStore().readEventsForward(streamId, sliceStart, sliceCount);
    } catch (final StreamNotFoundException ex) {
        throw new AggregateNotFoundException(getAggregateType(), id);
    } catch (final StreamDeletedException ex) {
        throw new AggregateDeletedException(getAggregateType(), id);
    }
    for (final CommonEvent commonEvent : currentSlice.getEvents()) {
        final DomainEvent<?> event = (DomainEvent<?>) commonEvent.getData();
        aggregate.loadFromHistory(event);
    }
    sliceStart = intVersion(currentSlice.getNextEventNumber());
} while ((aggregate.getVersion() != targetAggregateVersion) && !currentSlice.isEndOfStream());
```

**2）乐观锁追加 + 冲突裁决重试（EventStoreRepository.java:216-236）**

```java
long expectedVersion = expectedVersion(aggregate);
int retryCount = 0;
boolean unsaved = true;
do {
    try {
        final int eventStoreNextVersion = intVersion(getEventStore().appendToStream(streamId, expectedVersion, eventDataList));
        if ((expectedVersion + eventDataList.size()) != eventStoreNextVersion) {
            throw new IllegalStateException(/* 聚合版本与存储版本不一致 */);
        }
        aggregate.markChangesAsCommitted();
        unsaved = false;
    } catch (final WrongExpectedVersionException ex) {
        expectedVersion = resolveConflicts(aggregate, integerVersion(ex.getActual()), retryCount++);
    }
} while (unsaved);
```

`resolveConflicts`（:285-309）先限次（`retryCount == getMaxTryCount()` 即抛 `AggregateVersionConflictException`），再拉取未见事件交 `conflictsResolved()` 钩子裁决——默认直接冲突。

**3）流标识——AggregateStreamId（AggregateStreamId.java:93-95）**

```java
@Override
public String asString() {
    return type + "-" + paramValue.asString();
}
```

## 优点（值得借鉴的）

- **分页切片回放**：`readEventsForward` + `readPageSize`（默认 100）循环读取（:151-178），大流不会一次性载入内存，且读历史版本只补差量（`sliceStart = aggregate.getVersion() + 1`）。
- **乐观锁重试 + 冲突裁决钩子**：`WrongExpectedVersionException` 不直接失败，而是经 `resolveConflicts`（:285-309）拉未见事件交业务子类 `conflictsResolved()`（:426-428）裁决，配 `getMaxTryCount()`（:437-439）防无限重试——并发写语义完整。
- **追加后一致性断言**：`expectedVersion + events.size() == nextVersion`（:222-225），存储层计数漂移当场暴露而非静默错版。
- **add 即 update**：新建聚合就是向空流追加（版本 -1 → `ExpectedVersion.NO_OR_EMPTY_STREAM`，:259-264），冲突翻译为 `AggregateAlreadyExistsException`（:251-252）——一套写入路径，两种语义。
- **仓储层异常翻译**：存储异常（StreamNotFound/StreamDeleted）一律转领域异常（:165-169、:232-234），esc-api 类型不外泄到调用方。
- **版本感知缓存**：`read(id, version)` 比对缓存版本大/等/小三态（:107-119），低于目标才补读。
- **模板钩子选点克制**：默认全可用（NoCache/100/3/false），子类按需覆写（:426-478）。

## 缺点（应规避的）

- **抽象类绑定实现**：`EventStoreRepository` 是 abstract class 且构造器硬接 esc-api `EventStore`（:75-82），esc-api 的 `CommonEvent`/`TypeName`/`ExpectedVersion` 等类型经 `asCommonEvents`（:379-396）渗入领域层；换存储实现即换继承体系。
- **受检异常噪音**：core `Repository` 每个方法 throws 2-3 个受检异常；`read(id)` 还得把不可能发生的 `AggregateVersionNotFoundException` 包成 RuntimeException（:95-98）自圆签名。
- **int/long 版本混用**：聚合版本 int、事件存储 long，靠运行时 `intVersion` 防线（:398-413）兜底，超界即 `IllegalStateException`。
- **delete 是硬删流**：`deleteStream`（:320）直接物理删除事件流，违背 ES「事件不可变、只追加」原则，历史荡然无存。
- **冲突解决的临时补丁**：事件存储不回传实际版本时整读聚合兜底（:289-292，TODO 引 EventStore issue 1052），重试路径开销不可控。
- **metaData 无类型**：`update(aggregate, String metaType, Object metaData)`（:203）元数据裸 `Object`，序列化契约全凭实现自觉。
- **接口样板**：core `Repository` 强制 `getAggregateClass()`/`getAggregateType()`/`create()` 工厂三件套，每个仓储重复实现。

## ddd4j 自研决策

> **结论：ddd4j-core 已有纯接口 `EventSourcingRepository`（`io/ddd4j/core/ddd/repository/EventSourcingRepository.java:35-66`：read×2/add/update，无受检异常），与 CRUD 型 `Repository`（对齐 MyBatis-Plus BaseMapper，Repository.java:49）分流；fuin 的并发/分页机制在阶段 3/4 实现层借鉴，抽象类形态与 esc-api 绑定不借鉴。**

- **借鉴（新增）**：
  - 乐观锁重试 + 冲突裁决钩子 + 最大重试次数（默认 3）——阶段 3/4 `ddd4j-data-event-store` 实现采纳（钩子改用策略接口，非 protected 方法）。
  - 分页切片读取（`readEventsForward(streamId, start, count)` 形态 + 可配 pageSize）——阶段 3 Task 3.2 EventStore SPI 签名直接采纳。
  - 追加后 `expected + size == nextVersion` 一致性断言——阶段 4 JPA 实现采纳。
  - add = update + `AggregateAlreadyExistsException` 翻译——一套写入路径。
  - 仓储层异常翻译原则（存储异常不外泄为存储类型）。
- **改写/已对齐**：
  - 接口契约：ddd4j `EventSourcingRepository`（read/read(id,version)/add/update）已覆盖 fuin Repository 的 ES 子集，且**去掉全部受检异常**、去掉 metaType/metaData 重载（阶段 3 按需以类型化元数据补）。
  - 流命名：fuin `AggregateStreamId.asString()` 的 `type + "-" + id` 规则改写进 ddd4j EventStore SPI 的流标识。
  - int/long 边界：fuin 运行时防护（:398-413）改写为「SPI 层 long、聚合层 int、边界处一次性转换 + 显式上限检查」的工具方法。
  - 缓存：fuin `AggregateCache` 钩子改写为 ddd4j 缓存模块（ddd4j-cache）的可选装饰，不入核心写入路径。
- **不借鉴**：
  - 抽象类模板 + 构造器硬绑 esc-api——ddd4j-core 只留纯接口，实现下沉 ddd4j-data-event-store（多运行时，ADR-0003）。
  - `delete(id, expectedVersion)` 硬删流——若 ddd4j 补删除语义，只做软删除（墓碑事件），永不物理删流。
  - 受检异常 `throws` 声明、`RuntimeException("Cannot happen")` 自圆补丁（:95-98）。
  - `getAggregateClass()`/`create()` 接口级工厂样板——ddd4j 由实现模块自管实例化。
  - `Object metaData` 无类型元数据——改为类型化元数据对象或暂不提供。

## 落地计划

- [ ] 阶段 3（Task 3.2）：EventStore SPI 采纳分页签名 `readEventsForward(streamId, start, count)` 与切片返回（含 nextEventNumber/isEndOfStream 等价物）。
- [ ] 阶段 3（Task 3.2）：`AggregateVersionConflictException` 字段对齐 fuin 语义（aggregateType/aggregateId/expectedVersion/actualVersion）。
- [ ] 阶段 4：实现内置乐观锁重试（默认 3 次）+ 类型化冲突裁决策略（`conflictsResolved` 等价物，默认拒绝）。
- [ ] 阶段 4：add 复用 update + `AggregateAlreadyExistsException` 翻译；追加后 nextVersion 一致性断言。
- [ ] 阶段 4：int/long 版本边界转换工具 + `Integer.MAX_VALUE` 显式上限测试。
- [ ] 评估：ddd4j-core `EventSourcingRepository` 是否补 `delete(id, expectedVersion)`——若补，限定软删除语义并在 ADR-0005 记录。
- [ ] Task 1.10：ADR-0005（event-store SPI）引用本文档「抽象类不借鉴、并发机制借鉴」结论。
