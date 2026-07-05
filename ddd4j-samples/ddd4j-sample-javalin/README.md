# ddd4j-sample-javalin

ddd4j 在 **Javalin 框架**下的完整业务示例：在最小 SPI 注入演示之上，**额外**集成
**第二轨（Order 充血模型）** 与 **第三轨（Goods Model/Query）** 两条 DDD 业务线，
方便业务方在同一进程内对比两种建模风格。

## 🎯 示例目标

本示例同时演示三件事：

1. **Javalin SPI 注入**：保留最小启动示例，4 个核心 SPI 手动注入到 `BaseContext`。
2. **第二轨（充血模型）**：Order 聚合根 + OrderRepository + OrderEvent。
3. **第三轨（Model/Query）**：Goods PO + GoodsQuery + GoodsRepository（实现 RichRepository）。

## 🚀 启动方式

```bash
mvn exec:java
```

Javalin 监听端口 **7000**，启动后会打印：

```
[Bootstrap] DomainEventPublisher = NoOpDomainEventPublisher
Javalin started on http://localhost:7000
Try: curl http://localhost:7000/api/goods/page?current=1&size=10
```

## 🔑 SPI 注入机制

启动类 `JavalinSample` 演示了**手动注入 4 个核心 SPI**：

```java
public static void main(String[] args) {
    // 1. 业务方自己准备 4 个 SPI 实例
    DomainEventPublisher domainEventPublisher = new NoOpDomainEventPublisher();
    MQEventPublisher mqEventPublisher = new NoOpMQEventPublisher();
    SubjectProvider subjectProvider = new AnonymousSubjectProvider();
    I18nProvider i18nProvider = new DefaultI18nProvider();

    // 2. 启动前一次性把 SPI 注入到 JVM 级 BaseContext
    BaseContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, domainEventPublisher);
    BaseContext.inject(SpiKeys.MQ_EVENT_PUBLISHER, MQEventPublisher.class, mqEventPublisher);
    BaseContext.inject(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class, subjectProvider);
    BaseContext.inject(SpiKeys.I18N_PROVIDER, I18nProvider.class, i18nProvider);

    // 3. 仓储注册到全局注册表（让 GoodsQuery 充血查询能找到对应仓储）
    RepositoryRegistry.register(Order.class, new InMemoryOrderRepository());
    RepositoryRegistry.register(Goods.class, new InMemoryGoodsRepository());

    // 4. 启动 Javalin
    Javalin app = Javalin.create(config -> {
        config.routes.apiBuilder(() -> {
            orderController.routes();
            goodsController.routes();
        });
    });
    app.start(7000);
}
```

业务方代码内部统一通过 `io.ddd4j.core.context.Contexts.inject(...)` 查找 SPI，**零框架耦合**：

```java
DomainEventPublisher publisher = Contexts.injectOrThrow(
        SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class);
```

## 🤔 为什么不需要 ddd4j-runtime-javalin 模块

把 SPI 注入到 `BaseContext` 这件事，本质上就是 4 行 `BaseContext.inject(...)` 调用。
任何基于 Javalin 的工程都可以在自己的 main 方法里完成，没有必要、也不应该有专门模块。

| 框架 | 是否有 DI 容器 | 是否有 ddd4j runtime 模块 | SPI 注入方式 |
|------|----------------|--------------------------|-------------|
| Spring | 有（`ApplicationContext`） | 有（`ddd4j-runtime-spring`） | 容器启动期扫描 `@Bean` 注入 |
| Quarkus | 有（CDI） | 有（`ddd4j-runtime-quarkus`） | CDI Observer 启动期注入 |
| Guice | 有（`Injector`） | 有（`ddd4j-runtime-guice`） | `Ddd4jGuiceModule#configure` 注入 |
| **Javalin** | **无** | **无** | **业务方在 main 里手动 `BaseContext.inject`** |

Spring / Quarkus / Guice 需要独立 runtime 模块，因为它们要在容器启动期**反射拿 SPI Bean**；
Javalin 不存在容器抽象，所以也省掉了这一层。

## 🚦 路由速查

### 第二轨：Order 充血模型（`/api/orders`）

| HTTP | 路径 | 用途 |
|------|------|------|
| `POST` | `/api/orders` | 创建草稿订单 |
| `POST` | `/api/orders/{id}/lines` | 添加订单行 |
| `POST` | `/api/orders/{id}/pay` | 支付订单 |
| `POST` | `/api/orders/{id}/ship` | 发货订单 |
| `POST` | `/api/orders/{id}/cancel` | 取消订单 |
| `GET`  | `/api/orders/{id}` | 按 ID 查询 |
| `GET`  | `/api/orders/by-no?orderNo=xxx` | 按订单编号查询 |

### 第三轨：Goods Model/Query（`/api/goods`）

| HTTP | 路径 | 用途 |
|------|------|------|
| `POST`   | `/api/goods` | 创建商品 |
| `PUT`    | `/api/goods/{id}` | 更新商品 |
| `PUT`    | `/api/goods/{id}/status?status=ON_SALE` | 调整商品状态 |
| `DELETE` | `/api/goods/{id}` | 软删除商品 |
| `GET`    | `/api/goods/{id}` | 按 ID 查询 |
| `GET`    | `/api/goods/by-code?code=SKU-001` | 按编码查询 |
| `GET`    | `/api/goods/page?current=1&size=10&nameLike=iPhone` | 充血分页查询 |
| `GET`    | `/api/goods/list?status=ON_SALE` | 充血列表查询 |
| `GET`    | `/api/goods/count?status=ON_SALE` | 充血计数 |

## 🧪 curl 示例

### 第二轨：Order 充血模型

```bash
# 1. 创建草稿订单（业务规则：订单从草稿开始）
curl -X POST http://localhost:7000/api/orders \
     -H "Content-Type: application/json" \
     -d '{"orderNo":"O-2026-0001","buyerId":"U-1001","buyerName":"Alice"}'

# 2. 添加订单行（仅 DRAFT 状态可执行；业务不变量下沉到 Order 聚合内）
curl -X POST http://localhost:7000/api/orders/{orderId}/lines \
     -H "Content-Type: application/json" \
     -d '{"goodsId":"P-001","goodsName":"iPhone 15","quantity":2,"unitPrice":5999.00}'

# 3. 支付订单（状态机迁移 DRAFT → PAID；自动发布 OrderPaidEvent）
curl -X POST http://localhost:7000/api/orders/{orderId}/pay

# 4. 发货订单（状态机迁移 PAID → SHIPPED）
curl -X POST http://localhost:7000/api/orders/{orderId}/ship

# 5. 取消订单（任意状态可取消，但 SHIPPED 状态不允许）
curl -X POST http://localhost:7000/api/orders/{orderId}/cancel

# 6. 按 ID 查询
curl http://localhost:7000/api/orders/{orderId}

# 7. 按订单编号查询
curl "http://localhost:7000/api/orders/by-no?orderNo=O-2026-0001"
```

> 所有 Order 操作都通过 `Order.draft()` / `order.addLine()` / `order.pay()` 等**充血方法**完成；
> 状态机、不变量、领域事件全部在 `Order` 聚合内，控制器只做"HTTP → 应用服务"翻译。

### 第三轨：Goods Model/Query

```bash
# 1. 创建商品
curl -X POST http://localhost:7000/api/goods \
     -H "Content-Type: application/json" \
     -d '{"code":"SKU-001","name":"iPhone 15","price":5999.00,"stock":100}'

# 2. 更新商品
curl -X PUT http://localhost:7000/api/goods/1001 \
     -H "Content-Type: application/json" \
     -d '{"name":"iPhone 15 Pro","price":7999.00}'

# 3. 调整商品状态（上架）
curl -X PUT "http://localhost:7000/api/goods/1001/status?status=ON_SALE"

# 4. 软删除
curl -X DELETE http://localhost:7000/api/goods/1001

# 5. 按 ID 查询
curl http://localhost:7000/api/goods/1001

# 6. 按编码查询
curl "http://localhost:7000/api/goods/by-code?code=SKU-001"

# 7. 充血分页查询（Query 直接绑定 page/list/count/orderBys/nameLike/status/priceMin/priceMax）
curl "http://localhost:7000/api/goods/page?current=1&size=10&nameLike=iPhone&orderBys=price_DESC"

# 8. 充血列表查询（按状态过滤）
curl "http://localhost:7000/api/goods/list?status=ON_SALE&priceMin=1000"

# 9. 充血计数
curl "http://localhost:7000/api/goods/count?status=ON_SALE"
```

> Goods 是"轻量 PO"：仅含数据字段（`@Data` + Lombok），无状态机、无领域事件；
> 服务层直接 set/get 字段完成 CRUD。`GoodsQuery` 通过 `RichRepository` 享受充血查询能力。

## 🚄 双轨对比

| 维度 | 第二轨：Order | 第三轨：Goods |
|------|---------------|-----------------|
| 聚合根 | `Order extends AggregateRoot` 充血方法 | `Goods extends AggregateRoot` 纯 PO |
| 状态机 | DRAFT → PAID → SHIPPED / CANCELLED | 无（status 字段由服务设置） |
| 业务不变量 | 在 `addLine`/`pay`/`ship`/`cancel` 内校验 | 在 `GoodsApplicationService` 内校验 |
| 领域事件 | 5 个 `DomainEvent`（OrderCreated/LineAdded/Paid/Shipped/Cancelled） | 无 |
| Model/PO 分离 | 有：`Order` ↔ `OrderPO`/`OrderLinePO` | 无：Goods 本身就是 PO |
| 仓储 | 普通 `Repository` | `RichRepository`（支持充血查询） |
| 查询方式 | 按 ID / 按业务键 | `GoodsQuery#page()/#list()/#count()/#one()` |
| 适用场景 | 业务规则复杂、状态迁移多 | 简单 CRUD、读多写少 |

## 📁 项目结构

```
ddd4j-sample-javalin/
├── pom.xml                              # 仅依赖 ddd4j-core + ddd4j-kit + javalin + jackson + lombok
└── src/main/java/io/ddd4j/sample/javalin/
    ├── JavalinSample.java               # 启动类（main + SPI 注入 + 仓储注册 + Javalin 启动）
    ├── spi/                             # 4 个 SPI 示例实现（NoOp / Anonymous / DefaultI18n）
    │   ├── AnonymousSubjectProvider.java
    │   ├── DefaultI18nProvider.java
    │   ├── NoOpDomainEventPublisher.java
    │   └── NoOpMQEventPublisher.java
    ├── order/                           # 第二轨：Order 充血模型
    │   ├── domain/
    │   │   ├── model/
    │   │   │   ├── Order.java           # 聚合根（含 addLine/pay/ship/cancel 充血方法）
    │   │   │   ├── OrderLine.java       # 订单行实体
    │   │   │   ├── OrderStatus.java     # 生命周期枚举
    │   │   │   └── Money.java           # 金额值对象
    │   │   ├── event/
    │   │   │   ├── OrderCreatedEvent.java
    │   │   │   ├── OrderLineAddedEvent.java
    │   │   │   ├── OrderPaidEvent.java
    │   │   │   ├── OrderShippedEvent.java
    │   │   │   └── OrderCancelledEvent.java
    │   │   └── repository/OrderRepository.java
    │   ├── application/OrderApplicationService.java
    │   ├── infrastructure/
    │   │   ├── OrderPO.java             # 持久化对象（Model/PO 分离）
    │   │   ├── OrderLinePO.java
    │   │   └── InMemoryOrderRepository.java   # 实现 Repository + DomainObjectMapper
    │   └── web/OrderController.java     # 7 个 Javalin 路由
    └── goods/                         # 第三轨：Goods Model/Query
        ├── domain/
        │   ├── Goods.java             # 轻量 PO（@Data + Lombok）
        │   ├── GoodsId.java           # 标识值对象
        │   ├── GoodsStatus.java       # 状态枚举
        │   ├── GoodsRepository.java   # 普通 Repository
        │   └── GoodsQuery.java        # 充血查询对象（继承 Query<Goods>）
        ├── application/GoodsApplicationService.java
        ├── infrastructure/InMemoryGoodsRepository.java   # 实现 RichRepository
        └── web/GoodsController.java   # 9 个 Javalin 路由
```

4 个 SPI 文件位于 `spi/` 包下，均为**示例实现**（NoOp / Anonymous），
真实业务应替换为：
- `DomainEventPublisher` → Guava EventBus / Reactor Sinks / Akka Actor
- `MQEventPublisher` → ddd4j-mq-kafka / ddd4j-mq-rabbitmq 等
- `SubjectProvider` → ddd4j-auth-javalin（与 Sa-Token/Shiro 集成）
- `I18nProvider` → 基于 ResourceBundle 的国际化实现

## 🔗 相关示例

| 示例 | 演示内容 |
|------|---------|
| [ddd4j-sample-spring](../ddd4j-sample-spring) | Spring Boot 完整业务（含 DDD/CQRS/Cache/MQ） |
| [ddd4j-sample-quarkus](../ddd4j-sample-quarkus) | Quarkus 完整业务（CDI 启动期注入 SPI） |
| [ddd4j-sample-javalin-satoken](../ddd4j-sample-javalin-satoken) | Javalin + Sa-Token 鉴权 |
| [ddd4j-sample-javalin-shiro](../ddd4j-sample-javalin-shiro) | Javalin + Shiro 鉴权 |
| [ddd4j-sample-rich-model](../ddd4j-sample-rich-model) | 纯 Java 充血模型参考（与本示例 order/ 同源） |
| [ddd4j-sample-model](../ddd4j-sample-model) | 纯 Java 第三轨 Model/Query 参考（与本示例 goods/ 同源） |

## 📄 相关文档

- [ddd4j 主仓库](https://github.com/hiwepy/ddd4j)
- [`BaseContext` SPI 注入文档](../../ddd4j-core/src/main/java/io/ddd4j/core/context/BaseContext.java)
- [`Contexts` 门面查找文档](../../ddd4j-core/src/main/java/io/ddd4j/core/context/Contexts.java)
- [`SpiKeys` SPI 约定 key](../../ddd4j-core/src/main/java/io/ddd4j/core/constant/SpiKeys.java)
- [`RepositoryRegistry` 仓储注册表](../../ddd4j-core/src/main/java/io/ddd4j/core/ddd/repository/RepositoryRegistry.java)
