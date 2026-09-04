# Task 3.2 Report: EventStore SPI + StoredEvent + AggregateVersionConflictException

**Status:** DONE
**Commit:** `712e5112` — `feat(data): EventStore SPI + StoredEvent + AggregateVersionConflictException`（feature/2.0.x，基于 3e683c91）
**Gate:** `./mvnw -pl ddd4j-data/ddd4j-data-event-store -am install` → **BUILD SUCCESS**（7/7 reactor 模块绿）

## 交付文件（6，含 pom 修改）

| 文件 | 说明 |
| --- | --- |
| `ddd4j-data/ddd4j-data-event-store/src/main/java/io/ddd4j/data/eventstore/EventStore.java` | 四方法 SPI：`append(String, AggregateRootId, List<? extends DomainEvent<?>>, long)` / `read×2`（全量 + 版本区间）/ `readAll(long fromPosition, int limit)`。javadoc：乐观锁→`AggregateVersionConflictException`、实现清单指向 `{jpa,panache,jdbi,r2dbc}`（r2dbc 标注走 AsyncEventStore 单轨）、与 esc-api 五项差异引 05 篇 |
| `ddd4j-data/ddd4j-data-event-store/src/main/java/io/ddd4j/data/eventstore/StoredEvent.java` | final 类，9 字段照 brief；5 个对象参数 `Objects.requireNonNull`（version/position 为 primitive 不可 null，见 brief 修正③）；访问器 `eventId()` 等扁平风格；javadoc 含全局 position 与流内 version 的语义区分 |
| `ddd4j-data/ddd4j-data-event-store/src/main/java/io/ddd4j/data/eventstore/AggregateVersionConflictException.java` | extends RuntimeException + `@Serial serialVersionUID`（对齐 EventId 风格）；4 final 字段 + 全参构造（message 用 sketch 的 format 串）+ 4 访问器；javadoc 引 ADR-0005（含与 esc-api `WrongExpectedVersionException` 的对照） |
| `ddd4j-data/ddd4j-data-event-store/src/test/java/io/ddd4j/data/eventstore/StoredEventTest.java` | 7 用例：5 项非空对象参数 NPE（@ParameterizedTest + @MethodSource）、correlationId/causationId 可空合法、9 访问器返回构造原值。测试夹具（TestAggregateRootId record / TestEvent）照 AggregateRootApplyTest 风格 |
| `ddd4j-data/ddd4j-data-event-store/src/test/java/io/ddd4j/data/eventstore/arch/EventStoreModuleIndependenceTest.java` | @ArchTest 字段风格照 CoreIndependenceTest，3 规则：`no_spring_in_event_store` / `no_jakarta_persistence_in_event_store` / `module_deps_allowlist`（io.ddd4j.. / java.. / jackson annotation|core|databind / lombok） |
| `ddd4j-data/ddd4j-data-event-store/pom.xml` | **删除** 3.1 的 maven-jar-plugin `skipIfEmpty=false` 覆盖（源码非空后失效，build 段整体移除） |

## Brief 修正记录

1. **跳过 sketch Step 4 的 `EventStoreContractTest`/`@TestTemplate`/`EventStoreInvocationProvider`**（按 controller 指令）：provider 是坏桩（sketch 中 `CollectionsList.ofEmptyList()` 甚至无法编译，且空 invocation 列表会使 @TestTemplate 静默不执行）。ADR-0005「义务」条目的三契约用例改由阶段 4/5 各实现的 IT 覆盖。sketch Step 6 的提交信息（含 ContractTest 模板）相应替换为 controller 指定的提交信息。
2. **AggregateRootId 实测面**：controller context 称「3 方法 getType/asString/asTypedString」——该 3 方法来自父接口 `EntityId`；`AggregateRootId extends EntityId` 本身是纯标记接口（AggregateRootId.java:6-7）。结论不变：SPI 直接用接口类型。另 sketch 测试片段用的 `last()`/`asString()` 匿名实现不成立（EntityId 契约是 getType/asString/asTypedString），因跳过 contract test 未受影响。
3. **「非空 7 项 requireNonNull」**：9 字段中 version/position 为 primitive `long` 不可能为 null，实际 null 检查为 5 个对象参数（eventId/aggregateType/aggregateId/timestamp/payload）——与 sketch 代码一致，brief 文字表述将「7 项必填」与「7 项可 null 检查」混同。
4. **无需新增测试依赖**：junit-jupiter（含 params）与 archunit-junit5 由 `ddd4j-dependencies` 父 pom 全局 `<dependencies>` 段（test scope）继承，模块 pom 保持 ddd4j-core + jackson-databind 两项。
5. 风格偏差（有意）：sketch 的 `import io.ddd4j.core.ddd.event.*` 通配改为显式导入（对齐仓库惯例，EventId/AggregateRootApplyTest 均显式导入）。

## 门禁输出摘要

```
[INFO] Tests run: 26, Failures: 0 ... (ddd4j-annotation)
[INFO] Tests run: 57, Failures: 0 ... (ddd4j-kit)
[INFO] Tests run: 256, Failures: 0 ... (ddd4j-core)
[INFO] Tests run: 10, Failures: 0 ... (ddd4j-data-event-store)
[INFO] BUILD SUCCESS（reactor 7/7：ddd4j, dependencies, annotation, kit, core, data, data-event-store）
```

模块 10 测试 = StoredEventTest 7（5 NPE 参数化 + 1 可空合法 + 1 访问器）+ EventStoreModuleIndependenceTest 3（ArchUnit 规则）。

## Out of scope 确认

未写 EventPayloadSerializer（Task 3.3）；未写 JPA/Panache/JDBI/R2DBC 实现（阶段 4/5）；未动 ddd4j-core；未提交 docs/superpowers/plans/ 下两个未跟踪计划文档（非本任务产物）。

## Self-review 备注

- `read(String, AggregateRootId)` javadoc 补充「空流返回空列表」（05 篇「读侧轻量状态探测思想」的落地表述），SPI 方法签名与 sketch 逐字一致。
- pom 删除 build 段后 jacoco/surefire/source-jar 等行为全部回归父 pom 默认，install 产物（jar + sources + pom）正常落库。
