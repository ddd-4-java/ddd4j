# ddd4j 最新内容 CodeGraph 导览

分析时间：2026-07-08  
分析目录：`/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j`  
CodeGraph 状态：索引最新，`1,332` 个文件、`23,405` 个节点、`46,055` 条边。  
当前分支：`feature/2.0.x`  
最近提交：`d9906a3b 2026-07-07 23:44:19 +0800 refactor(mq): 重构消息队列客户端实现`

## 一句话结论

当前 `ddd4j` 主线已经从早期偏 Spring Boot 的结构，收敛成一个框架无关的 DDD 基础库：`ddd4j-core` 持有
DDD/CQRS/Auth/Cache/MQ 等核心契约，`ddd4j-runtime-*` 负责 Spring、Quarkus、Guice 容器绑定，`ddd4j-web`、`ddd4j-data`、
`ddd4j-mq`、`ddd4j-auth`、`ddd4j-cache` 按能力聚合，`ddd4j-boot` 深度自动装配则留在外部 Boot 仓库。

## 根模块结构

根 `pom.xml` 的当前版本线是 `2.0.x.20260630-SNAPSHOT`，Java 基线是 17。顶层模块按职责分为：

- 契约与基础层：`ddd4j-annotation`、`ddd4j-core`、`ddd4j-kit`
- 架构规则层：`ddd4j-ddd-rules`
- 能力聚合层：`ddd4j-data`、`ddd4j-mq`、`ddd4j-web`、`ddd4j-auth`、`ddd4j-cache`
- 运行时绑定层：`ddd4j-runtime`
- 跨领域扩展：`ddd4j-extensions`
- 发布与样例：`ddd4j-parent`、`ddd4j-bom`、`ddd4j-dependencies`、`ddd4j-samples`

这个结构的关键变化是：能力模块不再混入 Boot 自动装配职责，Spring Boot 自动配置和深度整合应继续归到
`workspace-ddd4j-boot/ddd4j-boot` 侧。

## 核心契约层

`ddd4j-core` 是当前主线的中心，CodeGraph 能看到这些核心入口：

- `io.ddd4j.core.ddd.event.DomainEvent`：领域事件基类，支持 `tenantIn`、`supports`、`publish` 等事件语义。
- `io.ddd4j.core.ddd.event.DomainEventPublisher`：领域事件发布 SPI。
- `io.ddd4j.core.ddd.repository.Repository`：聚合仓储 SPI，包含 `findById`、`save`、`existsById`、`delete`、`deleteById`。
- `io.ddd4j.core.cqrs.command.Command`：CQRS 命令标记接口，运行时侧由 Quarkus/Spring/Guice 的 CommandBus 适配。
- `io.ddd4j.core.cqrs.readmodel.TypedEventHandler`：读模型事件处理器，样例中的 CQRS 事件分发依赖它。

数据 SPI 的收敛方向也已经写进 `ddd4j-data/pom.xml`：`Repository/TypeHandlerRegistry` 这类基础契约应在 `ddd4j-core`
，数据模块只保留 MyBatis、Spring、crypto、external、logs、datascope 等能力实现。

## DDD 架构规则

`ddd4j-ddd-rules` 负责把 DDD 分层纪律做成可执行规则，目前包含：

- `ddd4j-ddd-rules-clean`
- `ddd4j-ddd-rules-cola`

`CleanDDDLayerRules` 是注解驱动的 ArchUnit 规则集，检查：

- `@DomainEntity`、`@DomainService` 必须在 `domain` 包。
- `@ApplicationService`、`@CommandExecutor`、`@QueryService` 必须在 `app` 或 `application` 包。
- `@DomainRepository` 必须在 `infrastructure` 或 `infras` 包。
- `domain` 包不得依赖 `web/controller/adapter`、`infrastructure/infras`、Spring/MyBatis 等框架。

`CleanArchitectureChecker` 在目录层面要求 `domain/application/adapter/infrastructure` 四层，并尝试用 ArchUnit
做类依赖检查。这个模块适合后续作为业务项目架构守护测试的入口。

## 运行时绑定

`ddd4j-runtime` 当前聚合三个容器实现：

- `ddd4j-runtime-spring`
- `ddd4j-runtime-quarkus`
- `ddd4j-runtime-guice`

事件发布的绑定关系非常清晰：

- Spring：`SpringDomainEventPublisher` 通过 `ApplicationEventPublisher.publishEvent` 发布领域事件。
- Quarkus：`CdiDomainEventPublisher` 通过 CDI `Event<Object>.fire` 发布。
- Guice/Javalin：`GuiceDomainEventPublisher` 通过 Guava `EventBus.post` 发布。

Guice 侧还有一个重要入口 `DddAnnotationModule`：它用 ClassGraph 扫描 `@DomainService`、`@ApplicationService` 等 DDD 注解，按
Singleton 规则绑定到 Guice 容器。这是 Javalin 方向收敛后的主要运行时胶水层。

## Web 能力

`ddd4j-web` 当前包含：

- `ddd4j-web-javalin`
- `ddd4j-web-quarkus`
- `ddd4j-web-webflux`
- `ddd4j-web-webmvc`

`QuarkusAggregateController` 提供通用聚合 REST 骨架，包括 `page/getById/create/update/delete/disable/enable`
等方法，并把具体业务动作留给 `listPage/detail/save/modify/remove/doDisable/doEnable`。WebMVC/WebFlux
侧则有各自的错误处理、国际化资源解析和控制器抽象。

## MQ 最新重构

最近提交和当前工作树都集中在 MQ 重构上。CodeGraph 中的核心关系是：

- `MQClient` 是 broker 实现的统一 SPI，定义 `impl/init/initProducer/initConsumer/start/close/consume`。
- `MQClient.init` 会按 `MQProperties.enabled` 和 `properties.broker == impl()` 决定是否初始化当前 broker。
- 生产者通过 `BaseContext` 注册到 `MQEvent.MQ_EVENT_PUBLISHER`，现在是 `Map<String, Consumer<MQEvent>>`，key 为 `impl()`
  ，支持多 broker 共存。
- 消费时 `MQClient.consume` 会先按 `event.supports(listener.supports())` 过滤，再设置租户上下文，必要时通过
  `MQEventStorer` 持久化，最后反射调用监听方法。
- `MQEvent` 带 `namespace/topic/tag/concat/tenantId/broker`，发布入口支持指定 topic、tag、tenantId，并最终按 broker 找到对应
  producer。

当前工作树还有未提交改动，涉及：

- `ddd4j-mq/ddd4j-mq-core/src/main/java/io/ddd4j/mq/MQClient.java`
- `ddd4j-mq/ddd4j-mq-core/src/main/java/io/ddd4j/mq/event/MQEvent.java`
- `ddd4j-mq/ddd4j-mq-activemq/src/main/java/io/ddd4j/mq/activemq/ActiveMQEventPublisher.java`
- 以及 Akka、Jackson、Monitor、Validation 等扩展模块的 POM 和实现调整。

注意：`TypeHandlerRegistry.java` 和 `ActiveMQEventPublisher.java` 当前存在 staged add + unstaged delete
的状态，后续提交前需要明确是保留新文件，还是完成删除/迁移。

## 数据、认证、缓存

`ddd4j-data` 的定位是数据实现聚合：

- `ddd4j-data-mybatis`
- `ddd4j-data-spring`
- `ddd4j-data-crypto`
- `ddd4j-data-external`
- `ddd4j-data-logs`
- `ddd4j-data-datascope`

`ddd4j-auth` 聚合三类认证/授权实现：

- `ddd4j-auth-satoken`
- `ddd4j-auth-security`
- `ddd4j-auth-shiro`

Auth 的 `Subject/SubjectKit/SubjectProvider/AuthRequest/SubjectDataProvider/SubjectStrategy` 契约位于 `ddd4j-core`
，具体运行环境桥接通过 `ddd4j-runtime-*` 提供。

`ddd4j-cache` 现在是单 jar、多后端可选依赖模式，缓存 SPI 在 `ddd4j-core/cache`，实现包括
Caffeine、Guava、Hutool、Jedis、Lettuce、Redisson、Memcached、JetCache，其中外部客户端大多是 optional。

## 扩展与样例

`ddd4j-extensions` 聚合跨领域扩展：

- `ddd4j-extension-akka`
- `ddd4j-extension-excel`
- `ddd4j-extension-jackson`
- `ddd4j-extension-license`
- `ddd4j-extension-pf4j`
- `ddd4j-extension-qlexpress`
- `ddd4j-extension-validation`
- `ddd4j-extension-monitor`

`ddd4j-samples` 是理解主线设计的最佳业务入口，覆盖：

- Spring、Quarkus、Javalin 的普通 DDD 示例。
- Spring、Quarkus、Javalin 的 CQRS/ES 示例。
- Spring/Quarkus/Javalin 与 Sa-Token、Shiro、Spring Security 的鉴权矩阵。

## 后续阅读路线

建议按下面顺序继续深入：

1. `ddd4j-core/src/main/java/io/ddd4j/core/ddd/event/DomainEvent.java`
2. `ddd4j-core/src/main/java/io/ddd4j/core/ddd/repository/Repository.java`
3. `ddd4j-ddd-rules/ddd4j-ddd-rules-clean/src/main/java/io/ddd4j/ddd/clean/rules/CleanDDDLayerRules.java`
4. `ddd4j-runtime/ddd4j-runtime-guice/src/main/java/io/ddd4j/guice/DddAnnotationModule.java`
5. `ddd4j-runtime/ddd4j-runtime-spring/src/main/java/io/ddd4j/spring/event/SpringDomainEventPublisher.java`
6. `ddd4j-runtime/ddd4j-runtime-quarkus/src/main/java/io/ddd4j/quarkus/event/CdiDomainEventPublisher.java`
7. `ddd4j-mq/ddd4j-mq-core/src/main/java/io/ddd4j/mq/MQClient.java`
8. `ddd4j-mq/ddd4j-mq-core/src/main/java/io/ddd4j/mq/event/MQEvent.java`
9. `ddd4j-web/ddd4j-web-quarkus/src/main/java/io/ddd4j/web/quarkus/controller/QuarkusAggregateController.java`
10. `ddd4j-samples/ddd4j-sample-javalin-cqrs`

## 常用 CodeGraph 命令

```bash
codegraph status
codegraph node pom.xml
codegraph query "AggregateRoot DomainEvent DomainEventPublisher Command Query Repository"
codegraph query "SpringDomainEventPublisher CdiDomainEventPublisher GuiceDomainEventPublisher DddAnnotationModule"
codegraph query "MQEvent MQClient MQListener MQEventSerialization MQEventStorer"
codegraph explore "MQClient MQEvent ActiveMQEventPublisher TypeHandlerRegistry"
codegraph explore "ApplicationService DomainService DddAnnotationModule CleanArchitectureChecker CleanDDDLayerRules"
```

