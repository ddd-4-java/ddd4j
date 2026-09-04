# Task 8.2 Brief — 剩余 4 个 CQRS sample（micronaut-cqrs / helidon-cqrs / vertx-cqrs / dropwizard-cqrs）

## 背景
- 3 个 CQRS sample 已存在：`ddd4j-sample-spring-cqrs`、`ddd4j-sample-quarkus-cqrs`、`ddd4j-sample-javalin-cqrs`
- 4 个运行时已有基础 sample：`ddd4j-sample-micronaut`、`ddd4j-sample-helidon`、`ddd4j-sample-vertx`、`ddd4j-sample-dropwizard`
- **任务：创建 4 个新 CQRS sample**，每个对应一个运行时，展示该运行时下 CQRS 完整集成（写侧：Command → Aggregate → EventStore；读侧：Projection → 查询模型）

## 交付（每个 sample 结构一致）

### A. ddd4j-sample-micronaut-cqrs
1. `pom.xml`：parent ddd4j-samples；依赖 `ddd4j-sample-order-domain` + `ddd4j-data-event-store-jpa` + `ddd4j-data-cqrs-micronaut` + `ddd4j-data-projection-micronaut` + `ddd4j-data-projection-jpa` + `micronaut-runtime` + `micronaut-http-server-netty` + `micronaut-jackson-databind` + `slf4j-simple`；test `micronaut-test-junit5`
2. `CreateOrderCommand` + `CreateOrderCommandHandler`（@CommandHandler）+ `EventSourcingOrderRepository` + `OrderSummaryView`（ProjectionView）+ `OrderController` + `MicronautOrderApplication`
3. `MicronautOrderCqrsIT.java`：`@MicronautTest`，真实 BeanContext；3 用例（create+projection、query、idempotency）
4. README.md

### B. ddd4j-sample-helidon-cqrs
同 micronaut，但用 Helidon SE（jakarta.inject @Singleton + ServiceLoader 发现 + HelidonServiceLoader）；`HelidonOrderCqrsIT` 用 `@HelidonTest` 或手动 BeanContainer 组装。

### C. ddd4j-sample-vertx-cqrs
同 micronaut，但用 Vertx 5（`Vertx.vertx()` + `VertxProjectionScheduler` + `VertxCommandBus`）；`VertxOrderCqrsIT` 用 `@ExtendWith(VertxExtension.class)`。

### D. ddd4j-sample-dropwizard-cqrs
同 micronaut，但用 Dropwizard 5（`Application<Configuration>.run` + `DropwizardCommandBus` + `DropwizardProjectionScheduler`）；`DropwizardOrderCqrsIT` 用 `DropwizardTestSupport`。

## 共用逻辑（从 8.1 提取）
- `CreateOrderCommand`、`CreateOrderCommandHandler`、`EventSourcingOrderRepository`、`OrderSummaryView`、`OrderSummaryViewEntity`、`OrderSummaryViewRepository` 的**领域逻辑不变**，只有**运行时框架适配注解**不同（@Component vs @Singleton vs @Inject）。
- `OrderController` 的 REST 端点（POST /orders + GET /orders/{id}）不变，只是框架注解不同。
- `application.yml` 配置相同（H2 内存库 + JPA + 投影调度）。

## 门禁
每个 sample 独立 install + 全量 verify（4 模块）BUILD SUCCESS。

## 提交
4 commits（每个 sample 一个）：
- `feat(sample): ddd4j-sample-micronaut-cqrs——Micronaut CQRS 集成示例`
- `feat(sample): ddd4j-sample-helidon-cqrs——Helidon CQRS 集成示例`
- `feat(sample): ddd4j-sample-vertx-cqrs——Vertx CQRS 集成示例`
- `feat(sample): ddd4j-sample-dropwizard-cqrs——Dropwizard CQRS 集成示例`

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-8.2-report.md`。Reply ≤15 lines.
