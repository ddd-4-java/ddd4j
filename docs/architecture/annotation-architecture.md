# DDD4J 注解架构设计：下沉策略与三套同名注解模块

> **文档版本**：v2.0.x
> **适用范围**：`ddd4j`（通用基础层） / `ddd4j-boot`（Spring） / `ddd4j-javalin`（Javalin） / `ddd4j-quarkus`（Quarkus）
> **配套架构图**：参见 `architecture.md` 与 `ddd4j_architecture.html`
> **状态校准**：当前 `ddd4j` 仓库保持平铺结构；Javalin 注解能力已收敛到 `ddd4j-runtime-guice`，Quarkus 注解/CDI 适配位于
`ddd4j-runtime-quarkus`，不再使用旧 `ddd4j-javalin-annotation` / `ddd4j-quarkus` 内部模块名。
> **核心理念**：**只下 DDD 注解**——让业务代码只写一个 `@DomainService`，同时获得 DDD 语义 + 框架 Bean 自动注册；*
*其他注解（auth/websocket/cache/cqrs 投影事件）保留在 ddd4j-annotation 或由具体业务模块提供**

---

## 一、架构决策（Context）

### 1.1 决策背景

`ddd4j` 作为**框架无关**的 DDD 通用基础层，需要同时支持三套容器框架：

- `ddd4j-boot`（基于 Spring Boot 3.5.x，注解生态最丰富）
- `ddd4j-javalin`（基于 Javalin 6 + Guice，**注解生态最弱**）
- `ddd4j-quarkus`（基于 Quarkus 3.x + Arc CDI，标准 Jakarta EE 注解）

三套框架的注解能力**严重不对等**：

- Spring 自带 `@Service` / `@Repository` / `@Component` 等几十个注解
- Quarkus 通过 Jakarta EE + MicroProfile 提供 `@ApplicationScoped` / `@Path` / `@GET` / `@ConfigProperty` 等
- Javalin 6 **完全没有注解**——所有路由都是编程式 API（`app.get("/path", handler)`）

### 1.2 下沉的真正目的（核心设计目标）

**业务项目当前的使用现状（痛点）**：

```java
// ❌ 业务代码要写两个注解——既繁琐又冗余
@DomainService                                    // DDD 语义（ArchUnit 识别）
@Service                                          // Spring 自动注册 Bean
public class UserDomainServiceImpl implements UserDomainService {
    // 业务代码
}
```

**下沉后的目标（解决痛点）**：

```java
// ✅ 业务代码只写一个注解——同时获得 DDD 语义 + Spring Bean 自动注册
@DomainService                                    // 一个注解兼顾 DDD 语义 + Spring @Service
public class UserDomainServiceImpl implements UserDomainService {
    // 业务代码
}
```

**这就是下沉的真正目的**：

- 业务代码**只写一个 DDD 注解**
- DDD 注解**底层自动融合**框架的 Bean 注册机制
- 框架自动注册 Bean、ArchUnit 自动识别 DDD 分层、**业务代码完全无感**

### 1.3 注解的三大分类（精准策略）

**不是所有注解都应该下沉**——只有真正与框架相关的才下沉。按此标准，`ddd4j-annotation` 现有 25 个注解应分为三类：

| 分类                               | 处理                                | 包含注解                                                                                        |
|----------------------------------|-----------------------------------|---------------------------------------------------------------------------------------------|
| **A. 真正下沉（同名复制到底层）**             | ✅ 在 3 套框架注解模块同名复制 + 用框架元注解融合      | 12 个 DDD 注解（`@DomainService`、`@DomainRepository` 等）                                         |
| **B. 通用抽象（保留 ddd4j-annotation）** | ✅ 保留在 `ddd4j-annotation`，由各业务模块实现 | 权限、API 抽象、CQRS 投影事件、API 通用                                                                  |
| **C. 已废弃/无用（应删除）**               | ❌ 直接删除                            | `@BaseAuth`（已由 ddd4j-auth 替代）、`@EnableBaseAuth`、`@Inside`、`@WebSocketMapping`、`@RedisTopic` |

### 1.4 决策结论

**`ddd4j-annotation` 不做模块化拆分**，但**只保留基本通用注解**（纯 Java 标记）。三套框架各自建立**同名注解模块**——同名注解在
3 个框架下分别用各自容器技术实现，**底层自动融合**框架的 Bean 注册能力。

| 模块                      | 职责                                                                | 注解技术来源                                                           |
|-------------------------|-------------------------------------------------------------------|------------------------------------------------------------------|
| `ddd4j-annotation`      | **通用基础注解**（纯 Java 标记 + 业务模块抽象）                                    | 零框架依赖                                                            |
| `ddd4j-runtime-spring`  | **Spring 深度整合**：DDD 注解同名复制 + `@Service`/`@Repository` 元注解         | 用 Spring 原生注解作为**元注解**                                           |
| `ddd4j-runtime-guice`   | **Javalin/Guice 整合**：DDD 注解同名复制 + Guice `@Singleton` 元注解 + 路由参数解析 | Javalin 没有注解，用 Guice `@Singleton` 作为**元注解** + 新增 Javalin 框架缺失的能力 |
| `ddd4j-runtime-quarkus` | **Quarkus CDI 整合**：DDD 注解同名复制 + Jakarta `@ApplicationScoped` 元注解  | 用 Jakarta CDI 原生注解作为**元注解**                                      |

---

## 二、注解分类详解

### 2.1 A 类：真正下沉（同名复制到底层框架注解模块）

**只有 DDD 构造型注解需要下沉**——这是下沉的真正目的。

| DDD 注解                    | 业务代码写法                    | Spring 元注解                                    | Quarkus 元注解                | Javalin 元注解        |
|---------------------------|---------------------------|-----------------------------------------------|----------------------------|--------------------|
| `@DomainEntity`           | `@DomainEntity`           | `+ @Component`                                | `+ @ApplicationScoped`     | `+ @Singleton`     |
| `@DomainValueObject`      | `@DomainValueObject`      | `+ @Component`                                | `+ @ApplicationScoped`     | `+ @Singleton`     |
| **`@DomainService`**      | **`@DomainService`**      | **`+ @Service`**                              | **`+ @ApplicationScoped`** | **`+ @Singleton`** |
| **`@DomainRepository`**   | **`@DomainRepository`**   | **`+ @Repository`**                           | **`+ @ApplicationScoped`** | **`+ @Singleton`** |
| `@DomainGateway`          | `@DomainGateway`          | `+ @Component`                                | `+ @ApplicationScoped`     | `+ @Singleton`     |
| `@DomainEvent`            | `@DomainEvent`            | **保留在 ddd4j-annotation**（纯 marker，不需注册为 Bean） | 同                          | 同                  |
| `@DomainAssembler`        | `@DomainAssembler`        | `+ @Component`                                | `+ @ApplicationScoped`     | `+ @Singleton`     |
| `@DomainConverter`        | `@DomainConverter`        | `+ @Component`                                | `+ @ApplicationScoped`     | `+ @Singleton`     |
| **`@ApplicationService`** | **`@ApplicationService`** | **`+ @Service`**                              | **`+ @ApplicationScoped`** | **`+ @Singleton`** |
| `@QueryService`           | `@QueryService`           | `+ @Service`                                  | `+ @ApplicationScoped`     | `+ @Singleton`     |
| `@CommandExecutor`        | `@CommandExecutor`        | `+ @Component`                                | `+ @ApplicationScoped`     | `+ @Singleton`     |
| `DDDAnnotation`           | 元注解                       | ✅ 保留在 ddd4j-annotation（3 套共同引用）               |

**`@DomainEvent` 不下沉的原因**：

- 事件对象是**数据载体**，**不需要**注册为 Bean
- 通过 `DomainEventPublisher.publish(event)` 发布
- 监听者（`@EventListener` / `@Observes`）才是 Bean——它们用 `@DomainService` / `@ApplicationService` 等
- 纯 marker 注解下沉只会产生 3 份完全相同的副本，**零价值**

**关键点**：

- 3 套 DDD 注解**名称完全一致**（业务代码统一）
- 3 套 DDD 注解**底层融合框架的 Bean 注册能力**（用元注解）
- 业务代码**只写一个同名注解**

### 2.2 B 类：通用抽象（保留在 ddd4j-annotation，由各业务模块实现）

**这些注解与具体鉴权框架、WebSocket 框架、缓存框架无关**——它们是抽象能力，由 ddd4j-* 业务模块（如
ddd4j-auth、ddd4j-websocket、ddd4j-cache）提供具体实现。

| 包           | 注解                      | 说明               | 处理                   |
|-------------|-------------------------|------------------|----------------------|
| `auth`      | ~~`@BaseAuth`~~         | ~~基础认证~~         | ❌ 删除（ddd4j-auth 已替代） |
| `auth`      | ~~`@EnableBaseAuth`~~   | ~~启用认证~~         | ❌ 删除（ddd4j-auth 已替代） |
| `auth`      | ~~`@Inside`~~           | ~~内部调用~~         | ❌ 删除（ddd4j-auth 已替代） |
| `api`       | `@ApiModule`            | API 模块分组         | ✅ 保留（业务模块通用）         |
| `api`       | `@ApiOperationLog`      | 操作日志             | ✅ 保留（业务模块通用）         |
| `api`       | `@ApiIdempotent`        | 幂等性              | ✅ 保留（业务模块通用）         |
| `api`       | `@ApiIdempotentType`    | 幂等类型             | ✅ 保留（业务模块通用）         |
| `api`       | `@RawResponse`          | 原始响应             | ✅ 保留（业务模块通用）         |
| `cqrs`      | `@CreateEvent`          | 投影事件             | ✅ 保留（DddView 方法标记）   |
| `cqrs`      | `@UpdateEvent`          | 投影事件             | ✅ 保留（DddView 方法标记）   |
| `cqrs`      | `@DeleteEvent`          | 投影事件             | ✅ 保留（DddView 方法标记）   |
| `*`         | `@BusinessType`         | 业务类型             | ✅ 保留（业务通用）           |
| ~~`*`~~     | ~~`@WebSocketMapping`~~ | ~~WebSocket 映射~~ | ❌ 删除（纯 marker，框架无关）  |
| ~~`cache`~~ | ~~`@RedisTopic`~~       | ~~Redis Topic~~  | ❌ 删除（纯 marker，框架无关）  |
| `*`         | `DDDAnnotation`         | DDD 元注解          | ✅ 保留（3 套共同引用）        |
| `*`         | `Contract`              | ddd4j 总契约标记      | ✅ 保留                 |

### 2.3 C 类：应删除的注解（已被业务模块替代或纯 marker 无价值）

| 注解                  | 删除原因                                                                     |
|---------------------|--------------------------------------------------------------------------|
| `@BaseAuth`         | **已被 `ddd4j-auth` 模块替代**——使用具体鉴权框架的注解（`@SaCheckLogin`、`@PreAuthorize` 等） |
| `@EnableBaseAuth`   | **已被 `ddd4j-auth` 模块替代**——通过 `EnableXxxAuth` 注解 + 自动配置启用                 |
| `@Inside`           | **已被 `ddd4j-auth` 模块替代**——使用具体鉴权框架的内部调用注解                                |
| `@WebSocketMapping` | **纯 marker 无价值**——WebSocket 路由是框架相关的，应由具体 WebSocket 框架提供                 |
| `@RedisTopic`       | **纯 marker 无价值**——Redis Topic 是 Redis 客户端相关的，应由具体 Redis 框架提供             |

**核心原则**：

- 如果 ddd4j 已有专门模块（`ddd4j-auth`、`ddd4j-websocket`、`ddd4j-cache`）——**使用具体业务模块提供的注解**
- 如果只是纯 marker 没有框架相关元注解——**直接删除**，避免冗余

---

## 三、总体架构

```
                ┌──────────────────────────────────────────────┐
                │        ddd4j-annotation（通用基础注解）         │
                │        io.ddd4j.annotation.*                  │
                │                                               │
                │  职责：纯 Java 注解定义、零框架依赖               │
                │  内容：                                      │
                │    ✅ DDD 注解（作为契约源点，被 3 套同名复制）  │
                │    ✅ 通用抽象注解（API/CQRS/BusinessType）     │
                │    ✅ 元注解（DDDAnnotation/Contract）          │
                │    ❌ 已删除：auth 注解（ddd4j-auth 替代）     │
                │    ❌ 已删除：@WebSocketMapping（纯 marker）   │
                │    ❌ 已删除：@RedisTopic（纯 marker）         │
                └────────────────────┬─────────────────────────┘
                                     │ 同名复制 + 框架元注解融合
              ┌──────────────────────┼──────────────────────┐
              ▼                      ▼                      ▼
┌────────────────────┐  ┌────────────────────┐  ┌────────────────────┐
│  ddd4j-boot-       │  │  ddd4j-quarkus-    │  │  ddd4j-javalin-    │
│  annotation        │  │  annotation        │  │  annotation        │
│                    │  │                    │  │                    │
│  ✅ 12 个 DDD 注解  │  │  ✅ 12 个 DDD 注解  │  │  ✅ 12 个 DDD 注解  │
│  同名复制：        │  │  同名复制：        │  │  同名复制：        │
│  @DomainService    │  │  @DomainService    │  │  @DomainService    │
│    └ @Service     │  │    └ @ApplicationScoped│  └ @Singleton  │
│  @DomainRepository │  │  @DomainRepository │  │  @DomainRepository │
│    └ @Repository  │  │    └ @ApplicationScoped│  └ @Singleton  │
│                    │  │                    │  │                    │
│  ❌ 不创建 auth/   │  │  ❌ 不创建 auth/   │  │  ❌ 不创建 auth/   │
│     websocket/     │  │     websocket/     │  │     websocket/     │
│     cache 注解     │  │     cache 注解     │  │     cache 注解     │
│     （业务模块提供）│  │     （业务模块提供）│  │     （业务模块提供）│
│                    │  │                    │  │                    │
│  Web 路由用：      │  │  Web 路由用：      │  │  路由参数用：       │
│  @GetMapping       │  │  @GET/@Path       │  │  @PathParam         │
│  @RestController   │  │  @ApplicationPath │  │  @CookieParam       │
│  (Spring 原生)     │  │  (Jakarta 原生)   │  │  (ddd4j 填补空白)  │
└────────────────────┘  └────────────────────┘  └────────────────────┘

业务模块（已在 ddd4j-annotation 之外）：
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ ddd4j-auth      │  │ ddd4j-websocket │  │ ddd4j-cache     │
│  @SaCheckLogin  │  │ (具体实现)      │  │ (具体实现)       │
│  @PreAuthorize  │  │                 │  │                 │
│  @RequireRoles  │  │                 │  │                 │
└─────────────────┘  └─────────────────┘  └─────────────────┘
```

---

## 四、`ddd4j-annotation`（通用基础注解层）—— **精简 + 精准**

### 4.1 模块定位

- **位置**：`ddd4j/ddd4j-annotation`
- **职责**：定义**所有框架通用**的 DDD 标记注解 + 业务通用抽象
- **依赖约束**：**零框架依赖**

### 4.2 当前内容（25 个注解）的精准分类

| 包       | 注解                        | 角色               | 处理                                                                |
|---------|---------------------------|------------------|-------------------------------------------------------------------|
| `ddd`   | `@DomainEntity`           | 实体标记             | ⬇️ A 类：下沉到 3 套（兼顾 `@Component`）                                   |
| `ddd`   | `@DomainValueObject`      | 值对象标记            | ⬇️ A 类：下沉到 3 套（兼顾 `@Component`）                                   |
| `ddd`   | **`@DomainService`**      | **领域服务标记**       | ⬇️ **A 类：下沉（兼顾 `@Service`/`@ApplicationScoped`/`@Singleton`）**    |
| `ddd`   | **`@DomainRepository`**   | **仓储标记**         | ⬇️ **A 类：下沉（兼顾 `@Repository`/`@ApplicationScoped`/`@Singleton`）** |
| `ddd`   | `@DomainGateway`          | 网关标记             | ⬇️ A 类：下沉到 3 套（兼顾 `@Component`）                                   |
| `ddd`   | `@DomainEvent`            | 领域事件标记           | ✅ **B 类：保留 ddd4j-annotation**（纯 marker，事件不需注册 Bean）               |
| `ddd`   | `@DomainAssembler`        | 装配器标记            | ⬇️ A 类：下沉到 3 套（兼顾 `@Component`）                                   |
| `ddd`   | `@DomainConverter`        | 转换器标记            | ⬇️ A 类：下沉到 3 套（兼顾 `@Component`）                                   |
| `ddd`   | **`@ApplicationService`** | **应用服务标记**       | ⬇️ **A 类：下沉**                                                     |
| `ddd`   | `@QueryService`           | 查询服务标记           | ⬇️ A 类：下沉                                                         |
| `ddd`   | `@CommandExecutor`        | 命令执行器标记          | ⬇️ A 类：下沉                                                         |
| `ddd`   | `@DDDAnnotation`          | DDD 元注解          | ✅ **保留在 ddd4j-annotation**（3 套共同引用）                               |
| `api`   | `@ApiModule`              | API 模块分组         | ✅ B 类：保留在 ddd4j-annotation                                        |
| `api`   | `@ApiOperationLog`        | 操作日志             | ✅ B 类：保留在 ddd4j-annotation                                        |
| `api`   | `@ApiIdempotent`          | 幂等性              | ✅ B 类：保留在 ddd4j-annotation                                        |
| `api`   | `@ApiIdempotentType`      | 幂等类型             | ✅ B 类：保留在 ddd4j-annotation                                        |
| `api`   | `@RawResponse`            | 原始响应             | ✅ B 类：保留在 ddd4j-annotation                                        |
| `auth`  | ~~`@BaseAuth`~~           | ~~基础认证~~         | ❌ **C 类：删除**（ddd4j-auth 已替代）                                      |
| `auth`  | ~~`@EnableBaseAuth`~~     | ~~启用认证~~         | ❌ **C 类：删除**（ddd4j-auth 已替代）                                      |
| `auth`  | ~~`@Inside`~~             | ~~内部调用~~         | ❌ **C 类：删除**（ddd4j-auth 已替代）                                      |
| `cache` | ~~`@RedisTopic`~~         | ~~Redis Topic~~  | ❌ **C 类：删除**（纯 marker，无价值）                                        |
| `cqrs`  | `@CreateEvent`            | 投影事件             | ✅ B 类：保留在 ddd4j-annotation                                        |
| `cqrs`  | `@UpdateEvent`            | 投影事件             | ✅ B 类：保留在 ddd4j-annotation                                        |
| `cqrs`  | `@DeleteEvent`            | 投影事件             | ✅ B 类：保留在 ddd4j-annotation                                        |
| `*`     | `@BusinessType`           | 业务类型             | ✅ B 类：保留在 ddd4j-annotation                                        |
| `*`     | ~~`@WebSocketMapping`~~   | ~~WebSocket 映射~~ | ❌ **C 类：删除**（纯 marker，无价值）                                        |

### 4.3 清理后的 ddd4j-annotation 内容

清理后保留 **20 个注解**（删除 5 个无意义注解）：

| 类别                                           | 数量 | 注解                                                                                                                                                                                        |
|----------------------------------------------|----|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **A 类（DDD 注解，11 个下沉到 3 套同名复制）**              | 11 | `@DomainEntity` `@DomainValueObject` `@DomainService` `@DomainRepository` `@DomainGateway` `@DomainAssembler` `@DomainConverter` `@ApplicationService` `@QueryService` `@CommandExecutor` |
| **B 类（DDD 注解，纯 marker 保留 ddd4j-annotation）** | 1  | `@DomainEvent`（事件是数据载体，不需注册 Bean）                                                                                                                                                         |
| **B 类（通用抽象）**                                | 8  | `@ApiModule` `@ApiOperationLog` `@ApiIdempotent` `@ApiIdempotentType` `@RawResponse` `@CreateEvent` `@UpdateEvent` `@DeleteEvent` `@BusinessType`                                         |
| **元注解**                                      | 2  | `@DDDAnnotation` `@Contract`（建议新增）                                                                                                                                                        |

### 4.4 关键约束

- ❌ **不依赖**任何框架（Spring/CDI/Guice）
- ❌ **不提供**任何 `@Service` / `@ApplicationScoped` / `@Singleton` 等框架元注解
- ❌ **不提供** auth/websocket/cache 相关的框架注解（由 ddd4j-auth 等具体业务模块提供）
- ✅ **保留** `DDDAnnotation` / `Contract` 等 marker 元注解
- ✅ **保留** 12 个 DDD 注解 + 8 个通用抽象（API/CQRS/BusinessType）

---

## 五、Spring 深度整合层 `ddd4j-runtime-spring`

### 5.1 模块定位

- **位置**：`/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-runtime/ddd4j-runtime-spring/`
- **职责**：**同名复制 12 个 DDD 注解** + 用 Spring 原生注解作为**元注解**——实现 `@DomainService` 兼顾 `@Service`
- **依赖**：`spring-context`（提供元注解）
- **使用方**：基于 ddd4j-boot 的 Spring Boot 业务项目

### 5.2 核心设计原则（关键）

**✅ 创建 Spring 元注解融合的 DDD 注解**（核心目标）：

- ✅ `@DomainService` = DDD 语义 + `@Service`（业务代码只写一个）
- ✅ `@DomainRepository` = DDD 语义 + `@Repository`（自动启用 Spring 异常转换）
- ✅ `@ApplicationService` = DDD 语义 + `@Service`
- ✅ `@QueryService` = DDD 语义 + `@Service`
- ✅ `@DomainEntity` = DDD 语义 + `@Component`
- ✅ `@DomainGateway` = DDD 语义 + `@Component`
- ✅ `@DomainAssembler` = DDD 语义 + `@Component`
- ✅ `@DomainConverter` = DDD 语义 + `@Component`
- ✅ `@DomainValueObject` = DDD 语义 + `@Component`
- ✅ `@CommandExecutor` = DDD 语义 + `@Component`
- ❌ **`@DomainEvent` 不下沉**（纯 marker，事件不需注册 Bean）

**❌ 不创建的注解**（避免重复造轮子）：

- ❌ 不创建 auth 相关注解（**ddd4j-auth 已提供**：`@SaCheckLogin`、`@PreAuthorize` 等）
- ❌ 不创建 websocket 相关注解（**ddd4j-websocket 已提供**）
- ❌ 不创建 cache 相关注解（**ddd4j-cache 已提供**）
- ❌ 不创建 Spring 已有能力的替代（`@Service`/`@GetMapping`/`@Autowired` 等业务代码直接用）

### 5.3 推荐的包结构（极简）

```
ddd4j-runtime/ddd4j-runtime-spring/
├── src/main/java/io/ddd4j/spring/annotation/
│   ├── DomainEntity.java       // 兼顾 @Component
│   ├── DomainValueObject.java  // 兼顾 @Component
│   ├── DomainService.java      // 兼顾 @Service  ★
│   ├── DomainRepository.java   // 兼顾 @Repository  ★
│       ├── DomainGateway.java      // 兼顾 @Component
│       ├── DomainAssembler.java    // 兼顾 @Component
│       ├── DomainConverter.java    // 兼顾 @Component
│       ├── ApplicationService.java // 兼顾 @Service
│       ├── QueryService.java       // 兼顾 @Service
│       └── CommandExecutor.java    // 兼顾 @Component
│       （@DomainEvent 不下沉，保留在 ddd4j-annotation）
└── pom.xml
```

### 5.4 实现示例

```java
package io.ddd4j.spring.annotation;

import io.ddd4j.annotation.ddd.DDDAnnotation;
import org.springframework.stereotype.Service;
import org.springframework.core.annotation.AliasFor;
import java.lang.annotation.*;

/**
 * Spring 业务服务 Bean（领域服务）
 * 
 * <p><b>核心目标</b>：业务代码只写一个 @DomainService，同时获得：
 * <ul>
 *   <li>DDD 语义（被 ArchUnit 规则识别）</li>
 *   <li>Spring 自动注册为 Bean（@Service 元注解）</li>
 *   <li>ddd4j AOP 拦截能力</li>
 * </ul>
 * 
 * <p>业务代码使用方式：
 * <pre>
 * &#64;DomainService   // ← 只需写一个注解！
 * public class UserDomainServiceImpl implements UserDomainService {
 *     // 自动被 Spring 注册为 Bean
 *     // 同时被 ArchUnit 识别为 DomainService
 * }
 * </pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DDDAnnotation                                  // 标注为 DDD 注解（ArchUnit 识别）
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Service                                        // ★ 关键：直接用 Spring 原生元注解
@Inherited
public @interface DomainService {

    /**
     * Bean 名称（透传给 @Service）
     */
    @AliasFor(annotation = Service.class, attribute = "value")
    String value() default "";
}
```

```java
package io.ddd4j.spring.annotation;

import io.ddd4j.annotation.ddd.DDDAnnotation;
import org.springframework.stereotype.Repository;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * Spring 领域仓储 Bean
 *
 * <p><b>核心目标</b>：业务代码只写一个 @DomainRepository，同时获得：
 * <ul>
 *   <li>DDD 语义（被 ArchUnit 规则识别）</li>
 *   <li>Spring 自动注册为 Bean（@Repository 元注解）</li>
 *   <li>Spring 自动数据访问异常转换（@Repository 内置）</li>
 * </ul>
 */
@DDDAnnotation
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repository                                     // ★ 关键：直接用 Spring 原生元注解
@Inherited
public @interface DomainRepository {

    @AliasFor(annotation = Repository.class, attribute = "value")
    String value() default "";
}
```

```java
package io.ddd4j.spring.annotation;

import io.ddd4j.annotation.ddd.DDDAnnotation;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.AliasFor;
import java.lang.annotation.*;

/**
 * Spring 领域实体（充血模型）
 * 
 * <p><b>核心目标</b>：业务代码只写一个 @DomainEntity，自动注册为 Spring Bean。
 * 
 * <p>业务代码使用方式：
 * <pre>
 * &#64;DomainEntity(aggregateRoot = true)
 * public class User { ... }
 * </pre>
 */
@DDDAnnotation
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component                                       // ★ 关键：自动注册为 Bean
@Inherited
public @interface DomainEntity {

    /**
     * 是否是聚合根
     */
    boolean aggregateRoot() default false;

    @AliasFor(annotation = Component.class, attribute = "value")
    String value() default "";
}
```

```java
package io.ddd4j.spring.annotation;

import io.ddd4j.annotation.ddd.DDDAnnotation;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * Spring 领域网关（防腐层）
 *
 * <p>用于标注外部服务调用的网关接口（ACL）。
 */
@DDDAnnotation
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
@Inherited
public @interface DomainGateway {

    @AliasFor(annotation = Component.class, attribute = "value")
    String value() default "";
}
```

```java
package io.ddd4j.spring.annotation;

import io.ddd4j.annotation.ddd.DDDAnnotation;
import org.springframework.stereotype.Service;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * Spring 应用层服务
 *
 * <p>应用层服务编排领域服务、事务管理、事件发布。
 */
@DDDAnnotation
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Service
@Inherited
public @interface ApplicationService {

    @AliasFor(annotation = Service.class, attribute = "value")
    String value() default "";
}
```

```java
package io.ddd4j.spring.annotation;

import io.ddd4j.annotation.ddd.DDDAnnotation;
import org.springframework.stereotype.Service;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * Spring 查询服务（CQRS 读侧）
 */
@DDDAnnotation
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Service
@Inherited
public @interface QueryService {

    @AliasFor(annotation = Service.class, attribute = "value")
    String value() default "";
}
```

```java
package io.ddd4j.spring.annotation;

import io.ddd4j.annotation.ddd.DDDAnnotation;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * Spring 命令执行器（CQRS 写侧）
 */
@DDDAnnotation
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
@Inherited
public @interface CommandExecutor {

    @AliasFor(annotation = Component.class, attribute = "value")
    String value() default "";
}
```

### 5.5 业务代码使用方式（最简——只写一个 DDD 注解）

```java
import io.ddd4j.spring.annotation.DomainService;     // ← 只需要 ddd4j Spring 注解
import io.ddd4j.spring.annotation.DomainRepository;
import io.ddd4j.spring.annotation.ApplicationService;
import org.springframework.web.bind.annotation.RestController;  // Web 用 Spring 原生
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

// ============== 领域服务：只写一个注解 ==============
@DomainService    // ← 同时获得 DDD 语义 + Spring @Service Bean 注册
public class UserDomainServiceImpl implements UserDomainService {
    @Autowired
    private UserRepository userRepository;
}

// ============== 领域仓储：只写一个注解 ==============
@DomainRepository  // ← 同时获得 DDD 语义 + Spring @Repository（自动异常转换）
public interface UserRepository {
    User save(User user);
}

// ============== 应用服务：只写一个注解 ==============
@ApplicationService  // ← 同时获得 DDD 语义 + Spring @Service
public class UserApplicationServiceImpl implements UserApplicationService {
    @Autowired
    private UserDomainService userDomainService;
}

// ============== 鉴权（使用 ddd4j-auth 提供的 Sa-Token 注解） ==============
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;

@ApplicationService
public class UserApplicationServiceImpl implements UserApplicationService {

    @SaCheckLogin                                    // ← ddd4j-auth 提供的具体鉴权注解
    @SaCheckRole("admin")
    public User createUser(CreateUserCmd cmd) { ... }
}

// ============== Web 路由：用 Spring 原生注解 ==============
@RestController  // ← Spring 原生（ddd4j 不重新发明 Web 路由）
@RequestMapping("/users")
public class UserController {

    @GetMapping("/{id}")
    public User getUser(@PathVariable String id) { ... }

    @PostMapping
    public User createUser(@RequestBody CreateUserCmd cmd) { ... }
}
```

### 5.6 pom.xml 设计

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.ddd4j</groupId>
        <artifactId>ddd4j-parent</artifactId>
        <version>${revision}</version>
    </parent>

    <groupId>io.ddd4j</groupId>
    <artifactId>ddd4j-runtime-spring</artifactId>
    <description>ddd4j-runtime-spring 注解模块：DDD 注解同名复制（用 Spring 元注解融合）</description>

    <dependencies>
        <!-- 依赖 ddd4j-annotation（提供 DDDAnnotation marker 元注解） -->
        <dependency>
            <groupId>io.ddd4j</groupId>
            <artifactId>ddd4j-annotation</artifactId>
            <version>${ddd4j.version}</version>
        </dependency>
        <!-- Spring 核心（用于 DDD 注解的元注解） -->
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
        </dependency>
    </dependencies>
</project>
```

---

## 六、Quarkus 深度整合层 `ddd4j-runtime-quarkus`

### 6.1 模块定位

- **位置**：`/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-runtime/ddd4j-runtime-quarkus/`
- **职责**：**同名复制 12 个 DDD 注解** + 用 Jakarta CDI 原生注解作为**元注解**——实现 `@DomainService` 兼顾
  `@ApplicationScoped`
- **依赖**：`jakarta.enterprise.cdi-api`
- **使用方**：基于 ddd4j-quarkus 的业务项目

### 6.2 核心设计原则（与 Spring 风格一致）

**✅ 创建 Jakarta 元注解融合的 DDD 注解**（核心目标）：

- ✅ `@DomainService` = DDD 语义 + `@ApplicationScoped`（业务代码只写一个）
- ✅ `@DomainRepository` = DDD 语义 + `@ApplicationScoped`
- ✅ `@ApplicationService` = DDD 语义 + `@ApplicationScoped`
- ✅ `@QueryService` = DDD 语义 + `@ApplicationScoped`
- ✅ `@DomainEntity` = DDD 语义 + `@ApplicationScoped`
- ✅ `@DomainGateway` = DDD 语义 + `@ApplicationScoped`
- ✅ `@DomainAssembler` = DDD 语义 + `@ApplicationScoped`
- ✅ `@DomainConverter` = DDD 语义 + `@ApplicationScoped`
- ✅ `@DomainValueObject` = DDD 语义 + `@ApplicationScoped`
- ✅ `@CommandExecutor` = DDD 语义 + `@ApplicationScoped`
- ❌ **`@DomainEvent` 不下沉**（纯 marker，事件不需注册 Bean）

**❌ 不创建**：

- ❌ auth/websocket/cache 相关注解（**业务模块提供**）
- ❌ Quarkus 已有能力的替代（`@Path`/`@GET`/`@Inject` 等业务代码直接用）

### 6.3 推荐的包结构（极简）

```
ddd4j-runtime/ddd4j-runtime-quarkus/
├── src/main/java/io/ddd4j/quarkus/annotation/
│   └── ddd/                        ← 只有 DDD 注解同名复制（11 个）
│       ├── DomainEntity.java
│       ├── DomainValueObject.java
│       ├── DomainService.java      // 兼顾 @ApplicationScoped  ★
│       ├── DomainRepository.java   // 兼顾 @ApplicationScoped  ★
│       ├── DomainGateway.java
│       ├── DomainAssembler.java
│       ├── DomainConverter.java
│       ├── ApplicationService.java
│       ├── QueryService.java
│       └── CommandExecutor.java
│       （@DomainEvent 不下沉，保留在 ddd4j-annotation）
└── pom.xml
```

### 6.4 实现示例

```java
package io.ddd4j.quarkus.annotation.ddd;

import io.ddd4j.annotation.ddd.DDDAnnotation;
import jakarta.enterprise.context.ApplicationScoped;
import java.lang.annotation.*;

/**
 * Quarkus 业务服务 Bean（领域服务）
 * 
 * <p><b>核心目标</b>：业务代码只写一个 @DomainService，同时获得：
 * <ul>
 *   <li>DDD 语义（被 ArchUnit 规则识别）</li>
 *   <li>Quarkus CDI 自动注册为 Bean（@ApplicationScoped 元注解）</li>
 * </ul>
 */
@DDDAnnotation
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ApplicationScoped                               // ★ 关键：直接用 Jakarta CDI 元注解
@Inherited
public @interface DomainService {
}
```

```java
package io.ddd4j.quarkus.annotation.ddd;

import io.ddd4j.annotation.ddd.DDDAnnotation;
import jakarta.enterprise.context.ApplicationScoped;
import java.lang.annotation.*;

/**
 * Quarkus 领域仓储
 */
@DDDAnnotation
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ApplicationScoped
@Inherited
public @interface DomainRepository {
}
```

```java
package io.ddd4j.quarkus.annotation.ddd;

import io.ddd4j.annotation.ddd.DDDAnnotation;
import jakarta.enterprise.context.ApplicationScoped;
import java.lang.annotation.*;

/**
 * Quarkus 应用服务
 */
@DDDAnnotation
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ApplicationScoped
@Inherited
public @interface ApplicationService {
}
```

### 6.5 业务代码使用方式

```java
import io.ddd4j.quarkus.annotation.ddd.DomainService;
import io.ddd4j.quarkus.annotation.ddd.DomainRepository;
import io.ddd4j.quarkus.annotation.ddd.ApplicationService;
import io.quarkus.security.PermitAll;                              // ← Quarkus 安全
import jakarta.ws.rs.Path;                                       // ← Jakarta JAX-RS
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;

// ============== 领域服务：只写一个注解 ==============
@DomainService   // ← 同时获得 DDD 语义 + Quarkus CDI @ApplicationScoped
public class UserDomainServiceImpl implements UserDomainService {
    @Inject
    private UserRepository userRepository;
}

// ============== 鉴权（使用 Quarkus 原生安全） ==============
@ApplicationService
public class UserApplicationServiceImpl implements UserApplicationService {

    @PermitAll                                       // ← Quarkus 原生安全（不套壳）
    public User getUser(Long id) { ... }
}

// ============== Web 路由：用 Jakarta 原生 ==============
@Path("/users")
public class UserController {

    @GET
    @Path("/{id}")
    public User getUser(@PathParam("id") String id) { ... }
}
```

### 6.6 pom.xml 设计

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.ddd4j.quarkus</groupId>
        <artifactId>ddd4j-quarkus-parent</artifactId>
        <version>${revision}</version>
    </parent>

    <artifactId>ddd4j-quarkus</artifactId>
    <description>ddd4j-quarkus 模块：Quarkus DDD 注解与 CDI 适配实现</description>

    <dependencies>
        <!-- 依赖 ddd4j-annotation（提供 DDDAnnotation marker） -->
        <dependency>
            <groupId>io.ddd4j</groupId>
            <artifactId>ddd4j-annotation</artifactId>
            <version>${ddd4j.version}</version>
        </dependency>
        <!-- Jakarta CDI API -->
        <dependency>
            <groupId>jakarta.enterprise</groupId>
            <artifactId>jakarta.enterprise.cdi-api</artifactId>
        </dependency>
    </dependencies>
</project>
```

---

## 七、Javalin/Guice 深度整合层 `ddd4j-runtime-guice`

### 7.1 模块定位

- **位置**：`/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-runtime/ddd4j-runtime-guice/`
- **职责**：**同名复制 12 个 DDD 注解** + 用 Guice `@Singleton` 作为**元注解** + **新增 Javalin 框架真正没有的路由参数解析
  **
- **依赖**：`guice` / `javalin`（provided）
- **当前状态**：Javalin 注解与 Guice 元注解能力已收敛到 `ddd4j-runtime-guice`；Javalin Web 能力位于 `ddd4j-web-javalin`

### 7.2 核心设计原则

**✅ 创建 Guice 元注解融合的 DDD 注解**（核心目标）：

- ✅ `@DomainService` = DDD 语义 + Guice `@Singleton`（业务代码只写一个）
- ✅ `@DomainRepository` = DDD 语义 + `@Singleton`
- ✅ `@ApplicationService` = DDD 语义 + `@Singleton`
- ✅ `@QueryService` = DDD 语义 + `@Singleton`
- ✅ `@DomainEntity` = DDD 语义 + `@Singleton`
- ✅ `@DomainGateway` = DDD 语义 + `@Singleton`
- ✅ `@DomainAssembler` = DDD 语义 + `@Singleton`
- ✅ `@DomainConverter` = DDD 语义 + `@Singleton`
- ✅ `@DomainValueObject` = DDD 语义 + `@Singleton`
- ✅ `@CommandExecutor` = DDD 语义 + `@Singleton`
- ❌ **`@DomainEvent` 不下沉**（纯 marker，事件不需注册 Bean）

**✅ 新增 Javalin 框架真正没有的路由参数解析**（填补框架空白）：

- ✅ `PathParam` / `QueryParam` / `FormParam` / `HeaderParam` / `BodyParam`（虽然 Javalin 用编程式 API，但注解+反射可让代码更简洁）
- ✅ **`CookieParam`**（Javalin 6 缺失，**真正需要新增**）
- ✅ `Context`（Javalin 必须手动传递 Context，ddd4j 通过反射自动注入）

**❌ 不创建**：

- ❌ auth/websocket/cache 相关注解（**业务模块提供**）

### 7.3 推荐的包结构

```
ddd4j-runtime/ddd4j-runtime-guice/
├── src/main/java/io/ddd4j/guice/annotation/
│   ├── ddd/                        ← 11 个 DDD 注解同名复制（核心）
│   │   ├── DomainEntity.java
│   │   ├── DomainValueObject.java
│   │   ├── DomainService.java      // 兼顾 @Singleton  ★
│   │   ├── DomainRepository.java   // 兼顾 @Singleton  ★
│   │   ├── DomainGateway.java
│   │   ├── DomainAssembler.java
│   │   ├── DomainConverter.java
│   │   ├── ApplicationService.java
│   │   ├── QueryService.java
│   │   └── CommandExecutor.java
│   │   （@DomainEvent 不下沉，保留在 ddd4j-annotation）
│   ├── cqrs/                       ← 3 个 CQRS 投影事件注解（保留在 ddd4j-annotation 风格）
│   │   ├── CreateEvent.java        // 与 ddd4j-annotation 同名
│   │   ├── UpdateEvent.java
│   │   └── DeleteEvent.java
│   ├── api/                        ← 5 个 API 抽象注解
│   │   ├── ApiModule.java
│   │   ├── ApiOperationLog.java
│   │   ├── ApiIdempotent.java
│   │   ├── ApiIdempotentType.java
│   │   └── RawResponse.java
│   ├── common/                     ← 业务通用注解
│   │   └── BusinessType.java
│   └── web/                        ← Javalin 路由参数解析（**真正需要新增**）
│       ├── PathParam.java
│       ├── QueryParam.java
│       ├── FormParam.java
│       ├── HeaderParam.java
│       ├── CookieParam.java        // Javalin 6 缺失
│       ├── BodyParam.java
│       └── Context.java
└── pom.xml
```

### 7.4 实现示例

```java
package io.ddd4j.javalin.annotation.ddd;

import com.google.inject.Singleton;
import io.ddd4j.annotation.ddd.DDDAnnotation;
import java.lang.annotation.*;

/**
 * Javalin 业务服务 Bean（领域服务）
 * 
 * <p><b>核心目标</b>：业务代码只写一个 @DomainService，同时获得：
 * <ul>
 *   <li>DDD 语义（被 ArchUnit 规则识别）</li>
 *   <li>Guice 自动注册为 Singleton（@Singleton 元注解）</li>
 * </ul>
 */
@DDDAnnotation
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Singleton                                       // ★ 关键：直接用 Guice 原生元注解
@Inherited
public @interface DomainService {
}
```

```java
package io.ddd4j.javalin.annotation.ddd;

import com.google.inject.Singleton;
import io.ddd4j.annotation.ddd.DDDAnnotation;
import java.lang.annotation.*;

/**
 * Javalin 领域仓储
 */
@DDDAnnotation
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Singleton
@Inherited
public @interface DomainRepository {
}
```

**Javalin 路由参数解析（填补框架空白）**：

```java
package io.ddd4j.javalin.annotation.web;

import java.lang.annotation.*;

/**
 * Javalin 路径参数注解（**Javalin 6 真正缺失的便捷能力**）
 * 
 * <p>Javalin 原生 API：{@code ctx.pathParam("id")}
 * <p>ddd4j 注解版：通过反射自动注入到方法参数
 * 
 * <p>业务代码使用：
 * <pre>
 * app.get("/users/{id}", controller::getUser);
 * 
 * public User getUser(&#64;PathParam("id") String id) { ... }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface PathParam {
    String value();
    String defaultValue() default "";
}
```

```java
package io.ddd4j.javalin.annotation.web;

import java.lang.annotation.*;

/**
 * Javalin Cookie 参数注解（**Javalin 6 缺失的能力**）
 * 
 * <p>Javalin 原生 API：{@code ctx.cookie("name")}
 * <p>ddd4j 注解版：通过反射自动注入
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface CookieParam {
    String value();
    String defaultValue() default "";
}
```

```java
package io.ddd4j.javalin.annotation.web;

import io.javalin.http.Context;
import java.lang.annotation.*;

/**
 * Javalin Context 注入注解
 * 
 * <p>业务代码可直接接收 Context，无需手动传递。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface Context {
}
```

### 7.5 业务代码使用方式

```java
import com.google.inject.Inject;                              // ← 用 Guice 原生 DI
import io.ddd4j.javalin.annotation.ddd.DomainService;         // ← ddd4j 注解
import io.ddd4j.javalin.annotation.ddd.DomainRepository;
import io.ddd4j.javalin.annotation.web.PathParam;             // ← ddd4j 新增（Javalin 没原生）
import io.ddd4j.javalin.annotation.web.QueryParam;            // ← ddd4j 新增
import io.ddd4j.javalin.annotation.web.CookieParam;           // ← ddd4j 新增
import io.ddd4j.javalin.annotation.web.Context;               // ← ddd4j 新增
import io.javalin.Javalin;                                    // ← 用 Javalin 原生 API 注册路由

// ============== 领域服务：只写一个注解 ==============
@DomainService   // ← 同时获得 DDD 语义 + Guice @Singleton
public class UserDomainServiceImpl implements UserDomainService {
    @Inject
    private UserRepository userRepository;
}

// ============== Web 控制器：Javalin 原生 + ddd4j 路由参数解析 ==============
public class UserController {
    public void register(Javalin app) {
        app.get("/users/{id}", this::getUser);
    }

    public User getUser(
            @PathParam("id") String id,             // ← ddd4j 新增（填补 Javalin 缺失）
            @QueryParam("name") String name,         // ← ddd4j 新增
            @CookieParam("session") String session,  // ← ddd4j 新增
            @Context Context ctx                    // ← ddd4j 新增
    ) {
        // 业务逻辑
    }
}
```

### 7.6 pom.xml 设计

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.ddd4j</groupId>
        <artifactId>ddd4j-dependencies</artifactId>
        <version>${revision}</version>
    </parent>

    <artifactId>ddd4j-runtime-guice</artifactId>
    <description>ddd4j-runtime-guice 适配模块：DDD 注解同名复制（用 Guice @Singleton 元注解融合）+ Guice/Javalin 通用适配</description>

    <dependencies>
        <!-- 依赖 ddd4j-annotation（提供 DDDAnnotation marker） -->
        <dependency>
            <groupId>io.ddd4j</groupId>
            <artifactId>ddd4j-annotation</artifactId>
            <version>${ddd4j.version}</version>
        </dependency>
        <!-- Guice（用于 DDD 注解的元注解） -->
        <dependency>
            <groupId>com.google.inject</groupId>
            <artifactId>guice</artifactId>
        </dependency>
        <!-- Javalin 6（运行时反射使用） -->
        <dependency>
            <groupId>io.javalin</groupId>
            <artifactId>javalin</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

---

## 八、3 套同名注解核心对照表

### 8.1 A 类 DDD 注解同名复制（业务代码统一——核心目标，11 个）

| DDD 注解                    | 业务代码写法                    | Spring 模块底层                                    | Quarkus 模块底层               | Javalin 模块底层       |
|---------------------------|---------------------------|------------------------------------------------|----------------------------|--------------------|
| `@DomainEntity`           | `@DomainEntity`           | `+ @Component`                                 | `+ @ApplicationScoped`     | `+ @Singleton`     |
| `@DomainValueObject`      | `@DomainValueObject`      | `+ @Component`                                 | `+ @ApplicationScoped`     | `+ @Singleton`     |
| **`@DomainService`**      | **`@DomainService`**      | **`+ @Service`**                               | **`+ @ApplicationScoped`** | **`+ @Singleton`** |
| **`@DomainRepository`**   | **`@DomainRepository`**   | **`+ @Repository`**                            | **`+ @ApplicationScoped`** | **`+ @Singleton`** |
| `@DomainGateway`          | `@DomainGateway`          | `+ @Component`                                 | `+ @ApplicationScoped`     | `+ @Singleton`     |
| `@DomainAssembler`        | `@DomainAssembler`        | `+ @Component`                                 | `+ @ApplicationScoped`     | `+ @Singleton`     |
| `@DomainConverter`        | `@DomainConverter`        | `+ @Component`                                 | `+ @ApplicationScoped`     | `+ @Singleton`     |
| **`@ApplicationService`** | **`@ApplicationService`** | **`+ @Service`**                               | **`+ @ApplicationScoped`** | **`+ @Singleton`** |
| `@QueryService`           | `@QueryService`           | `+ @Service`                                   | `+ @ApplicationScoped`     | `+ @Singleton`     |
| `@CommandExecutor`        | `@CommandExecutor`        | `+ @Component`                                 | `+ @ApplicationScoped`     | `+ @Singleton`     |
| `@DomainEvent`            | `@DomainEvent`            | **保留在 ddd4j-annotation**（纯 marker，事件不需注册 Bean） | 同                          | 同                  |

**关键点**：

- 3 套 DDD 注解**名称完全一致**（`@DomainService`/`@DomainRepository`/...）
- 3 套 DDD 注解**底层融合框架的 Bean 注册能力**（用元注解）
- 业务代码**只写一个同名注解**，**同时获得 DDD 语义 + 框架 Bean 注册**

### 8.2 B 类通用抽象（保留 ddd4j-annotation）

| 注解                   | 业务项目使用                                            | 来源               |
|----------------------|---------------------------------------------------|------------------|
| `@ApiModule`         | ✅ 直接用 `io.ddd4j.annotation.api.ApiModule`         | ddd4j-annotation |
| `@ApiOperationLog`   | ✅ 直接用 `io.ddd4j.annotation.api.ApiOperationLog`   | ddd4j-annotation |
| `@ApiIdempotent`     | ✅ 直接用 `io.ddd4j.annotation.api.ApiIdempotent`     | ddd4j-annotation |
| `@ApiIdempotentType` | ✅ 直接用 `io.ddd4j.annotation.api.ApiIdempotentType` | ddd4j-annotation |
| `@RawResponse`       | ✅ 直接用 `io.ddd4j.annotation.api.RawResponse`       | ddd4j-annotation |
| `@CreateEvent`       | ✅ 直接用 `io.ddd4j.annotation.cqrs.CreateEvent`      | ddd4j-annotation |
| `@UpdateEvent`       | ✅ 直接用 `io.ddd4j.annotation.cqrs.UpdateEvent`      | ddd4j-annotation |
| `@DeleteEvent`       | ✅ 直接用 `io.ddd4j.annotation.cqrs.DeleteEvent`      | ddd4j-annotation |
| `@BusinessType`      | ✅ 直接用 `io.ddd4j.annotation.BusinessType`          | ddd4j-annotation |

**这些注解不需下沉**——业务项目直接 import ddd4j-annotation 即可。

### 8.3 业务模块提供的具体实现（不通过 ddd4j-annotation 抽象）

| 业务能力          | 来源              | 业务项目使用                                               |
|---------------|-----------------|------------------------------------------------------|
| **鉴权**        | ddd4j-auth      | `@SaCheckLogin` / `@PreAuthorize` / `@RequiresRoles` |
| **WebSocket** | ddd4j-websocket | 业务模块提供具体实现                                           |
| **缓存**        | ddd4j-cache     | 业务模块提供具体实现                                           |
| **消息**        | ddd4j-mq        | 业务模块提供具体实现                                           |

**业务代码直接用各业务模块的具体实现注解**——不通过 ddd4j-annotation 抽象。

### 8.4 应删除的注解（C 类）

| 注解                  | 删除原因                                                         |
|---------------------|--------------------------------------------------------------|
| `@BaseAuth`         | **已被 `ddd4j-auth` 模块替代**——使用具体鉴权框架的注解                        |
| `@EnableBaseAuth`   | **已被 `ddd4j-auth` 模块替代**——通过 `EnableXxxAuth` 注解 + 自动配置启用     |
| `@Inside`           | **已被 `ddd4j-auth` 模块替代**——使用具体鉴权框架的内部调用注解                    |
| `@WebSocketMapping` | **纯 marker 无价值**——WebSocket 路由是框架相关的，应由具体 WebSocket 框架提供     |
| `@RedisTopic`       | **纯 marker 无价值**——Redis Topic 是 Redis 客户端相关的，应由具体 Redis 框架提供 |

### 8.5 框架原生能力（不重新发明轮子）

| 能力         | Spring 模块                                        | Quarkus 模块                        | Javalin 模块                      |
|------------|--------------------------------------------------|-----------------------------------|---------------------------------|
| **Web 路由** | ❌ 用 Spring 原生（`@RestController` / `@GetMapping`） | ❌ 用 Jakarta 原生（`@Path` / `@GET`）  | ❌ 用 Javalin 原生（编程式 `app.get()`） |
| **依赖注入**   | ❌ 用 Spring 原生（`@Autowired`）                      | ❌ 用 Jakarta 原生（`@Inject`）         | ❌ 用 Guice 原生（`@Inject`）         |
| **配置注入**   | ❌ 用 Spring 原生（`@Value`）                          | ❌ 用 Jakarta 原生（`@ConfigProperty`） | ❌ 用 Guice 原生（`@Provides`）       |
| **事务**     | ❌ 用 Spring 原生（`@Transactional`）                  | ❌ 用 Jakarta 原生（`@Transactional`）  | ❌ 手动控制                          |
| **鉴权**     | ❌ 用 `ddd4j-auth` 模块的具体注解（`@SaCheckLogin` 等）      | ❌ 用 Quarkus 原生安全（`@RolesAllowed`） | ❌ 用 Guice AOP                   |

### 8.6 Javalin 新增的路由参数解析（填补框架空白）

| 注解                | 说明         | 框架原生                                     |
|-------------------|------------|------------------------------------------|
| `PathParam`       | 路径参数       | Javalin 用 `ctx.pathParam()`，ddd4j 用反射注入  |
| `QueryParam`      | 查询参数       | Javalin 用 `ctx.queryParam()`，ddd4j 用反射注入 |
| `FormParam`       | 表单参数       | Javalin 用 `ctx.formParam()`，ddd4j 用反射注入  |
| **`CookieParam`** | Cookie 参数  | **Javalin 6 缺失，ddd4j 必须新增**              |
| `HeaderParam`     | 请求头参数      | Javalin 用 `ctx.header()`，ddd4j 用反射注入     |
| `BodyParam`       | 请求体参数      | Javalin 用 `ctx.body()`，ddd4j 用反射注入       |
| `Context`         | Context 注入 | **Javalin 必须手动传递，ddd4j 通过反射注入**          |

---

## 九、模块依赖关系

### 9.1 总体依赖图

```
                    ddd4j-annotation（通用基础 marker + 通用抽象）
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
   ddd4j-runtime-spring       ddd4j-runtime-guice         ddd4j-runtime-quarkus
   annotation         annotation          annotation
   (DDD 注解)         (DDD 注解)          (DDD 注解)
        │                   │                   │
        ▼                   ▼                   ▼
   spring-context      guice               jakarta.enterprise.cdi-api
   (元注解)            (元注解)            (元注解)

业务模块（独立）：
  ddd4j-auth        ddd4j-web          ddd4j-cache      ddd4j-mq
  (具体鉴权注解)     (具体 WS 注解)      (具体缓存注解)    (具体 MQ 注解)
```

### 9.2 依赖规则

| 规则     | 说明                                                                   |
|--------|----------------------------------------------------------------------|
| **R1** | `ddd4j-annotation` **绝不依赖**任何框架                                      |
| **R2** | 框架注解模块**强依赖** `ddd4j-annotation`（用 `DDDAnnotation` marker）           |
| **R3** | 框架注解模块**互不依赖**                                                       |
| **R4** | 业务项目**按需**引入 1 个框架注解模块，**禁止**同时引入多个                                  |
| **R5** | 业务代码**只写同名 DDD 注解**（如 `@DomainService`）+ 业务模块具体注解（如 `@SaCheckLogin`） |
| **R6** | 业务代码**不通过 ddd4j-annotation 抽象**业务模块的具体能力（auth/websocket/cache）       |

---

## 十、ArchUnit 规则适配

`CleanDDDLayerRules` / `ColaDDDLayerRules` 中的规则**需要更新**——检测**同名 DDD 注解**（来自 3 套框架注解模块的任意一套）：

```java

@ArchTest
static final ArchRule domain_service_in_domain =
        classes().that().areAnnotatedWith(
                        // 检测框架注解模块中的同名 DDD 注解
                        io.ddd4j.spring.annotation.DomainService.class
                                .or(io.ddd4j.quarkus.annotation.ddd.DomainService.class)
                                .or(io.ddd4j.guice.annotation.ddd.DomainService.class)
                )
                .should().resideInAPackage("..domain..");
```

**业务项目无论使用哪套框架注解模块，ArchUnit 都能正确识别 DDD 分层结构**。

---

## 十一、清理任务（删除无价值注解）

| 任务         | 删除文件                                                                                               |
|------------|----------------------------------------------------------------------------------------------------|
| **Task 1** | 删除 `ddd4j-annotation/src/main/java/io/ddd4j/annotation/auth/BaseAuth.java`（已被 ddd4j-auth 替代）       |
| **Task 2** | 删除 `ddd4j-annotation/src/main/java/io/ddd4j/annotation/auth/EnableBaseAuth.java`（已被 ddd4j-auth 替代） |
| **Task 3** | 删除 `ddd4j-annotation/src/main/java/io/ddd4j/annotation/auth/Inside.java`（已被 ddd4j-auth 替代）         |
| **Task 4** | 删除 `ddd4j-annotation/src/main/java/io/ddd4j/annotation/WebSocketMapping.java`（纯 marker 无价值）        |
| **Task 5** | 删除 `ddd4j-annotation/src/main/java/io/ddd4j/annotation/cache/RedisTopic.java`（纯 marker 无价值）        |

---

## 十二、渐进式落地步骤

| 阶段         | 任务                                                                                     | 工作量      |
|------------|----------------------------------------------------------------------------------------|----------|
| **Step 1** | 删除 5 个无价值注解（`@BaseAuth`/`@EnableBaseAuth`/`@Inside`/`@WebSocketMapping`/`@RedisTopic`） | 1 天      |
| **Step 2** | 创建 `ddd4j-runtime-spring` 模块（顶层），同名复制 12 个 DDD 注解 + 用 Spring 元注解融合                     | 1 周      |
| **Step 3** | Javalin 注解能力收敛到 `ddd4j-runtime-guice`，Javalin Web 能力收敛到 `ddd4j-web-javalin`            | 已完成/持续补强 |
| **Step 4** | Quarkus 注解/CDI 能力收敛到 `ddd4j-runtime-quarkus`，Quarkus Web 能力收敛到 `ddd4j-web-quarkus`     | 已完成/持续补强 |
| **Step 5** | 更新 ArchUnit 规则支持 3 套同名注解                                                               | 1 周      |
| **Step 6** | 改造 3 个 samples 业务代码使用新注解（**只写一个 DDD 注解**）                                              | 1-2 周    |
| **Step 7** | 编写统一使用文档 + 迁移指南                                                                        | 1 周      |

---

## 十三、关键设计决策总结

| 决策                                                                    | 原因                                                            |
|-----------------------------------------------------------------------|---------------------------------------------------------------|
| **3 套 DDD 注解同名复制（11 个）**                                              | 业务代码在不同框架下**完全一致**（`@DomainService` 就是 `@DomainService`）      |
| **DDD 注解底层用框架元注解融合**                                                  | **核心目标**——业务代码只写一个注解，同时获得 DDD 语义 + 框架 Bean 自动注册               |
| **`@DomainEvent` 不下沉**                                                | 纯 marker，事件对象是数据载体**不需**注册 Bean——通过 `DomainEventPublisher` 发布 |
| **`@BaseAuth` 等删除**                                                   | 已被 ddd4j-auth 模块替代——业务项目用具体鉴权框架的注解                            |
| **`@WebSocketMapping`/`@RedisTopic` 删除**                              | 纯 marker 无价值——WebSocket/Redis 是框架相关的，应由具体框架提供                 |
| **`@CreateEvent`/`@UpdateEvent`/`@DeleteEvent` 保留在 ddd4j-annotation** | CQRS 投影事件抽象，与框架无关——业务项目直接 import ddd4j-annotation 即可          |
| **API 抽象注解（`@ApiModule`/`@ApiOperationLog` 等）保留在 ddd4j-annotation**   | 通用 API 抽象，与框架无关——业务项目直接 import ddd4j-annotation 即可            |
| **新增 `CookieParam` 但不叫 `JavalinCookieParam`**                         | 填补 Javalin 框架空白，与 Spring/Quarkus 同名，框架无关命名                    |
| **3 套注解模块互不依赖**                                                       | 业务项目按需选型，避免依赖污染                                               |

**核心原则**：
> **下沉只下沉 DDD 注解**（11 个）——让业务代码只写一个 `@DomainService`。
> **`@DomainEvent` 不下沉**——事件是数据载体，通过 `DomainEventPublisher` 发布，不需要注册 Bean。
> **其他抽象（auth/websocket/cache）由具体业务模块提供**——不通过 ddd4j-annotation 抽象。
> **CQRS 投影事件 / API 抽象**保留在 ddd4j-annotation——这些是框架无关的通用抽象。
> **Web 路由 / DI / 配置 / 事务用框架原生注解**——**不引入任何套壳**。
