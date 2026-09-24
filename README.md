# Ddd4j — 框架无关的 DDD / CQRS / ES 通用基础层

<p align="center">
  <img src="assets/ddd4j-logo-v2.png" alt="ddd4j logo" width="120" />
</p>

<p align="center">
  <strong>用框架无关的领域契约，把业务模型、命令、事件、投影与可靠投递连成一条可演进的 Java 路径。</strong><br>
  面向 DDD、CQRS、Event Sourcing、多框架运行时与生产级消息交付的通用基础层。
</p>

<p align="center">
  <img src="assets/ddd4j-hero-v2.png" alt="ddd4j: DDD、CQRS、事件溯源与可靠消息投递" width="100%" />
</p>

<p align="center">
  <a href="README.en.md">English</a> ·
  <a href="ddd4j-samples/README.md">示例工程</a> ·
  <a href="8、ddd4j-Architecture.zh_CN.md">系统架构</a> ·
  <a href="5、ddd4j-技术方案与路线.md">技术方案</a> ·
  <a href="docs/superpowers/plans/2026-09-10-mq-startup-lifecycle.md">MQ 启动生命周期</a>
</p>

> **Ddd4j** 是一个**不与任何具体容器框架强绑定**的 DDD 项目脚手架，为 [ddd4j-boot](https://github.com/hiwepy/ddd4j-boot)
> （Spring Boot）、[ddd4j-quarkus](https://github.com/hiwepy/ddd4j-quarkus)、[ddd4j-javalin](https://github.com/hiwepy/ddd4j-javalin)
> 以及 Vert.x、Helidon、Dropwizard、Micronaut、Guice 等运行时提供**同一套纯净、可复用的领域层基础**。
>
> 领域驱动设计、命令查询职责分离（CQRS）和事件溯源（Event Sourcing）的抽象层全部由 `ddd4j-core` **自研实现**，
> 遵循 Eric Evans 与 Vaughn Vernon 的 DDD 经典理论；API 形态参考了
> [ddd-4-java](https://github.com/fuinorg/ddd-4-java) 与 [cqrs-4-java](https://github.com/fuinorg/cqrs-4-java)（参考来源，不依赖）。
>
> Rust 语义移植与 82 项兼容矩阵见 [ddd4r 迁移入口](./docs/DDD4R_MIGRATION.md)。

---

## 1. 核心定位

| 维度 | 定位 |
|:---|:---|
| **本质** | 框架无关的 DDD / CQRS / ES 通用基础层（非 Spring Boot 应用本身） |
| **运行时** | Java 8（1.0.x）/ Java 17（2.0.x）/ Java 21（3.0.x）三轨并行 |
| **消费方** | `ddd4j-boot`（Spring Boot）· `ddd4j-quarkus`（Quarkus）· `ddd4j-javalin` · 自研 Vert.x / Helidon / Micronaut / Dropwizard / Guice 业务工程 |
| **底层依赖** | 零第三方 DDD 框架依赖；DDD / CQRS / ES 抽象全部由 `ddd4j-core` 自研 |
| **铁律** | core / annotation / mq-core 等基础契约层 **零** `@AutoConfiguration` · **零** `spring.factories` · **零** starter；Spring / Web / Auth / Extensions 等适配层只保留显式 `@Configuration` / `@Component` 胶水 |

## 2. 一眼看懂

<p align="center">
  <img src="assets/ddd4j-architecture-v2.png" alt="ddd4j：命令、聚合、事件存储、读模型、Outbox 与消息客户端的可靠运行时链路" width="100%" />
</p>

> **运行时主链路**：Command → Aggregate → Event Store → Projection / Query View。
> 领域事件与业务写**同事务**进入 Outbox，再由 Dispatcher 安全发布到消息基础设施。

### 2.1 三层架构分离

```
┌─────────────────────────────────────────────────────────────────────┐
│ 第一层 · 业务应用层（用户项目）                                       │
│   用户的 Spring Boot / Quarkus / Javalin / Micronaut / Vert.x 工程       │
└──────────────────────────┬──────────────────────────────────────────┘
                           │  引入对应框架脚手架
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 第二层 · 具体框架脚手架（自动装配 / 胶水代码）                          │
│   ddd4j-boot / ddd4j-quarkus / ddd4j-javalin / 自研 Vert.x 适配      │
│   含：@AutoConfiguration · starter · Bean 注册 · 拦截器 · 异常处理    │
└──────────────────────────┬──────────────────────────────────────────┘
                           │  依赖（基础契约层零自动装配、零 starter）
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 第三层 · ddd4j 通用基础层（本仓库）                                    │
│   纯 Java 契约 / SPI / 抽象基类 / DDD 构建块 / ArchUnit 规则           │
└──────────────────────────┬──────────────────────────────────────────┘
           ┌───────────────┼───────────────┐
           ▼               ▼               ▼
        DDD 构建块       CQRS 抽象       ES 抽象
       (ddd4j-core)   (ddd4j-core)   (ddd4j-core)
```

## 3. 主要特性

- ✅ **框架无关**：核心契约层零 Spring / MyBatis / Servlet import，可同时被 Spring Boot / Quarkus / Javalin / Micronaut / Vert.x / Helidon / Dropwizard / Guice 复用
- ✅ **DDD 战术模式**：提供普通充血模型 `AggregateRoot` / `Repository<M, P, ID>`（对齐 MyBatis-Plus `BaseMapper`），并由 `ddd4j-core` 自研提供 ES 轨道 `DddAggregateRoot` / `DddDomainEvent` / `DddEventStoreRepository`
- ✅ **CQRS 命令查询分离**：基于 `ddd4j-core` 自研抽象，提供 `Command` / `View` / `ProjectionPosition` 等 SPI
- ✅ **事件溯源（ES）**：聚合根状态通过事件流重建，支持时间旅行和完整审计
- ✅ **三轨 DDD 模型**：兼容轻量 `PO / Query` 快速 CRUD 轨道 + `AggregateRoot / Repository` 普通充血模型轨道 + `ddd4j-core` 自研 CQRS / ES 轨道
- ✅ **Lambda 充血查询**：`Query<T>` 支持 Lambda 类型安全条件构建（`eq` / `like` / `in` / `between` / `orderByDesc` 等），三 ORM 模块各自实现
- ✅ **三组核心 SPI**：`DomainEventPublisher`（进程内事件）· `MQEventPublisher`（跨进程消息）· `Repository<M, P, ID>`（统一领域仓储，对齐 BaseMapper）
- ✅ **MQ 统一抽象**：保留 `ddd4j-mq-core` 纯 Java SPI + `ddd4j-mq-spring` 桥接，以及 Kafka / RabbitMQ / RocketMQ / Redis Stream / NATS / Pulsar / ActiveMQ / MQTT / ONS / SQS / TDMQ / Disruptor 等 12 个实现
- ✅ **多框架运行时绑定**：`ddd4j-runtime` 聚合 Spring / Quarkus / Guice / Micronaut / Vert.x / Helidon / Dropwizard / Testkit；Web 侧由对应的 `ddd4j-web-*` 模块承载
- ✅ **ArchUnit 编译期守护**：9 条架构边界规则，CI 阶段强制执行分层纪律
- ✅ **COLA / Clean Architecture 支持**：注解驱动的架构规范检查
- ✅ **MQ 启动生命周期（2026-09 新增）**：LIFO 资源关闭 + checkpoint / rollback + 启动状态机 NEW / STARTING / READY / DEGRADED / FAILED / STOPPED，必选 listener 失败阻断启动，可选失败进入 DEGRADED

## 4. 三轨版本策略

ddd4j 当前**三条主线并行演化**，HEAD 同步最新同语义提交：

| 主线 | JDK | Maven | Jackson | JPA 命名空间 | 当前 HEAD | 角色 |
|:---|:---:|:---:|:---:|:---:|:---|:---|
| **feature/1.0.x** | **8** | **3.9.16** | Jackson 2 | `javax.persistence` | `41f690b7`（2026-09-15） | 生产在用 + 三线治理中枢 |
| **feature/2.0.x** | **17** | **3.9.16** | Jackson 2 | `jakarta.persistence`（迁移中） | `e3ceb44e`（2026-09-15） | 生产主力线 |
| **feature/3.0.x** | **21** | **4.0.0-rc-6** | **Jackson 3 / tools.jackson** | 完全 jakarta | `d21e90a4`（2026-09-15） | 前瞻线（JDK 21 + Maven 4） |

> **三线对齐约束**：2.0.x ↔ 3.0.x 在 2026-09-09 严格结构 / Class / 公开 API / CodeGraph 签名门禁中**全部 0 差异**；1.0.x ↔ 2.0.x 因 JDK8 字节码 / Helidon 缺失保留 19 处公开 API 差异 + 231 处方法体差异（详见 [`docs/superpowers/reports/2026-09-09-three-line-source-parity-audit.md`](docs/superpowers/reports/2026-09-09-three-line-source-parity-audit.md)）。

## 5. Maven 模块矩阵

| 模块 | 角色 | 关键产物 |
|:---|:---|:---|
| `ddd4j-bom` | BOM 版本管理 | 外部项目引用统一版本 |
| `ddd4j-dependencies` | 第三方依赖集中管理 | Spring 6.x / Jackson 2.22 / Reactor 等 |
| `ddd4j-annotation` | DDD 注解 + API 注解 | `@DomainEntity` `@DomainService` `@ApplicationService` `@DomainRepository` |
| `ddd4j-core` | **纯 Java 契约层** | `AggregateRoot` `Repository<M,P,ID>` `Query<T>`（Lambda 充血查询）`Page` `R` `DomainEvent` `DddAggregateRoot` `SFunction` `LambdaKit` |
| `ddd4j-kit` | 工具箱 | 继承式增强 Hutool，Cache / Lang / Web 工具 |
| `ddd4j-ddd-rules` | DDD 架构规范检查 | `CleanDDDLayerRules` `ColaDDDLayerRules`（ArchUnit） |
| `ddd4j-data` | 数据层抽象 | 三 ORM 轨道 + 加密 / 数据权限 / 外部服务 / 日志 |
| `ddd4j-mq` | 消息队列抽象 | `MQBrokerAdapter` SPI + Spring 桥接 + 12 个 Broker 实现 |
| `ddd4j-web` | Web 层抽象 | WebMVC / WebFlux / Javalin / Quarkus / Vert.x / Micronaut / Helidon / Dropwizard 适配 |
| `ddd4j-auth` | 认证授权抽象 | `Subject` SPI + Sa-Token / Security / Shiro 实现 |
| `ddd4j-cache` | 缓存抽象 | 缓存 SPI 及实现 |
| `ddd4j-runtime` | 多框架运行时绑定 | Spring / Quarkus / Guice / Micronaut / Vert.x / Helidon / Dropwizard / Testkit |
| `ddd4j-extensions` | 跨领域扩展 | akka / excel / jackson / license / monitor / pf4j / qlexpress / validation |
| `ddd4j-parent` | Maven 父 POM | 编译 / 打包 / 发布规则 |
| `ddd4j-samples` | 示例工程 | 共享 Order 业务内核 + 9 种运行时示例 |

## 6. 模块结构树

```
|--ddd4j                                  # 通用基础层（基础契约层零 starter，零自动装配）
|----ddd4j-bom                            # BOM 依赖管理
|----ddd4j-dependencies                   # 公共依赖集中管理
|----ddd4j-annotation                     # DDD 注解层（@DomainEntity 等）
|----ddd4j-core                           # 核心契约层（纯 Java）
|----ddd4j-kit                            # 工具箱
|----ddd4j-ddd-rules
|------ddd4j-ddd-rules-clean              # Clean Architecture 规则
|------ddd4j-ddd-rules-cola               # COLA 菱形架构规则
|----ddd4j-data                           # 数据抽象（三 ORM 轨道）
|------ddd4j-data-mybatisplus              # MyBatis-Plus 实现
|------ddd4j-data-mybatis                 # 纯 MyBatis 实现
|------ddd4j-data-jpa                     # JPA Criteria 实现
|------ddd4j-data-crypto                  # 加解密策略
|------ddd4j-data-datascope               # 数据权限
|------ddd4j-data-external                # 外部服务
|------ddd4j-data-logs                    # API 操作日志
|------ddd4j-data-cqrs-*                  # CQRS 适配（spring/guice/quarkus/vertx/javalin/micronaut/helidon/dropwizard）
|------ddd4j-data-event-store-*           # EventStore 实现（esdb/jdbi/jpa/panache/r2dbc）
|------ddd4j-data-projection-*            # Projection 调度（spring/guice/quarkus/vertx/javalin/micronaut/helidon/dropwizard/jpa/jdbi/r2dbc/panache）
|----ddd4j-mq                             # 消息队列抽象
|------ddd4j-mq-core                      # 纯 Java MQ SPI
|------ddd4j-mq-spring                    # Spring Messaging 桥接
|------ddd4j-mq-{kafka,rabbitmq,rocketmq,redis-stream,nats,pulsar,activemq,mqtt,mqtt-mica,ons,sqs,tdmq,disruptor}
|----ddd4j-web                            # Web 抽象
|------ddd4j-web-{webmvc,webflux,javalin,quarkus,vertx,micronaut,helidon,dropwizard,core,validation,testkit}
|----ddd4j-auth                           # 认证 / 授权
|------ddd4j-auth-{spring,satoken,security,shiro,datascope,license}
|----ddd4j-cache                          # 缓存抽象
|----ddd4j-runtime                         # 多框架运行时
|------ddd4j-runtime-{spring,quarkus,guice,micronaut,vertx,helidon,dropwizard,testkit}
|----ddd4j-extensions                     # 跨领域扩展
|------ddd4j-extension-{akka,excel,jackson,license,monitor,pf4j,qlexpress,qrcode,validation,otel}
|----ddd4j-parent                         # 父 POM
|----ddd4j-samples                        # 示例工程
|--------ddd4j-sample-{order-*,javalin,javalin-cqrs,quarkus,quarkus-cqrs,quarkus-satoken,
|                          quarkus-shiro,javalin-satoken,javalin-shiro,micronaut,
|                          micronaut-cqrs,vertx,vertx-cqrs,helidon,helidon-cqrs,
|                          dropwizard,dropwizard-cqrs}
```

## 7. 多框架运行时绑定

| 框架 | 运行时绑定 | DI 容器 | 事件发布 | Web 框架 |
|:---|:---|:---|:---|:---|
| Spring Boot | `ddd4j-runtime-spring` | `ApplicationContext` | `AppCtx.publishEvent()` | Spring MVC / WebFlux |
| Quarkus | `ddd4j-runtime-quarkus` | Arc (CDI) | `Event<T>.fire()` | RESTEasy / JAX-RS |
| Javalin | `ddd4j-runtime-guice` | Guice Injector | `EventBus.post()` | Javalin |
| Micronaut | `ddd4j-runtime-micronaut` | Micronaut Context | `publishEvent()` | Micronaut HTTP |
| Vert.x | `ddd4j-runtime-vertx` | 显式 Runtime | Vert.x EventBus | Vert.x Web |
| Helidon | `ddd4j-runtime-helidon` | CDI / BeanManager | CDI Event | Helidon WebServer |
| Dropwizard | `ddd4j-runtime-dropwizard` | 显式 Bundle | Listener 集合 | Jersey |

## 8. ArchUnit 架构边界守护

ddd4j 内置 9 条 ArchUnit 规则，在 CI 阶段强制执行架构纪律：

| 规则 | 说明 |
|:---|:---|
| `no_autoconfiguration_in_ddd4j` | ddd4j 全模块不得包含 `@AutoConfiguration` |
| `no_spring_in_core_modules` | core / kit / annotation 不得依赖 `org.springframework.*` |
| `no_spring_messaging_in_mq_core` | mq-core 不得依赖 `org.springframework.messaging.*` |
| `no_spring_factories_in_core` | core 不得引用 `AutoConfiguration.imports` |
| `no_hutool_all_in_core` | core 不得依赖 hutool 全量包 |
| `core_no_mybatis` | core 不得依赖 `com.baomidou.*` |
| `core_no_servlet` | core 不得依赖 `jakarta.servlet.*` |
| `core_no_validator` | core 不得依赖 `org.hibernate.validator.*` |
| `core_no_aspectj` | core 不得依赖 `org.aspectj.*` |

## 9. 使用说明

### 9.1 通过 BOM 引入（推荐）

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.ddd4j</groupId>
            <artifactId>ddd4j-bom</artifactId>
            <version>${ddd4j.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

```xml
<dependencies>
    <!-- 核心契约（必选） -->
    <dependency>
        <groupId>io.ddd4j</groupId>
        <artifactId>ddd4j-core</artifactId>
    </dependency>
    <!-- 数据层（按需） -->
    <dependency>
        <groupId>io.ddd4j</groupId>
        <artifactId>ddd4j-data-mybatis</artifactId>
    </dependency>
    <!-- Web 层（按需） -->
    <dependency>
        <groupId>io.ddd4j</groupId>
        <artifactId>ddd4j-web-webmvc</artifactId>
    </dependency>
    <!-- 运行时绑定（按需） -->
    <dependency>
        <groupId>io.ddd4j</groupId>
        <artifactId>ddd4j-runtime-spring</artifactId>
    </dependency>
</dependencies>
```

### 9.2 普通充血模型与 PO 分离

ddd4j 的普通 DDD 主路径**不要求**领域模型继承 MyBatis-Plus 的 `Model`，也不要求通过固定前缀或固定父类识别模型。领域层只依赖 `ddd4j-core`，基础设施层再用 `ddd4j-data-mybatis` 适配 MyBatis-Plus。

```java
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.kit.lang.StrKit;
import java.util.Objects;

public class Order extends AggregateRoot<Long> {

    private final Long id;
    private String buyerName;

    public Order(Long id, String buyerName) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        renameBuyer(buyerName);
    }

    @Override
    public Long id() {
        return id;
    }

    public void renameBuyer(String buyerName) {
        if (StrKit.isBlank(buyerName)) {
            throw new IllegalArgumentException("buyerName must not be blank");
        }
        this.buyerName = buyerName;
    }

    public String buyerName() {
        return buyerName;
    }
}
```

```java
import io.ddd4j.core.ddd.repository.Repository;
import java.util.Optional;

public interface OrderRepository extends Repository<Order, OrderPO, Long> {
    Optional<Order> findByOrderNo(String orderNo);
}
```

```java
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.ddd4j.data.mybatis.repository.MybatisAggregateRepository;
import io.ddd4j.kit.lang.StrKit;
import java.util.Optional;

public class MybatisOrderRepository extends MybatisAggregateRepository<Order, OrderPO, Long>
        implements OrderRepository {

    public MybatisOrderRepository(BaseMapper<OrderPO> mapper) {
        super(mapper);
    }

    @Override
    public Optional<Order> findByOrderNo(String orderNo) {
        if (StrKit.isBlank(orderNo)) {
            return Optional.empty();
        }
        return Optional.ofNullable(lambdaQuery()
                .eq(OrderPO::getOrderNo, orderNo)
                .one())
                .map(this::toModel);
    }

    @Override
    public Order toModel(OrderPO po) {
        return new Order(po.getId(), po.getBuyerName());
    }

    @Override
    public OrderPO toPersistenceObject(Order model) {
        OrderPO po = new OrderPO();
        po.setId(model.id());
        po.setBuyerName(model.buyerName());
        return po;
    }
}
```

### 9.3 Lambda 充血查询

```java
// 定义 ORM 无关的充血 Query
public class OrderQuery extends Query<Order> {
    @Override
    public Repository repository() {
        return RepositoryRegistry.repository(Order.class);
    }
}

// 业务层默认使用领域模型属性
Page<Order> page = new OrderQuery()
    .eq(Order::getStatus, "PAID")
    .like(Order::getOrderNo, "2024")
    .ge(Order::getCreateTime, startTime)
    .page();

// 基础设施层必须显式进入 PO 属性作用域
Page<OrderPO> persistencePage = new OrderQuery()
    .withPO(OrderPO.class)
    .eq(OrderPO::getStatus, "PAID")
    .orderByDesc(OrderPO::getCreateTime)
    .current(1).size(20)
    .page();

// 条件重载（消除 if-else 样板）
List<Order> list = new OrderQuery()
    .eq(StrKit.isNotBlank(status), Order::getStatus, status)
    .like(StrKit.isNotBlank(keyword), Order::getOrderNo, keyword)
    .list();
```

`Repository<M, P, ID>` 统一仓储接口对齐 MyBatis-Plus `BaseMapper` 全部常用方法：

```
Repository<M, P, ID>
├── 单条 CRUD:    findById / save / updateById / insertOrUpdate / delete / deleteById
├── 批量操作:      findByIds / deleteByIds / saveBatch / updateBatchById / insertOrUpdateBatch
├── 无条件查询:    findFirst / findAll / count / exists
├── 条件查询:      findFirst(Query<M>) / findList(Query<M>) / page(Query<M>) / count(Query<M>) / maps(Query<M>) / exists(Query<M>)
├── 条件操作:      update(M, Query<M>) / deleteByQuery(Query<M>)
└── 聚合填充:      fill(Query<M>, M) / fill(Query<M>, List<M>)
```

### 9.4 业务项目继承父 POM

```xml
<parent>
    <groupId>io.ddd4j</groupId>
    <artifactId>ddd4j-parent</artifactId>
    <version>${revision}</version>
    <relativePath>../ddd4j-parent/pom.xml</relativePath>
</parent>
```

## 10. DDD 分层目录结构（业务项目参考）

业务项目建议采用 **COLA V5 架构**（菱形架构），依赖方向：`adapter` → `app` → `domain` ← `infrastructure`

```
order-service/
├─ src/main/java/com/example/order/
│  ├─ adapter/        # 适配层（Web / MQ / RPC 入口）
│  │  ├─ web/OrderController.java
│  │  └─ mq/OrderEventListener.java
│  ├─ client/         # 接口层（对外 API + DTO）
│  │  ├─ api/OrderServiceI.java
│  │  └─ dto/command/CreateOrderCmd.java
│  ├─ app/            # 应用层（用例编排 + 事务边界）
│  │  ├─ executor/CreateOrderCmdExe.java
│  │  └─ service/OrderServiceImpl.java
│  ├─ domain/         # 领域层（核心，零外部依赖）
│  │  ├─ model/entity/Order.java
│  │  ├─ service/OrderDomainService.java
│  │  ├─ gateway/OrderGateway.java
│  │  └─ event/OrderCreatedEvent.java
│  └─ infrastructure/ # 基础设施层（技术实现）
│     ├─ persistence/mapper/OrderMapper.java
│     ├─ gatewayimpl/OrderGatewayImpl.java
│     └─ mq/RocketMQProducer.java
└─ pom.xml
```

## 11. 文档与方案

| 文档 | 说明 |
|:---|:---|
| [8、ddd4j-Architecture.zh_CN.md](./8、ddd4j-Architecture.zh_CN.md) | 系统架构设计（分层、运行时、依赖、安全、部署） |
| [5、ddd4j-技术方案与路线.md](./5、ddd4j-技术方案与路线.md) | 当前进行中的 MQ 启动生命周期 + License Gate Hardening 技术方案 |
| [6、ddd4j-产品与版本规划.md](./6、ddd4j-产品与版本规划.md) | **三轨版本治理 · 升级路径 · 技术兼容性矩阵 · 支持窗口** |
| [7、ddd4j-领域模型设计.md](./7、ddd4j-领域模型设计.md) | 限界上下文、聚合、事件契约 |
| [1.0.x/8、ddd4j-1.0.x-Architecture.zh_CN.md](./1.0.x/8、ddd4j-1.0.x-Architecture.zh_CN.md) | 1.0.x 版本级架构与三线差异表 |
| [docs/superpowers/specs/2026-07-15-current-source-architecture-design.md](./docs/superpowers/specs/2026-07-15-current-source-architecture-design.md) | 当前源码架构导览（CodeGraph） |
| [docs/superpowers/specs/2026-06-29-ddd4j-boundary-rules-design.md](./docs/superpowers/specs/2026-06-29-ddd4j-boundary-rules-design.md) | 架构边界规范 |
| [docs/superpowers/reports/2026-09-09-three-line-source-parity-audit.md](./docs/superpowers/reports/2026-09-09-three-line-source-parity-audit.md) | 三线严格审计报告 |
| [docs/ddd/DDD%20思维导图.md](./docs/ddd/DDD%20思维导图.md) | DDD 战略+战术设计知识体系 |
| [docs/ddd/CQRS%20思维导图.md](./docs/ddd/CQRS%20思维导图.md) | CQRS 核心概念 |

---

**文档版本**：V1.0.0 · **最后更新**：2026-09-18 · **文档状态**：✅ 待评审 · **对齐代码 HEAD**：`41f690b7`