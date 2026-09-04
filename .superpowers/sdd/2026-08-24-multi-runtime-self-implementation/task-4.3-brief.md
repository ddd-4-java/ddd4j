# Task 4.3 Brief — JpaEventStore 实现（Spring Data JPA 版 EventStore）

## 交付
`ddd4j-data/ddd4j-data-event-store-jpa/src/main/java/io/ddd4j/data/eventstore/jpa/JpaEventStore.java`：
- `@Component`，构造器注入 `(SpringDataStoredEventRepository repository, EventPayloadSerializer serializer)`（后者来自 io.ddd4j.data.eventstore.jackson，3.3 落地；纯类无注解，由集成方供 Bean——javadoc 说明）。
- `@Transactional append(String, AggregateRootId, List<? extends DomainEvent<?>>, long)`：requireNonNull(events)→`findCurrentVersion(type, id.asString())`→不等 expected 抛 `AggregateVersionConflictException`→逐事件 version++ 构造 StoredEventEntity（eventId=event.getEventId().asString()、eventType=event.getClass().getName()、payload=serializer.serialize、correlationId/causationId 空安全取 asString、createdAt=now）→save。javadoc 注明乐观锁+uk 约束双保险、对照 ADR-0005。
- `read/read(from,to)/readAll(fromPosition,limit)`：查 Repository→map `toStoredEvent`。readAll 的 limit 用 stream().limit(limit)（sketch 同）。
- 私有 `toStoredEvent(StoredEventEntity)`：
  - `Class.forName(entity.getEventType())` 强转 `Class<? extends DomainEvent<?>>`，ClassNotFoundException→IllegalStateException。
  - **坑①修正**：`EventId.valueOf(entity.getEventId())`（非 `new EventId(String)`——不存在；valueOf 已空安全）。
  - **坑②修正**：aggregateId 需 `AggregateRootId` 接口实例——私有 record `StringAggregateRootId(String value) implements AggregateRootId`（getType 返回 `new StringEntityType("String")`、asString、asTypedString 三方法照 StringEntityId.java:38-49 抄）。**勿**用 StringEntityId（只实现 EntityId）。
  - payload=serializer.deserialize(entity.getPayload(), eventType)；correlationId/causationId 经 valueOf（可空）。

## 单元测试（无 Spring 上下文，Mockito——父级全局已供 mockito-core）
`src/test/java/io/ddd4j/data/eventstore/jpa/JpaEventStoreTest.java`：
- fixture：SampleEvent extends DomainEvent<StringAggregateRootId …>（AggregateRootId 直接用受测的 record 或测试自有 record；payload 序列化 mock 掉——`when(serializer.serialize(any())).thenReturn("{}")` / deserialize 返回同一事件实例）。
- 用例 a 乐观锁冲突：findCurrentVersion 返回 3，append(expected=0)→抛 AggregateVersionConflictException 且 4 字段值正确、**未调用 save**（verify(repository, never()).save(any())）。
- 用例 b 顺序追加：expected 与 actual 相等、3 事件→save 3 次、版本 1/2/3 递增（ArgumentCaptor 捕获实体断言 version/eventId/eventType/payload）。
- 用例 c toStoredEvent 重建：read 路径 mock findBy… 返回含已知字段的实体→断言 StoredEvent 各访问器（aggregateId.asString()、payload 同一实例、correlationId 空安全）。
- 用例 d 未知 eventType：实体 eventType="no.such.Clazz"→read 抛 IllegalStateException（cause ClassNotFoundException）。
（StoredEventEntity 需可构造：无 setter 的 position 之外都有 setter，测试用 setter 组装。）

## 门禁
`./mvnw -pl ddd4j-data/ddd4j-data-event-store-jpa -am install` BUILD SUCCESS（模块测试 = 3 ArchUnit + 新 4 用例 ≥7）；ArchUnit allowlist 已含 stereotype/transaction——@Component/@Transactional 无需改规则。

## 提交
单 commit：`feat(data): JpaEventStore 实现（乐观锁+Jackson 载荷重建）`

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-4.3-report.md`。Reply ≤15 lines.
