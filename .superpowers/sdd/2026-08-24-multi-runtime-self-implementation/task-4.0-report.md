# Task 4.0 Report — core DomainEvent Jackson 回读修复（阶段 4 前置·承重）

Status: **DONE** · Commit: `93c1116f` (`fix(core): DomainEvent Jackson 回读——EntityIdPath @JsonCreator + event-type READ_ONLY`, on `feature/2.0.x`, parent `e3fcc5af`) · 3 files, +189/−1.

## 交付物

1. **`ddd4j-core/src/main/java/io/ddd4j/core/ddd/event/EntityIdPath.java`**
   - 新增 `@JsonCreator public static EntityIdPath valueOf(String path)`（约 :70）+ 私有 `parseSegment(String, String)` + 私有常量 `TYPE_SEPARATOR = ":"`。
   - 解析契约（与 `asString()` 对偶）：按 `PATH_SEPARATOR` 分段（`split("/", -1)` 保留空段以拒绝而非静默吞掉）；每段按**首个** `:` 切 type/value，重建为 `new StringEntityId(value)`。
   - IAE 条件：整串空/空白、空段（含尾部 `/`）、段内无 `:`、空 type、空 value。消息同时含**出错段原文**与完整 path（对齐 02 篇 fuin 敌手缺陷教训）。
   - **限制 javadoc 位置：`valueOf` 方法 javadoc 的「限制」段（EntityIdPath.java :66-73 一带）**，逐条写明：① 回读段一律重建为 `StringEntityId`（保留 value；重序列化后 type 统一为 `String:`），自定义 EntityId 实现类不还原为原始类——类型注册表留待后续 ADR；② 值内含 `/` 或 `:` 的标识不受支持（typed-string 惯例约束）。
2. **`ddd4j-core/src/main/java/io/ddd4j/core/ddd/event/DomainEvent.java`**
   - `getEventType()`（:165）改 `@JsonProperty(value = "event-type", access = JsonProperty.Access.READ_ONLY)`；javadoc 三句注明：序列化仍输出、反序列化跳过绑定、值由 `ClassValue` 从 `getClass()` 派生。
3. **`ddd4j-core/src/test/java/io/ddd4j/core/ddd/event/DomainEventRoundTripTest.java`**（新，5 用例）
   - fixture：朴素 `SampleEvent extends DomainEvent<StringEntityId>`（`String orderId` / `long amount` 字段式 payload）——**无** `@JsonIgnoreProperties`、无任何 Jackson workaround。
   - a) round-trip：mapper `JsonMapper.builder().findAndAddModules().build()` 保持默认 `FAIL_ON_UNKNOWN_PROPERTIES=true`（用例 b 显式断言该默认未放宽）；断言 `entity-id-path`/`last()`/payload/eventId/aggregateVersion/eventType/时间戳 instant 全等。
   - b) `valueOf` 合法（单段/多段）+ 非法（空/空白/无冒号/空段/空 type/空 value）→ IAE（消息含出错段原文）。
   - c) `valueOf(asString())` 幂等。

## TDD 证据（verbatim）

**RED ①（未修复，probe 运行）**：
```
com.fasterxml.jackson.databind.exc.InvalidDefinitionException:
Cannot construct instance of `io.ddd4j.core.ddd.event.EntityIdPath` (no Creators, like default constructor, exist): no String-argument constructor/factory method to deserialize from String value ('String:order-1')
```
（`valueOf` 尚不存在时测试直接编译失败：`找不到符号: 方法 valueOf(java.lang.String)`。）

**RED ②（仅修复 EntityIdPath 后，event-type 缺口暴露）**：
```
com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException:
Unrecognized field "event-type" (class io.ddd4j.core.ddd.event.DomainEventRoundTripTest$SampleEvent), not marked as ignorable (10 known properties: "supportKeys", "correlation-id", "amount", "orderId", "aggregate-version", "result", "event-id", "causation-id", "event-timestamp", "entity-id-path")
```

**GREEN**：`Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in io.ddd4j.core.ddd.event.DomainEventRoundTripTest`

## 门禁

| 门禁 | 结果 |
|---|---|
| `./mvnw -pl ddd4j-core -am test` | **BUILD SUCCESS，Tests run: 261, Failures: 0, Errors: 0**（基线 256 + 新增 5 ≥ 260） |
| ArchUnit `CoreIndependenceTest` | **Tests run: 10, Failures: 0**（10/10；jackson-annotations 原有 allowlist，零新依赖） |
| `./mvnw -pl ddd4j-core,ddd4j-data/ddd4j-data-event-store -am test` | **BUILD SUCCESS**；event-store **15**（StoredEventTest 7 + IndependenceTest 3 + EventPayloadSerializerTest 5）全绿 |
| core main 零 `org.slf4j` | `grep -r org.slf4j ddd4j-core/src/main/java` → 0 命中 |

## 自审与备注

- 范围守住 3 文件；EventPayloadSerializer 及其 fixture 未动（brief 可选项未取）——其 `@JsonIgnoreProperties({"entity-id-path", "event-type"})` workaround 现已过时但无害（15 绿为证），移除留给后续任务/ADR。
- 断言细节：时间戳按 `toInstant()` 断言（Jackson 数字时间戳不携带 ZoneId，回读统一 UTC——与 EventPayloadSerializerTest 既有注释一致）。
- 阶段 4 JpaEventStore.read 首个真实载荷回读的两大阻断已消除；聚合身份（entity-id-path）回读后完整保留。
