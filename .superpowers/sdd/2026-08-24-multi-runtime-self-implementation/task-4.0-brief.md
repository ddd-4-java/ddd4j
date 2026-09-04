# Task 4.0 Brief（控制器插入·承重前置）— core DomainEvent Jackson 回读修复

## 背景与证据（Task 3.3 评审裁定 REAL）
EventPayloadSerializer 序列化任何真实 DomainEvent 后**无法回读**：① `EntityIdPath` 两个构造器均不可用于字符串绑定（无 @JsonCreator；@JsonValue asString() 序列化为 `Type:value/Type:value` 却无解析回路）⇒ `entity-id-path` 绑定失败丢聚合身份；② `DomainEvent.getEventType()`（:159-163 getter-only @JsonProperty("event-type")）无 setter/creator，默认 FAIL_ON_UNKNOWN_PROPERTIES=true ⇒ UnrecognizedPropertyException。core 无参构造器 javadoc 明言「Jackson 反序列化 + 事件回放时使用」——本属 core 意图，系疏漏。阶段 4 JpaEventStore.read 首个真实载荷即撞。

## 交付（2 主文件 + 1 测试文件）

### 1. `EntityIdPath` 加 @JsonCreator 静态工厂（io.ddd4j.core.ddd.event）
```java
@JsonCreator
public static EntityIdPath valueOf(String path) { ... }
```
- 解析契约（与 asString() 对偶）：按 `PATH_SEPARATOR`("/") 分段，每段按**首个** `:` 切成 type 文本与 value 文本，重建为 `new StringEntityId(value)`。
- 空串/空白/无分隔段/段内无 `:` → IllegalArgumentException（消息含出错段原文——对齐 02 篇记录的 fuin 敌手缺陷）。
- javadoc 必须写明**限制**：回读段一律重建为 StringEntityId（保留 type 文本与 value，asString/asTypedString 语义不变）；自定义 EntityId 实现类不还原为其原始类（类型注册表留待后续 ADR）；值内含 `/` 或 `:` 的 ID 不受支持（typed-string 惯例约束）。
- 解析逻辑私有化（可测的包私有 parse 或内联均可），保持 final 类不可变风格。

### 2. `DomainEvent.getEventType()` 改只读属性
在 `@JsonProperty("event-type")`（:162 一带）追加 `access = JsonProperty.Access.READ_ONLY`。效果：序列化仍输出 event-type；反序列化跳过绑定（值由 ClassValue 从 getClass() 派生，天然正确）。javadoc 一句注明。

### 3. 回读测试（core 侧，暴露缺口的对证）
在 `ddd4j-core/src/test/java/io/ddd4j/core/ddd/event/` 新增 `DomainEventRoundTripTest`（或扩展 DomainEventJsonTest——新类更清晰）：
- fixture：**不加** @JsonIgnoreProperties 的朴素 SampleEvent extends DomainEvent<StringEntityId>（字段式 payload，如 String orderId/long amount）。
- 用例 a：serialize→deserialize round-trip（mapper：`JsonMapper.builder().findAndAddModules().build()`，**保持默认 FAIL_ON_UNKNOWN_PROPERTIES=true** 以证明 event-type 不再炸）→ 断言 getEntityIdPath().asString() 等于原值、last().asString() 等于原 id、payload 字段等值、getEventType().asString() 等于事件简单名。
- 用例 b：EntityIdPath.valueOf 合法解析（单段/多段）与非法输入（空/无冒号/空段）抛 IAE。
- 用例 c：valueOf(asString()) 幂等（path.asString() 重建后再 asString 相等）。

## 门禁
`./mvnw -pl ddd4j-core -am test` 全绿（基线 256 → ≥260）；ArchUnit 10/10（jackson-annotations 已在 allowlist，@JsonCreator/@JsonProperty 无新依赖）；顺带验证 event-store 模块 15 测试仍绿（`-pl ddd4j-core,ddd4j-data/ddd4j-data-event-store -am`）。

## 范围与提交
只动上述 3 文件（EntityIdPath/DomainEvent/新测试）。**不改** EventPayloadSerializer 及其 fixture（其 @JsonIgnoreProperties workaround 在 core 修复后自然过时——顺手在该 fixture javadoc 加一句「core 已于 Task 4.0 修复，本 workaround 可移除」为可选，不强求）。单 commit：`fix(core): DomainEvent Jackson 回读——EntityIdPath @JsonCreator + event-type READ_ONLY`。

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-4.0-report.md`。Reply ≤15 lines.
