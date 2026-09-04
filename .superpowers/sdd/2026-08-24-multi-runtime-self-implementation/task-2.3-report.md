# Task 2.3 Report: AggregateRoot 事件处理器全覆盖测试

**Status:** COMPLETE
**Commit:** `4a241ec0` — `test(core): AggregateRoot 事件处理器全覆盖`（单 commit，仅新增 1 个测试文件，208 行）
**File:** `ddd4j-core/src/test/java/io/ddd4j/core/ddd/model/AggregateRootEventHandlerTest.java`（与 `AggregateRootApplyTest` 同包）

## 用例清单与逐条对应（7 个）

| # | 测试方法 | 来源 | 断言要点 |
|---|---------|------|---------|
| 1 | `eventHandlerInvokedOnce` | 计划用例 1 | 一次 `apply` → `@EventHandler` 恰好派发一次（count==1） |
| 2 | `eventHandlerInvokedForEachTrigger` | 计划用例 2 | 三次 `apply` → count==3，逐次派发 |
| 3 | `ignoreOnReplayHandlerNotInvokedOnLoad` | 计划用例 3 | `loadFromHistory` 重建状态（count==1）且跳过 `ignoreOnReplay=true` 处理器（sideEffectRan==false） |
| 4 | `ignoreOnReplayHandlerInvokedOnApply` | 计划用例 4 | `apply` 会调用 `ignoreOnReplay=true` 处理器（sideEffectRan==true，且不误触 count） |
| 5 | `handlerCacheReused` | 计划用例 5 | 两个实例共享按聚合类 ClassValue 缓存的处理器映射，实例状态互不污染（各 count==1） |
| 6 | `privateHandlerAccessible` | 计划用例 6 + 评审并入 A（合并为一个用例） | `private void on(...)` 经 `setAccessible` 在 **apply 与 loadFromHistory 两条派发路径** 均可达（javadoc 宣告 private 可用的显式验证） |
| 7 | `applyThrowsWhenNoHandlerRegisteredAndDoesNotEnqueue` | 评审并入 B | 无处理器时抛 `IllegalStateException`（消息含事件类型 + 聚合类型）**且** `domainEvents().isEmpty()` / `!hasDomainEvents()` —— 失败不入队的显式验证，另加 count==0（无状态突变） |

合并说明：计划 sketch 的 `privateHandlerAccessible` 与【并入 A】按 controller 指示合并为上表 #6，未重复；最终 7 用例（≥7 达标）。

## 与计划 sketch 的两处 fixture 偏差（均为测试侧调整，非主代码问题）

1. **同事件类型双处理器拆分**：计划 sketch 中 `on` 与 `onSideEffect` 均绑定 `IncrementEvent`，而 `scanHandlers` 以事件类型为键 `putIfAbsent`，同类型仅注册一个，且 `getDeclaredMethods()` 顺序 JVM 未定义 —— sketch 原样落地会非确定性失败。已照 `AggregateRootApplyTest` 的 OrderCreatedEvent/OrderNotifiedEvent 模式将副作用处理器拆到独立事件 `CountNotifiedEvent`（用例 3 相应回放两个事件使 skip 断言非平凡）。已在 fixture javadoc 中注明原因。
2. **`Counter` 实现 `id()`**：`AggregateRoot` 是抽象类且 `id()` 来自 `DomainModel` 契约，sketch 的空构造器写法无法编译；按 controller 指示补上 `id()`。事件 ID 采用 EntityId record 三方法面（`getType`/`asString`/`asTypedString`），完全照抄 `OrderId` 模式。

## RED 阶段不适用说明

本任务为纯测试补强（Task 2.2 已落地 apply/loadFromHistory 实现），被测行为已存在且被 `AggregateRootApplyTest`（5 测试）部分覆盖 —— 不存在"先写测试看其失败"的 RED 阶段；验证方式为新用例全绿 + 全量门禁（若断言写错应失败而未失败，属断言问题而非缺实现）。未发现主代码 bug，未触碰主代码（测试边界遵守）。

## 验证结果

- 新类单独跑：`Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`
- 全量门禁：`./mvnw -pl ddd4j-core -am test` → **Tests run: 252, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS**（基线 245 + 新增 7 = 252，达标 ≥252）
- ArchUnit（`CoreIndependenceTest`，7 条规则）随全量绿；其 `DoNotIncludeTests` 不扫描测试代码
- 无新增依赖；commit 仅含 1 个新测试文件，工作树中未跟踪的计划文档未纳入

## Self-Review

- 用例 6 同时覆盖 apply / loadFromHistory 两条 `setAccessible` 路径，优于只测其一
- 用例 7 在 `AggregateRootApplyTest.applyThrowsWhenNoHandlerRegistered` 之上补齐"失败不入队"断言（该文件属 Task 2.2 产出，本任务只增不改）
- 无模块 module-info，测试运行于 classpath，private 处理器 `setAccessible` 无阻拦（实测通过）
- 命名/结构/静态导入/`@since 2.0.x`/作者标签均与 `AggregateRootApplyTest` 对齐
