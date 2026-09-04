# ddd4j 架构边界规范设计

- **日期**：2026-06-29
- **作者**：ddd4j 架构团队
- **状态**：已实施（2.0.x 重构版）

## 1. 目标与范围

定义 ddd4j 与各具体框架项目之间的职责铁律，确保通用基础层不混入框架特定代码。

**核心原则**：ddd4j 是基于 ddd4j-boot 抽象出来的、不与任何具体容器框架强绑定的通用 DDD/CQRS/ES 项目脚手架。

## 2. 三层职责分离铁律

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 第一层：业务应用层（用户项目）                                          │
│ 用户的 Spring Boot / Quarkus / Javalin 项目                            │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │ 引入具体框架的脚手架
                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 第二层：具体框架脚手架层（自动装配 / 胶水代码）                          │
│ ★ 包含：@AutoConfiguration / spring.factories / starter / Bean 注册    │
│ ★ 包含：BaseController 子类、GlobalExceptionHandler 子类               │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │ 依赖 ddd4j 通用基础层
                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 第三层：ddd4j 通用基础层（框架无关）                                    │
│ ★ 零 @AutoConfiguration  ★ 零 spring.factories  ★ 零 starter         │
│ ★ 只放：纯 Java 契约 / SPI 接口默认实现 / DDD 抽象基类 / 注解          │
└─────────────────────────────────────────────────────────────────────────┘
```

## 3. ddd4j 通用基础层禁止包含的内容

| 禁止项 | 原因 | 应放在哪里 |
|--------|------|-----------|
| `META-INF/spring.factories` | Spring Boot 1.x 自动装配清单 | `ddd4j-boot-*` |
| `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | Spring Boot 2.7+ 自动装配 | `ddd4j-boot-*` |
| `@AutoConfiguration` 注解类 | Spring Boot 自动装配入口 | `ddd4j-boot-ddd-autoconfigure` |
| `spring-boot-starter` 命名模块 | Spring Boot 启动器 | 不应有此模块 |
| `BaseController` / `BaseMapperController` 子类 | 业务 Controller 基类属于框架胶水 | `ddd4j-runtime-spring` / `ddd4j-web-webmvc` |
| 全局 `@ControllerAdvice` / `ExceptionMapper` | 异常处理属于框架胶水 | `ddd4j-web-webmvc` / `ddd4j-web-webflux` |
| `javax.servlet` / `jakarta.servlet` import | Servlet 容器属于具体框架 | `ddd4j-web-webmvc` |
| `com.baomidou.mybatisplus.*` import | MyBatis-Plus 属于具体 ORM | `ddd4j-data-mybatis` |
| `org.springframework.transaction.annotation.Transactional` | Spring 事务属于具体框架 | 改用 ddd4j 自有 `@DddTransactional` |

## 4. ddd4j 通用基础层应当包含的内容

| 内容类型 | 示例 | 路径 |
|---------|------|------|
| DDD 构造型注解 | `@DomainEntity` `@DomainService` `@ApplicationService` `@DomainRepository` | `ddd4j-annotation` |
| 纯 Java 契约 | `AggregateRoot` `Repository` `DomainEvent` `Query` | `ddd4j-core` |
| SPI 接口默认实现 | `NoopDomainEventPublisher` `InMemoryProjectionPositionRepository` | `ddd4j-core` |
| DDD 架构检查规则 | `CleanDDDLayerRules` `ColaDDDLayerRules` | `ddd4j-ddd-rules` |
| 工具类 | `JsonKit` `DateKit` `ArithKit` | `ddd4j-kit` |
