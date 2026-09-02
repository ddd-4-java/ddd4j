# ddd4j 3.0.x 发版说明（三方合并版，2026-09）

> 本版本线完成 1.0.x / 2.0.x / 3.0.x 三分支合并：以 2.0.x（804 commits 业务沉淀）为底座，
> 吸收 3.0.x 全部架构增量后融合回 3.0.x，1.0.x（JDK 8）已 EOL（tag `eol/jdk8`）。
> 合并全程以 codegraph 三 worktree 符号级 diff 验证语义一致（EventStore SPI 10 类签名零差异）。

## 从 2.0.x 吸收的能力（本次合并进入 3.0.x）

### 新模块（20 个）
- **CQRS 命令侧**（8）：`ddd4j-data-cqrs`（@CommandHandler 发现 + CommandRegistry）+
  spring / quarkus / micronaut / vertx / helidon / javalin / dropwizard 七容器适配
  （SpringCommandBus 等，各带 `*CommandBusIT` 集成测试）。
- **投影（读侧）**（12）：`ddd4j-data-projection`（ProjectionHandler SPI + Registry + Dispatcher）+
  jpa / jdbi / r2dbc / panache 持久化实现 + spring / quarkus / micronaut / vertx / helidon / javalin / dropwizard
  容器调度装配（SmartLifecycle / CDI ScheduledExecutorService / BeanContext 等）。

### 修复与统一
- **javalin samples（4 个）**：Jackson 3 HTTP 层 + `jackson-annotations:2.22` 显式钉版，
  根治三时代空 body bug（365/365 测试绿）。
- **BOM**：`debezium-bom` 压版导致的 junit/mockito 混版归一（6.1.0 / 5.23.0 显式管理条目）；
  grpc 整族显式钉版 1.82.1（修 esdb 客户端 `ForwardingChannelBuilder2` NoClassDefFoundError）；
  `com.eventstore:db-client-java:5.4.5` 管理条目。
- 样例 surefire 归一 `${skipTests}`（部分模块此前硬编码 false 使 CLI 失效）。

## 3.0.x 既有能力（本次合并保持）

- EventStore SPI 位于 `ddd4j-core`（EventStore / AsyncEventStore / StoredEvent / AsyncStoredEvent /
  EventStoreConstants / EntityIdRegistry / 安全 EventPayloadSerializer 无 @class 多态标记）；
- 五实现 + InMemory：JPA / JDBI（含 EventStoreRetry）/ R2DBC（同步适配器 + 异步双轨）/
  Panache（含 Retry）/ ESDB（EventStoreDB gRPC）+ 契约测试 `EventStoreContractTest`；
- `ddd4j-metrics`（OpenTelemetry 投影指标）、`ProjectionConstants`、`CoreIndependenceTest` ArchUnit 守护。

## 依赖版本线（终态）

| 组件 | 3.0.x（本线） | 2.0.x | 1.0.x（EOL） |
|---|---|---|---|
| Java | 21 | 17 | 8 |
| Jackson | tools.jackson 3.2.1 | com.fasterxml 2.22.2 | 2.22.2 |
| Spring Framework | 7.0.8 | 6.2.19 | 5.3.39（豁免） |
| Spring Security | 7.0.6 | 6.5.11 | 5.8.16 |
| Javalin / Jetty | 7.2.2 / 12.1.8 | 7.2.2 / 12.1.8 | — |
| junit / mockito | 6.1.0 / 5.23.0 | 同左 | 5.11.4 / 5.20.0* |

\* mockito 5.20 需 Java 11+，1.0.x 基线遗留，随 EOL 冻结。

## 验证

- 全 reactor `mvn install`：**118 模块 BUILD SUCCESS**；
- 关键测试轨全绿：core 335、jdbi 23（含 PG 容器 IT）、javalin samples 365、
  esdb 5 + 容器 IT、metrics 10、4 个 `-cqrs` 样例；
- 合并冲突 42 文件全部按预演策略解决（报告留档），StrPool 等隐性冲突人工复核。

## 已知遗留（治理清单，非阻塞）

1. Jackson 2 全局被某上游 BOM 压至 2.21.x（与声明 2.22.2 不一致）——依赖治理任务；
2. `panache`/`r2dbc` 的 `*IT` 可编译但默认 surefire 不执行（includes 约定待统一）；
3. 23 个 Endpoints（readiness/idempotent 等）测试覆盖待补；
4. `AggregateRoot` javadoc 示例仍引用旧 API 名（纯注释）。
