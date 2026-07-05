# ddd4j-sample-spring-cqrs

> ddd4j + Spring Boot 3.5.x 完整 **Order + Goods 双业务 / CQRS** 示例：
> 第二轨（Order 充血模型 + 5 个领域事件） + 第三轨（Goods Model/Query CRUD） +
> 命令/查询分离 + CacheKit 缓存读侧 + Spring 事件桥接。

## 这是什么？

本示例同时演示 ddd4j 框架在 Spring Boot 平台上的 **两个业务轨道 + CQRS 读写分离 + 缓存增强**：

| 轨道 | 业务包 | 风格 | 关键产物 |
| --- | --- | --- | --- |
| **第二轨**（充血模型） | `order/` | `Order` 聚合根 + 5 个领域事件 + `OrderApplicationService` | `OrderController` / `OrderQueryController` / `OrderCQRSQueryController` |
| **第三轨**（Model/Query） | `goods/` | `Goods` PO + `RichRepository` + `GoodsQuery` 充血查询 | `GoodsController` / `GoodsQueryController` / `GoodsReadController` |
| **CQRS 增强** | `cache/` + 读侧 Controller | `CacheKit` 缓存读侧统计 | `OrderCacheService` / `GoodsCacheService` |
| **Spring 事件桥接** | `event/` | `@EventListener` 监听领域事件 | `OrderEventListener` |

## 与其他示例的关系

| 示例 | 关系 |
| --- | --- |
| [`ddd4j-sample-spring`](../ddd4j-sample-spring) | 同框架 **非 CQRS** 版本（双轨业务 + 缓存，但无命令/查询分离） |
| [`ddd4j-sample-javalin-cqrs`](../ddd4j-sample-javalin-cqrs) | 同 CQRS 思路在 **Javalin** 平台的手动 DI 绑定 |
| [`ddd4j-sample-quarkus-cqrs`](../ddd4j-sample-quarkus-cqrs) | 同 CQRS 思路在 **Quarkus** 平台的 CDI 绑定 |

---

## 1. 演示的数据流

```
[REST] POST /api/orders           [REST] POST /api/goods
        │                                  │
        ▼                                  ▼
OrderController (写)               GoodsController (写)
        │                                  │
        ▼                                  ▼
OrderApplicationService            GoodsApplicationService
   │ Order.draft / addLine / pay         │ Goods CRUD
   │ 产生 Order*Event                    │ 直接写 RichRepository
        │                                  │
        ├─ OrderRepository.save           ├─ InMemoryGoodsRepository.save
        │                                  │
        ▼                                  ▼
InMemoryOrderRepository            InMemoryGoodsRepository
                                          │
                                          ▼
SpringDomainEventPublisher        GoodsCacheService.evictOnWrite
        │                                  │
        ▼                                  ▼
OrderEventListener                CacheKit.invalidate(GOODS_*)
(@EventListener)                          │
                                          ▼
                                  /api/goods/query/* (读)

[REST] GET /api/orders/query/stats  [REST] GET /api/goods/query/list
        │                                  │
        ▼                                  ▼
OrderCQRSQueryController           GoodsReadController
        │                                  │
        ▼                                  ▼
OrderCacheService                  GoodsCacheService
   │ CacheKit.get → hit / miss           │ CacheKit.get → hit / miss
   │ miss 时调用 repository.findAll      │ miss 时调用 GoodsQuery 充血查询
```

---

## 2. 核心模块

### 2.1 第二轨：Order 充血模型

```
io.ddd4j.sample.spring.cqrs.order/
├── domain/
│   ├── model/
│   │   ├── Order.java                 // 聚合根（AggregateRoot<Order, String>）
│   │   ├── OrderLine.java             // 实体
│   │   ├── OrderStatus.java           // 枚举（DRAFT / PAID / SHIPPED / CANCELLED）
│   │   └── Money.java                 // 值对象（金额 + 币种）
│   ├── event/
│   │   ├── OrderCreatedEvent.java
│   │   ├── OrderLineAddedEvent.java
│   │   ├── OrderPaidEvent.java
│   │   ├── OrderShippedEvent.java
│   │   └── OrderCancelledEvent.java   // 5 个领域事件
│   ├── repository/
│   │   └── OrderRepository.java       // @DomainRepository 标注
│   └── service/
│       └── OrderDomainService.java    // 领域服务
├── application/
│   ├── OrderApplicationService.java   // @Service 命令侧
│   ├── OrderQueryService.java         // @Service 简单查询侧
│   ├── CreateOrderCommand.java
│   └── AddOrderLineCommand.java
├── infrastructure/
│   └── InMemoryOrderRepository.java   // @Repository 内存实现
└── web/
    ├── OrderController.java           // @RestController 写侧
    ├── OrderQueryController.java      // @RestController 简单读侧
    ├── OrderCQRSQueryController.java  // @RestController 缓存读侧 (/api/orders/query/*)
    └── dto/
        ├── CreateOrderRequest.java
        ├── AddOrderLineRequest.java
        └── OrderResponse.java
```

### 2.2 第三轨：Goods Model/Query CRUD

```
io.ddd4j.sample.spring.cqrs.goods/
├── domain/
│   ├── Goods.java                     // PO + RichRepository 模式
│   ├── GoodsId.java
│   ├── GoodsStatus.java
│   ├── GoodsRepository.java           // RichRepository<Goods, GoodsId, GoodsQuery>
│   └── GoodsQuery.java                // 充血查询对象
├── application/
│   └── GoodsApplicationService.java   // @Service
├── config/
│   └── GoodsConfig.java               // @Configuration
├── infrastructure/
│   └── InMemoryGoodsRepository.java   // @Repository
└── web/
    ├── GoodsController.java           // @RestController 写侧 (/api/goods)
    ├── GoodsQueryController.java      // @RestController 读侧 (/api/goods/query)
    ├── GoodsReadController.java       // @RestController 缓存读侧 (/api/goods/query/read/*)
    └── dto/
        ├── CreateGoodsRequest.java
        └── UpdateGoodsRequest.java
```

### 2.3 CQRS 缓存层

```
io.ddd4j.sample.spring.cqrs.cache/
├── OrderCacheService.java             // @Service 订单统计缓存
└── GoodsCacheService.java             // @Service 商品缓存
```

`OrderCacheService` 缓存域：
- `order-stats`：订单状态分布统计
- `buyer-order-count`：买家订单计数

`GoodsCacheService` 缓存域：
- `goods-detail`：商品详情
- `goods-list`：商品列表查询结果

### 2.4 Spring 事件桥接

```
io.ddd4j.sample.spring.cqrs.event/
└── OrderEventListener.java            // @EventListener 监听 Order*Event
```

---

## 3. REST API

### 3.1 Order 命令侧（写）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST`   | `/api/orders`               | 创建草稿订单 |
| `POST`   | `/api/orders/{id}/lines`    | 添加订单行 |
| `POST`   | `/api/orders/{id}/pay`      | 支付订单 |
| `POST`   | `/api/orders/{id}/ship`     | 发货订单 |
| `POST`   | `/api/orders/{id}/cancel`   | 取消订单 |

### 3.2 Order 查询侧（读）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/orders/{id}` | 按 ID 查询 |
| `GET` | `/api/orders/by-no` | 按订单编号查询 |
| `GET` | `/api/orders` | 列出全部订单 |
| `GET` | `/api/orders/query/stats` | **CQRS 缓存读**：订单统计 |
| `GET` | `/api/orders/query/buyer/{buyerId}/count` | **CQRS 缓存读**：买家订单数 |
| `GET` | `/api/orders/query/detail/{id}` | **CQRS 缓存读**：订单详情 |

### 3.3 Goods 命令侧（写）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST`   | `/api/goods`             | 创建商品 |
| `PUT`    | `/api/goods/{id}`        | 更新商品 |
| `PUT`    | `/api/goods/{id}/status` | 调整商品状态 |
| `DELETE` | `/api/goods/{id}`        | 软删除商品 |

### 3.4 Goods 查询侧（读）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/goods/{id}` | 按 ID 查询 |
| `GET` | `/api/goods/by-code` | 按编码查询 |
| `GET` | `/api/goods/page` | 充血分页查询 |
| `GET` | `/api/goods/list` | 充血列表查询 |
| `GET` | `/api/goods/count` | 充血计数 |
| `GET` | `/api/goods/query/list` | **CQRS 缓存读**：商品列表 |
| `GET` | `/api/goods/query/{id}` | **CQRS 缓存读**：商品详情 |
| `GET` | `/api/goods/query/by-status` | **CQRS 缓存读**：按状态查询 |

---

## 4. 快速体验

```bash
# 启动应用（默认 8081 端口）
mvn spring-boot:run

# 1. 创建商品
curl -X POST http://localhost:8081/api/goods \
  -H 'Content-Type: application/json' \
  -d '{"code":"SKU-001","name":"iPhone","price":"5999.00","stock":100}'

# 2. 创建订单
curl -X POST http://localhost:8081/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"orderNo":"O-001","buyerId":"u-001","buyerName":"Alice"}'

# 3. 添加订单行
curl -X POST http://localhost:8081/api/orders/{id}/lines \
  -H 'Content-Type: application/json' \
  -d '{"goodsId":"1","goodsName":"iPhone","quantity":1,"unitPrice":"5999.00"}'

# 4. 支付 + 发货
curl -X POST http://localhost:8081/api/orders/{id}/pay
curl -X POST http://localhost:8081/api/orders/{id}/ship

# 5. CQRS 缓存读：订单统计
curl http://localhost:8081/api/orders/query/stats

# 6. CQRS 缓存读：商品列表
curl http://localhost:8081/api/goods/query/list
```

---

## 5. 启动方式

```bash
# 在 ddd4j 仓库根目录或本模块目录下
mvn spring-boot:run

# 或先打包再运行
mvn clean package
java -jar target/ddd4j-sample-spring-cqrs-*.jar
```

默认监听 **8081** 端口（见 `application.yml`）。

---

## 6. 技术栈

- Spring Boot 3.5.6
- ddd4j-core 2.0.x（AggregateRoot / Repository / DomainEvent / Page / Query / R）
- ddd4j-annotation 2.0.x（@DomainRepository / @CreateEvent / @DeleteEvent）
- ddd4j-kit 2.0.x（CollKit / StrKit）
- ddd4j-cache 2.0.x（CacheKit 缓存门面）
- ddd4j-runtime-spring 2.0.x（SpringDomainEventPublisher + Spring SPI 绑定）
- ddd4j-web-webmvc 2.0.x（Web MVC 适配）
- Lombok、Jackson

---

## 7. 设计要点

1. **业务代码零框架耦合**：`Order` / `OrderEvent` / `GoodsQuery` / `OrderApplicationService` 都可独立于 Spring 运行；
   Spring 注解只在跨边界类（应用服务、仓储、控制器、缓存服务）上。
2. **双轨业务**：第二轨（Order 充血聚合） + 第三轨（Goods Model/Query CRUD） 并存。
3. **CQRS 命令/查询分离**：
   - Order：`OrderController`（写）vs `OrderCQRSQueryController`（缓存读）
   - Goods：`GoodsController`（写）vs `GoodsReadController`（缓存读）
4. **CacheKit 缓存读侧**：`OrderCacheService` / `GoodsCacheService` 用 `CacheKit.get/put/invalidate` 实现缓存优先读，
   写操作完成后调用 `evictOnWrite` / `invalidate` 失效缓存。
5. **Spring 事件桥接**：`OrderEventListener` 通过 `@EventListener` 监听 ddd4j 领域事件，
   让业务代码保持零框架耦合语义的同时享受 Spring 事件基础设施。

---

## 8. 许可

本示例遵循 ddd4j 主仓库的同一许可证。