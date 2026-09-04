# Task 2.2 Report: AggregateRoot.apply/loadFromHistory 反射实现

- Commit: `a551fb6c` `feat(core): AggregateRoot.apply/loadFromHistory 反射实现`（单 commit，2 files，+300/−3）
- Files:
  - Modify: `ddd4j-core/src/main/java/io/ddd4j/core/ddd/model/AggregateRoot.java`
  - Test: `ddd4j-core/src/test/java/io/ddd4j/core/ddd/model/AggregateRootApplyTest.java`（新建）

## RED 证据（verbatim，`./mvnw -pl ddd4j-core -am test-compile -Dsurefire.failIfNoSpecifiedTests=false`）

```
[ERROR] .../AggregateRootApplyTest.java:[34,42] 找不到符号
  符号:   方法 apply(io.ddd4j.core.ddd.model.AggregateRootApplyTest.OrderCreatedEvent)
[ERROR] .../AggregateRootApplyTest.java:[46,28] 找不到符号
  符号:   方法 apply(io.ddd4j.core.ddd.model.AggregateRootApplyTest.UnhandledEvent)
[ERROR] .../AggregateRootApplyTest.java:[58,14] 找不到符号
  符号:   方法 loadFromHistory(java.util.List<...OrderCreatedEvent>)
[ERROR] .../AggregateRootApplyTest.java:[68,17] 找不到符号
  符号:   方法 apply(io.ddd4j.core.ddd.model.AggregateRootApplyTest.OrderNotifiedEvent)
[ERROR] .../AggregateRootApplyTest.java:[72,18] 找不到符号
  符号:   方法 loadFromHistory(...)
[ERROR] .../AggregateRootApplyTest.java:[81,14] 找不到符号
  符号:   方法 loadFromHistory(<nulltype>)
```

## GREEN 证据

- 单测：surefire `io.ddd4j.core.ddd.model.AggregateRootApplyTest` → `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`
- 全量门禁：`./mvnw -pl ddd4j-core -am test` → **`Tests run: 245, Failures: 0, Errors: 0, Skipped: 0` + `BUILD SUCCESS`**（基线 240 + 新增 5，≥244 达标）
- ArchUnit：`io.ddd4j.core.arch.CoreIndependenceTest` → `Tests run: 6, Failures: 0, Errors: 0`（仍绿）

## 实现要点（修正版设计）

`AggregateRoot.java`「事件管理」区块内、`registerEvent` 之后新增：

1. `protected <E extends DomainEvent<?>> E apply(E event)`：requireNonNull → `AGGREGATE_HANDLER_CACHE.get(getClass()).get(event.getClass())` → 无 handler 抛 `IllegalStateException`（消息含事件类型与聚合类型全名）→ `setAccessible(true)+invoke` → `ReflectiveOperationException` 包 `IllegalStateException` → 成功后 `mutableDomainEvents().add(event)` → 返回 event。**无 aggregateVersion 校验**（javadoc 注明留待阶段 3 EventStore 乐观锁）。
2. `public final void loadFromHistory(List<? extends DomainEvent<?>> history)`：null 直接 return → 逐事件查 `AGGREGATE_REPLAY_CACHE`（无 handler 静默跳过）→ invoke 异常包 `IllegalStateException` → 结束 `clearDomainEvents()`。
3. 双层缓存：`AGGREGATE_HANDLER_CACHE`（skipIgnored=false）/ `AGGREGATE_REPLAY_CACHE`（skipIgnored=true），均 `ClassValue<Map<Class<?>, Method>>`；`scanHandlers(Class, Map, boolean)` 自聚合类向上遍历超类（至 Object 前）收集 `@EventHandler` 单参 `DomainEvent` 子类方法，`putIfAbsent` 子类优先。无并发问题：Map 仅在 `computeValue` 内写入，之后只读。
4. 事件复用现有私有 `mutableDomainEvents()`，未新建事件容器；仅新增 import `EventHandler`、`java.lang.reflect.Method`（无新依赖，`Map/HashMap` 由既有 `java.util.*` 覆盖）。

## Sketch 偏差清单（相对计划 sketch，按 controller 修正指令）

| # | sketch | 实际 | 原因 |
|---|--------|------|------|
| 1 | 死代码 static finder（返回 null 的 `findHandlerInHierarchy`） | 未实现 | brief 指出的 sketch 缺陷 1：被双层 ClassValue 方案取代 |
| 2 | sketch javadoc 称 apply "验证 aggregateVersion 连贯性" | 不校验，javadoc 明示留待阶段 3 | brief 指出的 sketch 缺陷 2：版本校验属 EventStore 乐观锁 |
| 3 | 内联全限定名（`java.util.Map` 等）+ 方法级 `@SuppressWarnings` | 顶部 import + 文件既有 `java.util.*`；省略局部抑制（类级 `@SuppressWarnings({"unchecked","rawtypes"})` 已覆盖） | 遵循文件既有风格；`scanHandlers` 用 `Class<?>` 原始类型规避了 sketch 的 unchecked cast |
| 4 | sketch 测试 `OrderCreatedEvent.OrderId` 直接用 `StringEntityId` 且 record 只实现 `asString` | record 实现完整 3 方法（getType/asString/asTypedString + `StringEntityType("Order")`），事件 ID 构造用 `new OrderCreatedEvent.OrderId("order-1")` 全限定 | 照抄 Task 2.1 `EventHandlerTest` 的 ID record 模式；`EntityId` 是 3 方法接口 |
| 5 | 新增 3 个测试 | 新增 5 个测试（+`assertSame` 返回值、异常消息含双全名、null-history 安全） | brief 断言集"至少"口径；覆盖 javadoc 承诺的行为 |
| 6 | — | 类级 javadoc「事件能力」小节同步更新为 apply/@EventHandler 示例（`&#64;` 转义） | brief javadoc 约定（含 `<h3>` 用法示例） |

## Entity 适配说明

`AggregateRoot<ID extends Serializable> implements Entity<ID>`，`Entity extends DomainModel<ID>` 含抽象方法 `ID id()`，故测试聚合 `Order extends AggregateRoot<String>` 必须实现 `id()`。**选择：真实实现**——构造器保存 `orderId` 字段并 `@Override public String id()` 返回之（而非 sketch 的空构造器 + `id()` 返回 null），语义清晰且 `sameIdentityAs` 可用，成本为零。

## 测试明细（AggregateRootApplyTest，5 tests）

1. `applyInvokesAnnotatedHandlerAndRegistersEvent` — 派发改状态 + 事件入 `domainEvents` + 返回同一实例
2. `applyThrowsWhenNoHandlerRegistered` — `IllegalStateException`，消息含事件与聚合 FQN
3. `loadFromHistoryRebuildsAggregateWithoutEnqueueing` — 状态重建 + **`domainEvents` 为空**（回放不入队，对位 fuin 缺陷）
4. `ignoreOnReplayHandlerRunsOnApplyButSkippedOnReplay` — apply 执行 / replay 跳过
5. `loadFromHistoryIsNullSafe` — null 历史 no-op

## Self-review

- 无死代码、无版本校验、复用 `mutableDomainEvents()`：均符合 brief 修正指令。
- 线程安全：ClassValue 天然并发安全；内层 Map 写后只读。
- 超类链遍历使用 `getDeclaredMethods()` + `setAccessible(true)`，与 sketch 一致（框架层惯例）。
- Out of scope 未越界：未动 pom、未动 EventHandler 注解、未写 Task 2.3 的全覆盖测试、未实现 EventStore。
- 全部 245 tests + ArchUnit 6 tests 绿；无新依赖。

## Concerns

- javadoc `{@link #AGGREGATE_HANDLER_CACHE}` 指向私有字段，strict javadoc lint（`-Xdoclint`）下可能告警；当前构建未启用 javadoc lint，不影响门禁。
- 遗留 untracked 的两份 plan 文档（`docs/superpowers/plans/*.md`）非本任务产物，未纳入提交。
