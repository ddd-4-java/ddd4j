# ddd4j-sample-javalin-cqrs

> ddd4j Javalin 平台的 **Order + Goods 双业务 / CQRS** 示例：第二轨（Order 充血模型） +
> 第三轨（Goods Model/Query CRUD） + 命令/查询分离 + CacheKit 缓存读侧 +
> 进程内事件总线 + Javalin EndpointGroup 路由。

---

## 1. 这是什么

Javalin 本身**没有 DI 容器**，所以本示例在 `main` 中手动 new 出所有 Bean 并通过
`EndpointGroup` 注册路由；同时演示两个业务轨道 + CQRS 读写分离：

| 轨道                   | 业务包                      | 风格                                                             |
|----------------------|--------------------------|----------------------------------------------------------------|
| **第二轨**（充血模型）        | `order/`                 | `Order` 聚合根 + 5 个领域事件 + `OrderApplicationService`（手动 new）      |
| **第三轨**（Model/Query） | `goods/`                 | `Goods` PO + `RichRepository` + `GoodsQuery`（手动 new）           |
| **CQRS 增强**          | `cache/` + 读侧 Controller | `CacheKit` 缓存读侧统计                                              |
| **进程内事件总线**          | `event/`                 | `TypedEventDispatcher` 路由 `Order*Event` 到 `OrderEventListener` |

### Javalin 平台的 SPI 注入

Javalin 没有 DI 容器，业务方只需在启动前调用 `Ddd4jSampleConfig.bootstrap()` 即可注入 4 个核心 SPI：

```java
BaseContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, ...);
BaseContext.inject(SpiKeys.MQ_EVENT_PUBLISHER,        MQEventPublisher.class,    new NoOpMQEventPublisher());
BaseContext.inject(SpiKeys.SUBJECT_PROVIDER,          SubjectProvider.class,     new AnonymousSubjectProvider());
BaseContext.inject(SpiKeys.I18N_PROVIDER,             I18nProvider.class,        new DefaultI18nProvider());
```

之后业务代码通过 `Contexts.injectOrThrow(KEY, type)` 按需查找，**零框架耦合**。

| 框架          | DI 容器   | ddd4j runtime 模块        | SPI 注入方式                                    |
|-------------|---------|-------------------------|---------------------------------------------|
| Spring      | ✅       | `ddd4j-runtime-spring`  | 容器启动期扫描 `@Bean` 注入 BaseContext              |
| Quarkus     | ✅ (CDI) | `ddd4j-runtime-quarkus` | CDI Observer 启动期注入 BaseContext              |
| Guice       | ✅       | `ddd4j-runtime-guice`   | `Ddd4jGuiceModule#configure` 注入 BaseContext |
| **Javalin** | ❌       | **无**（业务方手动注入）          | `main` 里调用 `bootstrap()`                    |

---

## 2. 演示的数据流

```
[REST] POST /api/orders           [REST] POST /api/goods
        │                                  │
        ▼                                  ▼
OrderController                    GoodsController
（EndpointGroup 注册）             （EndpointGroup 注册）
        │                                  │
        ▼                                  ▼
OrderApplicationService            GoodsApplicationService
        │                                  │
        ▼                                  ▼
OrderRepository.save               GoodsRepository.save
        │                                  │
        ├─> events for each               │
        │   Order*Event                   │
        ▼                                  ▼
DomainEventPublisher.publish       GoodsCacheService.evictOnWrite
        │                                  │
        ▼                                  ▼
TypedEventDispatcher                CacheKit.invalidate
        │
        ▼
OrderEventListener
（handlers 处理每种事件）

[REST] GET /api/orders/query/stats   [REST] GET /api/goods/query/list
        │                                    │
        ▼                                    ▼
OrderCQRSQueryController             GoodsReadController
        │                                    │
        ▼                                    ▼
OrderCacheService                    GoodsCacheService
   │ CacheKit.get → hit/miss              │ CacheKit.get → hit/miss
   │ miss 时调用 repository.findAll       │ miss 时调用 goodsQuery
```

---

## 3. 项目结构

```
ddd4j-sample-javalin-cqrs/
├── pom.xml
├── README.md
└── src/main/java/io/ddd4j/sample/javalin/cqrs/
    ├── JavalinCqrsApplication.java           # main：装配 + 启动 Javalin
    ├── config/Ddd4jSampleConfig.java         # SPI 注入 + CacheKit 域初始化
    ├── spi/                                  # 4 个 SPI 示例实现
    │   ├── AnonymousSubjectProvider.java
    │   ├── DefaultI18nProvider.java
    │   ├── NoOpDomainEventPublisher.java
    │   └── NoOpMQEventPublisher.java
    ├── cache/                                # CQRS 缓存层
    │   ├── OrderCacheService.java
    │   └── GoodsCacheService.java
    ├── event/
    │   └── OrderEventListener.java           # TypedEventDispatcher 多事件处理
    ├── order/
    │   ├── domain/
    │   │   ├── model/                        # Order / OrderLine / OrderStatus / Money
    │   │   ├── event/                        # 5 个 Order*Event
    │   │   └── repository/OrderRepository.java
    │   ├── application/OrderApplicationService.java
    │   ├── infrastructure/
    │   │   ├── InMemoryOrderRepository.java
    │   │   ├── OrderPO.java                  # PO 持久化对象
    │   │   └── OrderLinePO.java
    │   └── web/
    │       ├── OrderController.java          # 写侧（EndpointGroup）
    │       └── OrderCQRSQueryController.java # 缓存读侧（/api/orders/query/*）
    └── goods/
        ├── domain/
        │   ├── Goods.java
        │   ├── GoodsId.java
        │   ├── GoodsStatus.java
        │   ├── GoodsRepository.java
        │   └── GoodsQuery.java
        ├── application/GoodsApplicationService.java
        ├── infrastructure/InMemoryGoodsRepository.java
        └── web/
            ├── GoodsController.java          # 写侧（EndpointGroup）
            └── GoodsReadController.java      # 缓存读侧（/api/goods/query/*）
```

---

## 4. REST API

### 4.1 Order 命令侧（写）

| 方法     | 路径                        | 说明     |
|--------|---------------------------|--------|
| `POST` | `/api/orders`             | 创建草稿订单 |
| `POST` | `/api/orders/{id}/lines`  | 添加订单行  |
| `POST` | `/api/orders/{id}/pay`    | 支付订单   |
| `POST` | `/api/orders/{id}/ship`   | 发货订单   |
| `POST` | `/api/orders/{id}/cancel` | 取消订单   |

### 4.2 Order 查询侧（读）

| 方法    | 路径                                        | 说明                 |
|-------|-------------------------------------------|--------------------|
| `GET` | `/api/orders/{id}`                        | 按 ID 查询            |
| `GET` | `/api/orders/by-no`                       | 按订单编号查询            |
| `GET` | `/api/orders/query/list`                  | 分页占位               |
| `GET` | `/api/orders/query/stats`                 | **CQRS 缓存读**：订单统计  |
| `GET` | `/api/orders/query/buyer/{buyerId}/count` | **CQRS 缓存读**：买家订单数 |
| `GET` | `/api/orders/query/detail/{id}`           | **CQRS 缓存读**：订单详情  |

### 4.3 Goods 命令侧（写）

| 方法       | 路径                       | 说明     |
|----------|--------------------------|--------|
| `POST`   | `/api/goods`             | 创建商品   |
| `PUT`    | `/api/goods/{id}`        | 更新商品   |
| `PUT`    | `/api/goods/{id}/status` | 调整商品状态 |
| `DELETE` | `/api/goods/{id}`        | 软删除商品  |

### 4.4 Goods 查询侧（读）

| 方法    | 路径                           | 说明                 |
|-------|------------------------------|--------------------|
| `GET` | `/api/goods/{id}`            | 按 ID 查询            |
| `GET` | `/api/goods/by-code`         | 按编码查询              |
| `GET` | `/api/goods/page`            | 充血分页查询             |
| `GET` | `/api/goods/list`            | 充血列表查询             |
| `GET` | `/api/goods/count`           | 充血计数               |
| `GET` | `/api/goods/query/list`      | **CQRS 缓存读**：商品列表  |
| `GET` | `/api/goods/query/{id}`      | **CQRS 缓存读**：商品详情  |
| `GET` | `/api/goods/query/by-status` | **CQRS 缓存读**：按状态查询 |

---

## 5. 运行

### 5.1 启动 HTTP 服务

```bash
mvn -pl ddd4j/ddd4j-samples/ddd4j-sample-javalin-cqrs exec:java
```

启动后会看到：

```
[Bootstrap] SPI initialized: DomainEventPublisher / MQEventPublisher / SubjectProvider / I18nProvider
[Bootstrap] CacheKit domain initialized: order-stats / buyer-order-count / goods-detail / goods-list
[Bootstrap] Repositories registered: Order / Goods
Javalin (CQRS) started on http://localhost:7001
```

### 5.2 体验完整 CQRS 流程

```bash
# 1. 创建商品
curl -X POST http://localhost:7001/api/goods \
     -H 'Content-Type: application/json' \
     -d '{"code":"SKU-001","name":"iPhone","price":"5999.00","stock":100}'

# 2. 创建订单
curl -X POST http://localhost:7001/api/orders \
     -H 'Content-Type: application/json' \
     -d '{"orderNo":"O-001","buyerId":"u-001","buyerName":"Alice"}'

# 3. 添加订单行
curl -X POST http://localhost:7001/api/orders/{id}/lines \
     -H 'Content-Type: application/json' \
     -d '{"goodsId":"1","goodsName":"iPhone","quantity":1,"unitPrice":"5999.00"}'

# 4. 支付
curl -X POST http://localhost:7001/api/orders/{id}/pay

# 5. CQRS 缓存读：订单统计
curl http://localhost:7001/api/orders/query/stats

# 6. CQRS 缓存读：商品列表
curl http://localhost:7001/api/goods/query/list
```

### 5.3 运行测试

```bash
mvn -pl ddd4j/ddd4j-samples/ddd4j-sample-javalin-cqrs test
```

---

## 6. 替换真实实现

本示例所有组件都是 In-Memory 版本。生产环境替换点：

| 示例实现                                      | 生产替换                                                     |
|-------------------------------------------|----------------------------------------------------------|
| `InMemoryOrderRepository`                 | MyBatis-Plus / JDBI / Hibernate                          |
| `InMemoryGoodsRepository`                 | 同上                                                       |
| `OrderCacheService` / `GoodsCacheService` | 替换为 Caffeine / Redis / Redisson 后端（业务代码通过 `CacheKit` 解耦） |
| `NoOpDomainEventPublisher`                | Guava EventBus / Reactor Sinks / Akka Actor              |
| `NoOpMQEventPublisher`                    | Kafka / RabbitMQ / RocketMQ 适配                           |
| `AnonymousSubjectProvider`                | sa-token / shiro / spring-security 适配                    |

业务代码（`order/domain/`、`goods/domain/`、`application/`）**完全不需要修改**。

---

## 7. 关键技术细节

### 7.1 进程内事件总线（`OrderEventListener`）

`OrderEventListener` 通过 ddd4j `TypedEventDispatcher` 按事件类型分派：

```java
TypedEventHandler<OrderCreatedEvent> onCreated = event -> log.info("订单已创建: {}", event.source());
TypedEventHandler<OrderPaidEvent>     onPaid   = event -> log.info("订单已支付: {}", event.source());
// ...5 个事件分别处理
```

`OrderApplicationService` 写完聚合后，通过 `DomainEventPublisher.publish` 发布事件到总线，
`OrderEventListener` 自动接收并处理。

### 7.2 CacheKit 缓存读侧

`OrderCacheService` / `GoodsCacheService` 使用 ddd4j `CacheKit` 缓存门面：

```java
// 读：cache-first
Object cached = CacheKit.get(BIZ_ORDER_STATS, STATS_KEY);
if (cached instanceof Map) return (Map<String, Object>) cached;
// miss：实时计算并 put
List<Order> all = repository.findAll();
CacheKit.put(BIZ_ORDER_STATS, STATS_KEY, stats);

// 写后失效缓存
CacheKit.invalidate(BIZ_ORDER_STATS, STATS_KEY);
```

切换缓存后端（Caffeine → Redis）业务代码零修改。

### 7.3 双轨业务并存

- **第二轨**（Order）：充血聚合，业务规则全部下沉到 `Order.addLine() / pay() / ship() / cancel()`，
  5 个 `Order*Event` 在领域方法中注册。
- **第三轨**（Goods）：PO + `RichRepository<Goods, GoodsId, GoodsQuery>`，充血查询对象 `GoodsQuery`
  支持多条件组合查询（`code like / name like / status / price range`）。

两个轨道共用 `ddd4j-core` 的 `R<T>` 响应包装和 Jackson JSON 序列化，业务响应格式一致。

---

## 8. 相关项目

- [`ddd4j-sample-javalin`](../ddd4j-sample-javalin/README.md) — Javalin 框架启动示例（非 CQRS 版本）
- 外部 `ddd4j-boot-samples/ddd4j-boot-sample-order` — Spring Boot CQRS 对照
- [`ddd4j-sample-quarkus-cqrs`](../ddd4j-sample-quarkus-cqrs) — 同 CQRS 思路在 Quarkus 平台
- ddd4j 核心抽象：`ddd4j-core`（`io.ddd4j.core.ddd.*`、`io.ddd4j.core.context.*`）
