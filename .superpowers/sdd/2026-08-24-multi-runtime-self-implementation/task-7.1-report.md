# Task 7.1 Report — ddd4j-data-projection（ProjectionHandler SPI 模块）

## Status: DONE

- Commit: `d292d8cf` `feat(data): ddd4j-data-projection——ProjectionHandler SPI + Registry + Dispatcher（不重定义 core 契约）`（8 files, +929）
- Gate: `./mvnw -pl ddd4j-data/ddd4j-data-projection,ddd4j-core -am install` → **BUILD SUCCESS**
- Tests: **本模块 21** = ProjectionHandlerRegistryTest 9 + ProjectionDispatcherTest 7 + ProjectionModuleIndependenceTest 5（ArchUnit）≥ brief 的 5；**core 261 不变**（Tests run: 261, Failures: 0, Errors: 0）。

## 交付物

| 文件 | 说明 |
| --- | --- |
| `ddd4j-data/ddd4j-data-projection/pom.xml` | parent ddd4j-data；依赖 ddd4j-core + ddd4j-annotation（均 ${revision}）+ reactor-core（版本走 reactor-bom，见下方偏差 1）；test 依赖全部走父级全局块，零运行时框架 |
| `src/main/java/io/ddd4j/data/projection/ProjectionHandler.java` | SPI：`getName()` / `eventTypes()`（`Collection<Class<? extends DomainEvent<?>>>`）/ `handle(DomainEvent<?>)` / `getCron()` 默认 `"0/5 * * * * *"` / `getChunkSize()` 默认 100；类型签名全部引用 core `DomainEvent`，javadoc 阐明与 core `ProjectionView` 的分工 |
| `.../ProjectionHandlerRegistry.java` | `register` 整批拒绝（all-or-nothing，冲突抛 ISE 含事件类型 FQCN，javadoc 注明与 CommandRegistry 同源语义）；`all()` 不可变去重视图；`findHandler(Class<E>)` 未注册返回 `Optional.empty()` |
| `.../ProjectionDispatcher.java` | 构造器 `(registry, EventChunkReader<DomainEvent<?>>, ProjectionPositionRepository, ProjectionService)`；`chunkByEvent(Class<E>)` 返回 Flux（handler.getName() 作 streamId，从 service 记录位置起按 getChunkSize() 拉块，读取时以订阅类型简单类名预过滤——对齐 core `DomainEvent#getEventType()` 默认派生，isInstance 过滤后下发，无订阅者→Flux.empty）；`dispatchOne(event, handler)` 先 handle 后 position++ 提交（失败位置不动），返回同步完成的 future，**不包事务** |
| `src/test/.../ProjectionHandlerRegistryTest.java` | 9 用例：精确路由、Optional.empty、null 校验、重复 ISE 含 FQCN、整批拒绝无半注册、all() 不可变、多 handler 多类型路由且多类型 handler 只出现一次、默认配置契约 |
| `src/test/.../ProjectionDispatcherTest.java` | 7 用例：脚本化 chunkReader（记录 read 参数）+ core `InMemoryProjectionPositionRepository`/`DefaultProjectionService` 真实依赖免 mock 容器；覆盖过滤下发与完成、未注册空流、持久化位置续读、dispatchOne 顺序应用 + position++、handler 失败位置不动、null 校验、构造器 null 校验 |
| `src/test/.../arch/ProjectionModuleIndependenceTest.java` | 5 规则：`projection_impl_deps_allowlist` + no_spring + no_quarkus + no_micronaut + no_vertx（与阶段 6 一致的防互窜约定） |
| `ddd4j-data/pom.xml` | Edit 工具注册于 `ddd4j-data-mybatisplus` 之后（列表末位）——字母序实证见下 |

## 注册槽位实证（字母序）

`grep -o "<module>..." pom.xml | LC_ALL=C sort` 加入 `ddd4j-data-projection` 后，排序尾四行为 `logs → mybatis → mybatisplus → projection`（m < p），故精确槽位是**全列表末位**（在 r2dbc/external/jpa/logs/mybatis/mybatisplus 之后）。brief 中「event-store-r2dbc 之后（r 段）」与实测一致（r2dbc < ... < mybatisplus < projection）；既有列表仅 jdbi/panache 一处历史乱序（非本次引入，未动）。

## 偏差与决策（均已在代码 javadoc/pom 注释中记录）

1. **reactor-core 依赖 + allowlist 加 `reactor..`**：brief C 强制 `Flux<DomainEvent<?>> chunkByEvent` 签名，而 brief D 的 allowlist（io.ddd4j../java../lombok..）不含 reactor——二者不可兼得。取 Flux（功能面要求，7.7+ 调度器按此合同装配），allowlist 增补 `reactor..` 并在规则 javadoc + pom 注释中注明 ADR-0005 单轨决策、与 ddd4j-data-event-store `AsyncEventStore`（同为 Reactor 进 SPI 层先例，其 allowlist 同样含 reactor..）同源约定。reactor 不属 brief 禁止的 Spring/Quarkus/Micronaut/Helidon/Javalin/Vertx/Dropwizard 任一，架构无关性未破坏。
2. **计划文档的 Task 7.1/7.2 分立骨架被 brief 合并取代**：plan 原稿 pom 依赖含 `ddd4j-data-event-store`，按 brief 只保留 core + annotation（chunkReader 契约在 core readmodel 包，本模块不需要 event-store 依赖）。
3. **Flux.generate 游标状态机**：Reactor `SynchronousSink` 每生成步只允许一次 `next()`，故以 `Cursor(position, chunk, index)` record 状态机实现「块内游标后移 + 块间拉取」，行为语义与逐块下发一致（测试实证 2 次 read 调用、按序过滤下发）。
4. **`dispatchOne` 的 +1 计数语义**：按 brief「position++.commit」，每应用一个事件位置 +1（按事件计数），而非直接采纳 chunk 的 nextEventNumber——chunkByEvent 只读不写位置，位置推进统一由 dispatchOne 提交，两者解耦；7.6+ 适配层如需按 chunk 位置推进可自行落库。

## 遗留关注（非阻塞）

1. 事件类型名映射取 `Class#getSimpleName()`（对齐 core `DomainEvent` ClassValue 默认 EventType 派生）；若未来某事件存储以 FQCN 落列，适配层 reader 自行归一，本 SPI 合同不变。
2. `ProjectionDispatcher` 同时持有 `ProjectionService`（读位置）与 `ProjectionPositionRepository`（写位置），javadoc 已注明两者应指向同一份位置存储（core `DefaultProjectionService` 即 repo 的默认包装）；装配期错配会导致读写漂移，属适配层装配责任。
3. core `TypedEventDispatcher` 未被本模块直接调用（其键为事件类型字符串、面向 `TypedEventHandler`；本模块按 `Class` 键控路由），javadoc 已注明分工，避免 7.6+ 适配层误认为需双层分发。

## Self-review 对照

- A ProjectionHandler 签名逐项一致（getName/eventTypes/handle/getCron/getChunkSize 及默认值）✓；不重定义 core 16 契约（模块内仅 import core readmodel/domain 类型，零本地投影抽象）✓
- B Registry 三方法 + 整批拒绝 + FQCN + Optional.empty ✓；javadoc「与 core/CommandRegistry 同源语义」✓
- C Dispatcher 构造器四参 / chunkByEvent 过滤流 / dispatchOne 顺序应用 + position++ 提交 ✓；不包事务 ✓
- D pom parent/依赖/零框架 + 5 ArchUnit（allowlist 名 `projection_impl_deps_allowlist`）+ 2 测试类 ✓
- E 字母序注册（末位）✓；门禁 BUILD SUCCESS + core 261 + 模块 21 ✓；单 commit `d292d8cf` ✓；Edit 工具改 pom（铁律）✓
