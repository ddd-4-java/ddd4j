# Ddd4j 架构与最终定位（架构师视角）

> 本文档综合 `codegraph` 索引（823 文件 / 13,671 节点 / 23,360 边）与全部架构剖析文档，形成 ddd4j 项目的**最终架构定位**与*
*模块全景**。
> 配套 SVG 架构图见：`ddd4j_architecture.html`

---

## 一、项目最终定位

**ddd4j 是一个框架无关的 DDD/CQRS/ES 通用基础层**，目标是为 Spring Boot、Quarkus、Javalin 三种容器框架提供**同一套纯净领域模型契约
**。

| 维度                 | 数据                                                                           |
|--------------------|------------------------------------------------------------------------------|
| **定位**             | DDD 战术模式 + CQRS 命令查询分离 + ES 事件溯源 的通用脚手架                                      |
| **运行时**            | Java 17；Spring Framework 6.x / Quarkus CDI / Guice-Javalin 三类适配并列            |
| **依赖 ddd-4-java**  | ✅ 强依赖（fuinorg DDD 基类）                                                        |
| **依赖 cqrs-4-java** | ✅ 强依赖（fuinorg CQRS 框架）                                                       |
| **依赖 esc-api**     | ✅ 强依赖（fuinorg EventStore API）                                                |
| **核心特性**           | 零 Spring 强绑定的纯 Java 契约 + 三框架 SPI 适配 + 13 个 MQ Broker/本地实现 + ArchUnit 编译期架构守护 |
| **代码规模**           | 823 文件 / 13,671 节点 / 23,360 边（codegraph 索引，2026-07-01 同步）                    |

---

## 二、整体架构分层

```
┌─────────────────────────────────────────────────────────────────────────┐
│                业务应用层（用户项目 / 用户自己的脚手架）                  │
│  Spring Boot 项目 / Quarkus 项目 / Javalin 项目 / 任何 Java 容器项目     │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │ 引入对应框架的具体脚手架
                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│        具体框架脚手架层（自动装配 / 框架胶水代码）                        │
│  ┌─────────────────────────────────┐  ┌────────────────────────────┐    │
│  │  ddd4j-boot                     │  │  ddd4j-quarkus             │    │
│  │  - ddd4j-boot-ddd               │  │  - Quarkus 专属扩展/装配     │    │
│  │  - ddd4j-boot-data              │  │  - 复用 ddd4j-runtime-quarkus   │    │
│  │  - ddd4j-boot-auth              │  │  - 复用 ddd4j-web-quarkus   │    │
│  │  - ddd4j-boot-auth-shiro        │  │                            │    │
│  │  - ddd4j-boot-auth-security     │  │                            │    │
│  │  - ddd4j-boot-ddd-cola          │  │                            │    │
│  └─────────────────────────────────┘  └────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │  ddd4j-javalin                                                  │    │
│  │  - Javalin 专属扩展/装配，复用 ddd4j-runtime-guice + ddd4j-web-javalin │    │
│  └─────────────────────────────────────────────────────────────────┘    │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │ 依赖（零自动装配、零 Spring 强绑定的契约）
                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                  ddd4j 通用基础层（本次主题）                            │
│  框架无关：纯 Java 契约 / SPI 接口 / 抽象基类 / DDD 构建块              │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │
│  │-annotation│ │  -core   │ │   -kit   │ │   -ddd   │ │  -data   │       │
│  │  注解    │ │  契约    │ │  工具    │ │ DDD/CQRS │ │ 仓库SPI  │       │
│  │          │ │  上下文  │ │          │ │  ES 基类 │ │          │       │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘       │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │
│  │  -web    │ │  -auth   │ │   -mq    │ │ -cache   │ │-extensions│       │
│  │ Web 抽象 │ │ 认证抽象 │ │ MQ 抽象  │ │ 缓存抽象 │ │ 扩展工具 │       │
│  │ 契约层   │ │ 契约层   │ │ 契约层   │ │ 契约层   │ │ 扩展能力 │       │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘       │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                                │
│  │  -spring │ │  -guice  │ │-quarkus-cdi│ ← **薄包装层**              │
│  │          │ │          │ │          │  SPI 接口的纯 Java 默认实现      │
│  │          │ │          │ │          │  （无自动装配 / 无 starter）    │
│  └──────────┘ └──────────┘ └──────────┘                                │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        ▼                      ▼                      ▼
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│  ddd-4-java      │  │  cqrs-4-java     │  │  esc-api         │
│  (fuinorg)       │  │  (fuinorg)       │  │  (fuinorg)       │
│  AggregateRoot   │  │  Command/View    │  │  EventStore DB   │
└──────────────────┘ └──────────────────┘ └──────────────────┘
```

### 关键边界原则

> **`ddd4j` 通用基础层 = 零框架强绑定 + 零自动装配 + 零 starter**
> **`ddd4j-boot` / `ddd4j-quarkus` / `ddd4j-javalin` = 具体框架的胶水代码 + 自动装配**

具体框架项目（`ddd4j-boot-*` 等）按需引入 `ddd4j` 子模块，**自动初始化代码必须放在具体框架项目中**：

- `ddd4j-boot-ddd-autoconfigure` → Spring Boot 的 DDD 自动装配
- `ddd4j-boot-data-mybatis` → Spring Boot 的 MyBatis-Plus 集成
- `ddd4j-boot-auth-satoken` / `-shiro` / `-security` → 三种认证方案
- `ddd4j-boot-ddd-cola` → COLA 架构变体的自动装配

`ddd4j` 本身**永远不依赖任何 starter / spring-boot-autoconfigure / META-INF/spring.factories**。

---

## 三、ddd4j 内部模块全景

| 模块                      | 角色              | 关键产物                                                                                                                    |
|-------------------------|-----------------|-------------------------------------------------------------------------------------------------------------------------|
| `ddd4j-annotation`      | DDD 注解 + API 注解 | `@DomainEntity` `@DomainService` `@ApplicationService` `@DomainRepository` `@EnableBaseAuth` `@ApiModule`               |
| `ddd4j-core`            | 纯 Java 契约       | `Model` `Query` `Page` `R` `BaseRepository` `DomainEvent` `DddAggregateRoot` `DddDomainEvent` `DddEventStoreRepository` |
| `ddd4j-kit`             | 工具箱             | 继承式增强 Hutool                                                                                                            |
| `ddd4j-ddd-rules`       | DDD 架构规范        | `CleanDDDLayerRules`（ArchUnit）+ `ColaDDDLayerRules`（ArchUnit）                                                           |
| `ddd4j-data`            | 数据层             | MyBatis-Plus 适配、Spring 桥接、加密、数据权限、外部服务、操作日志                                                                             |
| `ddd4j-mq`              | 消息队列            | core/spring + Kafka/RabbitMQ/RocketMQ/RedisStream/ActiveMQ/MQTT/Pulsar/NATS/Disruptor/MicaMQTT/ONS/SQS/TDMQ             |
| `ddd4j-web`             | Web 层           | `RequestInfo` `SessionContext` + Javalin/Quarkus/WebMVC/WebFlux 适配                                                      |
| `ddd4j-auth`            | 认证授权            | Subject SPI + Sa-Token/Spring Security/Shiro + License                                                                  |
| `ddd4j-cache`           | 缓存抽象            | Caffeine / Guava / Hutool / Jedis / Lettuce / Redisson / JetCache / Memcached                                           |
| `ddd4j-runtime-spring`  | Spring 适配       | `SpringContext` `SpringDomainEventPublisher`                                                                            |
| `ddd4j-runtime-quarkus` | Quarkus CDI 适配  | `CdiDomainEventPublisher` `CdiSubjectProvider` `QuarkusJpaViewManager`                                                  |
| `ddd4j-runtime-guice`   | Guice 适配        | `GuiceDomainEventPublisher` `GuiceContext`                                                                              |
| `ddd4j-extensions`      | 跨领域扩展           | akka / excel / jackson / monitor / pf4j / qlexpress / validation                                                        |

---

## 四、ddd4j 的三组核心 SPI

ddd4j 站在 fuinorg 三剑客之上，自身抽象了三组**框架无关的 SPI**：

### 4.1 进程内事件发布（DomainEventPublisher）

```java
public interface DomainEventPublisher {
    void publish(DomainEvent event);

    default void publishAll(Collection<DomainEvent> events) { ...}
}
```

**三框架实现**：

- `SpringDomainEventPublisher` → `ApplicationContext.publishEvent()`
- `CdiDomainEventPublisher` → CDI `Event<DomainEvent>`
- `GuiceDomainEventPublisher` → `Guava EventBus.post()`

### 4.2 跨进程事件发布（MQEventPublisher / MQBrokerAdapter）

```java
public interface MQBrokerAdapter {
    String brokerType();

    MQEventPublisher createPublisher(Ddd4jMQProperties props);

    boolean registerConsumer(MQListener listener);
}
```

**13 个 MQ Broker/本地实现**：Kafka / RabbitMQ / RocketMQ / RedisStream / ActiveMQ / MQTT / MicaMQTT / Pulsar / NATS /
ONS / SQS / TDMQ / Disruptor（进程内高性能）

### 4.3 仓库抽象（BaseRepository / Repository）

```java
// 纯 Java 契约
public interface BaseRepository<M extends Model, Q extends Query> {
    Map<Class<?>, Class<?>> REPOSITORY_MAPPINGS = new ConcurrentHashMap<>();
    Map<Class<?>, BaseRepository<?, ?>> REPOSITORY_INSTANCES = new ConcurrentHashMap<>();

    boolean save(M model);

    boolean update(M model); ...

    Page<M> page(Q query);

    List<M> list(Q query);

    void fill(Q query, M model); // CQRS 读侧聚合
}

// fdd4j-core 抽象 SPI（纯 Java，零 ORM）
public interface Repository<M, Q, P extends Serializable> {
    Optional<M> findById(P id);

    Optional<M> findOne(Q query);

    List<M> findList(Q query);

    M save(M entity);

    M updateById(M entity);
}
```

**多 ORM 实现**：MyBatis-Plus（ddd4j-data-mybatis）、Hibernate Panache（Quarkus）、JDBI（Javalin）

---

## 五、与底层基础库的关系

| ddd4j 产物                             | 复用自 fuinorg 系列             | 扩展点                                         |
|--------------------------------------|----------------------------|---------------------------------------------|
| `DddAggregateRoot`                   | 继承 `AbstractAggregateRoot` | 增加 `createTime`/`updateTime` 审计字段（无 ORM 注解） |
| `DddDomainEvent`                     | 继承 `AbstractDomainEvent`   | 兼容 Jackson 序列化                              |
| `DddEventStoreRepository`            | 继承 `EventStoreRepository`  | 封装乐观锁冲突重试（最多 3 次）                           |
| `DomainEventPublisher` (ddd4j 自创)    | —                          | 三框架运行时绑定（Spring/CDI/Guice）                  |
| `MQEventPublisher` (ddd4j 自创)        | —                          | 12 种 MQ 统一抽象                                |
| `BaseAggregateController` (ddd4j 自创) | —                          | 通用 CRUD + 业务行为（disable/enable）              |
| `CleanDDDLayerRules` (ddd4j 自创)      | —                          | ArchUnit 编译期架构守护                            |

---

## 六、ddd4j 应补充的 CQRS/ES 能力（基于 cqrs-4-java 对照）

| 优先级    | 缺失能力                                                | 实施建议                                                                          |
|--------|-----------------------------------------------------|-------------------------------------------------------------------------------|
| **P0** | `Command` / `AggregateCommand` 接口                   | 抽取到 `ddd4j-core` 作为契约                                                         |
| **P0** | `CommandExecutor` SPI                               | 三框架各自实现                                                                       |
| **P0** | `CommandHandler` 注解 + 自动注册                          | 兼容 Spring `@Component` / CDI Bean                                             |
| **P0** | `MultiCommandExecutor` 组合                           | 复用 fuinorg 实现                                                                 |
| **P0** | `Result<T>` + `ResultType`                          | 与 `R<T>` 区分（业务结果 vs HTTP 结果）                                                  |
| **P1** | `View` / `JpaView` 接口                               | 抽取到 `ddd4j-core`                                                              |
| **P1** | `ViewManager` 抽象                                    | ddd4j-runtime/ddd4j-runtime-spring 与 ddd4j-runtime/ddd4j-runtime-quarkus 各自实现 |
| **P1** | `ProjectionPosition` 投影位置持久化                        | 抽取为公共 SPI                                                                     |
| **P1** | `@CreateEvent` / `@UpdateEvent` / `@DeleteEvent` 注解 | 复用 fuinorg                                                                    |
| **P2** | 多序列化器（JAXB / JSON-B）                                | 视业务需求                                                                         |
| **P2** | APT 代码生成（Command/View 模板）                           | 参考 fuinorg codegen                                                            |

---

## 七、三框架运行时绑定对照表

| 维度         | ddd4j-runtime-spring                | ddd4j-runtime-quarkus     | ddd4j-runtime-guice |
|------------|-------------------------------------|---------------------------|---------------------|
| **DI 容器**  | `ApplicationContext`                | `Arc` (CDI)               | `Guice Injector`    |
| **事件发布**   | `ApplicationContext.publishEvent()` | `Event<T>.fire()`         | `EventBus.post()`   |
| **配置属性**   | `@ConfigurationProperties`          | `@ConfigProperty`         | `@Provides`         |
| **Web 框架** | Spring MVC / WebFlux                | Quarkus RESTEasy (JAX-RS) | Javalin             |
| **事务**     | `@Transactional`                    | `@Transactional` (JTA)    | 手动控制                |
| **调度**     | `SchedulingConfigurer`              | `@Scheduled`              | 自定义线程池              |
| **AOP**    | Spring AOP                          | Interceptor Binding       | MethodInterceptor   |

---

## 八、ddd4j 的核心竞争力

### 8.1 框架无关的纯净契约层

ddd4j-core 绝大部分文件（`api/`、`context/`、`ddd/`、`cqrs/`、`event/`、`util/`）零框架 import，可同时被 Spring / Quarkus / Javalin 复用。

### 8.2 12 种 MQ 统一抽象

`MQBrokerAdapter` SPI 让业务代码**完全无感**切换消息中间件——从 Kafka 切到 RabbitMQ 零业务代码改动。

### 8.3 ArchUnit 编译期架构守护

`CleanDDDLayerRules` / `ColaDDDLayerRules` 注解驱动规则，业务项目继承 `CleanArchitectureTest` 即可在 CI 阶段强制执行分层纪律。

### 8.4 双轨 DDD 模型

- **ActiveRecord 轨道**：`BaseEntity<T> extends Model<T>`（快速 CRUD，向后兼容）
- **纯净 DDD 轨道**：`DddAggregateRoot<ID> extends AbstractAggregateRoot<ID>`（零 ORM 依赖，支持 ES）

### 8.5 站在巨人肩膀上

直接复用 fuinorg ddd-4-java + cqrs-4-java + esc-api 多年沉淀的 DDD/CQRS/ES 实现，避免重复造轮子。

---

## 九、原始架构简图（旧版）

> 以下保留作为基线参考，完整版见 `ddd4j_architecture.html` SVG 图。

### 核心目的

- 基于 Spring Boot 3.5.x 的工程脚手架，统一依赖版本、标准响应与异常、自动配置与示例工程，降低新服务的搭建成本。

### 顶层结构

- 根聚合：`ddd4j/pom.xml`
- 管理三件套：
    - 版本对齐：`ddd4j-bom/pom.xml`
    - 依赖声明：`ddd4j-dependencies/pom.xml`
    - 打包父：`ddd4j-parent/pom.xml`
- 核心能力：`ddd4j-core`
- 组件集：`ddd4j-cmpt/*`
- 示例集：`ddd4j-samples/*`

### 版本与构建

- Java：17；Spring Boot：3.5.6；Spring Framework：6.2.x（根 POM）
- Maven 多模块聚合，统一 BOM 与插件管理（编译、源码、Javadoc、签名、Release、打包与 Docker）

### 模块关系（Mermaid）

```mermaid
graph TD
    A[ddd4j (聚合)] --> B[ddd4j-bom]
A --> C[ddd4j-dependencies]
A --> D[ddd4j-parent]
A --> E[ddd4j-core]
A --> F[ddd4j-cmpt]
A --> G[ddd4j-samples]

F --> F1[cmpt-webmvc]
F --> F2[cmpt-webflux]
F --> F3[cmpt-jackson]
F --> F4[cmpt-crypto]
F --> F5[cmpt-kafka]
F --> F6[cmpt-license]
F --> F7[cmpt-satoken]
F --> F8[cmpt-logs]
F --> F9[cmpt-datascope]
F --> F10[cmpt-external]
F --> F11[cmpt-akka]

G --> G1[sample-druid]
G --> G2[sample-hikaricp]
G --> G3[sample-r2dbc-webflux]
G1 --> G1a[amqp/kafka/rocketmq/mqtt]
G2 --> G2a[amqp/activemq/kafka/rocketmq]
```

## 核心能力速览

- 响应模型与状态码：`ddd4j-core/src/main/java/io/ddd4j/boot/core/ApiRestResponse.java`、
  `ddd4j-core/src/main/java/io/ddd4j/boot/core/ApiCode.java`
- Service/Mapper/Controller 基类：`ddd4j-core/src/main/java/io/ddd4j/boot/core/service/BaseServiceImpl.java`、
  `ddd4j-core/src/main/java/io/ddd4j/boot/core/mybatis/mapper/BaseMapper.java`、
  `ddd4j-core/src/main/java/io/ddd4j/boot/core/web/BaseController.java`
- 全局异常（MVC/WebFlux）：
  `ddd4j-cmpt/ddd4j-cmpt-webmvc/src/main/java/io/ddd4j/boot/cmpt/webmvc/webmvc/GlobalExceptionHandler.java`、
  `ddd4j-cmpt/ddd4j-cmpt-webflux/src/main/java/io/ddd4j/boot/cmpt/webflux/handler/GlobalExceptionHandler.java`
- 自动配置注册：`ddd4j-cmpt/*/src/main/resources/META-INF/spring.factories`

## 代码参考

- 根聚合与版本：`ddd4j/pom.xml:31-39`
- MVC 基础配置：
  `ddd4j-cmpt/ddd4j-cmpt-webmvc/src/main/java/io/ddd4j/boot/cmpt/webmvc/DefaultWebMvcConfiguration.java:53-76`
- 响应模型：`ddd4j-core/src/main/java/io/ddd4j/boot/core/ApiRestResponse.java:118-137`
- 全局异常（MVC）：
  `ddd4j-cmpt/ddd4j-cmpt-webmvc/src/main/java/io/ddd4j/boot/cmpt/webmvc/webmvc/GlobalExceptionHandler.java:72-81`
