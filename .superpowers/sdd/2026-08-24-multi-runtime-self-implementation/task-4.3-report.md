# Task 4.3 Report — JpaEventStore 实现

**Status**: COMPLETE · Commit `65455109` on `feature/2.0.x`（单 commit，2 文件 +386 行）

## 交付物

| 文件 | 说明 |
| --- | --- |
| `ddd4j-data/ddd4j-data-event-store-jpa/src/main/java/io/ddd4j/data/eventstore/jpa/JpaEventStore.java` | `@Component` 实现 EventStore SPI，构造器注入 `(SpringDataStoredEventRepository, EventPayloadSerializer)`；`append` 标注 `@Transactional` |
| `ddd4j-data/ddd4j-data-event-store-jpa/src/test/java/io/ddd4j/data/eventstore/jpa/JpaEventStoreTest.java` | 4 用例纯 Mockito 单测（无 Spring 上下文） |

## 两个预埋坑的修正位置（均在 `toStoredEvent`，JpaEventStore.java）

1. **坑① `EventId.valueOf` 而非 `new EventId(String)`**：计划 sketch 用 `new EventId(entity.getEventId())`——该构造器不存在（仅有 `EventId()` / `EventId(UUID)`）。实际实现：`EventId.valueOf(entity.getEventId())`，correlationId/causationId 同经 `valueOf`（`StrKit.isBlank` 空安全，null 列直接得 null `EventId`）。
2. **坑② 私有 record `StringAggregateRootId` 而非 `StringEntityId`**：`StoredEvent` 构造器要求 `AggregateRootId`，而 `StringEntityId` 只实现 `EntityId`。实际实现：文件尾部 `private record StringAggregateRootId(String value) implements AggregateRootId`，`getType()` 返回静态常量 `new StringEntityType("String")`，`asString`/`asTypedString`（含 `@JsonValue`）照 StringEntityId.java:38-49 逐行对齐。

## 实现要点

- `append`：`requireNonNull(events)` → `findCurrentVersion(type, id.asString())` → 不等 expected 抛 `AggregateVersionConflictException`（四字段）→ 逐事件 `version++` 组装实体（eventId/eventType/payload/空安全 correlationId·causationId/createdAt=循环前取 now）→ `save`。javadoc 注明**乐观锁＋`uk_aggregate_version` 唯一约束双保险**（ADR-0005）及同事务整体回滚。
- 类 javadoc 注明 `EventPayloadSerializer` 为纯类无容器注解，集成方自行供 Bean（附 Spring `@Bean` 示例文字）。
- `read`/`read(from,to)`/`readAll(fromPosition,limit)`：Repository 查询 → `map(this::toStoredEvent)`；readAll 用 `stream().limit(limit)`（与 sketch 一致）；返回 `toList()` 不可变列表。
- `resolveEventType`：`Class.forName` 强转 `Class<? extends DomainEvent<?>>`，`ClassNotFoundException` → `IllegalStateException`（cause 保留）。
- **计划外小修**（第三次门禁跑通所需）：`position` 列无 setter（DB 生成），手工组装的瞬态实体读回时 `getPosition()==null` 拆箱 NPE——改用 `Objects.requireNonNullElse(entity.getPosition(), 0L)` 并在 javadoc 说明"瞬态实体按 0"。持久化读回路径（Task 4.4 IT）不受影响。

## 测试用例映射（brief a–d）

| Brief | 测试方法 | 断言 |
| --- | --- | --- |
| a 冲突短路 | `append_版本冲突_应抛AggregateVersionConflictException且不调用save` | 异常四字段（type/id/expected=0/actual=3）＋`verify(repository, never()).save(any())` |
| b 顺序追加 | `append_顺序追加_应逐事件递增版本并组装实体` | save×3；ArgumentCaptor 捕获断言 version 1/2/3、eventId 逐事件对应、eventType、payload="{}" |
| c 重建 | `read_应重建StoredEvent且correlationId空安全` | aggregateId.asString()、aggregateId.getType()=="String"、payload `isSameAs`、correlationId null、causationId 解析、position=0（瞬态默认）、timestamp |
| d 未知类型 | `read_未知eventType_应抛IllegalStateException且cause为ClassNotFoundException` | "no.such.Clazz" → ISE，消息含类名，cause 为 CNFE |

fixture：`SampleEvent extends DomainEvent<TestAggregateId>`；测试自有 `TestAggregateId` record（受测类内 record 为 private 无法引用，契约一致）；serializer mock（serialize→"{}"；deserialize 用 `doReturn` 回同一实例——`when().thenReturn` 对 `DomainEvent<?>` 返回触发 javac 捕获转换不变性编译错）。

## 门禁

`./mvnw -pl ddd4j-data/ddd4j-data-event-store-jpa -am install` → **BUILD SUCCESS**。

- 本模块测试 **7 = 3 ArchUnit**（EventStoreJpaModuleIndependenceTest，allowlist 含 stereotype/transaction，未改规则）**+ 4 新用例**（JpaEventStoreTest），全绿。
- 反应堆含上游 `-am` 模块（annotation/kit/core/data-event-store）全绿。

## 测试依赖解析（父级 ddd4j-dependencies 全局 `<dependencies>`，非本模块 pom）

`org.mockito:mockito-core:5.19.0:test`、`org.mockito:mockito-junit-jupiter:5.23.0:test`、`org.junit.jupiter:junit-jupiter(-api/-engine/-params):6.1.0:test`（聚合 jar 经 BOM 链解析为 6.0.3，api/engine 均 6.1.0，运行正常）、`org.assertj:assertj-core:3.27.7:test`、`com.tngtech.archunit:archunit-junit5:1.4.2:test`。本模块 pom 未新增任何依赖。

## Self-review 残留关注

- `readAll` 全量 `findByPositionGreaterThanEqual` 后内存 limit——大流场景缺分页查询；与 sketch 一致，优化留给后续任务。
- `deserialize` 的 `doReturn` 用法绕开泛型捕获是 Mockito 处理通配符返回的标准做法，已在测试内注释说明。
