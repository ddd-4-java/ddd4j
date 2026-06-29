# Ddd4j — 框架无关的 DDD/CQRS/ES 通用基础层

**Ddd4j** 是一个**不与任何具体容器框架强绑定**的 DDD 项目脚手架，为 [ddd4j-boot](https://github.com/hiwepy/ddd4j-boot)（Spring Boot）、[ddd4j-quarkus](https://github.com/hiwepy/ddd4j-quarkus)、[ddd4j-javalin](https://github.com/hiwepy/ddd4j-javalin) 三种容器框架提供**同一套纯净的、可复用的领域层基础**。

基于轻量级 [ddd-4-java](https://github.com/fuinorg/ddd-4-java) 和 [cqrs-4-java](https://github.com/fuinorg/cqrs-4-java) 库实现领域驱动设计、命令查询职责分离（CQRS）和事件溯源（Event Sourcing），遵循 **Eric Evans** 和 **Vaughn Vernon** 的 DDD 经典理论。

### 🎯 核心定位

| 维度 | 定位 |
|------|------|
| **本质** | 框架无关的 DDD/CQRS/ES 通用基础层（非 Spring Boot 项目） |
| **运行时** | Java 17，不依赖任何容器框架 |
| **消费方** | ddd4j-boot（Spring Boot）/ ddd4j-quarkus / ddd4j-javalin |
| **底层依赖** | fuinorg ddd-4-java + cqrs-4-java + esc-api |
| **铁律** | 零 `@AutoConfiguration` · 零 `spring.factories` · 零 starter |

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
                           │ 依赖（零自动装配、零 starter）
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 第三层：ddd4j 通用基础层（本项目）                                    │
│ 纯 Java 契约 / SPI 接口 / 抽象基类 / DDD 构建块 / ArchUnit 规则     │
└──────────────────────────┬──────────────────────────────────────────┘
           ┌───────────────┼───────────────┐
           ▼               ▼               ▼
    ddd-4-java       cqrs-4-java       esc-api
    (fuinorg)        (fuinorg)         (fuinorg)
```

### ✨ 主要特性

- **框架无关**：核心契约层零 Spring/MyBatis/Servlet import，可同时被 Spring Boot / Quarkus / Javalin 复用
- **DDD 战术模式**：基于 fuinorg ddd-4-java，提供 `DddAggregateRoot`、`DddDomainEvent`、`DddEventStoreRepository` 等构建块
- **CQRS 命令查询分离**：基于 fuinorg cqrs-4-java，提供 Command/View/ProjectionPosition 等 SPI
- **事件溯源（ES）**：聚合根状态通过事件流重建，支持时间旅行和完整审计
- **双轨 DDD 模型**：ActiveRecord 轨道（快速 CRUD）+ 纯净 DDD 轨道（零 ORM 依赖，支持 ES）
- **三组核心 SPI**：`DomainEventPublisher`（进程内事件）/ `MQEventPublisher`（跨进程消息）/ `BaseRepository`（数据仓库）
- **12 种 MQ 统一抽象**：Kafka / RabbitMQ / RocketMQ / ActiveMQ / Pulsar / NATS / MQTT / Redis Stream / SQS / ONS / TDMQ / Disruptor
- **三框架适配**：`ddd4j-spring` / `ddd4j-quarkus` / `ddd4j-guice` 提供 SPI 的框架实现
- **ArchUnit 编译期守护**：9 条架构边界规则，CI 阶段强制执行分层纪律
- **COLA / Clean Architecture 支持**：注解驱动的架构规范检查

### 📚 DDD/CQRS 学习资源

- **[DDD 思维导图](./docs/ddd/DDD%20思维导图.md)**：战略设计 + 战术设计完整知识体系
- **[CQRS 思维导图](./docs/ddd/CQRS%20思维导图.md)**：命令查询职责分离核心概念
- **[参考示例项目](https://github.com/fuinorg/ddd-cqrs-4-java-example)**：Greg Young 风格的 DDD/CQRS/Event Sourcing 微服务示例
- **[架构边界规范](./docs/architecture/architecture-boundary.md)**：ddd4j 与各框架项目的职责铁律
- **[架构全景](./docs/architecture/architecture.md)**：模块全景、SPI 设计、双框架适配对照

### 🏗️ 项目架构

**Maven 模块架构**：

| 模块 | 角色 | 关键产物 |
|------|------|---------|
| `ddd4j-bom` | BOM 版本管理 | 外部项目引用统一版本 |
| `ddd4j-dependencies` | 第三方依赖集中管理 | Spring 6.x / Jackson 2.22 / Reactor 等 |
| `ddd4j-annotation` | DDD 注解 + API 注解 | `@DomainEntity` `@DomainService` `@ApplicationService` `@DomainRepository` |
| `ddd4j-core` | **纯 Java 契约层** | `Model` `Query` `Page` `R` `BaseRepository` `DomainEvent` `DddAggregateRoot` `DddDomainEvent` |
| `ddd4j-kit` | 工具箱 | 继承式增强 Hutool，Cache/Lang/Web 工具 |
| `ddd4j-ddd` | DDD 架构规范检查 | `CleanDDDLayerRules` `ColaDDDLayerRules`（ArchUnit） |
| `ddd4j-data` | 数据层抽象 | Repository SPI + MyBatis-Plus 实现 + 加密/数据权限/日志 |
| `ddd4j-mq` | 消息队列抽象 | `MQBrokerAdapter` SPI + 12 种 MQ 实现 |
| `ddd4j-web` | Web 层抽象 | `RequestInfo` `SessionContext` + WebMVC/WebFlux 实现 |
| `ddd4j-auth` | 认证授权抽象 | `Subject` SPI + Sa-Token/Security/Shiro 实现 |
| `ddd4j-cache` | 缓存抽象 | 缓存 SPI 及实现 |
| `ddd4j-spring` | Spring 适配层 | `SpringDomainEventPublisher` `SpringContext` `SpringI18nProvider` |
| `ddd4j-quarkus` | Quarkus 适配层 | `CdiDomainEventPublisher` + CDI 实现 |
| `ddd4j-guice` | Guice 适配层 | `GuiceDomainEventPublisher` + Guava EventBus |
| `ddd4j-extensions` | 跨领域扩展 | akka / excel / jackson / license / monitor / pf4j / qlexpress / validation |
| `ddd4j-parent` | Maven 父 POM | 编译/打包/发布规则 |
| `ddd4j-samples` | 示例工程 | Sa-Token / Spring Security / Shiro 集成示例 |

**模块结构树**：

```
|--ddd4j                                #通用基础层（零 starter，零自动装配）
|----ddd4j-bom                          #BOM依赖管理，用于外部项目引用 ddd4j 模块版本管理
|----ddd4j-dependencies                 #公共依赖，便于依赖组件版本控制
|----ddd4j-annotation                   #注解层，DDD构造型注解+API注解，零框架依赖
|----ddd4j-core                         #核心契约层，纯Java DDD基础抽象（Model/Query/BaseRepository/DomainEvent/DddAggregateRoot等）
|----ddd4j-kit                          #工具箱，继承式增强Hutool，提供Cache/Lang/Web工具
|----ddd4j-ddd                          #DDD架构规范检查（基于ArchUnit）
|------ddd4j-ddd-clean                  #Clean Architecture分层纪律规则
|------ddd4j-ddd-cola                   #COLA菱形架构分层纪律规则
|----ddd4j-data                         #数据抽象聚合
|------ddd4j-data-core                  #纯Java Repository SPI，零ORM依赖
|------ddd4j-data-mybatis               #MyBatis-Plus实现：BaseRepositoryImpl、TypeHandler、拦截器
|------ddd4j-data-spring                #Spring桥接：RepositoryBean注册、静态注册表初始化
|------ddd4j-data-crypto                #加解密策略：CryptoStrategy/Provider/注解
|------ddd4j-data-datascope             #数据权限组件
|------ddd4j-data-external              #外部服务集成：地理位置/天气/行政区划/序列号
|------ddd4j-data-logs                  #API操作日志：ApiOperationLogAspect+Provider
|----ddd4j-mq                           #消息队列抽象聚合
|------ddd4j-mq-core                    #纯Java MQ SPI：MQEventPublisher/MQBrokerAdapter/MQListener
|------ddd4j-mq-spring                  #Spring Messaging桥接：Message↔MQMessage转换
|------ddd4j-mq-kafka                   #Apache Kafka实现
|------ddd4j-mq-rabbitmq                #RabbitMQ实现
|------ddd4j-mq-rocketmq                #Apache RocketMQ实现
|------ddd4j-mq-activemq                #Apache ActiveMQ Artemis实现
|------ddd4j-mq-pulsar                  #Apache Pulsar实现
|------ddd4j-mq-nats                    #NATS JetStream实现
|------ddd4j-mq-mqtt                    #Eclipse Paho MQTT实现
|------ddd4j-mq-mqtt-mica               #Mica MQTT Client实现
|------ddd4j-mq-redis-stream            #Redis Stream实现
|------ddd4j-mq-sqs                     #AWS SQS实现
|------ddd4j-mq-ons                     #阿里云ONS实现
|------ddd4j-mq-tdmq                    #腾讯云TDMQ实现
|------ddd4j-mq-disruptor               #LMAX Disruptor本地MQ实现
|----ddd4j-web                          #Web抽象聚合
|------ddd4j-web-core                   #纯Java SPI：RequestInfo/SessionContext/IpUtils
|------ddd4j-web-webmvc                 #Spring MVC实现：Controller/拦截器/全局异常处理
|------ddd4j-web-webflux                #Spring WebFlux实现：响应式Controller/ErrorAttributes
|----ddd4j-auth                         #认证/授权抽象聚合
|------ddd4j-auth-core                  #纯Java SPI：Subject/AuthPrincipal/SubjectProvider
|------ddd4j-auth-spring                #Spring桥接：SubjectRegistrar
|------ddd4j-auth-satoken               #Sa-Token实现
|------ddd4j-auth-security              #Spring Security实现
|------ddd4j-auth-shiro                 #Apache Shiro实现
|----ddd4j-cache                        #缓存抽象及实现
|----ddd4j-spring                       #Spring适配层：DomainEventPublisher/I18nProvider/SubjectProvider
|----ddd4j-quarkus                      #Quarkus适配层：CDI实现
|----ddd4j-guice                        #Guice适配层：Guava EventBus实现
|----ddd4j-extensions                   #跨领域扩展
|------ddd4j-extension-akka             #Akka Actor系统组件
|------ddd4j-extension-excel            #Excel导入导出组件
|------ddd4j-extension-jackson          #Jackson序列化增强组件
|------ddd4j-extension-license          #软件授权TrueLicense组件
|------ddd4j-extension-monitor          #监控告警：钉钉/企微机器人+日志告警
|------ddd4j-extension-pf4j             #PF4J插件化组件
|------ddd4j-extension-qlexpress        #QLExpress规则引擎组件
|------ddd4j-extension-validation       #参数校验增强组件
|----ddd4j-parent                       #业务工程父POM，定义编译/打包/发布规则
|----ddd4j-samples                      #示例工程
|--------ddd4j-sample-auth-satoken      #Sa-Token集成示例，演示SubjectKit统一鉴权入口
|--------ddd4j-sample-auth-security     #Spring Security集成示例
|--------ddd4j-sample-auth-shiro        #Apache Shiro集成示例
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
    <!-- 框架适配（按需） -->
    <dependency>
        <groupId>io.ddd4j</groupId>
        <artifactId>ddd4j-spring</artifactId>
    </dependency>
</dependencies>
```

#### 2. 三框架适配方式

| 框架 | 适配层 | DI 容器 | 事件发布 | Web 框架 |
|------|--------|---------|---------|---------|
| Spring Boot | `ddd4j-spring` | `ApplicationContext` | `AppCtx.publishEvent()` | Spring MVC / WebFlux |
| Quarkus | `ddd4j-quarkus` | Arc (CDI) | `Event<T>.fire()` | RESTEasy (JAX-RS) |
| Javalin | `ddd4j-guice` | Guice Injector | `EventBus.post()` | Javalin |

#### 3. 业务项目继承父 POM

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

| 规则 | 说明 |
|------|------|
| `no_autoconfiguration_in_ddd4j` | ddd4j 全模块不得包含 `@AutoConfiguration` |
| `no_spring_in_core_modules` | core / kit / annotation 不得依赖 `org.springframework.*` |
| `no_spring_messaging_in_mq_core` | mq-core 不得依赖 `org.springframework.messaging.*` |
| `no_spring_factories_in_core` | core 不得引用 `AutoConfiguration.imports` |
| `no_hutool_all_in_core` | core 不得依赖 hutool 全量包 |
| `core_no_mybatis` | core 不得依赖 `com.baomidou.*` |
| `core_no_servlet` | core 不得依赖 `jakarta.servlet.*` |
| `core_no_validator` | core 不得依赖 `org.hibernate.validator.*` |
| `core_no_aspectj` | core 不得依赖 `org.aspectj.*` |

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

| 文档 | 说明 |
|------|------|
| [架构全景](./docs/architecture/architecture.md) | 模块全景、SPI 设计、双框架适配对照 |
| [架构边界规范](./docs/architecture/architecture-boundary.md) | ddd4j 与各框架项目的职责铁律 |
| [DDD 思维导图](./docs/ddd/DDD%20思维导图.md) | DDD 战略+战术设计知识体系 |
| [CQRS 思维导图](./docs/ddd/CQRS%20思维导图.md) | CQRS 核心概念 |
| [DDD 经典分层架构](./docs/ddd/1、DDD%20经典分层架构目录结构.md) | 分层架构目录参考 |
| [六边形架构](./docs/ddd/2、六边形架构详细目录结构参考.md) | 六边形架构目录参考 |
| [整洁架构](./docs/ddd/3、整洁架构详细目录结构参考.md) | 整洁架构目录参考 |
| [COLA V5 架构](./docs/ddd/4、COLA%20V5%20架构详细目录结构参考.md) | COLA 菱形架构目录参考 |
| [数据层优化计划](./docs/migration/ddd4j-data-optimization-plan.md) | ddd4j-data 模块优化方案 |
| [迁移指南](./docs/migration/optional-migrations.md) | 从旧版迁移到 2.0.x 的指南 |
