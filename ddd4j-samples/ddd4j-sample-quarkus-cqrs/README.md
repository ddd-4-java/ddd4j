# ddd4j-sample-quarkus-cqrs

## 当前商品交易入口

此模块以 CQRS 方式运行共享商品交易。先按[示例总览](../README.md#启动默认订单示例)配置 MySQL。以下命令从仓库根目录运行，使用实际
Maven 版本替换 `<version>`。

```bash
module=ddd4j-samples/ddd4j-sample-quarkus-cqrs
./mvnw -pl "$module" -am -DskipTests -Dmaven.javadoc.skip=true package
java -jar "$module/target/quarkus-app/quarkus-run.jar"
```

业务请求携带 `X-Tenant-Id`。商品地址为 `/orders/catalog/goods/{spuCode}`，订单地址为 `/orders/catalog/orders/{orderNo}`
。支付、取消和履约分别使用订单子路径 `/payment`、`/cancel`、`/fulfill`。

写操作经命令总线执行。调用 `POST /orders/catalog/projections/orders/{orderNo}` 构建订单投影。

<details>
<summary>商品交易迁移前的说明</summary>

> Quarkus + ddd4j **Order + Goods 双业务 / CQRS** 示例：第二轨（Order 充血模型） +
> 第三轨（Goods Model/Query CRUD） + 命令/查询分离 + CacheKit 缓存读侧 +
> Quarkus CDI `@Observes` 事件桥接。

---

## 1. 这是什么

本示例演示两个业务轨道和 CQRS 读写分离。使用仓库版本对应的 JDK。

| ddd4j 线 | JDK | Quarkus      | REST JSON 依赖             | 启动方式                           |
|----------|-----|--------------|----------------------------|------------------------------------|
| 1.0.x    | 8   | 1.13.7.Final | `quarkus-resteasy-jackson` | 默认内存，显式启用 PostgreSQL 投影 |
| 2.0.x    | 17  | 3.40.1.2     | `quarkus-rest-jackson`     | PostgreSQL 投影随默认构件启用      |
| 3.0.x    | 21  | 3.38.2       | `quarkus-rest-jackson`     | PostgreSQL 投影随默认构件启用      |

| 轨道                      | 业务包                   | 风格                                                                              |
|---------------------------|--------------------------|-----------------------------------------------------------------------------------|
| **第二轨**（充血模型）    | `order/`                 | `Order` 聚合根 + 5 个领域事件 + `OrderApplicationService`（`@ApplicationScoped`） |
| **第三轨**（Model/Query） | `goods/`                 | `Goods` PO + `RichRepository` + `GoodsQuery`（`@ApplicationScoped`）              |
| **CQRS 增强**             | `cache/` + 读侧 Resource | `CacheKit` 缓存读侧统计                                                           |
| **CDI 事件桥接**          | `event/`                 | `@Observes Order*Event` 同步监听                                                  |

---

## 2. 演示的数据流

```
HTTP POST /orders                      HTTP POST /goods
        │                                        │
        ▼                                        ▼
OrderResource (JAX-RS, @Transactional)  GoodsResource (JAX-RS)
        │                                        │
        ▼                                        ▼
OrderApplicationService                 GoodsApplicationService
(@ApplicationScoped)                    (@ApplicationScoped)
        │                                        │
        ├─ Order.draft(...)                     ├─ Goods.create / update ...
        │   产生 OrderCreatedEvent               │
        ├─ OrderRepository.save                 └─ InMemoryGoodsRepository.save
        │
        ▼
CDI Event<>
        │
        ├─→ OrderEventObserver (@Observes)
        │
        └─→ 写侧完成后可手动失效缓存

HTTP GET /api/orders/query/stats        HTTP GET /api/goods/query/list
        │                                        │
        ▼                                        ▼
OrderCQRSQueryResource                  GoodsReadResource
        │                                        │
        ▼                                        ▼
OrderCacheService                       GoodsCacheService
(@ApplicationScoped)                    (@ApplicationScoped)
        │                                        │
        ▼                                        ▼
CacheKit.get → hit/miss                CacheKit.get → hit/miss
        │                                        │
        ▼ miss                                   ▼ miss
orderRepository.findAll()              goodsRepository.findByQuery(q)
```

---

## 3. 项目结构

```
ddd4j-sample-quarkus-cqrs/
├── pom.xml
├── README.md
└── src/main/
    ├── java/io/ddd4j/sample/quarkus/cqrs/
    │   ├── QuarkusCqrsApplication.java              ── @QuarkusMain 启动
    │   ├── config/
    │   │   └── Ddd4jSampleConfig.java               ── CacheKit 域初始化
    │   ├── cache/
    │   │   ├── OrderCacheService.java               ── @ApplicationScoped 订单缓存
    │   │   └── GoodsCacheService.java               ── @ApplicationScoped 商品缓存
    │   ├── event/
    │   │   ├── OrderEventObserver.java              ── @Observes 同步监听
    │   │   └── OrderEventListener.java              ── @ApplicationScoped 事件桥接
    │   ├── order/
    │   │   ├── domain/
    │   │   │   ├── model/
    │   │   │   │   ├── Order.java                  ── 聚合根
    │   │   │   │   ├── OrderLine.java
    │   │   │   │   ├── OrderStatus.java
    │   │   │   │   └── Money.java
    │   │   │   ├── event/                          ── 5 个领域事件
    │   │   │   ├── repository/OrderRepository.java
    │   │   │   └── service/OrderDomainService.java
    │   │   ├── application/
    │   │   │   ├── OrderApplicationService.java    ── @ApplicationScoped
    │   │   │   ├── CreateOrderCommand.java
    │   │   │   └── AddOrderLineCommand.java
    │   │   ├── infrastructure/
    │   │   │   └── InMemoryOrderRepository.java    ── @ApplicationScoped
    │   │   ├── cache/OrderCacheService.java
    │   │   └── web/
    │   │       ├── OrderResource.java              ── 写侧 JAX-RS
    │   │       ├── OrderQueryResource.java         ── 简单读侧 JAX-RS
    │   │       └── OrderCQRSQueryResource.java     ── 缓存读侧 JAX-RS
    │   └── goods/
    │       ├── domain/
    │       │   ├── Goods.java
    │       │   ├── GoodsId.java
    │       │   ├── GoodsStatus.java
    │       │   ├── GoodsRepository.java            ── RichRepository
    │       │   └── GoodsQuery.java                 ── 充血查询
    │       ├── application/GoodsApplicationService.java
    │       ├── config/GoodsConfig.java
    │       ├── infrastructure/InMemoryGoodsRepository.java
    │       └── web/
    │           ├── GoodsResource.java              ── 写侧 JAX-RS
    │           ├── GoodsQueryResource.java         ── 简单读侧 JAX-RS
    │           └── GoodsReadResource.java          ── 缓存读侧 JAX-RS
    └── resources/
        └── application.properties
```

---

## 4. REST API

### 4.1 订单写入

| 方法 | 路径                  | 说明               |
|------|-----------------------|--------------------|
| POST | `/orders`             | 创建草稿订单       |
| POST | `/orders/{id}/lines`  | 添加订单行         |
| POST | `/orders/{id}:pay`    | 支付订单           |
| POST | `/orders/{id}:ship`   | 发货订单           |
| POST | `/orders/{id}:cancel` | 取消订单           |
| POST | `/orders/cancel-all`  | 按买家批量取消草稿 |

### 4.2 订单读取

| 方法 | 路径                                      | 说明               |
|------|-------------------------------------------|--------------------|
| GET  | `/orders/{id}`                            | 按 ID 查询         |
| GET  | `/query/orders/{id}`                      | 基础读侧按 ID 查询 |
| GET  | `/query/orders?status=PAID`               | 按状态查询         |
| GET  | `/query/orders/count`                     | 订单总数           |
| GET  | `/orders/orderNo/{orderNo}`               | 按编号查询         |
| GET  | `/api/orders/query/list`                  | 缓存读侧分页信息   |
| GET  | `/api/orders/query/stats`                 | 缓存读侧统计       |
| GET  | `/api/orders/query/buyer/{buyerId}/count` | 买家订单数         |
| GET  | `/api/orders/query/detail/{id}`           | 缓存读侧详情       |

### 4.3 商品写入

| 方法   | 路径                                | 说明         |
|--------|-------------------------------------|--------------|
| POST   | `/goods`                            | 创建商品     |
| PUT    | `/goods/{id}`                       | 更新商品     |
| PUT    | `/goods/{id}/status?status=ON_SALE` | 调整商品状态 |
| DELETE | `/goods/{id}`                       | 软删除商品   |

### 4.4 商品读取

| 方法 | 路径                          | 说明             |
|------|-------------------------------|------------------|
| GET  | `/goods/{id}`                 | 按 ID 查询       |
| GET  | `/goods/by-code?code=SKU-001` | 按编码查询       |
| GET  | `/query/goods/page`           | 分页查询         |
| GET  | `/query/goods/list`           | 列表查询         |
| GET  | `/query/goods/count`          | 统计数量         |
| GET  | `/api/goods/query/list`       | 缓存读侧商品列表 |
| GET  | `/api/goods/query/by-id/{id}` | 缓存读侧商品详情 |

## 5. 运行

### 5.1 Java 8 默认内存模式

从仓库根目录使用本线 JDK 8 和 Maven Wrapper 打包。以下命令使用 `clean` 清除旧测试类，并用 `-Dmaven.test.skip=true`
跳过测试编译和执行，避免上游 JPA
集成测试启动容器。测试结果见 [R14 实施计划](../../docs/superpowers/plans/2026-10-10-three-line-product-convergence.md#r14三线存储和多平台完整验收)。

```bash
./mvnw -pl ddd4j-samples/ddd4j-sample-quarkus-cqrs -am -Dmaven.test.skip=true clean package
java -jar ddd4j-samples/ddd4j-sample-quarkus-cqrs/target/quarkus-app/quarkus-run.jar
```

### 5.2 内存订单演示

在当前版本的应用启动后，以下命令完成一笔订单的写入和缓存读取。默认端口为 8080。将创建订单返回的 `data.id` 赋给 `ORDER_ID`。

```bash
curl -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d '{"orderNo":"O-001","buyerId":"u-001","buyerName":"Alice"}'

curl -X POST "http://localhost:8080/orders/$ORDER_ID/lines" \
  -H 'Content-Type: application/json' \
  -d '{"goodsId":"1","goodsName":"Book","quantity":2,"unitPrice":59.90}'

curl -X POST "http://localhost:8080/orders/${ORDER_ID}:pay" \
  -H 'Content-Type: application/json'
curl "http://localhost:8080/api/orders/query/detail/$ORDER_ID"
```

商品示例通过 `POST /goods` 创建，通过 `GET /api/goods/query/list` 查询缓存读侧。

### 5.3 PostgreSQL 投影

运行前提供 `ORDER_JDBC_URL`、`ORDER_DB_USER`、`ORDER_DB_PASSWORD`。数据库需具备已有订单、Outbox、事件和投影检查点表。应用使用
Hibernate `validate` 启动，测试在隔离 PostgreSQL 16 中准备表结构。

Java 17 和 21 使用本线默认构件，持久入口按应用配置在构建时启用。

```bash
./mvnw -pl ddd4j-samples/ddd4j-sample-quarkus-cqrs -am -Dmaven.test.skip=true clean package
java -jar ddd4j-samples/ddd4j-sample-quarkus-cqrs/target/quarkus-app/quarkus-run.jar
```

Java 8 使用 `durable-order` Maven profile 引入 PostgreSQL 驱动和 Panache 检查点适配器，并使用 Quarkus `durable` profile
构建和运行持久构件。

```bash
./mvnw -pl ddd4j-samples/ddd4j-sample-quarkus-cqrs -am -Pdurable-order \
  -Dquarkus.profile=durable -Dmaven.test.skip=true clean package
java -Dquarkus.profile=durable -jar ddd4j-samples/ddd4j-sample-quarkus-cqrs/target/durable-order/quarkus-app/quarkus-run.jar
```

### 5.4 PostgreSQL 集成测试

`NativeSharedCqrsTest` 通过 Testcontainers 启动 PostgreSQL 16，覆盖事件写入、投影失败重试与检查点推进。Java 8 测试需要
`-Pdurable-order`，该 profile 会重建 Quarkus 1.13 的应用模型。

```bash
./mvnw -pl ddd4j-samples/ddd4j-sample-quarkus-cqrs -am -Pdurable-order \
  -Dtest=io.ddd4j.sample.quarkus.cqrs.NativeSharedCqrsTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Java 17 和 21 使用同一条命令，省略 `-Pdurable-order`。

---

## 6. 持久 CQRS

持久模式从 `ddd4j-data-event-store-jpa` 读取事件，从 `ddd4j-data-event-store-panache` 保存投影位置。样例配置显式启用
`ddd4j.quarkus.cqrs.projection.position-store=panache`，Hibernate 包扫描仅包含本例选用的事件和位置实体。

`/orders`、`/api/orders/query` 和商品接口演示内存写入与缓存查询。`/sample/orders` 使用共享持久写入和独立的六字段读模型。

- `POST /sample/orders`：共享 `CreateOrderCommand`，返回订单 ID。
- `POST /sample/orders/lines`：共享 `AddOrderLineCommand`，含 orderId。
- `POST /sample/orders/{id}/payment?key=...`：共享支付。
- `GET /sample/orders/{id}`、`POST /sample/orders/query`：仅查询既有独立读模型表，返回
  id/orderNo/buyerId/buyerName/status/totalAmount；尚未投影时返回 404。
- `POST /sample/projections/orders/run`：显式触发真实中继/Runner；后台也由 native scheduler 定时执行同一管线。

CDI 的共享订单服务通过现有 JDBC 适配器在同一本地事务提交订单与 Outbox；单实例串行中继保留原 Outbox ID/载荷，使用
`JpaEventStore.participatingManaged` 在独立 JTA 事务提交真实事件后确认 Outbox。确认失败可按原事件 ID 幂等重试。事件表的真实全局
source position 推进 R08 原生 `ProjectionRunner` 的持久检查点，不能用筛选事件数推算。

事件读取和 Panache 检查点参加 native JTA；独立查询存储使用既有本地 JDBC
事务，先提交查询再提交检查点。此边界不具有跨资源原子性：检查点失败时已提交查询可能可见，但位置保持不动，重试按订单主键 UPSERT
恢复。投影处理器收到领域事件后读取已提交写模型并构造六字段快照。HTTP 查询直接访问独立查询仓储。

---

## 7. 关键依赖

| 依赖                    | 作用                                                     |
|-------------------------|----------------------------------------------------------|
| `ddd4j-core`            | CQRS/ES 抽象（AggregateRoot / Repository / DomainEvent） |
| `ddd4j-annotation`      | 构造型注解                                               |
| `ddd4j-kit`             | 工具包（StrKit / CollKit）                               |
| `ddd4j-runtime-quarkus` | 启动期自动注入 4 个核心 SPI（DddContextInitializer）     |
| `ddd4j-web-quarkus`     | TenantAwareResource、DefaultExceptionHandler             |
| `ddd4j-cache`           | CacheKit 缓存门面                                        |
| REST JSON 依赖          | 按版本使用第 1 节中的依赖                                |
| `quarkus-arc`           | CDI 容器                                                 |
| `jackson-databind`      | 值对象 / 事件的 JSON 序列化                              |

---

## 8. 生产化建议

| 示例组件                                  | 生产建议                                  |
|-------------------------------------------|-------------------------------------------|
| `InMemoryOrderRepository`                 | JDBI / MyBatis-Plus / Hibernate           |
| `InMemoryGoodsRepository`                 | 同上                                      |
| `OrderCacheService` / `GoodsCacheService` | 替换为 Caffeine / Redis / Redisson 后端   |
| `@Observes OrderEvent`                    | 改为 `@ObservesAsync`（需 quarkus-vertx） |

---

## 9. 相关项目

- [`ddd4j-sample-quarkus`](../ddd4j-sample-quarkus) — Quarkus 启动示例（演示 4 核心 SPI 与基础装配，非 CQRS）
- 外部 `ddd4j-boot-samples/ddd4j-boot-sample-order` — Spring Boot CQRS 对照
- [`ddd4j-sample-javalin-cqrs`](../ddd4j-sample-javalin-cqrs) — 同 CQRS 思路在 Javalin 平台

---

## 10. 许可

与 ddd4j 主项目一致：[Apache License 2.0](../../LICENSE)

</details>
