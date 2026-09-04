# ddd4j 架构全景设计

- **日期**：2026-06-29
- **作者**：ddd4j 架构团队
- **状态**：已实施（2.0.x 基线）
- **Codegraph 快照**：823 文件 / 13,671 节点 / 23,360 边（2026-07-01 同步）

## 1. 目标与范围

ddd4j 是一个**框架无关的 DDD/CQRS/ES 通用基础层**，为 Spring Boot、Quarkus、Javalin、Micronaut、Vert.x、Helidon、Dropwizard 等容器框架提供同一套纯净领域模型契约。

**核心需求**：

1. 核心契约层零 Spring/MyBatis/Servlet import，可同时被多个框架复用
2. 提供普通充血模型 `AggregateRoot` / `Repository<M, P, ID>` 和 fuinorg ES 轨道 `DddAggregateRoot` / `DddDomainEvent`
3. CQRS 命令查询分离：Command/View/ProjectionPosition 等 SPI
4. 13 个 MQ Broker/本地实现的统一抽象
5. 8 类 Web 适配器的统一契约
6. ArchUnit 编译期架构守护（9 条规则）

**非目标**：

- 不提供 Spring Boot 自动装配（留在外部 `ddd4j-boot` 仓库）
- 不提供业务脚手架（只提供基础层）

## 2. 总体架构

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
    ddd-4-java       cqrs-4-java       esc-api
    (fuinorg)        (fuinorg)         (fuinorg)
```

## 3. 模块结构

| 模块 | 角色 | 关键产物 |
|------|------|----------|
| `ddd4j-bom` | BOM 版本管理 | 外部项目引用统一版本 |
| `ddd4j-dependencies` | 第三方依赖集中管理 | Spring 6.x / Jackson 2.22 / Reactor 等 |
| `ddd4j-annotation` | DDD 注解 + API 注解 | `@DomainEntity` `@DomainService` `@ApplicationService` `@DomainRepository` |
| `ddd4j-core` | **纯 Java 契约层** | `AggregateRoot` `Repository<M,P,ID>` `Query<T>` `Page` `R` `DomainEvent` `DddAggregateRoot` `DddDomainEvent` |
| `ddd4j-kit` | 工具箱 | 继承式增强 Hutool，Cache/Lang/Web 工具 |
| `ddd4j-ddd-rules` | DDD 架构规范检查 | `CleanDDDLayerRules` `ColaDDDLayerRules`（ArchUnit） |
| `ddd4j-data` | 数据层抽象 | 三 ORM 轨道 + 加密/数据权限/外部服务/日志 |
| `ddd4j-mq` | 消息队列抽象 | `MQBrokerAdapter` SPI + Spring 桥接 + 13 个 Broker 实现 |
| `ddd4j-web` | Web 层抽象 | 8 类 Web 适配器 |
| `ddd4j-auth` | 认证授权抽象 | `Subject` SPI + Sa-Token/Security/Shiro 实现 |
| `ddd4j-cache` | 缓存抽象 | 缓存 SPI 及多实现 |
| `ddd4j-runtime` | 多框架运行时绑定 | Spring / Quarkus / Guice / Micronaut / Vert.x / Helidon / Dropwizard / Testkit |
| `ddd4j-extensions` | 跨领域扩展 | akka / excel / jackson / license / monitor / pf4j / qlexpress / validation |
| `ddd4j-samples` | 示例工程 | 共享 Order 业务内核 + 多运行时示例 |

## 4. 核心契约层

### 4.1 DDD 战术模式

- `AggregateRoot<ID>`：普通充血模型聚合根
- `Repository<M, P, ID>`：统一仓储接口，对齐 MyBatis-Plus BaseMapper
- `Query<T>`：Lambda 类型安全条件构建（`eq`/`like`/`in`/`between`/`orderByDesc` 等）
- `DddAggregateRoot<ID>`：基于 fuinorg ddd-4-java 的 ES 轨道聚合根
- `DddDomainEvent<ID>`：基于 fuinorg 的领域事件基类

### 4.2 CQRS 命令查询分离

- `Command`：CQRS 命令标记接口
- `TypedEventHandler`：读模型事件处理器
- `ProjectionPosition` / `ProjectionPositionRepository`：投影位置 SPI

### 4.3 三轨 DDD 模型

1. **轻量 CRUD 轨道**：`PO/Query` 快速 CRUD
2. **普通充血模型轨道**：`AggregateRoot/Repository` 普通 DDD
3. **fuinorg CQRS/ES 轨道**：`DddAggregateRoot/DddDomainEvent/EventStore`

### 4.4 三组核心 SPI

- `DomainEventPublisher`：进程内事件
- `MQEventPublisher`：跨进程消息
- `Repository<M, P, ID>`：统一领域仓储

## 5. 运行时绑定

| 框架 | 运行时绑定 | DI 容器 | 事件发布 |
|------|-----------|---------|---------|
| Spring Boot | `ddd4j-runtime-spring` | `ApplicationContext` | `AppCtx.publishEvent()` |
| Quarkus | `ddd4j-runtime-quarkus` | Arc (CDI) | `Event<T>.fire()` |
| Javalin | `ddd4j-runtime-guice` | Guice Injector | `EventBus.post()` |
| Micronaut | `ddd4j-runtime-micronaut` | Micronaut Context | `publishEvent()` |
| Vert.x | `ddd4j-runtime-vertx` | 显式 Runtime | Vert.x EventBus |
| Helidon | `ddd4j-runtime-helidon` | CDI / BeanManager | CDI Event |
| Dropwizard | `ddd4j-runtime-dropwizard` | 显式 Bundle | Listener 集合 |

## 6. ArchUnit 架构边界守护

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
