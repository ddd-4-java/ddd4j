# DDD4J 架构深度分析与 Quarkus 适配评估报告

> 基于 codegraph 对 ddd4j 2.0.x (719 files, 12,093 nodes) 和 ddd4j-quarkus 3.3.x 的深度探索

## 一、DDD4J 2.0.x 核心架构

### 1.1 整体分层

```
ddd4j (通用基础层)
├── ddd4j-annotation      ← DDD 注解层（零框架依赖）
├── ddd4j-core            ← 核心契约层（纯 Java SPI）
├── ddd4j-ddd             ← ArchUnit 架构守护（Clean/COLA 规则）
├── ddd4j-kit             ← 工具箱
├── ddd4j-data            ← 数据层 SPI
├── ddd4j-mq              ← 消息队列 SPI
├── ddd4j-web             ← Web 层 SPI
├── ddd4j-auth            ← 认证授权 SPI
├── ddd4j-cache           ← 缓存 SPI
├── ddd4j-spring          ← Spring 框架适配
├── ddd4j-guice           ← Guice 框架适配
├── ddd4j-quarkus         ← Quarkus 框架适配（当前评估对象）
└── ddd4j-extensions      ← 扩展模块
```

### 1.2 核心契约层 (ddd4j-core) 关键接口

| 接口/类 | 包路径 | 职责 |
|---------|--------|------|
| `DddAggregateRoot<ID>` | `io.ddd4j.core.ddd.aggregate` | 聚合根基类（继承 fuinorg AbstractAggregateRoot） |
| `DddDomainEvent<ID>` | `io.ddd4j.core.ddd.event` | 领域事件基类 |
| `DddCommandExecutor<CMD>` | `io.ddd4j.core.ddd.command` | 命令执行器基类 |
| `DddView` | `io.ddd4j.core.ddd.query` | 查询视图基类 |
| `ViewManager` | `io.ddd4j.core.cqrs.projection` | 视图管理器 SPI（纯 Java） |
| `ProjectionPosition` | `io.ddd4j.core.cqrs.projection` | 投影位置 SPI |
| `ProjectionPositionRepository` | `io.ddd4j.core.cqrs.projection` | 投影位置仓储 SPI |
| `DomainEventPublisher` | `io.ddd4j.core.contract` | 领域事件发布者 SPI |
| `BaseRepository` | `io.ddd4j.core.contract` | 基础仓储接口 |
| `Repository` | `io.ddd4j.core.contract` | 纯 Java 仓储接口 |
| `SubjectProvider` | `io.ddd4j.core.subject` | 认证主体提供者 SPI |
| `I18nProvider` | `io.ddd4j.core.context` | 国际化提供者 SPI |

### 1.3 三框架 SPI 适配模式

ddd4j-core 定义纯 Java SPI 接口，由三框架各自实现：

```
DomainEventPublisher (ddd4j-core)
├── SpringDomainEventPublisher (ddd4j-spring)  → ApplicationEventPublisher
├── CdiDomainEventPublisher (ddd4j-quarkus)    → CDI Event<DomainEvent>
└── GuiceDomainEventPublisher (ddd4j-guice)    → EventBus

ViewManager (ddd4j-core)
├── SpringJpaViewManager (ddd4j-spring)
├── QuarkusJpaViewManager (ddd4j-quarkus)      ← 需实现
└── JavalinViewManager (ddd4j-javalin)

SubjectProvider (ddd4j-core)
├── SpringSubjectProvider (ddd4j-spring)
├── CdiSubjectProvider (ddd4j-quarkus)
└── GuiceSubjectProvider (ddd4j-guice)
```

---

## 二、DDD4J-Quarkus 3.3.x 现状分析

### 2.1 模块清单

```
ddd4j-quarkus/
├── ddd4j-quarkus               ← DDD 注解 + CDI 元注解 + 核心 Quarkus 适配
├── ddd4j-quarkus-core          ← 核心适配（CDI 扩展）
├── ddd4j-quarkus-ddd           ← DDD 规则
├── ddd4j-quarkus-data          ← 数据层（含 Panache）
├── ddd4j-quarkus-mq            ← 消息队列
├── ddd4j-quarkus-web           ← Web 层
├── ddd4j-quarkus-auth          ← 认证授权
├── ddd4j-quarkus-cache         ← 缓存
├── ddd4j-quarkus-monitor       ← 监控
├── ddd4j-quarkus-bom           ← BOM
├── ddd4j-quarkus-dependencies  ← 依赖管理
├── ddd4j-quarkus-parent        ← 父 POM
└── ddd4j-quarkus-samples       ← 示例
```

### 2.2 已实现的核心适配

| 适配类 | 状态 | 说明 |
|--------|------|------|
| `DddCdiExtension` | ✅ 已实现 | CDI 扩展：扫描 DDD 注解 → 自动添加 @ApplicationScoped |
| `CdiDomainEventPublisher` | ✅ 已实现 | CDI Event 实现领域事件发布 |
| `QuarkusEventHandlerRegistry` | ✅ 已实现 | CQRS 事件处理器注册（@CreateEvent/@UpdateEvent/@DeleteEvent） |
| `CdiSubjectProvider` | ✅ 已实现 | CDI 注入 Subject 实现 |
| `DddInitializer` | ✅ 已实现 | 启动时注册 I18nProvider 和 SubjectProvider |
| `DddResultAdapter` | ✅ 已实现 | Quarkus REST 响应适配 |

### 2.3 DddCdiExtension 详细分析

```java
// 当前实现：扫描 5 个 DDD 注解 → 添加 @ApplicationScoped
public class DddCdiExtension implements Extension {
    <T> void processAnnotatedType(@Observes ProcessAnnotatedType<T> event) {
        // 扫描 @ApplicationService, @DomainService, @DomainRepository,
        //      @DomainAssembler, @DomainConverter
        // → 添加 @ApplicationScoped
    }
}
```

**注意**：该扩展位于 `ddd4j-quarkus-core`，依赖 `io.ddd4j.annotation.*`（来自 ddd4j 通用层）。

### 2.4 CdiDomainEventPublisher 详细分析

```java
@ApplicationScoped
public class CdiDomainEventPublisher implements DomainEventPublisher {
    @Inject
    Event<DomainEvent> eventBus;

    @Override
    public void publish(DomainEvent event) {
        eventBus.fire(event);  // CDI 事件机制
    }
}
```

**注意**：调用了 `event.getEventType()` 和 `event.getAggregateId()`，这些方法在 `DomainEvent` 接口中需要确认是否存在。

---

## 三、DDD4J-Quarkus 与 DDD4J 2.0.x 适配差距分析

### 3.1 已完成的适配（✅）

| 能力 | ddd4j-core SPI | ddd4j-quarkus 实现 | 状态 |
|------|---------------|-------------------|------|
| DDD 注解扫描 | `@DDDAnnotation` | `DddCdiExtension` | ✅ |
| 领域事件发布 | `DomainEventPublisher` | `CdiDomainEventPublisher` | ✅ |
| CQRS 事件处理 | `@CreateEvent/@UpdateEvent/@DeleteEvent` | `QuarkusEventHandlerRegistry` | ✅ |
| 认证主体 | `SubjectProvider` | `CdiSubjectProvider` | ✅ |
| 国际化 | `I18nProvider` | `CdiI18nProvider`（推测） | ✅ |
| 应用初始化 | 无 | `DddInitializer` | ✅ |

### 3.2 待实现的适配（❌）

| 能力 | ddd4j-core SPI | ddd4j-quarkus 需实现 | 优先级 |
|------|---------------|---------------------|--------|
| **视图管理器** | `ViewManager` | `QuarkusJpaViewManager` | **P0** |
| **投影位置仓储** | `ProjectionPositionRepository` | `QuarkusProjectionPositionRepository` | **P0** |
| **投影位置实体** | `ProjectionPosition` | `QuarkusProjectionPosition` | **P0** |
| **JPA 视图** | `DddView` / `JpaView` | `QuarkusJpaView` | **P0** |
| **EventStore 集成** | `EventStore` | Quarkus EventStore 配置 | **P1** |
| **命令总线** | `DddCommandExecutor` | CDI 命令路由 | **P1** |
| **聚合根仓储** | `DddEventStoreRepository` | Quarkus 仓储适配 | **P1** |
| **Web 控制器模板** | `BaseAggregateController` | Quarkus REST 控制器模板 | **P2** |

### 3.3 架构边界问题

#### 问题 1：DddCdiExtension 扫描范围不完整

当前只扫描 5 个注解，缺少：
- `@DomainEntity` → 应注册为 CDI Bean
- `@DomainValueObject` → 应注册为 CDI Bean  
- `@DomainGateway` → 应注册为 CDI Bean（防腐层）
- `@QueryService` → 应注册为 CDI Bean
- `@CommandExecutor` → 应注册为 CDI Bean

#### 问题 2：CdiDomainEventPublisher 调用了不存在的方法

```java
// 当前代码
logger.debugf("Publishing domain event: %s, aggregateId: %s", 
    event.getEventType(), event.getAggregateId());
```

需要确认 `DomainEvent` 接口是否定义了 `getEventType()` 和 `getAggregateId()` 方法。

#### 问题 3：缺少 CQRS 读侧完整实现

ddd4j-core 定义了完整的 CQRS 读侧 SPI：
- `ViewManager` — 视图生命周期管理
- `ProjectionPosition` — 投影位置追踪
- `ProjectionPositionRepository` — 投影位置持久化
- `DddView` / `JpaView` — 视图基类

ddd4j-quarkus **完全没有实现**这些 SPI，导致 CQRS 读侧无法在 Quarkus 中使用。

#### 问题 4：缺少 EventStore 集成

ddd4j-core 依赖 fuinorg `esc-api`（EventStore 抽象），但 ddd4j-quarkus 没有提供：
- EventStore 的 Quarkus 自动配置
- EventStore 连接工厂
- EventStore 健康检查

---

## 四、DDD4J-Quarkus 全面适配 DDD4J 2.0.x 路线图

### Phase 1：补全 DDD 注解扫描（1-2 天）

**文件**：`ddd4j-quarkus-core/src/main/java/io/ddd4j/quarkus/core/extension/DddCdiExtension.java`

**改动**：
```java
// 增加扫描的注解
if (type.isAnnotationPresent(DomainEntity.class)) { ... }
if (type.isAnnotationPresent(DomainValueObject.class)) { ... }
if (type.isAnnotationPresent(DomainGateway.class)) { ... }
if (type.isAnnotationPresent(QueryService.class)) { ... }
if (type.isAnnotationPresent(CommandExecutor.class)) { ... }
```

### Phase 2：实现 CQRS 读侧（3-5 天）

**新增文件**：

1. `ddd4j-quarkus-core/src/main/java/io/ddd4j/quarkus/cqrs/projection/QuarkusJpaViewManager.java`
   - 实现 `ViewManager` 接口
   - 使用 Quarkus `@Scheduled` 或 `ScheduledExecutorService` 调度视图更新
   - 注入 `EventStore`、`EntityManager`、`ProjectionPositionRepository`

2. `ddd4j-quarkus-core/src/main/java/io/ddd4j/quarkus/cqrs/projection/QuarkusProjectionPosition.java`
   - 实现 `ProjectionPosition` 接口
   - 使用 Panache 或 JPA 实体持久化

3. `ddd4j-quarkus-core/src/main/java/io/ddd4j/quarkus/cqrs/projection/QuarkusProjectionPositionRepository.java`
   - 实现 `ProjectionPositionRepository` 接口
   - 使用 Panache Repository 或 JPA Repository

4. `ddd4j-quarkus-core/src/main/java/io/ddd4j/quarkus/cqrs/query/QuarkusJpaView.java`
   - 实现 `JpaView` 接口
   - 集成 Quarkus JPA 事务管理

### Phase 3：EventStore 集成（2-3 天）

**新增文件**：

1. `ddd4j-quarkus-data/ddd4j-quarkus-data-eventstore/src/main/java/io/ddd4j/quarkus/data/eventstore/EventStoreConfig.java`
   - Quarkus 配置类：EventStore 连接参数

2. `ddd4j-quarkus-data/ddd4j-quarkus-data-eventstore/src/main/java/io/ddd4j/quarkus/data/eventstore/EventStoreProducer.java`
   - CDI Producer：创建 EventStore 实例

3. `ddd4j-quarkus-data/ddd4j-quarkus-data-eventstore/src/main/java/io/ddd4j/quarkus/data/eventstore/EventStoreHealthCheck.java`
   - Quarkus 健康检查

### Phase 4：命令总线路由（1-2 天）

**新增文件**：

1. `ddd4j-quarkus-core/src/main/java/io/ddd4j/quarkus/cqrs/command/QuarkusCommandBus.java`
   - CDI 实现命令总线
   - 自动发现 `@CommandExecutor` 标注的 Bean
   - 路由命令到对应的执行器

### Phase 5：Web 控制器模板（1-2 天）

**新增文件**：

1. `ddd4j-quarkus-web/src/main/java/io/ddd4j/quarkus/web/controller/QuarkusAggregateController.java`
   - JAX-RS 控制器模板
   - 提供标准 CRUD 端点
   - 集成 `R<T>` 响应封装

---

## 五、关键代码参考

### 5.1 ddd4j-core ViewManager SPI

```java
// ddd4j-core/src/main/java/io/ddd4j/core/cqrs/projection/ViewManager.java
public interface ViewManager {
    void start();
    void stop();
    boolean isRunning();
    void triggerOnce();
}
```

### 5.2 ddd4j-core ProjectionPosition SPI

```java
// ddd4j-core/src/main/java/io/ddd4j/core/cqrs/projection/ProjectionPosition.java
public interface ProjectionPosition {
    String getStreamId();
    long getNextEventNumber();
    void setNextEventNumber(long nextEventNumber);
}
```

### 5.3 ddd4j-core DddCommandExecutor 基类

```java
// ddd4j-core/src/main/java/io/ddd4j/core/ddd/command/DddCommandExecutor.java
public abstract class DddCommandExecutor<CMD extends Command> 
    implements CommandExecutor<Void, Result, CMD> {
    
    @Override
    public abstract Set<EventType> getCommandTypes();
    
    @Override
    public Result execute(Void ctx, CMD cmd) throws CommandExecutionFailedException {
        // 模板方法：解析聚合根ID → 读取聚合根 → 执行命令 → 保存
    }
}
```

### 5.4 ddd4j-spring ViewManager 实现参考

```java
// ddd4j-spring 中的 SpringJpaViewManager 可作为 Quarkus 实现的参考
// 关键差异：
// - Spring: SchedulingConfigurer + @Scheduled
// - Quarkus: @Scheduled 或 ScheduledExecutorService
// - Spring: PlatformTransactionManager
// - Quarkus: QuarkusTransaction 或 @Transactional
```

---

## 六、优先级总结

| 优先级 | 任务 | 工作量 | 影响范围 |
|--------|------|--------|---------|
| **P0** | 补全 DddCdiExtension 扫描范围 | 0.5 天 | 所有 DDD 注解类 |
| **P0** | 实现 ViewManager + ProjectionPosition | 3-5 天 | CQRS 读侧 |
| **P1** | EventStore 集成 | 2-3 天 | 事件溯源 |
| **P1** | 命令总线路由 | 1-2 天 | CQRS 写侧 |
| **P2** | Web 控制器模板 | 1-2 天 | REST API |

**总计预估**：8-14 个工作日完成 ddd4j-quarkus 对 ddd4j 2.0.x 的全面适配。

---

## 七、测试策略

### 7.1 单元测试
- `DddCdiExtensionTest`：验证注解扫描和 Bean 注册
- `CdiDomainEventPublisherTest`：验证事件发布
- `QuarkusCommandBusTest`：验证命令路由

### 7.2 集成测试
- `QuarkusJpaViewManagerIT`：验证视图投影端到端
- `EventStoreIntegrationIT`：验证 EventStore 读写
- `CqrsEndToEndIT`：验证完整 CQRS 流程

### 7.3 ArchUnit 测试
- 复用 `ddd4j-ddd-clean` 的 `CleanDDDLayerRules`
- 验证 Quarkus 项目遵循 DDD 分层规范
