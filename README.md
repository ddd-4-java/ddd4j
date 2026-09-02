# Ddd4j — 框架无关的 DDD/CQRS/ES 通用基础层

> Rust 语义移植与 82 项兼容矩阵见 [ddd4r 迁移入口](./docs/DDD4R_MIGRATION.md)。

**Ddd4j** 是一个**不与任何具体容器框架强绑定**的 DDD 项目脚手架，为 [ddd4j-boot](https://github.com/hiwepy/ddd4j-boot)
（Spring Boot）、[ddd4j-quarkus](https://github.com/hiwepy/ddd4j-quarkus)、[ddd4j-javalin](https://github.com/hiwepy/ddd4j-javalin)
以及 Micronaut、Vert.x、Helidon、Dropwizard 等运行时提供**同一套纯净的、可复用的领域层基础**。

领域驱动设计、命令查询职责分离（CQRS）和事件溯源（Event Sourcing）的抽象层全部由 `ddd4j-core` **自研实现**，遵循
**Eric Evans** 和 **Vaughn Vernon** 的 DDD 经典理论；API 形态参考了 [ddd-4-java](https://github.com/fuinorg/ddd-4-java)
与 [cqrs-4-java](https://github.com/fuinorg/cqrs-4-java)（参考来源，不依赖）。

### 🎯 核心定位

| 维度       | 定位                                                                                                                                                            |
|----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **本质**   | 框架无关的 DDD/CQRS/ES 通用基础层（非 Spring Boot 项目）                                                                                                                     |
| **运行时**  | Java 17，不依赖任何容器框架                                                                                                                                             |
| **消费方**  | ddd4j-boot（Spring Boot）/ ddd4j-quarkus / ddd4j-javalin                                                                                                        |
| **底层依赖** | 零第三方 DDD 框架依赖，DDD/CQRS/ES 抽象全部由 ddd4j-core 自研                                                                    |
| **铁律**   | core/annotation/mq-core 等基础契约层零 `@AutoConfiguration` · 零 `spring.factories` · 零 starter；Spring/Web/Auth/Extensions 等适配层只保留显式 `@Configuration`/`@Component` 胶水 |

### 🏗️ 三层架构分离

```
┌─────────────────────────────────────────────────────────────────────┐
│ 第一层：业务应用层（用户项目）                                        │
│ 用户的 Spring Boot / Quarkus / Javalin 项目                         │
└──────────────────────────┬──────────────────────────────────────────┘
                           │ 引入对应框架的脚手架
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 第二层：具体框架脚手架（自动装配 / 胶水代码）                          │
│ ddd4j-boot / ddd4j-quarkus / ddd4j-javalin                        │
│ 含：@AutoConfiguration / starter / Bean 注册 / 拦截器 / 异常处理     │
└──────────────────────────┬──────────────────────────────────────────┘
                           │ 依赖（基础契约层零自动装配、零 starter）
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 第三层：ddd4j 通用基础层（本项目）                                    │
│ 纯 Java 契约 / SPI 接口 / 抽象基类 / DDD 构建块 / ArchUnit 规则     │
└──────────────────────────┬──────────────────────────────────────────┘
           ┌───────────────┼───────────────┐
           ▼               ▼               ▼
      DDD 构建块         CQRS 抽象        ES 抽象
     (ddd4j-core)    (ddd4j-core)    (ddd4j-core)
```

### ✨ 主要特性

- **框架无关**：核心契约层零 Spring/MyBatis/Servlet import，可同时被 Spring Boot / Quarkus / Javalin 复用
- **DDD 战术模式**：提供普通充血模型 `AggregateRoot` / `Repository<M, P, ID>`（统一仓储接口，对齐 MyBatis-Plus
  BaseMapper），并由 ddd4j-core 自研提供 ES 轨道
  `DddAggregateRoot`、`DddDomainEvent`、`DddEventStoreRepository`
- **CQRS 命令查询分离**：基于 ddd4j-core 自研抽象，提供 Command/View/ProjectionPosition 等 SPI
- **事件溯源（ES）**：聚合根状态通过事件流重建，支持时间旅行和完整审计
- **三轨 DDD 模型**：兼容轻量 `PO/Query` 快速 CRUD 轨道 + `AggregateRoot/Repository` 普通充血模型轨道 + ddd4j-core
  自研 CQRS/ES 轨道
- **Lambda 充血查询**：`Query<T>` 支持 Lambda 类型安全条件构建（`eq`/`like`/`in`/`between`/`orderByDesc` 等），充血执行（
  `list()`/`page()`/`one()`/`count()`），三 ORM 模块各自实现
- **三组核心 SPI**：`DomainEventPublisher`（进程内事件）/ `MQEventPublisher`（跨进程消息）/ `Repository<M, P, ID>`（统一领域仓储，对齐
  BaseMapper）
- **MQ 统一抽象**：当前仓库保留 `ddd4j-mq-core` 纯 Java SPI、`ddd4j-mq-spring` 桥接，以及 Kafka/RabbitMQ/RocketMQ/Redis
  Stream/NATS/Pulsar/ActiveMQ/MQTT/ONS/SQS/TDMQ/Disruptor 等实现
- **多框架运行时绑定**：`ddd4j-runtime` 聚合 Spring、Quarkus、Guice、Micronaut、Vert.x、Helidon、Dropwizard
  运行时及统一 Testkit，Web 侧由对应的 `ddd4j-web-*` 模块承载
- **ArchUnit 编译期守护**：9 条架构边界规则，CI 阶段强制执行分层纪律
- **COLA / Clean Architecture 支持**：注解驱动的架构规范检查

### 📚 DDD/CQRS 学习资源

- **[DDD 思维导图](./docs/ddd/DDD%20思维导图.md)**：战略设计 + 战术设计完整知识体系
- **[CQRS 思维导图](./docs/ddd/CQRS%20思维导图.md)**：命令查询职责分离核心概念
- **[参考示例项目](https://github.com/fuinorg/ddd-cqrs-4-java-example)**（参考来源，不依赖）：Greg Young 风格的 DDD/CQRS/Event Sourcing 微服务示例
- **[架构边界规范](./docs/superpowers/specs/2026-06-29-ddd4j-boundary-rules-design.md)**：ddd4j 与各框架项目的职责铁律
- **[架构全景（历史基线）](./docs/superpowers/specs/2026-06-29-ddd4j-architecture-overview-design.md)**：早期模块全景、SPI 设计与三框架运行时基线
- **[当前源码架构导览](./docs/superpowers/specs/2026-07-15-current-source-architecture-design.md)**：基于 CodeGraph 的模块边界、核心调用链、Mermaid 架构图与设计风险
- **[发布质量门禁计划](./docs/superpowers/plans/2026-08-03-production-release-quality.md)**：Java 验证、SBOM、许可证与可选 CVE 报告说明

### 🏗️ 项目架构

**Maven 模块架构**：

| 模块                   | 角色              | 关键产物                                                                                                                                             |
|----------------------|-----------------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| `ddd4j-bom`          | BOM 版本管理        | 外部项目引用统一版本                                                                                                                                       |
| `ddd4j-dependencies` | 第三方依赖集中管理       | Spring 6.x / Jackson 2.22 / Reactor 等                                                                                                            |
| `ddd4j-annotation`   | DDD 注解 + API 注解 | `@DomainEntity` `@DomainService` `@ApplicationService` `@DomainRepository`                                                                       |
| `ddd4j-core`         | **纯 Java 契约层**  | `AggregateRoot` `Repository<M,P,ID>` `Query<T>`（Lambda 充血查询）`Page` `R` `DomainEvent` `DddAggregateRoot` `DddDomainEvent` `SFunction` `LambdaKit` |
| `ddd4j-kit`          | 工具箱             | 继承式增强 Hutool，Cache/Lang/Web 工具                                                                                                                   |
| `ddd4j-ddd-rules`    | DDD 架构规范检查      | `CleanDDDLayerRules` `ColaDDDLayerRules`（ArchUnit）                                                                                               |
| `ddd4j-data`         | 数据层抽象           | 三 ORM 轨道：`ddd4j-data-mybatisplus`（LambdaQueryWrapper）/ `ddd4j-data-mybatis`（纯 MyBatis）/ `ddd4j-data-jpa`（Criteria）+ 加密/数据权限/外部服务/日志              |
| `ddd4j-mq`           | 消息队列抽象          | `MQBrokerAdapter` SPI + Spring 桥接 + 多 Broker 实现                                                                                                  |
| `ddd4j-web`          | Web 层抽象         | WebMVC / WebFlux / Javalin / Quarkus / Vert.x / Micronaut / Helidon / Dropwizard 适配                                                            |
| `ddd4j-auth`         | 认证授权抽象          | `Subject` SPI + Sa-Token/Security/Shiro 实现                                                                                                       |
| `ddd4j-cache`        | 缓存抽象            | 缓存 SPI 及实现                                                                                                                                       |
| `ddd4j-runtime`      | 多框架运行时绑定        | Spring / Quarkus / Guice / Micronaut / Vert.x / Helidon / Dropwizard / Testkit                                                                  |
| `ddd4j-extensions`   | 跨领域扩展           | akka / excel / jackson / license / monitor / pf4j / qlexpress / validation                                                                       |
| `ddd4j-parent`       | Maven 父 POM     | 编译/打包/发布规则                                                                                                                                       |
| `ddd4j-samples`      | 示例工程            | 共享 Order 业务内核，以及 Quarkus / Javalin / Micronaut / Vert.x / Helidon / Dropwizard 运行时示例                                                        |

**模块结构树**：

```
|--ddd4j                                #通用基础层（基础契约层零 starter，零自动装配）
|----ddd4j-bom                          #BOM依赖管理，用于外部项目引用 ddd4j 模块版本管理
|----ddd4j-dependencies                 #公共依赖，便于依赖组件版本控制
|----ddd4j-annotation                   #注解层，DDD构造型注解+API注解，零框架依赖
|----ddd4j-core                         #核心契约层，纯Java DDD基础抽象（AggregateRoot/Repository<M,P,ID>/Query<T> Lambda充血查询/DomainEvent/DddAggregateRoot/SFunction/LambdaKit等）
|----ddd4j-kit                          #工具箱，继承式增强Hutool，提供Cache/Lang/Web工具
|----ddd4j-ddd-rules                          #DDD架构规范检查（基于ArchUnit）
|------ddd4j-ddd-rules-clean                  #Clean Architecture分层纪律规则
|------ddd4j-ddd-rules-cola                   #COLA菱形架构分层纪律规则
|----ddd4j-data                         #数据抽象聚合（三ORM轨道）
|------ddd4j-data-mybatisplus          #MyBatis-Plus实现：LambdaQueryWrapper深度整合/TableScheme/TypeHandler/拦截器
|------ddd4j-data-mybatis               #纯MyBatis实现：SqlSession+TableScheme+LambdaCondition→SQL
|------ddd4j-data-jpa                   #JPA实现：CriteriaBuilder+LambdaCondition→Predicate
|------ddd4j-data-crypto                #加解密策略：CryptoStrategy/Provider/注解
|------ddd4j-data-datascope             #数据权限组件
|------ddd4j-data-external              #外部服务集成：地理位置/天气/行政区划/序列号
|------ddd4j-data-logs                  #API操作日志：ApiOperationLogAspect+Provider
|------ddd4j-data-crypto                #加解密策略：CryptoStrategy/Provider/注解
|------ddd4j-data-datascope             #数据权限组件
|------ddd4j-data-external              #外部服务集成：地理位置/天气/行政区划/序列号
|------ddd4j-data-logs                  #API操作日志：ApiOperationLogAspect+Provider
|----ddd4j-mq                           #消息队列抽象聚合
|------ddd4j-mq-core                    #纯Java MQ SPI：MQEventPublisher/MQBrokerAdapter/MQListener/MQMessage
|------ddd4j-mq-spring                  #Spring Messaging桥接：Message↔MQMessage转换
|------ddd4j-mq-kafka                   #Kafka实现
|------ddd4j-mq-rabbitmq                #RabbitMQ实现
|------ddd4j-mq-rocketmq                #RocketMQ实现
|------ddd4j-mq-redis-stream            #Redis Stream实现（Jedis/Redisson/Lettuce）
|------ddd4j-mq-nats                    #NATS JetStream实现
|------ddd4j-mq-pulsar                  #Apache Pulsar实现
|------ddd4j-mq-activemq                #ActiveMQ实现
|------ddd4j-mq-mqtt                    #MQTT实现
|------ddd4j-mq-mqtt-mica               #Mica MQTT实现
|------ddd4j-mq-ons                     #阿里云ONS实现
|------ddd4j-mq-sqs                     #AWS SQS实现
|------ddd4j-mq-tdmq                    #腾讯TDMQ实现
|------ddd4j-mq-disruptor               #LMAX Disruptor本地MQ实现
|----ddd4j-web                          #Web抽象聚合
|------ddd4j-web-javalin                #Javalin Web适配
|------ddd4j-web-quarkus                #Quarkus Web适配
|------ddd4j-web-webmvc                 #Spring MVC实现：Controller/拦截器/全局异常处理
|------ddd4j-web-webflux                #Spring WebFlux实现：响应式Controller/ErrorAttributes
|------ddd4j-web-vertx                  #Vert.x Web适配
|------ddd4j-web-micronaut              #Micronaut Web适配
|------ddd4j-web-helidon                #Helidon Web适配
|------ddd4j-web-dropwizard             #Dropwizard Web适配
|----ddd4j-auth                         #认证/授权抽象聚合（Subject 契约定义在 ddd4j-core）
|------ddd4j-auth-spring                #Spring桥接：SubjectRegistrar
|------ddd4j-auth-satoken               #Sa-Token实现
|------ddd4j-auth-security              #Spring Security实现
|------ddd4j-auth-shiro                 #Apache Shiro实现
|----ddd4j-cache                        #缓存抽象及实现
|----ddd4j-runtime                       #运行时绑定聚合
|------ddd4j-runtime-spring              #Spring运行时绑定：DomainEventPublisher/I18nProvider/SubjectProvider
|------ddd4j-runtime-quarkus             #Quarkus CDI运行时绑定：核心SPI/CQRS/EventStore适配
|------ddd4j-runtime-guice               #Guice运行时绑定：Guava EventBus实现
|------ddd4j-runtime-micronaut           #Micronaut运行时绑定
|------ddd4j-runtime-vertx               #Vert.x运行时绑定
|------ddd4j-runtime-helidon             #Helidon运行时绑定
|------ddd4j-runtime-dropwizard          #Dropwizard运行时绑定
|------ddd4j-runtime-testkit             #跨运行时契约测试工具
|----ddd4j-extensions                   #跨领域扩展
|------ddd4j-extension-akka             #Akka Actor系统组件
|------ddd4j-extension-excel            #Excel导入导出组件
|------ddd4j-extension-license          #软件授权组件
|------ddd4j-extension-monitor          #监控告警：钉钉/企微机器人+日志告警
|------ddd4j-extension-qlexpress        #QLExpress规则引擎组件
|----ddd4j-parent                       #业务工程父POM，定义编译/打包/发布规则
|----ddd4j-samples                      #示例工程
|--------ddd4j-sample-order-*           #跨运行时共享 Order 领域、应用服务和业务契约
|--------ddd4j-sample-quarkus           #Quarkus 普通 DDD 示例：同一业务模型的 CDI/JAX-RS 适配
|--------ddd4j-sample-javalin           #Javalin 普通 DDD 示例：同一业务模型的 Guice/Javalin 适配
|--------ddd4j-sample-micronaut         #Micronaut 普通 DDD 示例：共享业务内核的编译期 DI/HTTP 适配
|--------ddd4j-sample-vertx             #Vert.x 普通 DDD 示例：共享业务内核的 EventBus/Router 适配
|--------ddd4j-sample-helidon           #Helidon 普通 DDD 示例：共享业务内核的 CDI/JAX-RS 适配
|--------ddd4j-sample-dropwizard        #Dropwizard 普通 DDD 示例：共享业务内核的 Bundle/Jersey 适配
|--------ddd4j-sample-quarkus-cqrs      #Quarkus CQRS 示例
|--------ddd4j-sample-javalin-cqrs      #Javalin CQRS 示例
|--------ddd4j-sample-quarkus-satoken   #Quarkus + Sa-Token 鉴权示例
|--------ddd4j-sample-quarkus-shiro     #Quarkus + Shiro 鉴权示例
|--------ddd4j-sample-javalin-satoken   #Javalin + Sa-Token 鉴权示例
|--------ddd4j-sample-javalin-shiro     #Javalin + Shiro 鉴权示例
```

### 📖 使用说明

#### 1. 通过 BOM 引入（推荐）

在业务项目的 `pom.xml` 中引入 BOM：

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

然后按需引入模块：

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

#### 2. 多框架运行时绑定方式

| 框架          | 运行时绑定                   | DI 容器                | 事件发布                    | Web 框架               |
|-------------|-------------------------|----------------------|-------------------------|----------------------|
| Spring Boot | `ddd4j-runtime-spring`  | `ApplicationContext` | `AppCtx.publishEvent()` | Spring MVC / WebFlux |
| Quarkus     | `ddd4j-runtime-quarkus` | Arc (CDI)            | `Event<T>.fire()`       | RESTEasy / JAX-RS    |
| Javalin     | `ddd4j-runtime-guice`   | Guice Injector       | `EventBus.post()`       | Javalin              |
| Micronaut   | `ddd4j-runtime-micronaut` | Micronaut Context  | `publishEvent()`        | Micronaut HTTP       |
| Vert.x      | `ddd4j-runtime-vertx`   | 显式 Runtime          | Vert.x EventBus         | Vert.x Web           |
| Helidon     | `ddd4j-runtime-helidon` | CDI / BeanManager     | CDI Event               | Helidon WebServer    |
| Dropwizard  | `ddd4j-runtime-dropwizard` | 显式 Bundle         | Listener 集合            | Jersey               |

#### 3. 普通充血模型与 PO 分离

ddd4j 的普通 DDD 主路径不要求领域模型继承 MyBatis-Plus 的 `Model`，也不要求通过固定前缀或固定父类识别模型。领域层只依赖
`ddd4j-core`，基础设施层再用 `ddd4j-data-mybatis` 适配 MyBatis-Plus。

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

这条路径的约束是：`Order` 是 Model，`OrderPO` 是持久化对象；MyBatis-Plus 的 `Wrappers`、`ChainQuery`、
`LambdaQueryChainWrapper`、`LambdaUpdateChainWrapper` 等只出现在基础设施仓储实现中，不进入领域模型。

#### 3.5 Lambda 充血查询

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
Page<Order> persistencePage = new OrderQuery()
    .withPO(OrderPO.class)
    .eq(OrderPO::getStatus, "PAID")
    .orderByDesc(OrderPO::getCreateTime)
    .current(1).size(20)
    .page();

List<Order> list = new OrderQuery()
    .eq(Order::getStatus, "ACTIVE")
    .list();

// 条件重载（消除 if-else 样板）
new OrderQuery()
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

#### 4. 业务项目继承父 POM

```xml
<parent>
    <groupId>io.ddd4j</groupId>
    <artifactId>ddd4j-parent</artifactId>
    <version>${revision}</version>
    <relativePath>../ddd4j-parent/pom.xml</relativePath>
</parent>
```

### 🛡️ ArchUnit 架构边界守护

ddd4j 内置 9 条 ArchUnit 规则，在 CI 阶段强制执行架构纪律：

| 规则                               | 说明                                                   |
|----------------------------------|------------------------------------------------------|
| `no_autoconfiguration_in_ddd4j`  | ddd4j 全模块不得包含 `@AutoConfiguration`                   |
| `no_spring_in_core_modules`      | core / kit / annotation 不得依赖 `org.springframework.*` |
| `no_spring_messaging_in_mq_core` | mq-core 不得依赖 `org.springframework.messaging.*`       |
| `no_spring_factories_in_core`    | core 不得引用 `AutoConfiguration.imports`                |
| `no_hutool_all_in_core`          | core 不得依赖 hutool 全量包                                 |
| `core_no_mybatis`                | core 不得依赖 `com.baomidou.*`                           |
| `core_no_servlet`                | core 不得依赖 `jakarta.servlet.*`                        |
| `core_no_validator`              | core 不得依赖 `org.hibernate.validator.*`                |
| `core_no_aspectj`                | core 不得依赖 `org.aspectj.*`                            |

### 📁 DDD 分层目录结构（业务项目参考）

业务项目建议采用 **COLA V5 架构**（菱形架构），依赖方向：`adapter` → `app` → `domain` ← `infrastructure`

```
order-service/
├─ src/main/java/com/example/order/
│   ├─ adapter/                     # 适配层（Web/MQ/RPC 入口）
│   │   ├─ web/OrderController.java
│   │   └─ mq/OrderEventListener.java
│   ├─ client/                      # 接口层（对外 API + DTO）
│   │   ├─ api/OrderServiceI.java
│   │   └─ dto/command/CreateOrderCmd.java
│   ├─ app/                         # 应用层（用例编排 + 事务边界）
│   │   ├─ executor/CreateOrderCmdExe.java
│   │   └─ service/OrderServiceImpl.java
│   ├─ domain/                      # 领域层（核心，零外部依赖）
│   │   ├─ model/entity/Order.java
│   │   ├─ service/OrderDomainService.java
│   │   ├─ gateway/OrderGateway.java
│   │   └─ event/OrderCreatedEvent.java
│   └─ infrastructure/              # 基础设施层（技术实现）
│       ├─ persistence/mapper/OrderMapper.java
│       ├─ gatewayimpl/OrderGatewayImpl.java
│       └─ mq/RocketMQProducer.java
└─ pom.xml
```

### 📄 相关文档

| 文档                                                          | 说明                     |
|-------------------------------------------------------------|------------------------|
| [架构全景（历史基线）](./docs/superpowers/specs/2026-06-29-ddd4j-architecture-overview-design.md)       | 早期模块全景、SPI 设计与三框架基线     |
| [当前源码架构导览](./docs/superpowers/specs/2026-07-15-current-source-architecture-design.md) | CodeGraph 调用链、Mermaid 图和当前设计边界 |
| [架构边界规范](./docs/superpowers/specs/2026-06-29-ddd4j-boundary-rules-design.md)      | ddd4j 与各框架项目的职责铁律      |
| [DDD 思维导图](./docs/ddd/DDD%20思维导图.md)                        | DDD 战略+战术设计知识体系        |
| [CQRS 思维导图](./docs/ddd/CQRS%20思维导图.md)                      | CQRS 核心概念              |
| [DDD 经典分层架构](./docs/ddd/1、DDD%20经典分层架构目录结构.md)              | 分层架构目录参考               |
| [六边形架构](./docs/ddd/2、六边形架构详细目录结构参考.md)                      | 六边形架构目录参考              |
| [整洁架构](./docs/ddd/3、整洁架构详细目录结构参考.md)                        | 整洁架构目录参考               |
| [COLA V5 架构](./docs/ddd/4、COLA%20V5%20架构详细目录结构参考.md)        | COLA 菱形架构目录参考          |
| [数据层优化计划](./docs/superpowers/plans/2026-06-29-ddd4j-data-optimization.md) | ddd4j-data 模块优化方案      |
| [迁移指南](./docs/superpowers/specs/2026-07-02-optional-migrations-design.md)             | 从旧版迁移到 2.0.x 的指南       |
