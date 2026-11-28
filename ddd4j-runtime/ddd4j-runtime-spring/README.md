# ddd4j-runtime-spring

> **ddd4j 的 Spring Framework 运行时绑定层（纯 SPI 实现）**：把 ddd4j-core 纯 Java SPI 接口（`DomainEventPublisher` /
> `I18nProvider` / `SubjectProvider`）落地到 Spring 容器。 **本模块不包含 Spring Boot 自动装配**，那是
> `ddd4j-boot-ddd-autoconfigure` 的职责。

---

## 一、模块边界原则

ddd4j 通用基础层的架构分层遵循一个 **铁律**：

| 层级                      | 内容                                                            | 归属                                                                     |
|---------------------------|-----------------------------------------------------------------|--------------------------------------------------------------------------|
| **纯契约层**              | 零框架 import 的 Java 接口 / 抽象类 / 注解                      | `ddd4j-core` / `ddd4j-annotation`                                        |
| **SPI 默认实现层**        | 三框架各自的 SPI 实现（无 starter）                             | `ddd4j-runtime-spring` / `ddd4j-runtime-quarkus` / `ddd4j-runtime-guice` |
| **自动装配 / 胶水代码层** | Spring Boot starter / `@AutoConfiguration` / `spring.factories` | `ddd4j-boot-ddd-autoconfigure` 等                                        |

> **错误做法**：把 `DddAutoConfiguration`（含 `@Bean` 装配 EventStore/MultiCommandExecutor）放进 `ddd4j-runtime-spring`
> **正确做法**：`ddd4j-runtime-spring` 只提供 `SpringDomainEventPublisher` 这种 **带 `@Component` 的 Bean 实现**；
> `DddAutoConfiguration` 属于 Spring Boot 装配，下移到 `ddd4j-boot-ddd-autoconfigure`

```
┌──────────────────────────────────────────────────────────────────┐
│                       业务 Spring Boot 项目                       │
└──────────────────────────────┬───────────────────────────────────┘
                               │ 引入 ddd4j-boot-*（自动装配层）
┌──────────────────────────────▼───────────────────────────────────┐
│  ddd4j-boot-ddd-autoconfigure / ddd4j-boot-data-mybatis / ...    │
│  （Spring Boot 自动装配 + starter + spring.factories）            │
└──────────────────────────────┬───────────────────────────────────┘
                               │ 依赖 ddd4j-runtime-spring（纯 SPI 实现）
┌──────────────────────────────▼───────────────────────────────────┐
│                          ddd4j-runtime-spring                             │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  SPI 桥接层（带 @Component 的 Bean 实现）                   │  │
│  │  - SpringDomainEventPublisher                              │  │
│  │  - SpringI18nProvider                                      │  │
│  │  - SpringSubjectProvider                                   │  │
│  │  - SpringContext                                           │  │
│  │  - Slf4jMDCInterceptor / AsyncAspect                       │  │
│  └────────────────────────────────────────────────────────────┘  │
│  （**禁止**：@AutoConfiguration / spring.factories / starter）   │
└──────────────────────────────┬───────────────────────────────────┘
                               │ 依赖 ddd4j-core（纯 Java）
┌──────────────────────────────▼───────────────────────────────────┐
│         ddd-4-java（聚合根）· cqrs-4-java（命令总线）· esc-mem    │
└──────────────────────────────────────────────────────────────────┘
```

| 维度         | 数据                                                                                   |
|--------------|----------------------------------------------------------------------------------------|
| **路径**     | `ddd4j-runtime/ddd4j-runtime-spring/src/main/java/io/ddd4j/spring/`                    |
| **代码量**   | 26 个 Java 文件 / 2,980 行                                                             |
| **核心定位** | SPI 接口的 Spring 实现 + 上下文门面                                                    |
| **强依赖**   | `ddd4j-core` / `ddd4j-kit` / `ddd4j-annotation` + 本发行线 BOM 管理的 Spring Framework |
| **禁止内容** | `META-INF/spring.factories`、`@AutoConfiguration`、starter                             |

---

## 二、激活方式

普通 DDD 用法导入 `SpringCoreConfig` 即可。基础配置注册通用运行时 Bean，命令总线和投影按需启用。
需要命令总线时，加入 `SpringCommandConfig`。

```java
@Configuration(proxyBeanMethods = false)
@Import({SpringCoreConfig.class, SpringCommandConfig.class})
public class ApplicationConfiguration {
}
```

需要投影时加入 `SpringProjectionConfig`，提供业务视图，并将 `ProjectionRunner` 接到实际的事件读取器和投影位点仓储。
使用默认投影调度适配器时，提供 Spring 的 `TaskScheduler`。配置会复用调用方声明的 `CommandBus`、`ViewManager` 或
`ViewScheduler`。
使用默认装配且缺少所需的 Bean 时，应用启动会报错。容器关闭时停止由 runtime 创建的投影任务。

这些入口可用于纯 Spring 和 Spring Boot 应用。若应用通过外部 `ddd4j-boot-*` starter 启用 DDD，沿用该 starter 的接入方式。

Spring 的命令侧装配由本模块提供。需要投影时，从 `SpringProjectionConfig` 接入。
旧命令包和 runtime 命令包均声明名为 `springCommandBus` 的组件。同一应用使用一个显式配置入口或原有的单包扫描即可。

Spring Framework 版本由当前发行线的 BOM 管理。Java 8 使用 Spring 5 和 `javax.persistence`，其 runtime 保留
Spring Data JPA 和 Persistence API 的传递依赖。Java 17 和 21 分别使用 Spring 6、Spring 7，使用
`SpringJpaProjectionPositionRepository` 等 JPA 适配器时需显式引入对应的 Spring Data JPA 和 `jakarta.persistence` 技术栈。
旧 JPA 适配器的构造器、实体表与字段映射保持兼容。配置 Spring 事务管理后，命令正常返回时提交，包括返回 `R.fail` 的情况。
符合 Spring 回滚规则的异常触发回滚。

---

### 显式注册 JPA 投影检查点仓储

使用数据库保存投影进度的应用，在自身配置中声明适配器。原先通过组件扫描加载适配器的应用，升级时加入以下配置。已有实体或 Spring Data 仓储扫描配置的应用，将对应包并入已有扫描范围即可。

```java
import io.ddd4j.data.projection.jpa.ProjectionPositionEntity;
import io.ddd4j.spring.projection.jpa.JpaProjectionPositionRepository;
import io.ddd4j.spring.projection.jpa.SpringDataProjectionPositionRepository;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackageClasses = ProjectionPositionEntity.class)
@EnableJpaRepositories(basePackageClasses = SpringDataProjectionPositionRepository.class)
class ProjectionPositionConfiguration {
    @Bean
    JpaProjectionPositionRepository jpaProjectionPositionRepository(SpringDataProjectionPositionRepository repository) {
        return new JpaProjectionPositionRepository(repository);
    }
}
```

方法名保留原 Bean 名 `jpaProjectionPositionRepository`，已有按名称注入的调用方可继续使用。

## 三、SPI 桥接层 — 把纯 Java 契约落地到 Spring

`ddd4j-core` 定义了三组 **框架无关的 SPI 接口**，`ddd4j-runtime-spring` 提供 Spring 实现：

### 3.1 进程内领域事件发布

```java
// ddd4j-core 纯 Java SPI（零 Spring 依赖）
public interface DomainEventPublisher {
    void publish(DomainEvent event);
    default void publishAll(Collection<DomainEvent> events) { ... }
}

// ddd4j-runtime-spring 实现
@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {
    private final ApplicationEventPublisher publisher;
    
    @Override
    public void publish(DomainEvent event) {
        publisher.publishEvent(event);  // 委托给 Spring
    }
}
```

业务方继承 `DomainEvent<T>` 即可，发布时框架自动适配 Spring：

```java
public class OrderCreatedEvent extends DomainEvent<Order> {
    public OrderCreatedEvent(Order source) { super(source); }
}

// 业务代码无需感知 Spring
public class OrderAppService {
    private final DomainEventPublisher publisher;  // ← SPI 接口
    
    public void createOrder(Order order) {
        // ... 业务逻辑
        publisher.publish(new OrderCreatedEvent(order));  // ← 自动用 Spring 发布
    }
}
```

### 3.2 国际化

```java
@Component
public class SpringI18nProvider implements I18nProvider {
    public String getMessage(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
```

业务方调用 `I18nKit.get(key)` 即获得 Spring MessageSource 解析结果， **业务代码不直接依赖 Spring**。

### 3.3 当前用户主体

```java
@Component
public class SpringSubjectProvider implements SubjectProvider {
    public Subject getSubject() { ... }
}
```

业务项目注入 `Subject` Bean（集成 Sa-Token / Spring Security），`SubjectProvider` 自动从容器获取。

---

## 四、外部 Spring Boot starter 的历史装配示例

本节记录早期外部 `ddd4j-boot-*` starter 的装配方案。下面的 `@EnableDdd4j`、属性名和扫描器用于说明历史接法，当前 starter
用法以其自身文档为准。运行时的当前入口见[激活方式](#二激活方式)。

### 4.1 默认内存 EventStore（零配置启动）

```java
@Bean
public EventStore inMemoryEventStore() {
    InMemoryEventStore store = new InMemoryEventStore(Runnable::run);
    store.open();
    return store;
}
```

业务项目 **无需部署 EventStoreDB** 即可体验完整事件溯源。生产环境切换 KurrentDB：

```yaml
# application.yml
ddd4j:
  ddd:
    eventstore:
      type: kurrent
      host: localhost
      port: 2113
```

### 4.2 领域事件 Jackson 序列化

```java
@Bean
public Ddd4JacksonModule ddd4JacksonModule(EntityIdFactory factory) {
    return new Ddd4JacksonModule(factory);
}
```

业务聚合根产生的事件可自动 Jackson 序列化（含 `EntityId` 多态反序列化）。

### 4.3 CQRS 命令总线

Spring 装配 `CommandBus` 并注册容器中的 `CommandExecutor` Bean。执行器通过
`supportedCommands()` 声明路由，返回 `io.ddd4j.core.api.R`：

```java
@Component
public class CreateOrderCommandExecutor implements CommandExecutor<CreateOrderCommand> {
    @Override
    public Set<Class<? extends Command>> supportedCommands() {
        return Set.of(CreateOrderCommand.class);
    }

    @Override
    public R<Void> execute(CreateOrderCommand command) {
        // 执行订单创建用例
        return R.ok();
    }
}
```

### 4.4 DDD 注解扫描器（关键创新点）

```java
public class DddClassPathBeanDefinitionScanner extends ClassPathBeanDefinitionScanner {
    protected void registerDddFilters() {
        addIncludeFilter(new AnnotationTypeFilter(DomainService.class));
        addIncludeFilter(new AnnotationTypeFilter(DomainRepository.class));
        addIncludeFilter(new AnnotationTypeFilter(ApplicationService.class));
        // ... 8 个 DDD 注解
    }
}
```

**问题**：`ddd4j-annotation` 的 `@DomainService` / `@ApplicationService` 没有 `@Service` 元注解（保持框架无关），Spring 默认
`@ComponentScan` 无法识别。

**解决**：`DddClassPathBeanDefinitionScanner` 注册额外的 AnnotationTypeFilter，让"无 Spring 元注解"的纯 Java DDD 注解也能被
Spring 注册为 Bean。

业务方配置扫描路径：

```yaml
ddd4j:
  ddd:
    scan-base-packages: com.example.domain,com.example.application
```

---

## 五、Web 基础设施层

### 5.1 `BaseController` — Controller 基类

```java
public class BaseController implements ApplicationContextAware,
                                     ApplicationContextAware,
                                     EmbeddedValueResolverAware {
    private StringValueResolver valueResolver;
    private ApplicationContext context;
    private NestedMessageSource messageSource;
    
    protected String getMessage(String key, Object... args);
    protected <T> ApiRestResponse<T> success(String key, Object... args);
    protected <T> ApiRestResponse<T> fail(String key, Object... args);
    protected void logException(Object source, Exception ex);
}
```

**自动注入**：

- `ApplicationContext`：获取任意 Bean
- `MessageSource`：国际化（基于 `I18nProvider` SPI）
- `EmbeddedValueResolver`：解析 `${...}` 占位符

**OpenAPI**：类级别 `@ApiResponses` 提供标准 HTTP 状态码的 Swagger 文档。

### 5.2 `Slf4jMDCInterceptor` — 日志追踪

```java
public class Slf4jMDCInterceptor implements HandlerInterceptor {
    public boolean preHandle(...) {
        MDC.put("requestId", UUID.randomUUID().toString());
        MDC.put("requestURL", request.getRequestURL().toString());
        MDC.put("remoteAddr", IpKit.getRemoteAddr(request));
        // ...
    }
    
    public void afterCompletion(...) { MDC.clear(); }
}
```

请求日志自动携带 `requestId / requestURL / remoteAddr`，便于分布式追踪。

### 5.3 `AsyncAspect` — 异步清理

```java

@Aspect
public class AsyncAspect {
    @After("@annotation(org.springframework.scheduling.annotation.Async)")
    public void afterAsyncMethod() {
        ThreadContext.clear();  // 清理 ThreadLocal，避免线程池复用时变量污染
    }
}
```

### 5.4 `AsyncAspect` — 异步清理

```java

@Aspect
public class AsyncAspect {
    @After("@annotation(org.springframework.scheduling.annotation.Async)")
    public void afterAsyncMethod() {
        ThreadContext.clear();  // 清理 ThreadLocal，避免线程池复用时变量污染
    }
}
```

---

## 六、Spring 上下文门面

### 6.1 `SpringContext` — 静态获取 Bean

```java

@Primary
@Order(PriorityOrdered.HIGHEST_PRECEDENCE)
public class SpringContext implements ApplicationContextAware {
    public static final CountDownLatch APP_START_SIGNAL = new CountDownLatch(1);

    public static ApplicationContext getApplicationContext();

    public static <T> T getBean(Class<T> clazz);

    public static <T> T getBeanAwait(Class<T> clazz);  // 阻塞等待启动完成

    public static Environment getEnv();
}
```

**关键设计**：通过 `CountDownLatch` 解决"Bean 初始化完成前调用 getBean"的竞态问题。

### 6.2 静态方法 vs 注入

| 场景                | 推荐方式                       |
|---------------------|--------------------------------|
| 普通 Service        | `@Autowired` 注入              |
| 工具类 / 静态上下文 | `SpringContext.getBean()`      |
| 启动期阻塞等待      | `SpringContext.getBeanAwait()` |

---

## 七、与 ddd4j 其他模块的关系

下图使用历史 Boot 接入示意。直接依赖本 runtime 的应用按[快速开始](#十快速开始)显式导入配置。

```
┌─────────────────────────────────────────────────────────────────┐
│                    业务 Spring Boot 项目                          │
│             外部 starter 的历史 @EnableDdd4j                       │
└─────────────────────────────┬───────────────────────────────────┘
                              │ 依赖
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  ddd4j-runtime-spring（本模块）                                            │
│  SPI 桥接 + DDD/CQRS 装配 + Web 基础设施 + Spring 门面            │
└───────┬──────────┬──────────┬──────────────┬────────────────────┘
        │          │          │              │
        ▼          ▼          ▼              ▼
┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐
│ ddd4j-   │ │ ddd4j-   │ │ ddd4j-   │ │ ddd-4-java       │
│ core     │ │ kit      │ │annotation│ │ cqrs-4-java      │
│ (契约)   │ │ (工具)   │ │ (注解)   │ │ esc-mem          │
└──────────┘ └──────────┘ └──────────┘ └──────────────────┘
```

---

## 八、与 ddd4j-runtime-quarkus / ddd4j-javalin 的对照

| 维度           | ddd4j-runtime-spring                       | ddd4j-runtime-quarkus | ddd4j-javalin              |
|----------------|--------------------------------------------|-----------------------|----------------------------|
| **DI 容器**    | ApplicationContext                         | Arc (CDI)             | Guice Injector             |
| **事件发布**   | `ApplicationEventPublisher.publishEvent()` | `Event<T>.fire()`     | Guava `EventBus.post()`    |
| **上下文入口** | `SpringContext`                            | `ArcContainerProxy`   | `GuiceContext`             |
| **Bean 扫描**  | `DddClassPathBeanDefinitionScanner`        | `Arc` 自动发现        | `Guice Module.configure()` |
| **AOP**        | Spring AOP (`@Aspect`)                     | Interceptor Binding   | MethodInterceptor          |
| **工具丰富度** | ⭐⭐⭐ 最多                                | ⭐⭐                  | ⭐                         |

`ddd4j-runtime-spring` 在三框架中 **工具最丰富、集成度最高**（因为 Spring 生态最成熟），但 **业务代码**通过 SPI 调用， **与
Quarkus/Javalin 项目结构相同**——这是 ddd4j 框架无关设计的核心价值。

---

## 九、当前问题与优化路线

### 🔴 P0 阻塞问题

| 序号 | 问题                                                                                 | 修复                                                         |
|------|--------------------------------------------------------------------------------------|--------------------------------------------------------------|
| 1    | `SpringDomainEvent` 是冗余基类，业务方不知道该继承哪个                               | **删除**，统一继承 `DomainEvent<T>`，发布时自动委托给 Spring |
| 2    | `BasePropertySourcePostProcessor` 158 行重复 Spring Boot 轮子                        | 标记 `@Deprecated` 引导迁移                                  |
| 3    | util 类（I18nKit/MappingKit/BeanKit/BizAssert/WebUtils）与 ddd4j-core/ddd4j-kit 重复 | 删除并统一引用                                               |

### 🟡 P1 重要改进

| 序号 | 改进                                                                     |
|------|--------------------------------------------------------------------------|
| 4    | 新增纯 Spring `@EnableDdd4j` 注解（替代业务侧重复 `@Import`）            |
| 5    | Spring Boot 自动装配、条件 Bean 与配置元数据统一由外部 `ddd4j-boot` 提供 |
| 6    | Runtime 配置仅使用 Spring Framework 条件与生命周期，不引入 Boot API      |
| 7    | `SpringContext` 改为"配置优先 + 栈跟踪降级"，移除反射开销                |
| 8    | 补齐 `ddd4j-annotation` 缺失的 `@DomainGateway` / `@QueryService`        |

### 🟢 P2 锦上添花

| 序号 | 改进                                                                       |
|------|----------------------------------------------------------------------------|
| 9    | 补充单元测试（SpringContextTest / SpringDomainEventPublisherTest 等 6 个） |
| 10   | Web 类型统一归属 `ddd4j-web-webmvc` / `ddd4j-web-webflux`                  |
| 11   | Spring Boot 2.x/3.x 迁移指南统一放在外部 `ddd4j-boot`                      |
| 12   | Boot 完整业务样例统一放在外部 `ddd4j-boot-samples`                         |

---

## 十、快速开始

### 10.1 引入依赖

```xml
<dependency>
    <groupId>io.ddd4j</groupId>
    <artifactId>ddd4j-runtime-spring</artifactId>
    <version>${revision}</version>
</dependency>
```

### 10.2 启用基础运行时

```java
package example;

import io.ddd4j.spring.config.SpringCoreConfig;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Application {
    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(SpringCoreConfig.class)) {
            System.out.println("DDD runtime ready: " + context.isActive());
        }
    }
}
```

Spring Boot 应用可以在已有启动类上添加 `@Import(SpringCoreConfig.class)`。需要命令或投影时按[激活方式](#二激活方式)
添加相应配置。

### 10.3 运行完整样例

- [默认订单示例](../../ddd4j-samples/ddd4j-sample-spring/README.md)
- [CQRS 订单示例](../../ddd4j-samples/ddd4j-sample-spring-cqrs/README.md)
- [Sa-Token 订单示例](../../ddd4j-samples/ddd4j-sample-spring-satoken/README.md)
- [Shiro 订单示例](../../ddd4j-samples/ddd4j-sample-spring-shiro/README.md)

---

## 十一、相关文档

- [架构总览](../docs/architecture/architecture.md)
- [ddd-4-java 架构剖析](../docs/architecture/ddd-4-java-architecture.md)
- [cqrs-4-java 架构剖析](../docs/architecture/cqrs-4-java-architecture.md)
- [ddd-cqrs-4-java-example 架构剖析](../docs/architecture/ddd-cqrs-4-java-example-architecture.md)
- [ddd4j SVG 架构图](../docs/architecture/ddd4j_architecture.html)
