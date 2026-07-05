# ddd4j-sample-quarkus-cqrs

> Quarkus + ddd4j **Order + Goods 双业务 / CQRS** 示例：第二轨（Order 充血模型） +
> 第三轨（Goods Model/Query CRUD） + 命令/查询分离 + CacheKit 缓存读侧 +
> Quarkus CDI `@Observes` 事件桥接。

---

## 1. 这是什么

本示例在 **Quarkus 3.15 LTS** 上同时演示两个业务轨道 + CQRS 读写分离：

| 轨道 | 业务包 | 风格 |
| --- | --- | --- |
| **第二轨**（充血模型） | `order/` | `Order` 聚合根 + 5 个领域事件 + `OrderApplicationService`（`@ApplicationScoped`） |
| **第三轨**（Model/Query） | `goods/` | `Goods` PO + `RichRepository` + `GoodsQuery`（`@ApplicationScoped`） |
| **CQRS 增强** | `cache/` + 读侧 Resource | `CacheKit` 缓存读侧统计 |
| **CDI 事件桥接** | `event/` | `@Observes Order*Event` 同步监听 |

---

## 2. 演示的数据流

```
HTTP POST /api/orders                  HTTP POST /api/goods
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

### 4.1 Order 命令侧（写）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST`   | `/api/orders`             | 创建草稿订单 |
| `POST`   | `/api/orders/{id}/lines`  | 添加订单行 |
| `POST`   | `/api/orders/{id}/pay`    | 支付订单 |
| `POST`   | `/api/orders/{id}/ship`   | 发货订单 |
| `POST`   | `/api/orders/{id}/cancel` | 取消订单 |

### 4.2 Order 查询侧（读）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/orders/{id}` | 按 ID 查询 |
| `GET` | `/api/orders/by-no` | 按订单编号查询 |
| `GET` | `/api/orders` | 列出全部订单 |
| `GET` | `/api/orders/query/stats` | **CQRS 缓存读**：订单统计 |
| `GET` | `/api/orders/query/buyer/{buyerId}/count` | **CQRS 缓存读**：买家订单数 |
| `GET` | `/api/orders/query/detail/{id}` | **CQRS 缓存读**：订单详情 |

### 4.3 Goods 命令侧（写）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST`   | `/api/goods`             | 创建商品 |
| `PUT`    | `/api/goods/{id}`        | 更新商品 |
| `PUT`    | `/api/goods/{id}/status` | 调整商品状态 |
| `DELETE` | `/api/goods/{id}`        | 软删除商品 |

### 4.4 Goods 查询侧（读）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/goods/{id}` | 按 ID 查询 |
| `GET` | `/api/goods/by-code` | 按编码查询 |
| `GET` | `/api/goods/page` | 充血分页查询 |
| `GET` | `/api/goods/list` | 充血列表查询 |
| `GET` | `/api/goods/count` | 充血计数 |
| `GET` | `/api/goods/query/list` | **CQRS 缓存读**：商品列表 |
| `GET` | `/api/goods/query/{id}` | **CQRS 缓存读**：商品详情 |

---

## 5. 运行

### 5.1 启动开发模式（热重载）

```bash
mvn quarkus:dev
```

应用启动后访问：

```bash
# 1. 创建商品
curl -X POST http://localhost:8080/api/goods \
     -H 'Content-Type: application/json' \
     -d '{"code":"SKU-001","name":"iPhone","price":"5999.00","stock":100}'

# 2. 创建订单
curl -X POST http://localhost:8080/api/orders \
     -H 'Content-Type: application/json' \
     -d '{"orderNo":"O-001","buyerId":"u-001","buyerName":"Alice"}'

# 3. 添加订单行 + 支付
curl -X POST http://localhost:8080/api/orders/{id}/lines \
     -H 'Content-Type: application/json' \
     -d '{"goodsId":"1","goodsName":"iPhone","quantity":1,"unitPrice":"5999.00"}'
curl -X POST http://localhost:8080/api/orders/{id}/pay

# 4. CQRS 缓存读：订单统计
curl http://localhost:8080/api/orders/query/stats

# 5. CQRS 缓存读：商品列表
curl http://localhost:8080/api/goods/query/list
```

启动日志中应能看到：

```
[OrderEventObserver] 订单已创建: orderId=xxx
[OrderEventObserver] 订单已支付: orderId=xxx
```

——这意味着 CDI `@Observes` 链路已通。

### 5.2 打包 & 运行

```bash
mvn quarkus:build
java -jar target/quarkus-app/quarkus-run.jar
```

---

## 6. 关键依赖

| 依赖 | 作用 |
| --- | --- |
| `ddd4j-core` | CQRS/ES 抽象（AggregateRoot / Repository / DomainEvent） |
| `ddd4j-annotation` | 构造型注解 |
| `ddd4j-kit` | 工具包（StrKit / CollKit） |
| `ddd4j-runtime-quarkus` | 启动期自动注入 4 个核心 SPI（DddContextInitializer） |
| `ddd4j-web-quarkus` | TenantAwareResource、DefaultExceptionHandler |
| `ddd4j-cache` | CacheKit 缓存门面 |
| `quarkus-rest` | Quarkus 3.x REST 实现 |
| `quarkus-arc` | CDI 容器 |
| `jackson-databind` | 值对象 / 事件的 JSON 序列化 |

---

## 7. 生产化建议

| 示例组件 | 生产建议 |
| --- | --- |
| `InMemoryOrderRepository` | JDBI / MyBatis-Plus / Hibernate |
| `InMemoryGoodsRepository` | 同上 |
| `OrderCacheService` / `GoodsCacheService` | 替换为 Caffeine / Redis / Redisson 后端 |
| `@Observes OrderEvent` | 改为 `@ObservesAsync`（需 quarkus-vertx） |

---

## 8. 相关项目

- [`ddd4j-sample-quarkus`](../ddd4j-sample-quarkus) — Quarkus 启动示例（演示 4 核心 SPI 与基础装配，非 CQRS）
- [`ddd4j-sample-spring-cqrs`](../ddd4j-sample-spring-cqrs) — 同 CQRS 思路在 Spring 平台
- [`ddd4j-sample-javalin-cqrs`](../ddd4j-sample-javalin-cqrs) — 同 CQRS 思路在 Javalin 平台

---

## 9. 许可

与 ddd4j 主项目一致：[Apache License 2.0](../../LICENSE)