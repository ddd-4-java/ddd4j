# Task 3.3 Report：EventPayloadSerializer（Jackson 多态序列化）

**Status**: DONE
**Commit**: `e3fcc5af` feat(data): EventPayloadSerializer Jackson 多态序列化（单 commit，3 文件 +241 行）
**Gate**: `./mvnw -pl ddd4j-data/ddd4j-data-event-store -am install` → BUILD SUCCESS；模块测试 **15 个**（EventPayloadSerializerTest 5 + StoredEventTest 7 + ArchUnit 3 规则全绿，jackson 新包在 allowlist 内未触发违规）。

## Files

- `ddd4j-data/ddd4j-data-event-store/src/main/java/io/ddd4j/data/eventstore/jackson/EventPayloadSerializer.java`（新增）
- `ddd4j-data/ddd4j-data-event-store/src/test/java/io/ddd4j/data/eventstore/jackson/EventPayloadSerializerTest.java`（新增，5 用例）
- `ddd4j-data/ddd4j-data-event-store/pom.xml`（+ jackson-datatype-jsr310 test 作用域，BOM 管无版本，先例 ddd4j-core）

## Sketch 关键缺陷修正（Controller 要求，已落实）

sketch 在构造器直接对**传入的** ObjectMapper 调 `activateDefaultTyping`，会污染调用方共享 mapper（如 Spring 全局 mapper 被强加多态 typing）。已改为：

```java
this.objectMapper = Objects.requireNonNull(source, "source must not be null").copy();
this.objectMapper.activateDefaultTyping(...);  // 只配置副本
```

javadoc 明确「构造后传入的 source mapper 保持不受影响」，并由测试 `constructionLeavesSourceMapperUntouched` 断言（source mapper 输出不含 `@class`）。

其余对齐：typing 配置照 sketch 逐字（`BasicPolymorphicTypeValidator.allowIfBaseType(DomainEvent)` + `NON_FINAL` + `As.PROPERTY`）；异常 catch `com.fasterxml.jackson.core.JacksonException`（对齐 ddd4j-kit JsonKit 风格，先读了 kit 确认）、包 `IllegalStateException` 且 cause 保留（测试断言 `hasCauseInstanceOf(JacksonException.class)`）。

## 测试（5 用例，4 必须 + 1 契约补充）

1. **round-trip**（含 ZonedDateTime）：基 mapper 注册 `JavaTimeModule`（jsr310 test 作用域），serializer `copy()` 携带该 module；断言具体类还原、业务字段、`eventId`、`source()` 与时间戳 instant 级相等。
2. **@class 多态标记**：序列化 JSON 含 `"@class":"...EventPayloadSerializerTest$OrderPlacedEvent"`（As.PROPERTY 验证）。
3. **源 mapper 隔离**：构造 serializer 并触发 typing 路径后，source mapper 序列化同一事件不含 `@class`。
4. **坏 JSON → IllegalStateException**：截断 JSON 触发 `JsonEOFException`（JacksonException 子类），断言消息与 cause。
5. null source → NPE（构造器契约）。

## ⚠ 重要发现（实现前用真实 jar 做了 2.22.2 行为探针）

**`DomainEvent` 完整 JSON 回读目前被 ddd4j-core 阻断**（与本 serializer 无关，但阶段 4 JPA 实现读回事件时必然撞上）：

- `EntityIdPath` 只有 `@JsonValue` 序列化，**无 String 反序列化 creator** → `InvalidDefinitionException: no String-argument constructor/factory method`（探针实测；`EventId`（UUID 参 ctor）经 delegating-creator 协变可正常回读）。
- `getEventType()` 是只读属性，回读时在默认 `FAIL_ON_UNKNOWN_PROPERTIES` 下报 `UnrecognizedPropertyException`。
- 测试 fixture 以 `@JsonIgnoreProperties({"entity-id-path", "event-type"})` 绕开（fixture 注释已说明），`entityIdPath` 由无参构造器重建同值。
- ZonedDateTime 经 Jackson 数字时间戳回读 zone 统一为 UTC（`equals` false），instant 纳秒级保真——断言用 `toInstant()` 相等。

**建议 follow-up task（ddd4j-core）**：给 `EntityIdPath` 补 `@JsonCreator` 静态工厂（解析 `asTypedString`）并处理只读 `event-type`，届时可移除 fixture 的 ignore 妥协。

## Self-review

- ArchUnit 3 规则仍绿（新类只依赖 io.ddd4j / java / jackson 三件套）。
- jsr310 仅 test 作用域，主代码 java.time 序列化仍由 JsonKit 承担（pom 注释同 ddd4j-core 先例）。
- 提交只含 3 个模块文件，未夹带 docs/ 计划文件与 .superpowers 工件。
