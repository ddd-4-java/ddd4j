# Task 8.1 Brief — ddd4j-sample-order-application 改造（Spring + EventStore + CQRS + Projection 完整集成）

## 背景
- `ddd4j-sample-order-application` 当前只依赖 `ddd4j-sample-order-domain`（领域层），没有 EventStore/CQRS/Projection 集成。
- 领域层 `Order` 已用 `AggregateRoot<String>`（含 `OrderCreatedEvent`、`OrderPaidEvent` 等事件）。
- 阶段 3-7 已交付：`ddd4j-data-event-store-jpa`（JPA EventStore）、`ddd4j-data-cqrs-spring`（SpringCommandBus）、`ddd4j-data-projection-spring`（SpringProjectionScheduler）。
- **任务：改造这个 sample 为真实 Spring CQRS 示例**，让业务方看到完整的写侧（Command → Aggregate → EventStore）+ 读侧（EventStore → Projection → 查询）流程。

## 交付

### A. pom.xml 改造
- 依赖 `ddd4j-data-event-store-jpa`（写侧持久化）+ `ddd4j-data-cqrs-spring`（命令总线）+ `ddd4j-data-projection-spring`（投影调度）+ `ddd4j-data-jpa`（JPA 通用）
- test 依赖 `spring-boot-starter-test` + `spring-boot-starter-data-jpa`（模块局部 spring-boot-it.version=3.4.4）

### B. 写侧实现
1. `CreateOrderCommand`：实现 `io.ddd4j.core.cqrs.command.Command`，包含 orderNo + buyerId + buyerName 字段
2. `CreateOrderCommandHandler`：实现 `CommandExecutor<CreateOrderCommand>`，用 `Order` 聚合根的构造器创建订单，通过 `EventStore.append()` 持久化事件
3. `OrderRepository`：实现 `Repository<Order, String>`，内部用 `JpaEventStore` 读取事件流重建聚合（`loadFromHistory`）

### C. 读侧实现
1. `OrderSummaryView`：实现 `ProjectionView`，订阅 `OrderCreatedEvent` + `OrderPaidEvent`，维护读模型（JPA Entity）
2. `OrderSummaryViewRepository`：Spring Data JPA 读模型查询

### D. Spring Boot 集成
1. `OrderSampleApplication`：`@SpringBootApplication`，扫描 `io.ddd4j.sample.order` + `io.ddd4j.data.*`
2. `OrderController`：REST 端点，`POST /orders` → `CreateOrderCommand`，`GET /orders/{id}` → 查询读模型
3. `application.yml`：H2 数据库 + JPA + 投影调度配置

### E. 集成测试
1. `OrderCqrsIT`：`@SpringBootTest` + `@AutoConfigureMockMvc`，真实 Spring 容器
   - ① `POST /orders` 创建订单 → 断言 EventStore 有事件 + 读模型有记录
   - ② `GET /orders/{id}` 查询读模型 → 断言返回正确数据
   - ③ 幂等性：同一 orderNo 重复创建 → 断言事件幂等

### F. README 更新
- 新增 "CQRS 集成示例" 章节，说明写侧/读侧流程
- 新增 "如何运行" 章节（`./mvnw -pl ddd4j-samples/ddd4j-sample-order-application -am install`）

## 门禁
`./mvnw -pl ddd4j-samples/ddd4j-sample-order-application -am install` BUILD SUCCESS；报告精确测试计数。

## 提交
单 commit：`feat(sample): ddd4j-sample-order-application 改造为 Spring CQRS 完整集成示例`

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-8.1-report.md`。Reply ≤15 lines.
