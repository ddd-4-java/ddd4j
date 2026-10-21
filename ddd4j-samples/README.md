# ddd4j 示例工程

本目录以当前 `ddd4j-samples/pom.xml` 为准，只纳管框架无关的 Order 业务内核，以及 Quarkus、Javalin、Micronaut、
Vert.x、Helidon、Dropwizard 运行时示例。Spring Boot 自动装配和认证样例归外部 `ddd4j-boot` 仓库。

## 示例矩阵

| 示例                           | 方向     | 说明                                        |
|--------------------------------|----------|---------------------------------------------|
| `ddd4j-sample-order-*`         | 共享内核 | Order 领域、应用服务与跨运行时业务契约      |
| `ddd4j-sample-quarkus`         | 普通 DDD | 使用 Quarkus CDI/JAX-RS 的运行时适配        |
| `ddd4j-sample-javalin`         | 普通 DDD | 使用 Guice/Javalin 的运行时适配             |
| `ddd4j-sample-micronaut`       | 普通 DDD | 使用 Micronaut 编译期 DI/HTTP 的运行时适配  |
| `ddd4j-sample-vertx`           | 普通 DDD | 使用 Vert.x EventBus/Router 的运行时适配    |
| `ddd4j-sample-helidon`         | 普通 DDD | 使用 Helidon CDI/JAX-RS 的运行时适配        |
| `ddd4j-sample-dropwizard`      | 普通 DDD | 使用 Dropwizard Bundle/Jersey 的运行时适配  |
| `ddd4j-sample-quarkus-cqrs`    | CQRS     | Quarkus 运行时下的 Order/Goods 读写分离示例 |
| `ddd4j-sample-javalin-cqrs`    | CQRS     | Javalin 运行时下的 Order/Goods 读写分离示例 |
| `ddd4j-sample-quarkus-satoken` | Auth     | Quarkus + Sa-Token 鉴权示例                 |
| `ddd4j-sample-quarkus-shiro`   | Auth     | Quarkus + Apache Shiro 鉴权示例             |
| `ddd4j-sample-javalin-satoken` | Auth     | Javalin + Sa-Token 鉴权示例                 |
| `ddd4j-sample-javalin-shiro`   | Auth     | Javalin + Apache Shiro 鉴权示例             |

## 普通 DDD 示例

共享内核与六个非 Boot 运行时示例使用同一组业务概念，重点不是复制 CRUD，而是对照运行时边界：

| 业务轨道      | 示例对象                                            | 设计重点                                                                     |
|---------------|-----------------------------------------------------|------------------------------------------------------------------------------|
| 充血聚合      | `Order` / `OrderLine` / `Money` / `OrderRepository` | 聚合根封装状态机、不变量和领域事件，领域层不依赖具体 Web/DI/ORM              |
| 轻量 PO/Query | `Goods` / `GoodsQuery` / `GoodsRepository`          | 简单 CRUD 场景保留轻量数据对象，查询能力通过 `Query` + `RichRepository` 承接 |

推荐先阅读：

```bash
mvn -pl ddd4j-samples/ddd4j-sample-quarkus -am test -DskipTests=false
mvn -pl ddd4j-samples/ddd4j-sample-javalin -am test -DskipTests=false
mvn -pl ddd4j-samples/ddd4j-sample-micronaut -am test -DskipTests=false
mvn -pl ddd4j-samples/ddd4j-sample-vertx -am test -DskipTests=false
mvn -pl ddd4j-samples/ddd4j-sample-helidon -am test -DskipTests=false
mvn -pl ddd4j-samples/ddd4j-sample-dropwizard -am test -DskipTests=false
```

Order JDBC 样例提供 `ReliableOrderOutboxPublisher` 作为可靠 Outbox 发布入口：`JdbcMQOutboxStore`
短事务领取，`MQOutboxDispatcher` 在事务外调用 `IntegrationEventPublisher`，再条件确认。
`JdbcOutboxPort.findById` 重建完整 `OutboxMessage`，因此 Kafka 的聚合 ID 键和事件封装沿用原格式。
订单 JDBC 模块提供 `JdbcOrderSampleRuntime.start(...)`，统一构造共享 JDBC 事务端口、订单应用服务和可靠 Outbox 工作器，并立即启动工作器；调用方在应用关闭时对运行实例调用 `close()`，自行管理传入的数据源、幂等端口和 broker 发布器。现有 HTTP 样例仍使用内存适配器，尚未自动装配该 JDBC 运行实例。工作器在业务事务外定期调用 `publishPending(limit)`，入口会拒绝嵌套在订单事务中的调用；每个运行实例需要稳定且互不重复的 `leaseOwner`。独立的 `ReliableOrderOutboxWorker` 仍可供已有手动接线调用。
旧 `TransactionalOutboxPublisher` 保留供兼容调用，但其 broker 发送期间持有数据库事务和行锁。

## CQRS 示例

CQRS 示例按非 Boot 运行时拆分，演示命令侧、查询侧、读模型和缓存的协作方式：

```bash
mvn -pl ddd4j-samples/ddd4j-sample-quarkus-cqrs -am test -DskipTests=false
mvn -pl ddd4j-samples/ddd4j-sample-javalin-cqrs -am test -DskipTests=false
```

## Auth 示例

Auth 示例覆盖不同运行时和不同鉴权实现。业务层应优先面向 ddd4j 的 `Subject`/`SubjectProvider`/`AuthRequest` 契约；具体
sa-token、Shiro、
Spring Security 差异留在对应示例和适配层。

```bash
mvn -pl ddd4j-samples/ddd4j-sample-quarkus-satoken -am test -DskipTests=false
mvn -pl ddd4j-samples/ddd4j-sample-quarkus-shiro -am test -DskipTests=false
mvn -pl ddd4j-samples/ddd4j-sample-javalin-satoken -am test -DskipTests=false
mvn -pl ddd4j-samples/ddd4j-sample-javalin-shiro -am test -DskipTests=false
```

## 当前约束

- 本目录的权威清单是 `ddd4j-samples/pom.xml` 的 `<modules>`，README 不应再引用未纳管目录。
- Spring Boot 自动装配示例应放在外部 `ddd4j-boot` 仓库；本目录只演示 ddd4j 通用能力在不同运行时下的最小可运行方式。
- Quarkus/Javalin 专属脚手架能力分别归外部 `ddd4j-quarkus`、`ddd4j-javalin`，本目录只保留可对照的业务样例。
