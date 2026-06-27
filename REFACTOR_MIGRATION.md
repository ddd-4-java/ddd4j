# ddd4j 重构迁移指南 (REFACTOR_MIGRATION)

> 适用版本：3.4.x（含 ddd4j-boot、ddd4j-quarkus、ddd4j-javalin 三大独立项目）
> 重构时间：2026-06-27
> 性质：**破坏性变更（Breaking Change）**

本文档列出 ddd4j 3.4.x 重构的**全部模块/包名/类名变更**，帮助现有项目迁移到新架构。

---

## 一、重构目标

将 ddd4j 从"Spring 强耦合的单一仓库"重构为"**纯 Java 公共底座 + 三框架适配层**"，使 `ddd4j-boot`、`ddd4j-quarkus`、`ddd4j-javalin` 三个独立项目可**自由选择**继承的模块，并支持后续扩展更多框架（Helidon / Micronaut 等）。

**关键原则**：
- 纯 Java 模块（`-core` 系列）pom 中**零** `org.springframework.*` 依赖
- **三层模块结构**：
  1. **顶层基础包**：与 `ddd4j-core` 同级（annotation/core-api/kit/monitor/ddd）
  2. **顶层三框架核心适配**：`ddd4j-spring`/`-quarkus`/`-javalin`（每个是简单 jar，提供 3 个核心 SPI 的框架实现）
  3. **业务模块聚合**：`ddd4j-data`/`-mq`/`-web`/`-auth`（pom 模块，下含 `-core` 纯 Java 抽象、`-*-spring`/`-*-quarkus`/`-*-javalin` 整合子模块、各领域实现）

---

## 二、最终模块结构（v3.4.x）

```
ddd4j/                                                    # 纯 Java 公共底座 + 三框架适配
├── 基础包（与 ddd4j-core 同级）
│   ├── ddd4j-annotation                                 # 纯 Java DDD 构造型注解
│   ├── ddd4j-core                                       # 纯 Java 核心契约（去 Spring）
│   ├── ddd4j-core-api                                   # 纯 Java API 父接口
│   ├── ddd4j-kit                                        # 工具箱：继承式增强 Hutool
│   ├── ddd4j-monitor                                    # 监控告警：钉钉/企微机器人
│   └── ddd4j-ddd                                        # 纯 DDD 轨道
│
├── 三框架核心适配（与 ddd4j-core 同级，每个简单 jar 模块）
│   ├── ddd4j-spring                                     # Spring 框架核心适配
│   ├── ddd4j-quarkus                                    # Quarkus 框架核心适配
│   └── ddd4j-javalin                                    # Javalin 框架核心适配
│
├── 业务模块聚合（pom 模块）
│   │
│   ├── ddd4j-data/                                      # 数据抽象聚合
│   │   ├── ddd4j-data-core                              # 纯 Java：Repository SPI
│   │   ├── ddd4j-data-mybatis                           # MyBatis-Plus 实现
│   │   ├── ddd4j-data-crypto                            # 字段加解密
│   │   ├── ddd4j-data-external                          # 外部服务
│   │   ├── ddd4j-data-logs                              # API 日志 AOP
│   │   ├── ddd4j-data-quarkus                           # Quarkus 整合（Hibernate Panache）
│   │   └── ddd4j-data-javalin                           # Javalin 整合（JDBI）
│   │
│   ├── ddd4j-mq/                                        # 消息队列聚合
│   │   ├── ddd4j-mq-core                                # 纯 Java：MQMessage SPI
│   │   ├── ddd4j-mq-{activemq,disruptor,kafka,mqtt,mqtt-mica,nats,ons,pulsar,rabbitmq,redis-stream,rocketmq,sqs,tdmq}
│   │   │                                                # 13 个 broker 实现
│   │   ├── ddd4j-mq-spring                             # Spring Messaging 桥接
│   │   ├── ddd4j-mq-quarkus                            # Quarkus Reactive Messaging 桥接
│   │   └── ddd4j-mq-javalin                            # Javalin EventBus 桥接
│   │
│   ├── ddd4j-web/                                       # Web 抽象聚合
│   │   ├── ddd4j-web-core                               # 纯 Java：ControllerAdvice SPI
│   │   ├── ddd4j-web-validation                         # JSR-303 验证
│   │   ├── ddd4j-web-webmvc                             # WebMVC 实现
│   │   ├── ddd4j-web-webflux                            # WebFlux 实现
│   │   ├── ddd4j-web-spring                             # Spring MVC 桥接
│   │   ├── ddd4j-web-quarkus                            # Quarkus REST 桥接
│   │   └── ddd4j-web-javalin                            # Javalin Handler 桥接
│   │
│   └── ddd4j-auth/                                      # 认证抽象聚合
│       ├── ddd4j-auth-core                              # 纯 Java：Subject SPI
│       ├── ddd4j-auth-datascope                         # 数据权限
│       ├── ddd4j-auth-license                           # License 授权
│       ├── ddd4j-auth-satoken                           # Sa-Token 集成
│       ├── ddd4j-auth-security                          # Spring Security 集成
│       ├── ddd4j-auth-shiro                             # Shiro 集成
│       ├── ddd4j-auth-spring                            # Spring Security 桥接
│       ├── ddd4j-auth-quarkus                           # Quarkus Security 桥接
│       └── ddd4j-auth-javalin                           # Javalin AccessManager 桥接
│
└── 跨领域扩展
    └── ddd4j-extensions/                                # 跨领域扩展
        ├── ddd4j-extension-akka                         # Akka 扩展
        ├── ddd4j-extension-excel                        # Excel 扩展
        ├── ddd4j-extension-jackson                      # Jackson 扩展
        ├── ddd4j-extension-pf4j                         # PF4J 插件
        └── ddd4j-extension-qlexpress                    # QLExpress 表达式
```

> **关键设计**：
> - **3 个三框架核心适配**（`ddd4j-spring`/`-quarkus`/`-javalin`）与 `ddd4j-core` 同级，每个都是简单 jar 模块（不再分二级子模块）。
> - **业务模块的 3 框架整合**下沉到各自业务聚合层下作为子模块：`ddd4j-{data,mq,web,auth}-{spring,quarkus,javalin}`。
> - `ddd4j-data-spring` 不单独建——Spring 整合通过 `ddd4j-data-mybatis`（mybatis-plus 本身即 Spring 生态实现）承担。

---

## 三、Maven 坐标变更

| 旧坐标 | 新坐标 | 备注 |
|---|---|---|
| `io.ddd4j:ddd4j-data` | `io.ddd4j:ddd4j-data:d...:ddd4j-data-mybatis` 或 `:ddd4j-data-core` | 聚合层 → 子模块 |
| `io.ddd4j:ddd4j-mq` | `io.ddd4j:ddd4j-mq:ddd4j-mq-core` | 同上 |
| `io.ddd4j:ddd4j-web` | `io.ddd4j:ddd4j-web:ddd4j-web-core` | 同上 |
| `io.ddd4j:ddd4j-kit` | `io.ddd4j:ddd4j-kit`（**提升为顶层**） | 基础包与 ddd4j-core 同级 |
| `io.ddd4j:ddd4j-monitor` | `io.ddd4j:ddd4j-monitor`（**提升为顶层**） | 基础包与 ddd4j-core 同级 |
| `io.ddd4j:ddd4j-extensions:ddd4j-mq-*` | `io.ddd4j:ddd4j-mq:ddd4j-mq-*` | broker 实现平移 |
| `io.ddd4j:ddd4j-extensions:ddd4j-data-*` | `io.ddd4j:ddd4j-data:ddd4j-data-*` | data 扩展平移 |
| `io.ddd4j:ddd4j-extensions:ddd4j-web-*` | `io.ddd4j:ddd4j-web:ddd4j-web-*` | web 扩展平移 |
| `io.ddd4j:ddd4j-extensions:ddd4j-auth-*` | `io.ddd4j:ddd4j-auth:ddd4j-auth-*` | auth 扩展平移 |

**新坐标**（3.4.x 引入）：
- `io.ddd4j:ddd4j-data:ddd4j-data-core`（纯 Java Repository SPI）
- `io.ddd4j:ddd4j-mq:ddd4j-mq-core`（纯 Java MQ 抽象）
- `io.ddd4j:ddd4j-mq:ddd4j-mq-spring`（Spring Messaging 桥接）
- `io.ddd4j:ddd4j-web:ddd4j-web-core`（纯 Java Web 抽象）
- `io.ddd4j:ddd4j-web:ddd4j-web-spring`（Spring MVC 桥接）
- `io.ddd4j:ddd4j-auth:ddd4j-auth-core`（纯 Java 认证抽象）
- `io.ddd4j:ddd4j-auth:ddd4j-auth-spring`（Spring 认证桥接）
- `io.ddd4j:ddd4j-quarkus:*`（Quarkus 适配）
- `io.ddd4j:ddd4j-javalin:*`（Javalin 适配）

---

## 四、Java 包名/类名变更

### 4.1 从 ddd4j-core 迁出到 ddd4j-spring

| 旧类（已删除） | 新类 |
|---|---|
| `io.ddd4j.core.config.BaseCoreConfig` | `io.ddd4j.spring.config.SpringCoreConfig` |
| `io.ddd4j.core.context.SpringContext` | `io.ddd4j.spring.context.SpringContext` |
| `io.ddd4j.core.utils.AsyncAspect` | `io.ddd4j.spring.aspect.AsyncAspect` |
| `io.ddd4j.core.web.BaseController` | `io.ddd4j.spring.web.BaseController` |
| `io.ddd4j.core.web.BaseMapperController` | `io.ddd4j.spring.web.BaseMapperController`（新建待补） |
| `io.ddd4j.core.web.servlet.handler.Slf4jMDCInterceptor` | `io.ddd4j.spring.web.Slf4jMDCInterceptor` |
| `io.ddd4j.core.properties.BasePropertySourcePostProcessor` | `io.ddd4j.spring.properties.SpringPropertySourcePostProcessor` |
| `io.ddd4j.core.service.BaseServiceImpl` | **已删除**（原 @Deprecated forRemoval=true） |

### 4.2 事件/异常去 Spring 化（已重写）

| 类 | 改动 |
|---|---|
| `io.ddd4j.core.event.SettingUpdateEvent` | 不再 extends `EnhancedEvent`，改 implements `DomainEvent` |
| `io.ddd4j.core.event.PropsUpdateEvent` | 同上 |
| `io.ddd4j.core.exception.PayloadExceptionEvent` | 不再 extends `ApplicationEvent`，改 implements `DomainEvent` |
| `io.ddd4j.core.exception.BizRuntimeException` | 不再 extends `NestedRuntimeException`，改 extends `RuntimeException` |
| `io.ddd4j.core.util.WebUtils` / `WebFluxUtils` / `BeanKit` / `ClassUtils` / `IdempotentUtils` / `MessagesUtils` | 整体迁入 `io.ddd4j.spring.util.*` 或 `io.ddd4j.spring.web.*` |

### 4.3 移到 ddd4j-auth-core 的孤儿代码

| 旧类（已删除） | 新类 |
|---|---|
| `io.ddd4j.annotation.auth.BaseAuth` | `io.ddd4j.auth.annotation.BaseAuth` |
| `io.ddd4j.annotation.auth.Inside` | `io.ddd4j.auth.annotation.Inside` |
| `io.ddd4j.annotation.auth.EnableBaseAuth` | `io.ddd4j.auth.annotation.EnableBaseAuth` |
| `io.ddd4j.web.auth.config.BaseAuthConfig` | `io.ddd4j.auth.BaseAuthConfig`（接口） |
| `io.ddd4j.web.auth.interceptor.BaseAuthWebInterceptor` | `io.ddd4j.auth.BaseAuthInterceptor`（接口） |

### 4.4 ddd4j-auth 新增的纯 Java SPI

```java
// 新增 - 替代原 io.ddd4j.core.subject.Subject
io.ddd4j.auth.Subject
io.ddd4j.auth.AuthenticationException
io.ddd4j.auth.BaseAuthInterceptor
io.ddd4j.auth.annotation.BaseAuth
io.ddd4j.auth.annotation.Inside
io.ddd4j.auth.annotation.EnableBaseAuth
```

### 4.5 框架适配 SPI（新建）

**Quarkus（CDI）**：
- `io.ddd4j.quarkus.core.CdiDomainEventPublisher`
- `io.ddd4j.quarkus.core.CdiSubjectProvider`
- `io.ddd4j.quarkus.core.CdiI18nProvider`

**Javalin（Guice）**：
- `io.ddd4j.javalin.core.GuiceDomainEventPublisher`
- `io.ddd4j.javalin.core.GuiceSubjectProvider`
- `io.ddd4j.javalin.core.GuiceI18nProvider`
- `io.ddd4j.javalin.core.Ddd4jJavalinModule`（一行启用 Guice Module）

### 4.6 ddd4j-data 新增的纯 Java SPI

```java
io.ddd4j.data.Repository<M, Q, P>           // 通用 Repository 接口
io.ddd4j.data.TypeHandlerRegistry           // TypeHandler 注册 SPI
```

---

## 五、依赖关系图

```
   ddd4j-annotation    ddd4j-kit (工具箱)   ddd4j-monitor (监控告警)
          │                  │                    │
          └────────┬─────────┴────────┬───────────┘
                   ▼                  ▼
              ddd4j-core-api      ddd4j-core (纯 Java, 零 Spring)
                                      │
        ┌─────────────┬──────────────┴──────────────┬─────────────┐
        ▼             ▼             ▼             ▼
   ddd4j-data     ddd4j-mq      ddd4j-web     ddd4j-auth
   ┌────────┐    ┌────────┐    ┌────────┐    ┌────────┐
   │ core   │    │ core   │    │ core   │    │ core   │  ← 纯 Java
   │ spring │    │ spring │    │ spring │    │ spring │  ← 桥接
   │ mybatis│    │ *-13个 │    │ mvc    │    │ satoken│  ← 实现
   │ crypto │    │ broker │    │ webflux│    │ shiro  │
   │ logs   │    │        │    │ valid  │    │ ...    │
   │ ext    │    │        │    │        │    │        │
   └────────┘    └────────┘    └────────┘    └────────┘

                  ┌──────────┬──────────┬──────────┐
                  ▼          ▼          ▼          ▼
              ddd4j-spring  ddd4j-quarkus  ddd4j-javalin
                            └──── 三个框架适配参考实现 ────┘
```

---

## 六、迁移步骤

### 6.1 Spring Boot 用户（最常见）

1. **更新 parent 坐标**：
   ```xml
   <parent>
     <groupId>io.ddd4j</groupId>
     <artifactId>ddd4j-boot-starter-parent</artifactId>
     <version>3.4.x</version>
   </parent>
   ```

2. **替换旧依赖**（如果用到了 ddd4j-mq/ddd4j-web/ddd4j-data 旧名）：
   ```xml
   <!-- 旧 -->
   <dependency>
     <groupId>io.ddd4j</groupId>
     <artifactId>ddd4j-mq</artifactId>
   </dependency>
   <!-- 新（直接引用 ddd4j-mq-core 或具体 broker） -->
   <dependency>
     <groupId>io.ddd4j</groupId>
     <artifactId>ddd4j-mq</artifactId>
     <version>${revision}</version>
   </dependency>
   <dependency>
     <groupId>io.ddd4j</groupId>
     <artifactId>ddd4j-mq-kafka</artifactId>
     <version>${revision}</version>
   </dependency>
   ```

3. **更新 import**（全局替换）：
   - `io.ddd4j.core.config.BaseCoreConfig` → `io.ddd4j.spring.config.SpringCoreConfig`
   - `io.ddd4j.core.context.SpringContext` → `io.ddd4j.spring.context.SpringContext`
   - `io.ddd4j.core.web.BaseController` → `io.ddd4j.spring.web.BaseController`
   - `io.ddd4j.web.auth.*` → `io.ddd4j.auth.*`（注意：仅注解、接口，纯 Java）

### 6.2 Quarkus 用户

1. **添加 ddd4j-quarkus 依赖**：
   ```xml
   <dependency>
     <groupId>io.ddd4j</groupId>
     <artifactId>ddd4j-quarkus-core</artifactId>
     <version>${revision}</version>
   </dependency>
   ```
   CDI 自动扫描 `io.ddd4j.quarkus.core.*`，无需 `@Bean` 注册。

2. **可选**：按需引入
   ```xml
   <dependency>
     <groupId>io.ddd4j</groupId>
     <artifactId>ddd4j-quarkus-data</artifactId>   <!-- Hibernate Panache 适配 -->
   </dependency>
   <dependency>
     <groupId>io.ddd4j</groupId>
     <artifactId>ddd4j-quarkus-mq</artifactId>     <!-- Reactive Messaging 桥接 -->
   </dependency>
   ```

3. **不要引入** `ddd4j-spring`、`ddd4j-data-spring`、`ddd4j-mq-spring`（避免 Spring 污染）

### 6.3 Javalin 用户

1. **添加 ddd4j-javalin 依赖**：
   ```xml
   <dependency>
     <groupId>io.ddd4j</groupId>
     <artifactId>ddd4j-javalin-core</artifactId>
     <version>${revision}</version>
   </dependency>
   ```

2. **一行启用 Guice Module**：
   ```java
   Injector injector = Guice.createInjector(new Ddd4jJavalinModule());
   // 后续使用：
   DomainEventPublisher publisher = injector.getInstance(DomainEventPublisher.class);
   SubjectProvider subjectProvider = injector.getInstance(SubjectProvider.class);
   ```

3. **不要引入** `ddd4j-spring`（避免 Spring 污染）

---

## 七、版本号建议

本次为**破坏性变更**，建议发版：
- `io.ddd4j:ddd4j-parent` → `3.4.0`（从 2.0.x 升）
- `io.ddd4j:ddd4j` → `3.4.0`

---

## 八、风险与缓解

| 风险 | 缓解措施 |
|---|---|
| core 重构破坏大量现有调用方 | 在 `ddd4j-spring` 中保留 `@Deprecated` 兼容类，提供 1-2 版本过渡期 |
| 包名迁移导致 IDE 索引失败 | IDE 全局 Replace + `mvn clean install -U` 强制重下载依赖 |
| 12+ 个 mq-* 模块平移后 parent 循环 | 严格按"聚合模块在父，子模块在子"的 Maven 标准模式 |
| Quarkus/Javalin 框架官方接口复杂 | 参考实现仅做核心 SPI 桥接，完整 starter 由独立项目实现 |
| ddd4j-dependencies 中 javalin/quarkus/guice/jdbi 版本未声明 | 在 `ddd4j-dependencies/pom.xml` 的 `<properties>` 与 `<dependencyManagement>` 中补充 |

---

## 九、待办（Phase 5+ 后续工作）

> 本次提交完成**骨架级别**重构。下列工作需要在后续版本中补齐：

1. **ddd4j-core 大爆炸**（用户已批准，但未在本次执行）
   - 删除 ddd4j-core 中 35 个含 Spring 导入的源文件
   - 事件/异常/工具类去 Spring 化
   - ddd4j-core/pom.xml 移除 spring-* 依赖
2. **ddd4j-data-spring 桥接模块**：将 BaseRepositoryImpl 等迁移到该模块
3. **ddd4j-web-spring 桥接模块**：将 BaseWebConfig 等迁移到该模块
4. **ddd4j-dependencies 版本补充**：javalin / quarkus / guice / jdbi
5. **完整 mvn clean install 验证**

---

## 十、参考实现代码

### Quarkus CDI DomainEventPublisher

```java
@ApplicationScoped
public class CdiDomainEventPublisher implements DomainEventPublisher {
    @Inject Event<Object> event;
    @Override public void publish(DomainEvent e) { event.fire(e); }
}
```

### Javalin Guice Module（一行启用）

```java
public class Ddd4jJavalinModule extends AbstractModule {
    @Override protected void configure() {
        bind(DomainEventPublisher.class).to(GuiceDomainEventPublisher.class).in(Singleton.class);
        bind(SubjectProvider.class).to(GuiceSubjectProvider.class).in(Singleton.class);
        bind(I18nProvider.class).to(GuiceI18nProvider.class).in(Singleton.class);
    }
    @Provides @Singleton public EventBus eventBus() { return new EventBus(); }
}
```

### Spring DomainEventPublisher

```java
@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {
    private final ApplicationEventPublisher publisher;
    public SpringDomainEventPublisher(ApplicationEventPublisher publisher) { this.publisher = publisher; }
    @Override public void publish(DomainEvent event) { publisher.publishEvent(event); }
}
```

---

**文档结束**。如有问题，请在 ddd4j 仓库提 issue。
