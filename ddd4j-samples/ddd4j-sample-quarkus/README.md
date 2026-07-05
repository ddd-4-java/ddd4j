# ddd4j-sample-quarkus

> ddd4j 在 **Quarkus** 框架下的**完整 DDD/CQRS 业务示例**：演示充血聚合、值对象、领域事件、仓储、应用服务、CQRS 查询、缓存、MQ 事件发布的端到端装配。
>
> **本示例同时演示 ddd4j 的双轨业务**：
> - **第二轨（充血模型）**：Order 聚合根 + OrderRepository + OrderEvent
> - **第三轨（Model/Query）**：Goods PO + GoodsQuery + GoodsRepository

## 项目定位

这是 ddd4j 在 Quarkus 框架下的**完整业务示例**，是所有 Quarkus 系列示例（auth/mq）的**业务基础**。

**核心价值：与 ddd4j-sample-spring-rich-model 的业务代码完全一致，仅框架绑定代码不同**，证明 ddd4j 的"三框架业务代码零差异"设计。

**双轨合一**：本示例同时整合 `ddd4j-sample-rich-model`（Order 充血模型）和 `ddd4j-sample-model`（Goods 第三轨）的核心业务，让一个示例演示 ddd4j 在 Quarkus 下的**两套建模范式**。

| 维度 | ddd4j-sample-spring-rich-model | ddd4j-sample-quarkus（本示例） |
| --- | --- | --- |
| 框架 | Spring Boot | Quarkus |
| DI 容器 | ApplicationContext | CDI (Arc) |
| SPI 注入 | `@Bean` 扫描 | `@Observes StartupEvent` |
| HTTP 框架 | Spring MVC | Quarkus REST（JAX-RS） |
| 领域事件监听 | `@EventListener` | CDI `@Observes` |
| **领域模型代码** | **完全相同** | **完全相同** |

## 演示的 ddd4j 核心能力

| 能力 | 实现类 | 说明 |
| --- | --- | --- |
| 充血聚合根 | `Order`（第二轨） | 继承 `AggregateRoot`，封装状态机与业务规则 |
| 值对象 | `Money` | 实现 `ValueObject`，不可变金额计算 |
| 实体 | `OrderLine` | 实现 `Entity`，聚合内的订单行 |
| 领域事件 | `OrderCreatedEvent` 等 | 继承 `DomainEvent`，聚合行为自动注册 |
| 仓储模式 | `OrderRepository` / `GoodsRepository` | 继承 `Repository`，接口与实现分离 |
| 应用服务 | `OrderApplicationService` / `GoodsApplicationService` | `@ApplicationScoped`，编排用例 |
| 领域服务 | `OrderDomainService` | 跨聚合根规则、SPI 演示 |
| **Model/Query 充血查询** | `GoodsQuery extends Query<Goods>` | 第三轨：通过 Query 充血方法（`page()`/`list()`/`count()`）直接查询仓储 |
| **RepositoryRegistry** | `GoodsConfig.onStart` | 启动期注册 `Goods` 仓储到 ddd4j 全局上下文，让 `Query` 充血查询能正确找到仓储 |
| CQRS 查询 | `OrderQueryResource` / `GoodsQueryResource` | 命令端/查询端分离（Order 缓存优先，Goods 充血查询优先） |
| 缓存 | `OrderCacheService` | 基于 `CacheKit`（Caffeine 本地缓存） |
| MQ 事件发布 | `NoOpMQEventPublisher` | 实现 `MQEventPublisher` SPI |
| 领域事件监听 | `OrderEventListener` | CDI `@Observes` 监听领域事件 |
| CDI 启动绑定 | `Ddd4jSampleConfig` / `GoodsConfig` | `@Observes StartupEvent` 校验 SPI 注入 + 注册 Goods 仓储 |

## 项目结构

```
ddd4j-sample-quarkus/
├── pom.xml                                                # Quarkus BOM + ddd4j 全套依赖
├── README.md
└── src/
    ├── main/java/io/ddd4j/sample/quarkus/
    │   ├── order/                                         # ===== 第二轨：Order 充血模型 =====
    │   │   ├── domain/                                    #       领域层（零框架注解）
    │   │   │   ├── model/
    │   │   │   │   ├── Order.java                         #       聚合根（充血模型）
    │   │   │   │   ├── OrderLine.java                     #       实体
    │   │   │   │   ├── OrderStatus.java                   #       状态枚举
    │   │   │   │   └── Money.java                         #       值对象
    │   │   │   ├── event/
    │   │   │   │   ├── OrderCreatedEvent.java             #       订单创建事件
    │   │   │   │   ├── OrderPaidEvent.java                #       订单支付事件
    │   │   │   │   ├── OrderShippedEvent.java             #       订单发货事件
    │   │   │   │   └── OrderCancelledEvent.java           #       订单取消事件
    │   │   │   ├── repository/OrderRepository.java        #       仓储接口
    │   │   │   └── service/OrderDomainService.java        #       领域服务（SPI 演示）
    │   │   ├── application/                               #       应用层
    │   │   │   ├── OrderApplicationService.java           #       应用服务（@ApplicationScoped）
    │   │   │   ├── CreateOrderCommand.java                #       创建订单命令
    │   │   │   └── AddOrderLineCommand.java               #       添加订单行命令
    │   │   ├── infrastructure/                            #       基础设施层
    │   │   │   └── InMemoryOrderRepository.java           #       内存仓储实现
    │   │   ├── cache/                                     #       缓存层
    │   │   │   └── OrderCacheService.java                 #       订单缓存（CacheKit + Caffeine）
    │   │   └── web/                                       #       Web 层
    │   │       ├── OrderResource.java                     #       命令端 JAX-RS 端点
    │   │       └── OrderQueryResource.java                #       查询端 CQRS 端点
    │   ├── goods/                                       # ===== 第三轨：Goods Model/Query =====
    │   │   ├── domain/                                    #       领域层（轻量 PO 风格）
    │   │   │   ├── Goods.java                           #       聚合根（PO 实体）
    │   │   │   ├── GoodsId.java                         #       值对象
    │   │   │   ├── GoodsStatus.java                     #       状态枚举
    │   │   │   ├── GoodsRepository.java                 #       仓储接口
    │   │   │   └── GoodsQuery.java                      #       充血查询对象（extends Query）
    │   │   ├── application/                               #       应用层
    │   │   │   └── GoodsApplicationService.java         #       应用服务（@ApplicationScoped）
    │   │   ├── infrastructure/                            #       基础设施层
    │   │   │   └── InMemoryGoodsRepository.java         #       内存仓储（实现 RichRepository）
    │   │   ├── web/                                       #       Web 层
    │   │   │   ├── GoodsResource.java                   #       命令端 JAX-RS 端点
    │   │   │   ├── GoodsQueryResource.java              #       查询端（充血 Query 端点）
    │   │   │   └── dto/
    │   │   │       ├── CreateGoodsRequest.java          #       创建商品请求 record
    │   │   │       └── UpdateGoodsRequest.java          #       更新商品请求 record
    │   │   └── config/                                    #       配置层
    │   │       └── GoodsConfig.java                     #       @Observes StartupEvent 注册仓储
    │   ├── event/
    │   │   └── OrderEventListener.java                    # CDI @Observes 领域事件监听
    │   ├── spi/                                           # ===== SPI 实现 =====
    │   │   ├── NoOpDomainEventPublisher.java              # 领域事件发布者（进程内）
    │   │   ├── NoOpMQEventPublisher.java                  # MQ 事件发布者（No-Op）
    │   │   ├── AnonymousSubjectProvider.java              # 认证主体（匿名）
    │   │   └── DefaultI18nProvider.java                   # 国际化（ResourceBundle）
    │   └── config/
    │       └── Ddd4jSampleConfig.java                     # @Startup 启动配置
    ├── main/resources/
    │   └── application.properties
    └── test/java/io/ddd4j/sample/quarkus/
        ├── OrderResourceTest.java                         # @QuarkusTest 集成测试（Order 充血流）
        └── goods/
            └── GoodsResourceTest.java                   # @QuarkusTest 集成测试（Goods CRUD 流）
```

## 运行

### 前置条件

- JDK 17+
- Maven 3.6+
- Quarkus 3.15.x（本示例默认 Quarkus BOM）

### 启动开发模式

```bash
# 在仓库根目录
mvn -pl ddd4j/ddd4j-samples/ddd4j-sample-quarkus quarkus:dev
```

启动成功后会看到类似日志：

```
[Ddd4jSampleConfig] DomainEventPublisher = NoOpDomainEventPublisher
[Ddd4jSampleConfig] MQEventPublisher       = NoOpMQEventPublisher
[Ddd4jSampleConfig] SubjectProvider        = AnonymousSubjectProvider
[Ddd4jSampleConfig] I18nProvider           = DefaultI18nProvider
[Ddd4jSampleConfig] Sample application is ready at http://localhost:8080
[OrderCacheService] 本地缓存已注册: biz=order, expire=300s
```

### 端口与端点

**默认端口 8080**

#### 命令端（OrderResource — `/orders`，第二轨充血模型）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/orders` | 创建草稿订单 |
| POST | `/orders/{id}/lines` | 添加订单行 |
| POST | `/orders/{id}:pay` | 支付订单 |
| POST | `/orders/{id}:ship` | 发货订单 |
| POST | `/orders/{id}:cancel` | 取消订单 |
| POST | `/orders/cancel-all` | 批量取消买家草稿订单 |
| GET | `/orders/{id}` | 按 ID 查询订单 |
| GET | `/orders/orderNo/{orderNo}` | 按订单编号查询 |

#### 查询端（OrderQueryResource — `/query/orders`）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/query/orders/{id}` | 按 ID 查询（缓存优先） |
| GET | `/query/orders?status=DRAFT` | 按状态查询列表 |
| GET | `/query/orders/count` | 统计订单数量 |

#### 命令端（GoodsResource — `/goods`，第三轨 Model/Query）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/goods` | 创建商品（自动分配 ID） |
| PUT | `/goods/{id}` | 更新商品名称 / 价格 |
| PUT | `/goods/{id}/status?status=ON_SALE` | 调整商品状态 |
| DELETE | `/goods/{id}` | 软删除商品 |
| GET | `/goods/{id}` | 按 ID 查询商品 |
| GET | `/goods/by-code?code=SKU-001` | 按编码查询商品 |

#### 查询端（GoodsQueryResource — `/query/goods`，充血 Query）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/query/goods/page?code=SKU&current=1&size=10` | 充血分页查询（GoodsQuery.page()） |
| GET | `/query/goods/list?status=ON_SALE` | 充血列表查询（GoodsQuery.list()） |
| GET | `/query/goods/count?nameLike=iPhone` | 充血计数（GoodsQuery.count()） |

### 端点示例

#### 1. 创建草稿订单

```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{
    "orderNo": "ORDER-1001",
    "buyerId": "BUYER-1",
    "buyerName": "Alice"
  }'
```

#### 2. 添加订单行

```bash
curl -X POST http://localhost:8080/orders/{id}/lines \
  -H "Content-Type: application/json" \
  -d '{
    "goodsId": "SKU-1",
    "goodsName": "DDD Book",
    "quantity": 2,
    "unitPrice": 39.80
  }'
```

#### 3. CQRS 查询（缓存优先）

```bash
curl http://localhost:8080/query/orders/{id}
```

#### 4. 支付 + 发货

```bash
curl -X POST http://localhost:8080/orders/{id}:pay
curl -X POST http://localhost:8080/orders/{id}:ship
```

#### 5. 创建商品（第三轨）

```bash
curl -X POST http://localhost:8080/goods \
  -H "Content-Type: application/json" \
  -d '{
    "code": "SKU-001",
    "name": "iPhone 15",
    "price": 5999.00,
    "stock": 100
  }'
```

#### 6. 更新商品

```bash
curl -X PUT http://localhost:8080/goods/{id} \
  -H "Content-Type: application/json" \
  -d '{
    "name": "iPhone 15 Pro",
    "price": 7999.00
  }'
```

#### 7. 调整商品状态（上架）

```bash
curl -X PUT "http://localhost:8080/goods/{id}/status?status=ON_SALE"
```

#### 8. 按编码查询商品

```bash
curl "http://localhost:8080/goods/by-code?code=SKU-001"
```

#### 9. 软删除商品

```bash
curl -X DELETE http://localhost:8080/goods/{id}
```

#### 10. 充血分页查询（在售商品，按创建时间倒序）

```bash
curl "http://localhost:8080/query/goods/page?status=ON_SALE&current=1&size=10&orderBys=createTime_DESC"
```

#### 11. 充血列表查询（模糊匹配商品名）

```bash
curl "http://localhost:8080/query/goods/list?nameLike=iPhone"
```

#### 12. 充血计数（统计在售商品数）

```bash
curl "http://localhost:8080/query/goods/count?status=ON_SALE"
```

### 打包与运行

```bash
# 构建可执行 jar（fast-jar）
mvn -pl ddd4j/ddd4j-samples/ddd4j-sample-quarkus package

# 运行
java -jar ddd4j/ddd4j-samples/ddd4j-sample-quarkus/target/quarkus-app/quarkus-run.jar
```

### 运行测试

```bash
mvn -pl ddd4j/ddd4j-samples/ddd4j-sample-quarkus test
```

测试覆盖：

- 4 个核心 SPI 注入校验（`Contexts.inject` vs CDI `@Inject` 一致性）
- 订单完整业务流（创建 → 添加行 → 支付 → 发货）
- REST 端点（命令端 + 查询端）
- 缓存命中验证
- CQRS 端到端流程
- 商品 CRUD（创建 → 更新 → 改状态 → 软删）
- 充血 Query 校验（`page` / `list` / `count`）
- Goods REST 端点（命令端 + 查询端）

## 双轨差异演示（第二轨 vs 第三轨）

本示例同时承载 ddd4j 的两套建模范式。下表逐项对比两者的实现差异：

| 维度 | Order（第二轨 充血模型） | Goods（第三轨 Model/Query） |
| --- | --- | --- |
| 业务场景 | 状态机、规则、事件密集（订单生命周期） | 轻量 CRUD、无复杂业务规则（商品主数据） |
| 领域对象 | `Order extends AggregateRoot<String>` | `Goods extends AggregateRoot<Long>` |
| 状态机 | `Order.draft / pay / ship / cancel` 全部下沉到聚合方法 | 服务层直接 set 状态字段，聚合内无业务方法 |
| 业务校验 | 聚合构造器 + `assertDraft()` 等内部断言 | 应用服务 `validateCode / validateName / validatePrice` 集中校验 |
| 领域事件 | `OrderCreatedEvent / OrderPaidEvent / ...` 自动 registerEvent | 不发布任何领域事件 |
| 仓储 | `OrderRepository` + `InMemoryOrderRepository`（普通 `Repository`） | `GoodsRepository` + `InMemoryGoodsRepository implements RichRepository`（富查询） |
| 仓储注册 | 框架 CDI 启动后即可直接 `@Inject` 使用 | 需 `GoodsConfig.onStart` 调用 `RepositoryRegistry.register(Goods.class, GoodsQuery.class, repo)`，让 `Query` 充血查询能找到仓储 |
| 查询模型 | 单独 `OrderQueryResource`（CQRS 缓存优先） | `GoodsQuery extends Query<Goods>`，充血方法 `page() / list() / count()` 直接出结果 |
| 缓存 | `OrderCacheService`（基于 `CacheKit`） | 无缓存（简单查询无需缓存） |
| 应用服务编排 | `repository.findById(id).get().pay()` —— 状态变更走聚合方法 | `goods.setName(x); goods.setPrice(y); repository.save(goods)` —— 状态变更走服务 |

**关键代码对比**

第二轨 Order 充血模型（`OrderApplicationService.pay`）：

```java
public Order pay(String orderId) {
    Order order = repository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("order not found: " + orderId));
    order.pay();                          // ← 状态变更走聚合方法
    repository.save(order);
    cacheService.putOrder(order);
    return order;
}
```

第三轨 Goods CRUD（`GoodsApplicationService.update`）：

```java
public Goods update(GoodsId id, String name, BigDecimal price) {
    Goods goods = repository.findById(id.value())
            .orElseThrow(() -> new BizRuntimeException("goods.not.found", ...));
    goods.setName(name);                // ← 服务直接 set 字段
    goods.setPrice(price);
    goods.setUpdateTime(LocalDateTime.now());
    return repository.save(goods);
}
```

第三轨 Goods 充血查询（`GoodsQueryResource.page`）：

```java
@GET
@Path("/page")
public Response page(GoodsQuery query) {     // ← JAX-RS 直接绑定 Query 字段
    if (query == null) query = new GoodsQuery();
    Page<Goods> page = applicationService.pageQuery(query);   // ← query.page() 触发仓储查询
    return ok(page);
}
```

**何时选择哪一轨？**

- **第二轨（充血模型）**：业务规则复杂、有状态机、需要发布领域事件、聚合内有多实体协作（如订单的订单行）。**值得为充血付出的复杂度**。
- **第三轨（Model/Query）**：业务以 CRUD 为主、无状态机、无领域事件、追求开发效率（如后台管理类业务）。**轻量是优势**。

> 本示例把两轨放在同一个 Quarkus 应用中，证明它们可以共存且互不干扰。

## SPI 注入流程（Quarkus 轨道）

```
+--------------------------------+
| Quarkus 启动                   |
+--------------------------------+
              |
              v
+--------------------------------------------------+
| CDI 容器扫描                                     |
|   @ApplicationScoped Bean:                       |
|     NoOpDomainEventPublisher / NoOpMQEventPublisher |
|     AnonymousSubjectProvider / DefaultI18nProvider  |
|     OrderCacheService / OrderEventListener          |
+--------------------------------------------------+
              |
              v
+--------------------------------------------------+
| @Observes StartupEvent 触发                      |
|   DddContextInitializer (ddd4j-runtime-quarkus)  |
|   Ddd4jSampleConfig (本示例)                     |
+--------------------------------------------------+
              |
              v
+--------------------------------------------------+
| BaseContext.inject(SpiKeys.*, Type, Bean)        |
|   SpiKeys.DOMAIN_EVENT_PUBLISHER / MQ_EVENT_PUBLISHER |
|   SpiKeys.SUBJECT_PROVIDER / I18N_PROVIDER       |
+--------------------------------------------------+
              |
              v
+--------------------------------------------------+
| 业务代码（零框架耦合）                            |
|   OrderApplicationService / OrderResource        |
|   OrderCacheService / OrderEventListener         |
+--------------------------------------------------+
```

## Quarkus 框架适配要点

| 适配点 | Spring 版 | Quarkus 版 |
| --- | --- | --- |
| Bean 声明 | `@Service` / `@Component` | `@ApplicationScoped` |
| 构造器注入 | `@Autowired`（可省略） | `@Inject` |
| 领域事件监听 | `@EventListener` | `@Observes` |
| 启动事件 | `@PostConstruct` / `ApplicationReadyEvent` | `@Observes StartupEvent` |
| 配置属性 | `@Value` / `@ConfigurationProperties` | `@ConfigProperty` |
| Web 资源基类 | `TenantAwareController` | `TenantAwareResource` |
| 请求注解 | `@GetMapping` / `@PostMapping` | `@GET` / `@POST` / `@Path` |
| 测试 | `@SpringBootTest` | `@QuarkusTest` |

## 依赖说明

| 依赖 | 说明 |
| --- | --- |
| `ddd4j-core` | 纯 Java 业务契约：聚合根、值对象、领域事件、SPI |
| `ddd4j-annotation` | DDD 构造型注解 |
| `ddd4j-kit` | 工具包：StrKit、JsonKit |
| `ddd4j-runtime-quarkus` | Quarkus CDI 启动期 SPI 自动注入 |
| `ddd4j-web-quarkus` | TenantAwareResource、JAX-RS 异常映射 |
| `ddd4j-cache` | 缓存能力：CacheKit、Caffeine 本地缓存 |
| `ddd4j-mq-core` | MQ 核心契约：MQEvent、MQEventPublisher（纯 Java） |
| `quarkus-rest` | Quarkus RESTEasy Reactive（JAX-RS） |

> **注意**：`ddd4j-mq-disruptor` 依赖 `spring-context`，在 Quarkus CDI 下不适用。
> 真实项目可引入 Quarkus 兼容的 MQ 实现（如 SmallRye Reactive Messaging + Kafka）。

## 扩展建议

1. **替换持久化**：把 `InMemoryOrderRepository` 换成 Hibernate Panache / MyBatis-Plus 实现
2. **替换缓存**：把本地 Caffeine 缓存换成 Redis（引入 `ddd4j-cache` 的 Redisson/Lettuce 实现）
3. **替换事件总线**：把 `NoOpDomainEventPublisher` 换成基于 `jakarta.enterprise.event.Event` 的真实实现
4. **替换 MQ**：把 `NoOpMQEventPublisher` 换成 Kafka / RabbitMQ 实现
5. **替换鉴权**：把 `AnonymousSubjectProvider` 换成基于 sa-token / shiro 的实现
6. **添加鉴权拦截器**：通过 `ContainerRequestFilter` 注入当前 Subject

## 版本说明

| 组件 | 版本 |
| --- | --- |
| Quarkus Platform BOM | 3.15.1 |
| ddd4j | `${revision}`（与父工程一致） |
| JDK | 17+ |
| Maven Surefire / Failsafe | 3.2.5 |

## 参考

- [ddd4j-core 源码](../../ddd4j-core)
- [ddd4j-runtime-quarkus 源码](../../ddd4j-runtime/ddd4j-runtime-quarkus)
- [ddd4j-web-quarkus 源码](../../ddd4j-web/ddd4j-web-quarkus)
- [ddd4j-sample-spring-rich-model](../ddd4j-sample-spring-rich-model)（Spring 版对照）
- [ddd4j-sample-cache](../ddd4j-sample-cache)（缓存示例参考）

## 作者

[PartMe.AI](https://github.com/partme-ai)
