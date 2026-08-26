# 2026-08-25 EventStore SPI 硬化与最近 24h 代码变更风险修复

> 背景：2026-08-24 ~ 2026-08-25 代码审查发现 ddd4j 主仓新增的 EventStore / Jackson / AggregateRoot
> 模块存在 5 个高置信风险点。本次硬化对应 5 项修复，配套文档化决策与未来 TODO。

---

## 风险清单回顾

| 编号 | 风险等级 | 描述 | 修复归属改动 |
|---|---|---|---|
| #1 | 🔴 高 | EntityIdPath 反序列化丢失自定义 EntityId 类型 | 改动 1 |
| #2 | 🔴 高 | EntityIdPath 值内含 `/` 或 `:` 的标识无法 round-trip | 改动 2 |
| #3 | 🔴 高 | AggregateRoot.apply() 反射调用未处理 JDK 17+ 模块系统 | 改动 3 |
| #4 | 🔴 高 | JpaEventStore.append() 逐条 save，无批量 INSERT | 改动 5 |
| #5 | 🔴 高 | EventPayloadSerializer.activateDefaultTyping 存在多态反序列化风险 | 改动 4 |

---

## 改动 1：EntityIdRegistry + EntityIdPath 反序列化

**决策**：引入 `EntityIdRegistry` 注册表 + 兜底 `StringEntityId` 双轨方案。

**原因**：
- 早期反序列化路径直接以 `StringEntityId` 重建所有段，自定义 `OrderId` / `CustomerId`
  等业务类型在事件溯源回放后丢失类型信息（`OrderId:o1` → `String:o1`）。
- 完全切换到注册表会引入「未注册类型即抛异常」的强约束，对未升级业务代码不兼容。
- 双轨方案保留向后兼容：未注册类型走 `StringEntityId` 兜底，注册类型正确还原。

**使用方式**：

```java
// 启动期注册（Spring: @PostConstruct；Quarkus: @Startup；Javalin: service init）
EntityIdRegistry.register("OrderId", value -> new OrderId(OrderIdType.INSTANCE, value));
EntityIdRegistry.register("CustomerId", CustomerId::new);

// 反序列化路径 "OrderId:o-1/CustomerId:c-9" 自动还原为 OrderId + CustomerId
EntityIdPath path = EntityIdPath.valueOf("OrderId:o-1/CustomerId:c-9");
```

**API 契约**：
- `@JsonCreator public static EntityIdPath valueOf(String path)` 与 `asString()` 对偶
- 空段 / 缺 `:` / 空 type / 空 value 抛 `IllegalArgumentException`，消息含原文
- 未注册类型回退 `StringEntityId`（与历史行为一致）

---

## 改动 2：EntityIdPath 段内分隔符转义

**决策**：在 `asString()` 序列化时对每段做转义，`valueOf(String)` 反序列化时先反转义再做段切分。

**原因**：
- 早期实现对值内的 `/` 和 `:` 不做任何处理，导致业务 ID 含这些字符时反序列化失败
- URL 路径编码惯例：`\` → `\\`，`/` → `\/`，`:` → `\:`，与 RFC 3986 风格一致

**示例**：

```java
// 修复前：值为 "o:1" 时 asString() = "OrderId:o:1"，valueOf() 会切出 ["OrderId", "o", "1"]
// 修复后：asString() = "OrderId:o\\:1"，valueOf() 正确还原为 OrderId("o:1")
```

---

## 改动 3：AggregateRoot.apply() 反射加固

**决策**：精确捕获 `InvocationTargetException` 与 `IllegalAccessException`，业务异常透传，受检异常包装，模块系统问题给出明确指引。

**原因**：
- 早期 `catch (Exception e)` 会把 `InvocationTargetException` 的业务异常也包装为 `BizRuntimeException`，
  导致原始堆栈与异常类型丢失，调用方无法 catch 业务异常做重试 / 补偿
- `IllegalAccessException` 在 JDK 17+ 模块系统下意味着 `setAccessible(true)` 被拒，
  用户错误信息需要明确指引 `module-info.java opens` 或 `--add-opens` 解决方案

**新增异常路径**：
- `InvocationTargetException` 解包：RuntimeException / Error 直接透传，受检异常包 `BizRuntimeException`
- `IllegalAccessException`：错误消息含 `opens` / `--add-opens` 解决方案提示
- 其他反射异常（如 `InaccessibleObjectException`）：保持 `BizRuntimeException` 包装

---

## 改动 4：EventPayloadSerializer 移除多态反序列化（安全收紧）

**决策**：移除 `activateDefaultTyping` 与 `BasicPolymorphicTypeValidator`，改由调用方通过
`deserialize(String, Class)` 显式传入目标类型。

**原因**：
- Jackson `activateDefaultTyping` 启用后会在 JSON 中写入 `@class` 多态标记，反序列化时
  据此还原任意类。即便用 `BasicPolymorphicTypeValidator.builder().allowIfBaseType(DomainEvent.class).build()`
  限定基类型，`DomainEvent` 子类若自身有危险的 `@JsonCreator` 仍可被攻击者利用。
- 早期版本的 `deserialize(String, Class<? extends DomainEvent<?>> eventType)` 签名已包含
  `eventType` 参数，多态信息其实是冗余的——调用方（如 `JpaEventStore`）已经从持久化的
  `event_type` 列获取到类型名，完全可以直接传入。
- 这是符合「纵深防御」的安全收紧，不影响现有调用方（`JpaEventStore` / `PanacheEventStore`）。

**API 变化**：
- 序列化产物不再含 `@class` 标记（breaking change 仅对依赖 `@class` 解析的旧 reader 影响）
- 反序列化完全依赖调用方传入的 `eventType`

**测试 fixture 同步修复**：
- `EventPayloadSerializerTest` 删除 `serializedJsonCarriesPolymorphicClassMarker` 测试
- 删除 `OrderPlacedEvent` 上的 `@JsonIgnoreProperties({"entity-id-path", "event-type"})` 绕开
- 新增 `roundTripPreservesEntityIdPath` 与 `roundTripPreservesEventType` 测试验证回读正确

---

## 改动 5：JpaEventStore 批量插入优化

**决策**：循环构造 entity 后 `entityManager.flush()` + `entityManager.clear()`，配合 Hibernate `batch_size` 配置。

**原因**：
- 早期循环 `repository.save(entity)` 仅依赖 Hibernate 自动 batching，但当前默认配置下
  Hibernate 不会自动合并 INSERT（需要显式 `hibernate.jdbc.batch_size` 配置）
- 调用方在 `JpaEventStoreIT` 一次性追加 50 条事件时实际产生 50 条独立 INSERT，事务开销大

**使用方式**：

```properties
# application.yml（参考 ddd4j-event-store-batch.properties）
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
```

**未配置 batch_size 时的行为**：每次 `flush()` 仍会发出单条 INSERT，行为正确，但无性能提升。

---

## 改动 6：ddd4j-quarkus CI 注释

**决策**：在 `ci.yml` 顶部加入版本对齐 TODO 说明，不修改 install 列表。

**原因**：
- 当前 `ci.yml` checkout `feature/2.0.x` 但 `ddd4j-quarkus/pom.xml` 声明 `ddd4j.version=3.0.x.20260630-SNAPSHOT`，
  存在版本线漂移
- 完全自动化的 `-pl -am` 重构需要进一步确认所有 ddd4j-quarkus 模块的传递依赖，超出本次修复范围
- 通过显式 TODO 注释将问题留作独立工单，避免在本次硬化中引入更大的版本对齐风险

---

## 改动 7：补充说明与文档（即本文件）

- 本 spec 描述决策与原因
- 不替换既有 ADR 文档（项目无 ADR 习惯）

---

## 测试与验证

```bash
# 改动 1+2+3
./mvnw -B -Denforcer.skip=true -pl ddd4j-core test

# 改动 4（事件存储 SPI 模块）
./mvnw -B -Denforcer.skip=true -pl ddd4j-data/ddd4j-data-event-store test

# 改动 5
./mvnw -B -Denforcer.skip=true -pl ddd4j-data/ddd4j-data-event-store-jpa verify
```

---

## 不在本次范围

- ddd4j-boot 版本线对齐（chore 性质，无功能影响）
- ddd4j-quarkus pom.xml 与 ci.yml 版本线一致性（拆分为独立 TODO）
- ddd4j-data-event-store-panache 孤儿目录（已确认无 pom.xml，清理属独立工单）
- AggregateRoot 注解式派发（与本次风险无关）
- ADR 体系建立（项目无 ADR 习惯，不引入）
