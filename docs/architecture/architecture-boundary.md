# ddd4j 架构边界规范（铁律）

> **ddd4j 是基于 ddd4j-boot 抽象出来的、不与任何具体容器框架强绑定的通用 DDD 项目脚手架**。
> 它的目标是同时为 `ddd4j-boot` / `ddd4j-quarkus` / `ddd4j-javalin` 等具体容器框架提供**同一套、纯净的、可复用的领域层基础**。
> 本文档定义 ddd4j 与各具体框架项目之间的**职责铁律**。

---

## 一、三层职责分离铁律

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 第一层：业务应用层（用户项目）                                          │
│ 用户的 Spring Boot / Quarkus / Javalin 项目                            │
│ 通过 @SpringBootApplication / @QuarkusMain / Javalin.create() 启动     │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │ 引入具体框架的脚手架
                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 第二层：具体框架脚手架层（自动装配 / 胶水代码）                          │
│ ddd4j-boot / ddd4j-quarkus / ddd4j-javalin                            │
│                                                                         │
│  ★ 包含：@AutoConfiguration / spring.factories / starter / Bean 注册    │
│  ★ 包含：ddd4j-boot-data-mybatis / -auth-satoken / -auth-shiro /      │
│          -auth-security / -ddd-cola / -ddd-autoconfigure               │
│  ★ 包含：BaseController 子类（实际使用）、GlobalExceptionHandler 子类  │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │ 依赖 ddd4j 通用基础层
                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 第三层：ddd4j 通用基础层（本次主题 / 框架无关）                          │
│                                                                         │
│  ★ 零 @AutoConfiguration  ★ 零 spring.factories  ★ 零 starter         │
│  ★ 只放：纯 Java 契约 / SPI 接口默认实现 / DDD 抽象基类 / 注解          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 二、ddd4j 通用基础层**禁止**包含的内容

| 禁止项 | 原因 | 应放在哪里 |
|--------|------|----------|
| `META-INF/spring.factories` | Spring Boot 自动装配清单 | `ddd4j-boot-*` |
| `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | Spring Boot 2.7+ 自动装配 | `ddd4j-boot-*` |
| `@AutoConfiguration` 注解类 | Spring Boot 自动装配入口 | `ddd4j-boot-ddd-autoconfigure` |
| `spring.factories` 中的 `EnableAutoConfiguration=` | 同上 | `ddd4j-boot-ddd-autoconfigure` |
| `spring-boot-starter` 命名模块 | Spring Boot 启动器 | 不应有此模块 |
| `@Component` 注解的 Bean（仅用作 SPI 实现的除外） | 自动注册到 Spring 容器 | `ddd4j-boot-*` 或业务项目 |
| `META-INF/services/` Java SPI（除框架无关的扩展点外） | 框架无关的扩展点 OK | — |
| starter 风格的 `META-INF/spring.provides` | — | `ddd4j-boot-*` |

---

## 三、ddd4j 通用基础层**应当**包含的内容

| 内容类型 | 示例 | 路径 |
|---------|------|------|
| 纯 Java 注解 | `@DomainEntity` / `@DomainService` | `ddd4j-annotation/src/main/java/io/ddd4j/annotation/ddd/` |
| 纯 Java 契约接口 | `DomainEventPublisher` / `MQEventPublisher` / `BaseRepository` | `ddd4j-core/src/main/java/io/ddd4j/core/contract/` |
| 纯 Java 抽象基类 | `DomainEvent` / `Query` / `Page` / `R` | `ddd4j-core/src/main/java/io/ddd4j/core/contract/` |
| DDD 战术构建块 | `DddAggregateRoot` / `DddDomainEvent` / `DddEventStoreRepository` | `ddd4j-core/src/main/java/io/ddd4j/core/ddd/` |
| ArchUnit 规则集 | `CleanDDDLayerRules` / `ColaDDDLayerRules` | `ddd4j-ddd/ddd4j-ddd-*/` |
| 工具类（无框架依赖） | `MappingKit` / `JsonKit` | `ddd4j-kit/src/main/java/` |
| SPI 默认实现（带 `@Component` 注解） | `SpringDomainEventPublisher` / `SpringI18nProvider` | `ddd4j-spring/src/main/java/io/ddd4j/spring/event/` |
| 框架上下文门面 | `SpringContext` | `ddd4j-spring/src/main/java/io/ddd4j/spring/context/` |

---

## 四、具体框架项目**应当**包含的内容

### 4.1 ddd4j-boot（Spring Boot 框架胶水）

```
ddd4j-boot/
├── ddd4j-boot-bom                    版本对齐
├── ddd4j-boot-dependencies           公共依赖
├── ddd4j-boot-core                   Boot 启动基类、@SpringBootApplication 父类
├── ddd4j-boot-data                   数据层父 POM
│   ├── ddd4j-boot-data-mybatis       MyBatis-Plus 自动装配
│   │                                 ★ BaseRepositoryImpl 在这里
│   │                                 ★ MybatisPlusAutoConfiguration 在这里
│   ├── ddd4j-boot-data-crypto        加解密自动装配
│   ├── ddd4j-boot-data-logs          操作日志自动装配
│   └── ddd4j-boot-data-external      外部接口自动装配
├── ddd4j-boot-auth                   认证父 POM
│   ├── ddd4j-boot-auth-satoken       Sa-Token 自动装配 ★ 在这里
│   ├── ddd4j-boot-auth-shiro         Shiro 自动装配 ★ 在这里
│   ├── ddd4j-boot-auth-security      Spring Security 自动装配 ★ 在这里
│   ├── ddd4j-boot-auth-license       License 授权
│   └── ddd4j-boot-auth-datascope     数据权限
├── ddd4j-boot-mq                     消息队列父 POM
│   ├── ddd4j-boot-mq-kafka           Kafka 自动装配
│   ├── ddd4j-boot-mq-rocketmq        RocketMQ 自动装配
│   └── ...
├── ddd4j-boot-ddd                    DDD/CQRS 自动装配父 POM
│   ├── ddd4j-boot-ddd-autoconfigure  DDD/CQRS 自动装配 ★ 在这里
│   │                                 ★ DddAutoConfiguration 在这里
│   │                                 ★ @EnableDdd4j 在这里
│   │                                 ★ DddClassPathBeanDefinitionScanner 在这里
│   │                                 ★ spring.factories / AutoConfiguration.imports 在这里
│   └── ddd4j-boot-ddd-cola           COLA 架构变体自动装配 ★ 在这里
├── ddd4j-boot-web                    Web 父 POM
│   ├── ddd4j-boot-web-webmvc         WebMVC 全局异常 / 拦截器
│   ├── ddd4j-boot-web-webflux        WebFlux 全局异常
│   └── ddd4j-boot-web-validation     参数校验自动装配
└── ddd4j-boot-monitor                监控告警
```

### 4.2 ddd4j-quarkus（Quarkus 框架胶水）

```
ddd4j-quarkus/
├── ddd4j-quarkus-bom
├── ddd4j-quarkus-dependencies
├── ddd4j-quarkus-core               Quarkus 启动基类
├── ddd4j-quarkus-cdi                CDI 集成 + @EnableDdd4jQuarkus
├── ddd4j-quarkus-hibernate          Hibernate Panache 集成
├── ddd4j-quarkus-resteasy           JAX-RS 集成
└── ddd4j-quarkus-xxx                其他 Quarkus 专属扩展
```

### 4.3 ddd4j-javalin（Javalin 框架胶水）

```
ddd4j-javalin/
├── ddd4j-javalin-bom
├── ddd4j-javalin-dependencies
├── ddd4j-javalin-core               Javalin 启动基类
├── ddd4j-javalin-jetty              Jetty 集成
└── ddd4j-javalin-openapi            OpenAPI 集成
```

---

## 五、迁移清单：把 ddd4j 中"放错位置"的代码下移

### 5.1 必须下移到 `ddd4j-boot-*` 的内容

| 当前 ddd4j 中的代码 | 应迁移到 | 理由 |
|--------------------|---------|------|
| `DddAutoConfiguration`（含 EventStore / MultiCommandExecutor 装配） | `ddd4j-boot-ddd-autoconfigure` | Spring Boot 自动装配 |
| `DddClassPathBeanDefinitionScanner` | `ddd4j-boot-ddd-autoconfigure` | Spring 扫描器 |
| `EnableDdd4j` 注解（启动入口） | `ddd4j-boot-ddd-autoconfigure` | 跨框架统一入口 |
| `BaseController`（继承用法 + `extends` 的子类使用） | `ddd4j-boot-web-webmvc`（保留 `SpringContext` 在 `ddd4j-spring` 作为门面） | 业务 Controller 基类应属于具体框架 |
| `GlobalRestExceptionAdvice` | `ddd4j-boot-web-webmvc` | 业务 Controller 异常处理 |
| `BaseMapperController` | `ddd4j-boot-data-mybatis` | MyBatis-Plus 专属基类 |
| `I18nKit` 的 Hutool 实现 | `ddd4j-kit`（保持框架无关） | — |
| `Slf4jMDCInterceptor` | `ddd4j-boot-web-webmvc` | Web 拦截器属于框架胶水 |
| `AsyncAspect` | `ddd4j-boot-core` 或 `ddd4j-spring` | AOP 属于框架胶水 |
| `BasePropertySourcePostProcessor` | **删除** | 重复 Spring Boot 的轮子 |

### 5.2 保留在 ddd4j 通用基础层（`ddd4j-spring` / `ddd4j-quarkus` / `ddd4j-guice`）的内容

| 保留 | 理由 |
|------|------|
| `SpringDomainEventPublisher`（带 `@Component`） | 是 SPI 的 Spring 实现，业务方可在 Quarkus/Javalin 项目也通过 Spring 容器使用 |
| `SpringI18nProvider` | 同上 |
| `SpringSubjectProvider` | 同上 |
| `SpringContext`（静态门面） | Spring 上下文的纯 Java 门面，可被框架无关业务代码调用 |
| `CdiDomainEventPublisher` | Quarkus 适配 |
| `GuiceDomainEventPublisher` | Guice 适配 |

> ⚠️ 注意：带 `@Component` 的类不是"自动装配"——它只是声明这个类可以被 Spring 容器管理。**真正的"自动装配"是 `@AutoConfiguration` + `spring.factories`**——这两者才必须下移。

---

## 六、为什么这样设计

### 6.1 业务方使用 `ddd4j-boot` 时

```xml
<!-- 业务项目 pom.xml -->
<dependency>
    <groupId>io.ddd4j</groupId>
    <artifactId>ddd4j-boot-starter-web</artifactId>  <!-- 引入 ddd4j-boot-bom + 常用 starter -->
</dependency>
```

业务方**只依赖 `ddd4j-boot-*`**——它会自动依赖 `ddd4j` 通用基础层 + Spring Boot 自动装配。

### 6.2 业务方使用 `ddd4j-quarkus` 时

```xml
<dependency>
    <groupId>io.ddd4j.quarkus</groupId>
    <artifactId>ddd4j-quarkus-starter</artifactId>
</dependency>
```

业务方**只依赖 `ddd4j-quarkus-*`**——它会自动依赖 `ddd4j` 通用基础层 + Quarkus CDI 集成。

### 6.3 业务方混合使用（理论可行）

业务项目理论上可以：
- 同时引入 `ddd4j-boot-auth-satoken`（仅认证组件）
- 和 `ddd4j-quarkus-cdi`（Quarkus 容器）
- 但这违反单一容器原则，**不建议**

---

## 七、最终分层总图

```
═══════════════════════════════════════════════════════════════════
  业务应用层（用户项目）
═══════════════════════════════════════════════════════════════════
        ↓ 引入
┌──────────────────────────────────────────────────────────────┐
│  具体框架脚手架（自动装配层）                                  │
│  ┌──────────────────────┐ ┌──────────────────────┐ ┌──────┐ │
│  │ ddd4j-boot           │ │ ddd4j-quarkus        │ │ ...  │ │
│  │  ★ @AutoConfiguration│ │  ★ CDI Extensions   │ │      │ │
│  │  ★ spring.factories  │ │  ★ Quarkus Build Step│ │      │ │
│  │  ★ starter           │ │  ★ starter           │ │      │ │
│  │  ★ BaseController    │ │  ★ JAX-RS Resource  │ │      │ │
│  │    子类 / 全局异常    │ │                       │ │      │ │
│  │  ★ ddd4j-boot-       │ │  ★ ddd4j-quarkus-    │ │      │ │
│  │    -data-mybatis     │ │    -hibernate        │ │      │ │
│  │    -auth-satoken     │ │    -resteasy         │ │      │ │
│  │    -auth-shiro       │ │                       │ │      │ │
│  │    -auth-security    │ │                       │ │      │ │
│  │    -ddd-autoconfigure│ │                       │ │      │ │
│  │    -ddd-cola         │ │                       │ │      │ │
│  └──────────────────────┘ └──────────────────────┘ └──────┘ │
└──────────────────────────────────────────────────────────────┘
        ↓ 依赖
┌──────────────────────────────────────────────────────────────┐
│  ddd4j 通用基础层（框架无关）                                │
│  零 starter / 零 AutoConfiguration / 零 spring.factories     │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ 纯 Java 契约层                                         │  │
│  │  -annotation / -core / -kit / -ddd / -data / -web    │  │
│  │  -auth / -mq / -monitor / -extensions                │  │
│  └───────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ SPI 默认实现层（带 @Component 但无自动装配）           │  │
│  │  -spring / -quarkus / -guice                         │  │
│  └───────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
        ↓ 依赖
┌──────────────────────────────────────────────────────────────┐
│  fuinorg 基础库                                              │
│  ddd-4-java · cqrs-4-java · esc-api                          │
└──────────────────────────────────────────────────────────────┘
```

---

## 八、验证清单（CI 阶段检查项）

为防止后续贡献者误把代码放错位置，在 `ddd4j` 根 POM 添加 ArchUnit 规则：

```java
@AnalyzeClasses(packages = "io.ddd4j")
class Ddd4jIndependenceTest {

    // ddd4j 通用基础层不应有 Spring Boot 自动装配
    @ArchTest
    static final ArchRule no_autoconfiguration =
        noClasses().that().resideInAPackage("io.ddd4j..")
            .should().beAnnotatedWith("org.springframework.boot.autoconfigure.AutoConfiguration");

    // ddd4j 通用基础层不应有 spring.factories
    @ArchTest
    static final ArchRule no_spring_factories =
        noClasses().that().resideInAPackage("io.ddd4j..")
            .should().dependOnClassesThat().haveFullyQualifiedName(
                "org.springframework.boot.autoconfigure.AutoConfiguration.imports");

    // ddd4j-core 不应依赖 spring 框架
    @ArchTest
    static final ArchRule core_no_spring =
        noClasses().that().resideInAPackage("io.ddd4j.core..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    // ddd4j-core 不应依赖 mybatis-plus
    @ArchTest
    static final ArchRule core_no_mybatis =
        noClasses().that().resideInAPackage("io.ddd4j.core..")
            .should().dependOnClassesThat().resideInAPackage("com.baomidou..");

    // ddd4j-mq-core 不应依赖 spring-messaging
    @ArchTest
    static final ArchRule mq_core_no_spring_messaging =
        noClasses().that().resideInAPackage("io.ddd4j.mq.core..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.messaging..");
}
```

---

## 九、结论

**ddd4j 的价值不在于"包含多少功能"，而在于"绝不包含不属于它的功能"。**

- ✅ 包含：纯 Java 契约、SPI 默认实现、DDD 抽象基类、ArchUnit 规则
- ❌ 不包含：`@AutoConfiguration`、`spring.factories`、starter、BaseController 子类、全局异常处理器

这条铁律一旦破坏，`ddd4j-javalin` / `ddd4j-quarkus` 项目就必须忍受 Spring 依赖。守住边界 = 守住"通用"二字。
