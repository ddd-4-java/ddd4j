# ADR-0003: 跨 8 运行时适配策略

## Status

Accepted（2026-08-24）

> 相关：ADR-0002（core 零依赖）、ADR-0004（命令侧继承 DefaultCommandBus）、ADR-0005（EventStore SPI）；投影侧证据见 ../reference/fuin-api-patterns/07-cqrs-projection.md。

## Context

ddd4j 现状支持 8 种运行时（Spring WebMVC／WebFlux、Quarkus、Micronaut、Helidon、Javalin、Vert.x、Dropwizard），`ddd4j-web` 已为每种运行时提供 HTTP 适配器；但事件溯源／CQRS 层只有 Spring 一路集成。若 ES/CQRS 能力沿用「写死框架」的路线，其余 7 种运行时的业务方将无法使用。

fuin 的教训说明写死框架的代价（../reference/fuin-api-patterns/07-cqrs-projection.md「缺点」节）：

- ViewManager 调度生命周期全走 Spring 类型（`@Component`＋`SchedulingConfigurer`＋`ApplicationListener<ContextClosedEvent>`），任务注册与取消绑定 Spring 容器；
- Quarkus 需要 190 行整份重写同一投影循环——「同一投影循环两份维护」；
- 事务细节（`TransactionTemplate` REQUIRES_NEW＋硬编码 10s 超时）泄漏进投影循环，非 JPA 读模型（Redis／ES）无法套用；
- cron 每跳裸 `new Thread`、无线程池，异常仅记日志吞掉。

而 ddd4j-core `cqrs/readmodel/` 已验证反向路线可行（07 篇「改写／超出」节）：

- (a) 整个 readmodel 包零框架依赖，`ViewManager` 与 `ViewScheduler` 均为 SPI，调度策略留给各运行时；
- (b) 投影循环沉淀为纯类 `ProjectionRunner.runOnce`（读位置→EventChunkReader.read→handleEvents→updatePosition），事件存储经 `EventChunkReader` SPI 解耦；
- (d) 事件分发走 `Class` 校验（TypedEventDispatcher），而非字符串路由。

## Decision

**ddd4j-data-\* 模块按能力拆分，每个能力点以「1 SPI＋多实现」覆盖全部运行时**：

- 每个能力 1 个 SPI 接口，置于 ddd4j-core（或 ddd4j-data-{capability} 的 api 包），接口本身零框架依赖；
- 持久化至少 4 套实现：JPA（Spring／Hibernate）、Panache（Quarkus）、JDBI（Javalin／Vert.x）、R2DBC（响应式）；
- 调度器 7 套：Spring／Quarkus／Micronaut／Helidon／Javalin／Vert.x／Dropwizard 各一份 `ViewScheduler` SPI 实现，并发重入防护一并下沉到调度器实现（07 篇落地计划）；
- 投影侧直接复用 ddd4j-core readmodel 既有 16 类（ProjectionRunner／ProjectionView／ProjectionService／EventChunkReader 等），4 套持久化各自实现 `ProjectionPositionRepository`，不新起契约。

运行时 × 持久化 × 调度矩阵：

| 运行时 | 持久化 | 调度器 | HTTP/响应式 |
|--------|--------|--------|------------|
| Spring WebMVC | JPA | @Scheduled | Sync |
| Spring WebFlux | R2DBC | @Scheduled | Reactive |
| Quarkus | Panache | @Scheduled | Sync/Reactive |
| Micronaut | JPA | @Scheduled | Sync/Reactive |
| Helidon | JPA | @Scheduled | Sync |
| Javalin | JDBI | ScheduledExecutorService | Sync |
| Vert.x | JDBI/JPA | Vertx setPeriodic | Reactive |
| Dropwizard | JPA | ScheduledExecutorService | Sync |

命令侧同理：7 个运行时适配器统一继承 `DefaultCommandBus`（ADR-0004），不复制 fuin 式按框架整份重写的路线；EventStore 按 ADR-0005 的四方法 SPI 供四套持久化实现。

## Consequences

- 正面：业务方可自由选择运行时，ES/CQRS 能力与 ddd4j-web 的 8 运行时承诺对齐；
- 正面：ddd4j-core 仍是单 jar 零外部依赖（ADR-0002），框架耦合全部隔离在适配层；
- 正面：投影循环单一实现（ProjectionRunner），新运行时只需实现调度器与持久化仓储，避免 fuin「264 行＋190 行两份维护」的分裂（07 篇）；
- 正面：`ViewManager.start/stop/triggerOnce` 生命周期语义由 SPI 统一，调度器实现可替换、可测试；
- 负面：自研工作量较大，全计划合计 56-84 天（阶段 2-9 预估）；
- 负面：CI 需要跨 8 运行时跑测试矩阵，构建时长与维护成本上升；
- 负面：每能力至少 4 套持久化实现意味着同一契约要验证 4 次（EventStoreContractTest 等契约测试按实现重复执行）。

## Alternatives Considered

- 方案 A：只支持 Spring（约 17-25 天）——**已否决**：违背 ddd4j 跨运行时的存在理由；ddd4j-web 已有 8 运行时适配，ES/CQRS 单独缩圈会造成能力断层。
- 方案 B：Spring＋Quarkus 双运行时（约 27-35 天）——**已否决**：缺其余 6 运行时；且 fuin 先例（springboot／quarkus 各一份 ViewManager）证明双份框架专用实现即开始分裂维护（07 篇）。
- 方案 C：适配全部下沉到 SPI 自动发现（ServiceLoader），不写各运行时适配模块——**已否决**：各运行时的依赖注入（Spring 容器／Quarkus CDI／无容器手动装配）、事务与调度语义差异无法用单一发现机制覆盖；esc-api「无 META-INF/services、装配全靠手工」（05 篇附注）也说明发现机制并不能替代适配层。
