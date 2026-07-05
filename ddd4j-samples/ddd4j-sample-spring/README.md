# ddd4j-sample-spring

ddd4j + Spring Boot **完整 DDD/CQRS 示例工程**，演示 ddd4j 的所有核心能力。

本示例**同时演示双轨 DDD 业务**：
- **第二轨（充血模型）**：`order` 子模块 - `Order` 聚合根完整业务方法（addLine/pay/ship/cancel）
- **第三轨（Model/Query 轻量 PO）**：`goods` 子模块 - `Goods` PO 实体 + `GoodsQuery` 充血查询

## 演示内容

| 能力 | 实现 | 轨 |
|------|------|-----|
| **充血聚合** | `Order` 继承 `AggregateRoot`，封装 draft/addLine/pay/ship/cancel 完整业务行为 | 第二轨 |
| **值对象** | `Money` 实现 `ValueObject`（record），add/multiply/discount，币种不变式 | 第二轨 |
| **领域事件** | `OrderCreatedEvent` 等继承 `DomainEvent`，聚合内部 `registerEvent` 注册 | 第二轨 |
| **仓储** | `OrderRepository` 接口（`@DomainRepository`）+ `InMemoryOrderRepository` 实现 | 第二轨 |
| **领域服务** | `OrderDomainService`（`@DomainService`），演示跨聚合的折扣计算 | 第二轨 |
| **应用服务** | `OrderApplicationService`（`@ApplicationService`），编排用例 + 事务边界 | 第二轨 |
| **CQRS 查询** | `OrderQueryService`（读侧）+ `OrderQueryController`（独立查询端点） | 第二轨 |
| **PO 实体** | `Goods`（`@DomainEntity(aggregateRoot = true)`），`@Data` 轻量 PO 形态 | 第三轨 |
| **充血查询** | `GoodsQuery` 继承 `Query`，自带 `page()` / `list()` / `count()` 能力 | 第三轨 |
| **Goods 仓储** | `GoodsRepository` 接口（`@DomainRepository`）+ `InMemoryGoodsRepository`（实现 `RichRepository`） | 第三轨 |
| **Goods 应用服务** | `GoodsApplicationService`（`@ApplicationService`），轻量 CRUD 编排 | 第三轨 |
| **RepositoryRegistry 注册** | `GoodsConfig` 启动期将仓储注册到 ddd4j 全局上下文 | 第三轨 |
| **缓存** | `OrderCacheService` 使用 `CacheKit` 缓存门面（Caffeine 本地缓存） | 第二轨 |
| **MQ 事件** | `@MQEventListener` 注解 + `ddd4j-mq-disruptor` 本地 RingBuffer MQ | 第二轨 |
| **Spring 事件桥接** | `SpringDomainEventPublisher` 自动桥接 ddd4j `DomainEvent` → Spring `@EventListener` | 第二轨 |
| **统一响应** | `R<T>` 响应包装 + `GlobalRestExceptionAdvice` 全局异常处理 | 双轨 |

## 技术栈

- **ddd4j-core**：聚合根、值对象、实体、领域事件、仓储、查询
- **ddd4j-annotation**：DDD 注解契约
- **ddd4j-runtime-spring**：Spring 运行时绑定（SPI 自动注入、`@DomainService` / `@ApplicationService` / `@DomainRepository`）
- **ddd4j-web-webmvc**：`R<T>` 响应包装、`GlobalRestExceptionAdvice` 全局异常处理
- **ddd4j-cache**：`CacheKit` 缓存门面（Caffeine 本地缓存）
- **ddd4j-mq-core** + **ddd4j-mq-spring**：MQ 契约、`@MQEventListener` 自动注册
- **ddd4j-mq-disruptor**：LMAX Disruptor 进程内本地 MQ（无外部 Broker 依赖）
- **Spring Boot 3.x** + **Lombok**

## 项目结构

```
src/main/java/io/ddd4j/sample/spring/
├── SpringSampleApplication.java           # @SpringBootApplication 启动类
├── order/                                  # 第二轨：充血模型
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Order.java                 # 充血聚合根（继承 AggregateRoot）
│   │   │   ├── OrderLine.java             # 实体（订单行）
│   │   │   ├── OrderStatus.java           # 订单状态枚举
│   │   │   └── Money.java                 # 值对象（record 实现 ValueObject）
│   │   ├── event/
│   │   │   ├── OrderCreatedEvent.java     # 订单创建事件
│   │   │   ├── OrderPaidEvent.java        # 订单支付事件
│   │   │   ├── OrderShippedEvent.java     # 订单发货事件
│   │   │   ├── OrderCancelledEvent.java   # 订单取消事件
│   │   │   └── OrderLineAddedEvent.java   # 订单行添加事件
│   │   ├── repository/
│   │   │   └── OrderRepository.java       # @DomainRepository 仓储接口
│   │   └── service/
│   │       └── OrderDomainService.java    # @DomainService 领域服务
│   ├── application/
│   │   ├── OrderApplicationService.java   # @ApplicationService 编排用例
│   │   ├── OrderQueryService.java         # CQRS 查询服务（读侧）
│   │   ├── CreateOrderCommand.java        # 创建订单命令
│   │   └── AddOrderLineCommand.java       # 添加订单行命令
│   ├── infrastructure/
│   │   └── InMemoryOrderRepository.java   # 内存仓储实现（含 OrderRow 映射层）
│   └── web/
│       ├── OrderController.java           # REST 写侧端点（返回 R<T>）
│       ├── OrderQueryController.java      # CQRS 查询端点
│       └── dto/
│           ├── CreateOrderRequest.java    # 创建订单请求 DTO
│           └── AddOrderLineRequest.java   # 添加订单行请求 DTO
├── goods/                                  # 第三轨：Model/Query 快速 CRUD
│   ├── domain/
│   │   ├── Goods.java                   # PO 实体（@DomainEntity 聚合根）
│   │   ├── GoodsId.java                 # 值对象（不可变 ID 包装）
│   │   ├── GoodsStatus.java             # 状态枚举
│   │   ├── GoodsRepository.java         # @DomainRepository 仓储接口
│   │   └── GoodsQuery.java              # 充血查询对象（extends Query<Goods>）
│   ├── application/
│   │   └── GoodsApplicationService.java # @ApplicationService 轻量 CRUD 编排
│   ├── infrastructure/
│   │   └── InMemoryGoodsRepository.java # 内存仓储（实现 RichRepository 支持充血查询）
│   ├── config/
│   │   └── GoodsConfig.java             # RepositoryRegistry 注册 Goods
│   └── web/
│       ├── GoodsController.java         # REST 写侧端点（/api/goods）
│       ├── GoodsQueryController.java    # CQRS 读侧端点（充血分页/列表/计数）
│       └── dto/
│           ├── CreateGoodsRequest.java  # 创建商品请求 DTO（record）
│           └── UpdateGoodsRequest.java  # 更新商品请求 DTO（record）
├── event/
│   └── OrderEventListener.java            # Spring @EventListener 监听领域事件
├── mq/
│   ├── OrderMqListener.java               # @MQEventListener MQ 消费者
│   └── config/
│       └── DisruptorMqConfig.java         # Disruptor 本地 MQ 配置
├── cache/
│   └── OrderCacheService.java             # CacheKit 缓存服务
└── config/
    └── SpringSampleConfig.java            # @Configuration 缓存初始化
```

## 启动

```bash
# 编译并启动（端口 8080）
mvn spring-boot:run
```

## API 使用示例

### 创建订单

```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"orderNo":"ORDER-001","buyerId":"BUYER-1","buyerName":"Alice"}'
```

### 查询订单

```bash
curl http://localhost:8080/orders/{orderId}
```

### 添加订单行

```bash
curl -X POST http://localhost:8080/orders/{orderId}/lines \
  -H "Content-Type: application/json" \
  -d '{"goodsId":"SKU-1","goodsName":"DDD Book","quantity":2,"unitPrice":39.80}'
```

### 支付订单

```bash
curl -X POST http://localhost:8080/orders/{orderId}/pay
```

### 发货订单

```bash
curl -X POST http://localhost:8080/orders/{orderId}/ship
```

### 取消订单

```bash
curl -X POST http://localhost:8080/orders/{orderId}/cancel
```

### 分页查询（CQRS 读侧）

```bash
curl "http://localhost:8080/orders?page=1&pageSize=10"
```

### 订单详情（CQRS 读侧）

```bash
curl http://localhost:8080/orders/{orderId}/detail
```

### 预览折扣

```bash
curl http://localhost:8080/orders/{orderId}/discount
```

## Goods CRUD 端点（第三轨：Model/Query 快速 CRUD 模式）

> 与 Order 充血模型形成对比：本轨使用普通 PO + 充血查询对象，业务规则由应用服务编排。

### 创建商品

```bash
curl -X POST http://localhost:8080/api/goods \
  -H "Content-Type: application/json" \
  -d '{"code":"SKU-001","name":"iPhone 15","price":5999.00,"stock":100}'
```

### 更新商品

```bash
curl -X PUT http://localhost:8080/api/goods/{id} \
  -H "Content-Type: application/json" \
  -d '{"name":"iPhone 15 Pro","price":7999.00}'
```

### 调整商品状态

```bash
curl -X PUT "http://localhost:8080/api/goods/{id}/status?status=ON_SALE"
```

### 软删除商品

```bash
curl -X DELETE http://localhost:8080/api/goods/{id}
```

### 按 ID 查询商品

```bash
curl http://localhost:8080/api/goods/{id}
```

### 按编码查询商品

```bash
curl "http://localhost:8080/api/goods/by-code?code=SKU-001"
```

### 充血分页查询

```bash
# 查 ON_SALE 状态、按创建时间倒序、第 1 页、每页 20 条
curl "http://localhost:8080/api/goods/page?status=ON_SALE&current=1&size=20&orderBys=createTime_DESC"

# 名称模糊匹配 "iPhone"、价格区间 1000-10000
curl "http://localhost:8080/api/goods/page?nameLike=iPhone&priceMin=1000&priceMax=10000&orderBys=price_ASC"
```

### 充血列表查询

```bash
curl "http://localhost:8080/api/goods/list?status=ON_SALE&orderBys=id_ASC"
```

### 充血计数

```bash
curl "http://localhost:8080/api/goods/count?status=ON_SALE"
```

## 双轨 DDD 对比（Order vs Goods）

| 维度 | 第二轨：Order（充血模型） | 第三轨：Goods（轻量 PO） |
|------|------------------------|--------------------------|
| **模型形态** | 充血聚合根（继承 `AggregateRoot`） | 普通 PO 实体（`@Data` + `AggregateRoot`） |
| **业务方法** | `addLine()` / `pay()` / `ship()` / `cancel()` | 无（仅有 getter/setter） |
| **状态机** | 聚合内不变量 + 状态断言 | 服务层校验 |
| **业务规则** | 沉淀在聚合方法内 | 在 `GoodsApplicationService` 内 |
| **领域事件** | 通过 `registerEvent()` 在聚合方法内发布 | 无（保持轻量） |
| **持久化映射** | 仓储内置 `OrderRow` / `OrderLineRow` PO 映射 | 仓储直接以 `Goods` 作为持久化结构（零映射） |
| **查询方式** | `OrderQueryService` 自定义分页逻辑 | `GoodsQuery` 继承 `Query`，自带 `page()` / `list()` / `count()` |
| **适用场景** | 业务复杂、状态机多、有跨字段不变量 | 简单 CRUD、状态少、无复杂业务规则 |
| **代码量** | 较多（充血方法 + 事件 + 不变量） | 极少（只描述数据） |

**经验法则**：核心业务用第二轨（充血），周边数据/字典用第三轨（Model/Query）。
两轨可在同一 Spring Boot 应用中并存，互不干扰。

## 核心设计说明

### 事件发布链路

```
Order.pay() → registerEvent(OrderPaidEvent)
  → OrderApplicationService.publishDomainEvents()
    → DomainEvent.publish()
      → SPI 查找 SpringDomainEventPublisher
        → ApplicationEventPublisher.publishEvent()
          → Spring @EventListener（OrderEventListener）
```

### MQ 事件链路

```
MQEvent.publish()
  → SPI 查找 MQEventPublisher（DisruptorMQEventPublisher）
    → RingBuffer 投递
      → @MQEventListener 消费（OrderMqListener）
```

### ddd4j-runtime-spring 自动 SPI 绑定

Spring Boot 启动时，`ddd4j-runtime-spring` 模块自动：
1. 将 `SpringDomainEventPublisher` 注册到 ddd4j SPI 上下文
2. 扫描 `@DomainService` / `@ApplicationService` / `@DomainRepository` 注解的类
3. 注入对应的 Spring Bean

### ddd4j-web-webmvc 的 R<T> 响应包装

控制器方法返回 `R<T>`，由 `GlobalResponseRAdvice` 自动包装为标准响应格式：
```json
{
  "code": 200,
  "msg": "success",
  "data": { ... }
}
```

异常由 `GlobalRestExceptionAdvice` 自动捕获并返回统一错误响应。

### Goods 充血查询（RepositoryRegistry 注册）

第三轨 Goods 业务通过 `GoodsConfig#registerGoodsRepository()` 在启动期
将 `InMemoryGoodsRepository` 注册到 ddd4j 的 `RepositoryRegistry`：

```java
RepositoryRegistry.register(Goods.class, GoodsQuery.class, goodsRepository);
```

注册后，`GoodsQuery.page()` 等充血查询方法会通过
`RepositoryRegistry.repository(Goods.class)` 自动查找到仓储实例，
业务侧无需手动注入仓储到查询对象。

> 生产环境若使用 `ddd4j-data-mybatis`，由 Spring Boot 自动配置完成注册，
> 业务侧无需编写 `GoodsConfig` 类似配置。
