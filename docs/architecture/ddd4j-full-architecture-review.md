# ddd4j 2.0.x 全栈架构审查报告（架构师视角）

> **审查对象**：`/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j`（v2.0.x，平铺模块，823 文件 / 13,671
> 节点 / 23,360 边 codegraph 索引，2026-07-01 同步）
> **对标项目**：
> - `workspace-bmgw/codeup/ddd4j`（v1.x，com.dddframework，Spring 强绑定，Java 8）
> - `workspace-bmgw/codeup/cloud-agents`（基于 v1.x 的智能体服务集群）
    > **审查目标**：确认 v2.x 能否完全替代 v1.x，并指导 v1.x → v2.x 的迁移适配
>
> **状态校准**：本文为历史全栈审查报告，当前模块事实以根 `README.md`、`REFACTOR_MIGRATION.md` 和
`docs/architecture/architecture.md` 为准。

---

## 0. 摘要：四象限定位

| 象限          | 模块                                                       | 状态             | 优先级       |
|-------------|----------------------------------------------------------|----------------|-----------|
| **🟢 扩展模块** | auth / cache / data / mq / kit / dependencies            | 已达人类架构师水准      | 保持        |
| **🟡 核心模块** | annotation / core / web / bom / parent                   | **架构问题集中在这一层** | 重点        |
| **🟠 集成模块** | ddd / extensions / guice / quarkus / spring              | 三框架覆盖完整但深度不足   | 完善        |
| **🔴 框架模块** | ddd4j-boot / ddd4j-javalin / ddd4j-quarkus / ddd4j-cloud | 集成功能欠缺         | **P0 重点** |

---

## 一、v2.x vs v1.x 能力对照（替代可行性）

### 1.1 整体能力对照

| 维度             | v1.x（`com.dddframework`）           | v2.x（`io.ddd4j`）                                                                                   | 替代可行性     |
|----------------|------------------------------------|----------------------------------------------------------------------------------------------------|-----------|
| **Java 版本**    | Java 8                             | Java 17 + Records + Sealed                                                                         | ✅ 完全替代    |
| **Spring 绑定**  | Spring 2.7.18 强绑定                  | 框架无关 + 三框架 SPI                                                                                     | ✅ 优于 v1.x |
| **聚合根模型**      | `Model`（充血模型，耦合 Repository）        | `Model` + `DddAggregateRoot`（双轨，纯净 DDD 来自 fuinorg）                                                 | ✅ 完全替代    |
| **仓储 SPI**     | `BaseRepository`（带 MyBatis 注入）     | `BaseRepository` + `Repository`（纯 Java SPI）                                                        | ✅ 完全替代    |
| **MQ 抽象**      | 4 种（Kafka/RabbitMQ/RocketMQ/Redis） | 13 个 Broker/本地实现（增加 ActiveMQ/MQTT/Pulsar/NATS/Disruptor/ONS/TDMQ/SQS/MicaMQTT 等）                   | ✅ 优于 v1.x |
| **Web 控制器**    | `AggregateController` 动态路由         | `BaseAggregateController` + `BaseClientAggregateController` 模板方法 + `AggregateController` 动态路由（双模式） | ✅ 完全替代    |
| **认证**         | 仅 BaseAuth 拦截器                     | Subject SPI + Sa-Token + Spring Security + Shiro                                                   | ✅ 优于 v1.x |
| **数据权限**       | 无                                  | `ddl4j-data-datascope`（基于 MyBatis 拦截器）                                                             | ✅ 优于 v1.x |
| **加密**         | 无                                  | `ddd4j-data-crypto`                                                                                | ✅ 优于 v1.x |
| **DDD 架构守护**   | 无                                  | `CleanDDDLayerRules` + `ColaDDDLayerRules`（ArchUnit 注解驱动）                                          | ✅ 优于 v1.x |
| **CQRS 读侧**    | 无                                  | `DddCommandExecutor` + `JpaView` + `ViewManager`（Spring/Quarkus 实现）                                | ✅ 优于 v1.x |
| **事件溯源**       | 无                                  | `DddAggregateRoot` + `DddEventStoreRepository`（基于 fuinorg esc-api）                                 | ✅ 优于 v1.x |
| **多容器支持**      | 仅 Spring                           | Spring + Quarkus + Guice + Javalin                                                                 | ✅ 优于 v1.x |
| **缓存**         | RedisKit                           | `ddd4j-cache`（Caffeine + Redis 多级）                                                                 | ✅ 优于 v1.x |
| **扩展点**        | 单一 Hutool                          | akka / excel / jackson / monitor / pf4j / qlexpress / validation                                   | ✅ 优于 v1.x |
| **示例工程**       | `ddd-demo`                         | `ddd4j-samples` × 5（auth 三实现、多登录、Person CQRS/ES）                                                   | 🟡 继续增强   |
| **Quarkus 集成** | 无                                  | 完整模块结构 + 适配器                                                                                       | ✅ 优于 v1.x |
| **Javalin 集成** | 无                                  | 完整模块结构 + 适配器                                                                                       | ✅ 优于 v1.x |
| **Cloud 生态**   | 无                                  | 待定（仅有 ddd4j-boot 一站式）                                                                              | ❌ 缺口      |

### 1.2 关键 API 兼容性

| v1.x 类                                                         | v2.x 对应类                                                      | 兼容性      |
|----------------------------------------------------------------|---------------------------------------------------------------|----------|
| `com.dddframework.core.contract.Model`                         | `io.ddd4j.core.contract.Model`                                | 🟡 需修改包名 |
| `com.dddframework.core.contract.Query`                         | `io.ddd4j.core.contract.Query`                                | 🟡 需修改包名 |
| `com.dddframework.core.contract.BaseRepository`                | `io.ddd4j.core.contract.BaseRepository`                       | 🟡 需修改包名 |
| `com.dddframework.core.contract.R`                             | `io.ddd4j.core.contract.R`                                    | 🟡 需修改包名 |
| `com.dddframework.core.contract.MQEvent`                       | `io.ddd4j.core.contract.MQEvent`                              | 🟡 需修改包名 |
| `com.dddframework.core.contract.DomainEvent`                   | `io.ddd4j.core.contract.DomainEvent`                          | 🟡 需修改包名 |
| `com.dddframework.core.context.ThreadContext`                  | `io.ddd4j.core.context.ThreadContext`                         | 🟡 需修改包名 |
| `com.dddframework.core.context.SpringContext`                  | `io.ddd4j.spring.context.SpringContext`                       | 🟡 路径变化  |
| `com.dddframework.web.core.GlobalRestExceptionAdvice`          | `io.ddd4j.web.webmvc.core.GlobalRestExceptionAdvice`          | 🟡 路径变化  |
| `com.dddframework.web.api.AggregateController`                 | `io.ddd4j.web.webmvc.api.AggregateController`                 | 🟡 路径变化  |
| `com.dddframework.web.controller.BaseAggregateController`      | `io.ddd4j.web.webmvc.controller.BaseAggregateController`      | 🟡 路径变化  |
| `com.dddframework.web.auth.annotation.BaseAuth`                | `io.ddd4j.annotation.auth.BaseAuth`                           | 🟡 路径变化  |
| `com.dddframework.web.auth.interceptor.BaseAuthWebInterceptor` | `io.ddd4j.web.webmvc.auth.interceptor.BaseAuthWebInterceptor` | 🟡 路径变化  |

**结论**：v2.x 在**功能上**完全替代 v1.x，但**包名**和**路径**全部变化（`com.dddframework` → `io.ddd4j`），需要批量改名 + 重新
import。

### 1.3 替代路径建议

**不要**暴力替换 v1.x 的 classpath，建议采用**渐进式迁移**：

1. **阶段一：接口对齐**（1-2 周）
    - 在 v2.x 中新增 `io.ddd4j.legacy.v1` 兼容包，类直接继承 v1.x 类并加 `@Deprecated`
    - 或者用 `git mv + sed` 批量改 `com.dddframework` → `io.ddd4j`

2. **阶段二：双轨运行**（2-4 周）
    - v1.x 与 v2.x 共存于 classpath
    - 新模块用 v2.x，老模块按业务边界逐步切换

3. **阶段三：清除 v1.x**（4-6 周）
    - 所有业务模块切换完成，删除 v1.x 依赖
    - 启用 v2.x 的新特性（DDD 架构守护、CQRS 读侧、13 个 MQ Broker/本地实现切换）

---

## 二、扩展模块（🟢 已达人类架构师水准）

### 2.1 评估结论

| 模块                   | 子模块数                                             | 状态    | 评估                                          |
|----------------------|--------------------------------------------------|-------|---------------------------------------------|
| `ddd4j-auth`         | 5（license/satoken/security/shiro/spring）         | 🟢 优秀 | 多种认证方案并存，按需选型，Subject 契约位于 ddd4j-core       |
| `ddd4j-cache`        | 1                                                | 🟢 优秀 | Caffeine + Redis 多级缓存策略完整                   |
| `ddd4j-data`         | 6（crypto/datascope/external/logs/mybatis/spring） | 🟢 优秀 | 数据层完整抽象，MyBatis 插件化可扩展                      |
| `ddd4j-mq`           | 15（core/spring + 13 个 Broker/本地实现）               | 🟢 优秀 | **核心亮点**，MQBrokerAdapter SPI 让 MQ 切换零业务代码改动 |
| `ddd4j-kit`          | 1                                                | 🟢 优秀 | Hutool 继承式增强，不重复造轮子                         |
| `ddd4j-dependencies` | 1                                                | 🟢 优秀 | 第三方版本集中管理，已锁定 Spring 6.x                    |

### 2.2 人工调优点提炼

**`ddd4j-mq` 的 13 个 Broker/本地实现适配**——这是 ddd4j 区别于同类脚手架的**核心差异点**：

```
MQBrokerAdapter SPI（ddd4j-mq-core）
├── KafkaMQBrokerAdapter        (kafka)
├── RabbitMQBrokerAdapter       (rabbitmq)
├── RocketMQBrokerAdapter       (rocketmq)
├── OnsMQBrokerAdapter          (ons)  // 阿里云 ONS
├── ActiveMQBrokerAdapter       (activemq)
├── RedisStreamMQBrokerAdapter  (redis-stream)
├── MqttMQBrokerAdapter         (mqtt)
├── MicaMqttMQBrokerAdapter     (mqtt-mica)  // 国产 MQTT
├── PulsarMQBrokerAdapter       (pulsar)
├── NatsMQBrokerAdapter         (nats)
├── DisruptorMQBrokerAdapter    (disruptor)  // 进程内高性能
├── SqsMQBrokerAdapter          (sqs)        // AWS SQS
└── TdmqMQBrokerAdapter         (tdmq)       // 腾讯 TDMQ
```

**`ddd4j-auth` 的多方案 SPI**：

- 业务侧只需切换 starter，无需修改业务代码
- `sa-token` 国产首选 + `spring-security` 国际标准 + `shiro` 老项目兼容 + `oauth2` 微服务

### 2.3 微调建议

1. **`ddd4j-cache`**：建议增加 `CacheKeyGenerator` SPI（不同业务可自定义 key 拼接规则）
2. **`ddd4j-mq`**：建议增加 `MQMessageConverter` SPI（支持 Protobuf / Avro 序列化）
3. **`ddd4j-data-external`**：建议抽象 `ExternalApiClient` 统一防腐层（当前依赖 Feign）

---

## 三、核心模块（🟡 重点审查）

### 3.1 整体评估

| 模块                 | 当前状态  | 主要问题                                                                 |
|--------------------|-------|----------------------------------------------------------------------|
| `ddd4j-annotation` | 🟠 中等 | 5 个 DDD 注解仍 `extends @Service/@Repository/@Component`，**强耦合 Spring** |
| `ddd4j-core`       | 🟠 中等 | MyBatis-Plus 污染 7 个文件；Servlet 污染 1 个；依赖过重                            |
| `ddd4j-web`        | 🟢 良好 | 双栈支持完整（WebMVC + WebFlux），但缺 Quarkus/Javalin 实现                       |
| `ddd4j-bom`        | 🟢 优秀 | 版本集中管理清晰                                                             |
| `ddd4j-parent`     | 🟢 优秀 | Maven 父 POM 配置完整                                                     |

### 3.2 关键问题：框架耦合泄漏

**问题 1：`ddd4j-annotation` 仍依赖 `spring-context`**

```java
// 当前实现
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Service                              // ← Spring 元注解
public @interface DomainService { }

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repository                           // ← Spring 元注解
public @interface DomainRepository { }

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Service                              // ← Spring 元注解
public @interface ApplicationService { }
```

**影响**：Javalin/Quarkus/Guice 项目引入 `ddd4j-annotation` 时被迫拉入整个 Spring Context，违反"框架无关"声明。

**问题 2：`ddd4j-core` 内部 MyBatis-Plus 污染**

| 文件                                      | 耦合内容                                     |
|-----------------------------------------|------------------------------------------|
| `entity/BaseEntity.java`                | `@TableField`, `@TableLogic`, `Model<T>` |
| `entity/PaginationEntity.java`          | `@TableField`, `OrderItem`, `Model<T>`   |
| `service/IBaseService.java`             | `IService<T>`, `Page`, `Model<T>`        |
| `web/Result.java`                       | `Page` (MyBatis-Plus)                    |
| `dto/AbstractOrderedPaginationDTO.java` | `OrderItem`                              |
| `param/BasePaginationQueryParam.java`   | `OrderItem`                              |
| `exception/BaseExceptionHandler.java`   | `jakarta.servlet`                        |

**问题 3：`ddd4j-core/pom.xml` 依赖过重**

| 依赖                    | 必要性          | 处理                      |
|-----------------------|--------------|-------------------------|
| `mybatis-plus`        | ❌ 污染源        | 移到 `ddd4j-data-mybatis` |
| `jakarta.servlet-api` | ❌ 污染源        | 移到 `ddd4j-web-core`     |
| `hibernate-validator` | ❌ 非核心        | 移除                      |
| `hutool-all`          | ⚠️ 过重        | 用 hutool-core 替换        |
| `bouncycastle`        | ❌ 加密无关       | 移除                      |
| `fastjson2`           | ❌ Jackson 已在 | 移除                      |
| `dozer`               | ❌ 对象映射       | 移除                      |
| `swagger-annotations` | ❌ API 文档     | 移除                      |
| `aspectj`             | ❌ AOP        | 移除                      |
| `caffeine`            | ❌ 缓存         | 移除（已在 ddd4j-cache）      |

### 3.3 重构建议

**目标架构**：

```
ddd4j-annotation-api（纯 Java，零 Spring 依赖）   ← 新增
  @DomainEntity / @DomainService / @ApplicationService / @DomainRepository
  ↓ 被 ddd4j-annotation 重新实现
ddd4j-annotation（向后兼容，仍 @Service 等元注解，但注释中说明）
  ↓
  ┌──── 业务项目按需选择 ────┐
  ddd4j-runtime-spring 提供 @SpringDomainService
  ddd4j-runtime-quarkus 提供 Quarkus DDD 构造型注解与适配实现
```

**`ddd4j-core` 拆分**：

```
ddd4j-core-api（纯 Java 契约，零外部依赖）   ← 新增
  ├── contract/ (Model/Query/Page/R/IR/BaseRepository/DomainEvent/DomainEventPublisher/MQEvent/MQEventPublisher)
  ├── context/ (ThreadContext/BaseContext/I18nProvider/SubjectProvider)
  ├── ddd/ (DddAggregateRoot/DddDomainEvent/DddEventStoreRepository)
  ├── cqrs/ (Command/CommandExecutor/View/ViewManager/ViewScheduler/JpaView)
  ├── subject/ (AuthPrincipal/Subject)
  ├── exception/ (ServiceException/ValidateException)
  └── enums/ (ResultCode/IEnum)

ddd4j-core（向后兼容，保留 entity/service/web/dto/param 子包）   ← 标记 @Deprecated
  ↓ 业务项目迁移后删除
```

### 3.4 优先级

| 优先级    | 任务                            | 影响面                   |
|--------|-------------------------------|-----------------------|
| **P0** | `ddd4j-annotation` 解耦 Spring  | 阻塞 Javalin/Quarkus 落地 |
| **P0** | 7 个 MyBatis-Plus 文件迁出 core    | 阻塞跨框架                 |
| **P1** | `ddd4j-core/pom.xml` 瘦身       | 减少传递依赖                |
| **P1** | 拆分 `ddd4j-core-api` 纯 Java 模块 | 明确架构边界                |
| **P2** | 新增 ArchUnit 自检规则              | 防止再次污染                |

---

## 四、集成模块（🟠 三框架覆盖完整但深度不足）

### 4.1 整体评估

| 模块                      | 子模块                                                  | 状态    | 评估                                                             |
|-------------------------|------------------------------------------------------|-------|----------------------------------------------------------------|
| `ddd4j-ddd-rules`       | clean / cola                                         | 🟢 良好 | ArchUnit 注解驱动规则，业务项目可继承                                        |
| `ddd4j-extensions`      | akka/excel/jackson/monitor/pf4j/qlexpress/validation | 🟢 优秀 | 7 个扩展点，可按需引入                                                   |
| `ddd4j-runtime-guice`   | 1                                                    | 🟠 中等 | Guice Module + EventBus 实现，Javalin Web 能力在 `ddd4j-web-javalin` |
| `ddd4j-runtime-quarkus` | 1                                                    | 🟡 中等 | Quarkus CDI/CQRS 核心适配已存在，Web 能力在 `ddd4j-web-quarkus`           |
| `ddd4j-runtime-spring`  | 1                                                    | 🟡 中等 | Spring 适配器实现最完整，但与 ddd4j-web-webmvc 有部分重叠                      |

### 4.2 问题：集成模块"广而不深"

`ddd4j` 内部不再追求复制一套 `ddd4j-quarkus-*` 或 `ddd4j-javalin-*` 全家桶；当前约定是平铺公共能力：

- Quarkus 通用 CDI/CQRS/EventStore 适配位于 `ddd4j-runtime-quarkus`
- Quarkus Web 适配位于 `ddd4j-web/ddd4j-web-quarkus`
- Javalin 复用 Guice DI 能力，核心适配位于 `ddd4j-runtime-guice`
- Javalin Web 适配位于 `ddd4j-web/ddd4j-web-javalin`
- 具体框架专属自动装配、starter、样例放到外部 `ddd4j-quarkus` / `ddd4j-javalin`

### 4.3 改进建议

1. **保持平铺模块**：公共能力保留在 `ddd4j-*` 与各 feature 聚合下，不复制 Boot 式嵌套结构
2. **明确 Quarkus/Javalin 分工**：`ddd4j-runtime-quarkus` / `ddd4j-runtime-guice` 做核心 SPI，`ddd4j-web-*` 做 Web 适配
3. **继续收口 Spring/Web 边界**：`ddd4j-runtime-spring` 做容器 SPI，`ddd4j-web-webmvc` / `ddd4j-web-webflux` 做 Web 能力

---

## 五、框架模块（🔴 集成功能欠缺——P0 重点）

### 5.1 当前矩阵

| 集成项目                             | 模块数  | 成熟度    | 主要差距                                   |
|----------------------------------|------|--------|----------------------------------------|
| `ddd4j-boot` (Spring Boot 3.5.x) | ~20+ | 🟢 85% | 模块齐全，缺 Cloud 生态（Gateway/Config/Stream） |
| `ddd4j-quarkus`                  | 外部项目 | 待单独核实  | 当前文档不再用本仓 CodeGraph 结果推断外部仓完成度         |
| `ddd4j-javalin`                  | 外部项目 | 待单独核实  | 当前文档不再用本仓 CodeGraph 结果推断外部仓完成度         |
| `ddd4j-cloud`                    | 0    | ❌ 0%   | **完全缺失**                               |

### 5.2 ddd4j-boot（最成熟）

**已实现**：

- ddd4j-boot-bom / dependencies / parent / data / cache / auth / ddd
- ddd4j-boot-data-{crypto,datascope,external,logs,mybatis}
- ddd4j-boot-auth-{satoken,security,shiro}
- ddd4j-boot-cache / ddd4j-boot-extensions / ddd4j-boot-mq
- ddd4j-boot-samples × 16

**待补齐**：

- ❌ `ddd4j-boot-cloud-gateway`（Spring Cloud Gateway 封装）
- ❌ `ddd4j-boot-cloud-config`（Nacos/Apollo 配置中心集成）
- ❌ `ddd4j-boot-cloud-stream`（消息流）
- ❌ `ddd4j-boot-cloud-sleuth`（链路追踪）
- ❌ `ddd4j-boot-xxljob`（XXL-JOB 分布式任务）
- ❌ `ddd4j-boot-redis`（Redis 客户端封装）

### 5.3 ddd4j-quarkus

外部 `ddd4j-quarkus` 的模块成熟度需要在该仓单独用 CodeGraph 复核。本仓当前只确认：

- `ddd4j-runtime-quarkus` 提供通用 CDI/CQRS/EventStore 适配
- `ddd4j-web-quarkus` 提供 Quarkus Web 通用适配
- starter、自动装配、Quarkus 专属样例不应回流到 `ddd4j`

### 5.4 ddd4j-javalin

外部 `ddd4j-javalin` 的模块成熟度需要在该仓单独用 CodeGraph 复核。本仓当前只确认：

- `ddd4j-runtime-guice` 提供 Guice DI/EventBus 适配
- `ddd4j-web-javalin` 提供 Javalin Web 通用适配
- Javalin 专属 starter、路由装配、样例不应回流到 `ddd4j`

### 5.5 ddd4j-cloud

**完全缺失**。`workspace-bmgw/codeup/ddd4j-cloud/` 目录不存在或未索引。**这是 v1.x → v2.x 替代的关键缺口**。

### 5.6 优先级

| 优先级    | 任务                                        | 业务影响               |
|--------|-------------------------------------------|--------------------|
| **P0** | 补齐 `ddd4j-cloud` 微服务治理模块                  | 阻塞 v1.x 大型项目替代     |
| **P0** | 补齐 `ddd4j-javalin` 核心模块                   | 阻塞 Javalin 用户      |
| **P1** | 外部 `ddd4j-quarkus` 补强 Web/MQ/Auth 自动装配与示例 | 提升 Quarkus 集成度     |
| **P1** | `ddd4j-boot-cloud-*` Spring Cloud 集成      | 完善 Spring Cloud 生态 |
| **P2** | ddd4j-boot-xxljob / ddd4j-boot-redis      | 业务侧常用组件            |

---

## 六、cloud-agents 反向发现：迁移适配障碍点

> cloud-agents 是基于 v1.x ddd4j 的智能体服务集群，**293 个 Java 文件**，
> 通过分析其对 v1.x 的使用模式，可以发现 v2.x 的改进空间。

### 6.1 cloud-agents 的依赖画像

```
com.qushiyun（业务集团）
├── cloud-agents-bom / dependencies（版本管理）
├── cloud-agents-common（公共组件）
├── cloud-agents-pay（支付服务）
├── cloud-agents-aigc（智能体核心：app-core / domain / adapter / app）
├── cloud-agents-agent（智能体服务）
├── cloud-agents-admin（管理后台）
└── cloud-agents-server-{api,opencode,hermes,openclaw,aigc}（多个微服务）
```

**多模块结构特征**：

- 业务模块 5 层（common / pay / aigc / agent / admin / server）
- AIGC 模块细分 app-core / domain / adapter / app（COLA 风格）
- server 下多个微服务（hermes / openclaw / opencode / aigc）

### 6.2 对 v1.x ddd4j 的实际使用点（10 个核心文件）

| cloud-agents 文件                           | 使用的 v1.x 能力                          | v2.x 替代方案                                           |
|-------------------------------------------|--------------------------------------|-----------------------------------------------------|
| `HermesInvokeMqEventConsumer.java`        | `MQEventListener` 注解 + `MQEvent`     | ✅ 直接替代（包名变化）                                        |
| `OpenClawInvokeMqEventConsumer.java`      | 同上                                   | ✅ 直接替代                                              |
| `AigcTextCallbackPersistCoordinator.java` | `ApplicationContext` + `DomainEvent` | ✅ 替代为 `SpringContext`                               |
| `AigcCallbackMqProducer.java`             | `MQEvent.publish()`                  | ✅ 直接替代                                              |
| `AigcSubtask.java`                        | 充血模型（继承 `Model`）                     | ✅ 替代为 v2.x `Model`                                  |
| `AigcTask.java`                           | 同上                                   | ✅ 直接替代                                              |
| `AgentInvokeMqEvent.java`                 | `MQEvent`                            | ✅ 直接替代                                              |
| `AgentMqDefaults.java`                    | MQ 主题配置                              | ✅ 替代为 `Ddd4jMQProperties`                           |
| `AigcTaskProgressMqEvent.java`            | `MQEvent`                            | ✅ 直接替代                                              |
| `AigcSseApiController.java`               | Spring MVC Controller                | ✅ 替代为 `@RestController` + `BaseAggregateController` |

### 6.3 迁移障碍点识别

#### 障碍 1：包名全量替换

**问题**：cloud-agents 10 个核心文件全部引用 `com.dddframework`，v2.x 包名为 `io.ddd4j`。

**影响面**：每个使用 ddd4j 的业务类都需要改 import + 完全限定名（`com.dddframework.core.contract.Model` →
`io.ddd4j.core.contract.Model`）。

**v2.x 改进建议**：

- 在 v2.x 中提供 `io.ddd4j.legacy.v1` 兼容包，**类直接继承 v1.x 类**
- 或者用 `git mv` + `sed -i 's/com\.ddddframework/io.ddd4j/g'` 批量改

#### 障碍 2：Maven 坐标变化

**问题**：v1.x `groupId=com.dddframework`，v2.x `groupId=io.ddd4j`，artifacts 全部变化（`base-core` → `ddd4j-core`）。

**改进建议**：在 v2.x 提供 migration 脚本（`mvn dependency:tree` 对比 + 自动化替换）。

#### 障碍 3：AIGC 领域模型需要重新审视

**问题**：`AigcSubtask` 和 `AigcTask` 是充血模型（继承 v1.x `Model`），v2.x 的 `Model` 已经重构为充血模型但耦合度降低。

**v2.x 改进建议**：

- v2.x `Model` 已经实现充血模型（`save()` / `update()` / `saveOrUpdate()`）
- 但 cloud-agents 的充血模型可能用到了一些 v1.x `Model` 的私有方法，需要审计

#### 障碍 4：MQ 消费者注册方式

**问题**：`@MQEventListener` 注解使用方式是否变化？

**v2.x 现状**：

- `ddd4j-mq-core` 定义 `@MQEventListener`（与 v1.x 兼容）
- `ddd4j-mq-spring` 的 `MQListenerRegistrar` 自动扫描并注册消费者
- 业务侧只需注解 + 监听方法，零业务代码改动

**结论**：✅ 升级路径顺畅。

#### 障碍 5：Spring Cloud 生态缺失

**问题**：cloud-agents-server-* 是 Spring Cloud 微服务，依赖：

- Spring Cloud Gateway
- Spring Cloud OpenFeign
- Spring Cloud Alibaba Nacos
- Spring Cloud Stream
- Spring Cloud Sleuth

**v2.x 现状**：`ddd4j-boot-cloud-*` 完全缺失。

**严重度**：🔴 **P0 阻塞**。v2.x 现阶段**无法直接替代 v1.x + Spring Cloud 的组合**。

#### 障碍 6：XXL-JOB 等国产组件集成

**问题**：cloud-agents pom 中已使用 `spring-boot-starter-xxljob`（2.7.x）。

**v2.x 现状**：无 XXL-JOB 集成模块。

**改进建议**：新增 `ddd4j-boot-xxljob`（分布式任务调度封装）。

### 6.4 迁移优先级建议

| 优先级    | 任务                          | 工作量   | 业务影响           |
|--------|-----------------------------|-------|----------------|
| **P0** | 创建 `io.ddd4j.legacy.v1` 兼容包 | 1 周   | 兼容老代码，渐进式迁移    |
| **P0** | 创建 `ddd4j-cloud-*` 5 个模块    | 4-6 周 | 阻塞大型项目替代       |
| **P0** | 补齐 `ddd4j-javalin` 核心 5 个模块 | 3-4 周 | 阻塞 Javalin 用户  |
| **P1** | 创建 `ddd4j-boot-xxljob`      | 1 周   | 业务侧常用          |
| **P1** | 创建 `ddd4j-boot-redis`       | 1 周   | 业务侧常用          |
| **P1** | 提供 `v1-to-v2` 迁移脚本          | 1 周   | 自动化批量替换        |
| **P2** | 补齐 `ddd4j-quarkus` 子模块实现    | 2-3 周 | 提升 Quarkus 集成度 |

---

## 七、ddd4j-cloud 模块设计建议

> v2.x 替代 v1.x 的**关键缺口**。建议优先建设。

### 7.1 推荐的 ddd4j-cloud 模块结构

```
ddd4j-cloud/                                  ← 顶层聚合
├── ddd4j-cloud-dependencies                  ← 依赖管理
├── ddd4j-cloud-parent                        ← 父 POM
├── ddd4j-cloud-bom                           ← BOM
│
├── ddd4j-cloud-core                          ← 通用微服务抽象
│   ├── 服务注册发现 SPI（注册中心可插拔：Nacos/Eureka/Consul）
│   ├── 配置中心 SPI（Nacos/Apollo/Config Server）
│   ├── 分布式锁 SPI（Redisson/Zookeeper）
│   ├── 分布式 ID SPI（雪花/Leaf/UUID）
│   ├── 灰度/路由 SPI
│   └── 限流熔断 SPI（Sentinel/Resilience4j）
│
├── ddd4j-cloud-nacos                         ← Nacos 实现
│   ├── 服务注册发现
│   ├── 配置中心
│   └── 命名空间管理
│
├── ddd4j-cloud-eureka                        ← Eureka 实现
│
├── ddd4j-cloud-gateway                       ← API 网关
│   ├── 路由配置
│   ├── 过滤器链
│   ├── 限流
│   └── 鉴权（与 ddd4j-auth 集成）
│
├── ddd4j-cloud-sentinel                      ← 流量治理
│
├── ddd4j-cloud-seata                          ← 分布式事务
│
├── ddd4j-cloud-stream                        ← 消息流（Kafka/RocketMQ Binder）
│
├── ddd4j-cloud-sleuth                        ← 链路追踪（Micrometer Tracing + Zipkin）
│
├── ddd4j-cloud-xxljob                        ← 分布式任务
│
├── ddd4j-cloud-redis                         ← Redis 客户端
│
└── ddd4j-cloud-samples                       ← 示例
```

### 7.2 优先级路线图

| 季度     | 任务                                                                      |
|--------|-------------------------------------------------------------------------|
| **Q1** | ddd4j-cloud-core（SPI 定义）+ ddd4j-cloud-nacos（首选实现） + ddd4j-cloud-gateway |
| **Q2** | ddd4j-cloud-sentinel + ddd4j-cloud-seata + ddd4j-cloud-stream           |
| **Q3** | ddd4j-cloud-sleuth + ddd4j-cloud-xxljob + ddd4j-cloud-redis             |
| **Q4** | ddd4j-cloud-samples × 10 + 文档 + 培训                                      |

---

## 八、最终架构定位（更新版）

```
┌─────────────────────────────────────────────────────────────────────┐
│                  业务应用层（用户项目）                              │
│  cloud-agents / 各业务服务                                          │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        ▼                      ▼                      ▼
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│   ddd4j-boot     │  │   ddd4j-quarkus  │  │   ddd4j-javalin  │
│ (Spring Boot 3)  │  │   (Quarkus)      │  │   (Javalin)      │
└────────┬─────────┘  └────────┬─────────┘  └────────┬─────────┘
         │                     │                     │
         └─────────────────────┼─────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                  ddd4j 框架集成层（这次需要大力补齐）                │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌────────────┐   │
│  │ ddd4j-cloud │ │ ddd4j-boot  │ │ ddd4j-quarkus│ │ddd4j-javalin│   │
│  │ Gateway/Cfg │ │ -cloud-*    │ │ -web/mq/auth │ │ -core/web   │   │
│  │ Seata/Sleuth│ │ 微服务治理   │ │ 适配器      │ │ 适配器      │   │
│  └─────────────┘ └─────────────┘ └─────────────┘ └────────────┘   │
└──────────────────────────────┬──────────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                  ddd4j 通用基础层                                    │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐      │
│  │ core-api   │ │ core       │ │ ddd        │ │ data       │      │
│  │ (纯Java契约)│ │ (向后兼容) │ │ (ES+CQRS)  │ │ (MyBatis)  │      │
│  └────────────┘ └────────────┘ └────────────┘ └────────────┘      │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐      │
│  │ mq (12 种) │ │ web        │ │ auth       │ │ cache      │      │
│  │ 统一抽象   │ │ 双栈        │ │ 多方案      │ │ 多级        │      │
│  └────────────┘ └────────────┘ └────────────┘ └────────────┘      │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐                      │
│  │ runtime-spring │ │ runtime-quarkus │ │ runtime-guice │            │
│  │ 三框架运行时绑定│ │                 │ │               │            │
│  └────────────┘ └────────────┘ └────────────┘                      │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        ▼                      ▼                      ▼
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│  ddd-4-java      │  │  cqrs-4-java     │  │  esc-api         │
│  (fuinorg)       │  │  (fuinorg)       │  │  (fuinorg)       │
└──────────────────┘  └──────────────────┘  └──────────────────┘
```

---

## 九、总结：v2.x 替代 v1.x 的 6 大关键行动

1. **🔴 P0-1**：补齐 `ddd4j-cloud` 5 个核心模块（gateway/config/sentinel/sleuth/xxljob）
2. **🔴 P0-2**：补齐 `ddd4j-javalin` 核心 5 个模块（core/web/mq/auth/ddd）
3. **🔴 P0-3**：`ddd4j-annotation` 解耦 Spring，5 个注解不再 `extends @Service/@Repository`
4. **🔴 P0-4**：`ddd4j-core` 7 个 MyBatis-Plus/Servlet 污染文件迁出
5. **🟠 P1-1**：拆分 `ddd4j-core-api` 纯 Java 契约模块
6. **🟠 P1-2**：提供 `io.ddd4j.legacy.v1` 兼容包 + v1→v2 迁移脚本

**完成以上 6 项后，v2.x 可以完全替代 v1.x**：

- 功能上：✅ 已覆盖
- 性能上：✅ 优于 v1.x（Java 17 vs Java 8）
- 可维护性上：✅ 优于 v1.x（DDD 架构守护 + CQRS 读侧 + 12 种 MQ）
- 框架灵活性上：✅ 大幅优于 v1.x（支持 Spring/Quarkus/Javalin 三框架）
