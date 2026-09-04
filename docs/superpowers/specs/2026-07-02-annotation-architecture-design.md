# ddd4j 注解架构设计：下沉策略与三套同名注解模块

- **日期**：2026-07-02
- **作者**：ddd4j 架构团队
- **状态**：已实施（v2.0.x）
- **适用范围**：`ddd4j` / `ddd4j-boot` / `ddd4j-javalin` / `ddd4j-quarkus`

## 1. 目标与范围

定义 ddd4j 注解的下沉策略，让业务代码只写一个 `@DomainService`，同时获得 DDD 语义 + 框架 Bean 自动注册。其他注解（auth/websocket/cache/cqrs 投影事件）保留在 ddd4j-annotation 或由具体业务模块提供。

**核心理念**：只下 DDD 注解。

## 2. 架构决策

### 2.1 决策背景

ddd4j 需要同时支持三套容器框架，三套框架的注解能力严重不对等：

- **Spring**：自带 `@Service` / `@Repository` / `@Component` 等几十个注解
- **Quarkus**：通过 Jakarta EE + MicroProfile 提供 `@ApplicationScoped` / `@Path` / `@GET` 等
- **Javalin 6**：完全没有注解，所有路由都是编程式 API

### 2.2 下沉的真正目的

**痛点**：业务代码要写两个注解——既繁琐又冗余

```java
// ❌ 业务代码要写两个注解
@DomainService                                    // DDD 语义（ArchUnit 识别）
@Service                                          // Spring 自动注册 Bean
public class UserDomainServiceImpl implements UserDomainService {
    // 业务代码
}
```

**目标**：业务代码只写一个 `@DomainService`，框架自动识别并注册为 Bean。

## 3. 三套同名注解模块

| 注解 | ddd4j-annotation（纯语义） | ddd4j-boot（Spring） | ddd4j-runtime-guice（Javalin） | ddd4j-runtime-quarkus（Quarkus） |
|------|---------------------------|---------------------|-------------------------------|--------------------------------|
| `@DomainService` | DDD 语义标记 | → `@Service` | → `@Singleton` | → `@ApplicationScoped` |
| `@DomainEntity` | DDD 语义标记 | 不需要 | 不需要 | 不需要 |
| `@ApplicationService` | DDD 语义标记 | → `@Service` | → `@Singleton` | → `@ApplicationScoped` |
| `@DomainRepository` | DDD 语义标记 | → `@Repository` | → `@Singleton` | → `@ApplicationScoped` |

## 4. 实现机制

- **Spring**：通过 `BeanPostProcessor` 扫描 DDD 注解，自动注册为 Spring Bean
- **Guice/Javalin**：通过 `DddAnnotationModule` 使用 ClassGraph 扫描 DDD 注解，按 Singleton 规则绑定
- **Quarkus**：通过 CDI 扩展自动识别 DDD 注解
