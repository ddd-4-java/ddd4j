# 03. fuin API 模式：DomainEvent 领域事件

> 对应 README 索引第 03 项；本篇为「已对齐」主题——ddd4j-core 现有 `DomainEvent` 契约已与 fuin 对齐并在分发侧超出，重在盘点存量与微调，不新增借鉴。

## 来源

- 仓库：https://github.com/fuinorg/ddd-4-java
- 版本：0.7.0（本地快照：`workspace-ddd4j-boot/ddd-4-java`，tag `0.7.0`）
- 文件：
  - `core/src/main/java/org/fuin/ddd4j/core/DomainEvent.java:29-63`（领域事件接口）、`Event.java:29-71`（事件元数据契约）、`EventType.java:32-63`（事件类型值对象）
  - `jackson/src/main/java/org/fuin/ddd4j/jackson/AbstractDomainEvent.java:40-204`（事件基类 + Builder；core 主源码无此类，仅 jackson/jaxb/jsonb 三模块各一份）
- 关键 API：
  - `DomainEvent<ID extends EntityId> extends Event`（接口）：getEntityIdPath/getEntityId/getAggregateVersion/getAggregateVersionInteger。
  - `Event extends Serializable`：eventId/eventType/eventTimestamp/correlationId/causationId 五个元数据方法。
  - `EventType`：不可变字符串值对象（≤255 字符）。
  - `AbstractDomainEvent`（jackson 模块）：字段基类 + 泛型 Builder。
  - 注意：fuin **没有** `DomainEventPublisher`——全仓库无任何事件分发 SPI。

## fuin 的设计

契约分两层：`Event` 承载全部元数据（标识/类型/时间戳/关联/因果），`DomainEvent` 叠加聚合定位（实体路径 + 版本）；`EventType` 是 ≤255 字符的不可变字符串值对象。

**1）事件元数据契约——Event（Event.java:29-69，含 correlationId/causationId/eventTimestamp）**

```java
public interface Event extends Serializable {

    @NotNull EventId getEventId();
    @NotNull EventType getEventType();
    @NotNull ZonedDateTime getEventTimestamp();
    @Nullable EventId getCorrelationId();
    @Nullable EventId getCausationId();

}
```

**2）领域事件接口——DomainEvent（DomainEvent.java:29-63）**

```java
public interface DomainEvent<ID extends EntityId> extends Event {

    @NotNull EntityIdPath getEntityIdPath();
    @NotNull ID getEntityId();
    @Nullable AggregateVersion getAggregateVersion();
    @Nullable Integer getAggregateVersionInteger();

}
```

**3）基类与因果链——AbstractEvent(Event respondTo)（jackson AbstractEvent.java:81-83）**

```java
public AbstractEvent(@NotNull final Event respondTo) {
    this(respondTo.getCorrelationId(), respondTo.getEventId());
}
```

`AbstractDomainEvent` 持有 entityIdPath（:47）、aggregateVersion（:51）及父类四元元数据；`respondTo` 构造器直接透传前置事件的 correlationId——若其为 `null`，追踪链中途断链。每个具体事件还需手写 `static final EventType EVENT_TYPE` 常量（core 测试 ACreatedEvent.java:44 模式）。

## 优点（值得借鉴的）

- `EventType` 用不可变值对象包装类型字符串（`@Immutable`、≤255，EventType.java:31-51），避免裸 `Class` 在序列化/跨服务场景的歧义。
- 元数据五件套在 `Event` 接口一刀切齐（Event.java:36-69），correlationId/causationId/eventTimestamp 无需业务方自行拼装。
- `respondTo` 因果构造器（jackson AbstractEvent.java:81-83）：一行同时落 correlation/causation，语义清晰。
- `DomainEvent` 纯接口 + 泛型 `<ID extends EntityId>`：业务事件可自由选择基类或直接实现。
- null-safe 的 `getAggregateVersionInteger()`（DomainEvent.java:56-61）：消费侧免判空样板。

## 缺点（应规避的）

- **core 完全没有分发机制**：全仓库无 DomainEventPublisher/EventPublisher，进程内订阅无 SPI，事件只能经 Repository 落库或外部总线。
- `AbstractDomainEvent` 不在 core：jackson/jaxb/jsonb 三模块各一份拷贝（Builder 也三份），基类与序列化格式耦合。
- `respondTo` 构造器直接透传 `getCorrelationId()`（jackson AbstractEvent.java:82），前置事件未携带时链路静默断链，无兜底。
- 核心契约绑第三方：接口标 `jakarta.validation` 注解、`EventType` 继承 objects4j `AbstractStringValueObject`（EventType.java:22-32）。
- 事件类型靠每事件手写 `EVENT_TYPE` 常量返回（ACreatedEvent.java:44），无框架级自动派生/缓存，样板多。

## ddd4j 自研决策

> **结论：ddd4j-core 现有 `DomainEvent` 已对齐 fuin 契约，并在分发、类型派生、因果兜底三处超出；本篇零新增借鉴。**

- **借鉴（新增）**：无——fuin 的 API 形态 ddd4j 已全部覆盖。
- **已对齐（对等）**：
  - 元数据五件套：ddd4j `Event`（`io/ddd4j/core/ddd/event/Event.java:9-36`）与 fuin Event.java:29-69 逐方法对等，correlationId/causationId/eventTimestamp 均在（fuin 并不缺这三个字段）；
  - 聚合定位：entityIdPath/aggregateVersion/getAggregateVersionInteger（ddd4j DomainEvent.java:97-101、:236-249 ↔ fuin DomainEvent.java:37-61）；
  - 因果构造器 `DomainEvent(EntityIdPath, Event respondTo)`（ddd4j DomainEvent.java:139-145 ↔ fuin jackson AbstractDomainEvent.java:77-80）。
- **超出**：
  - **分发 SPI 整套**：`publish()`（DomainEvent.java:318-322，经 `Contexts` 查找注入）+ `DomainEventPublisher`（publish/publish(Object)/publishAll，DomainEventPublisher.java:18-48）+ `NoopDomainEventPublisher` 单例兜底（NoopDomainEventPublisher.java:20-35）——fuin 一样都没有；
  - **EventType 自动派生 + ClassValue 缓存**（DomainEvent.java:54-59、:163-165）：零手写常量、零重复分配；fuin 每事件手写 `EVENT_TYPE`；
  - **因果兜底**：correlationId 为 null 时复制前置事件 eventId（DomainEvent.java:142-143），链条永不断；fuin 直接透传；
  - **多租户/策略过滤**：`tenantIn`/`supports`（DomainEvent.java:279-306），fuin 无对应物。
- **不借鉴**：
  - `AbstractDomainEvent` 三模块拷贝模式——ddd4j 单一抽象基类（DomainEvent.java:49），Jackson 注解内联，无 Builder 样板；
  - `jakarta.validation`/objects4j 依赖——违背 ddd4j-core 零第三方依赖（ADR-0002）；
  - 手写 `EVENT_TYPE` 常量模式——ClassValue 已替代。

## 落地计划

- [ ] 阶段 2：验证 DomainEvent 六字段（event-id/event-timestamp/correlation-id/causation-id/entity-id-path/aggregate-version）在 ES JSON 序列化/反序列化往返无损。
- [ ] 阶段 2：为 `EventType` ClassValue 缓存（DomainEvent.java:54-59）补单测/基准，防回归。
- [ ] 阶段 3（event-store SPI）：`StoredEvent.payload` 必须保留 correlationId/causationId/eventTimestamp 三元追踪字段。
- [ ] Task 1.10：ADR 引用本文档「分发侧超出 fuin」结论（`NoopDomainEventPublisher` 为 ddd4j 独有）。
