# cqrs-4-java 架构剖析

## 一、项目定位

`cqrs-4-java`（fuinorg 出品）是 `ddd-4-java` 的姐妹项目，专门为**命令查询职责分离（CQRS）**提供 Java 实现。它在 `ddd-4-java`
的事件溯源基础上，加入命令路由、命令执行器、读侧投影、视图管理器等 CQRS 必备组件。

| 维度                | 数据                                              |
|-------------------|-------------------------------------------------|
| **核心价值**          | 命令总线 + 读侧投影 + 视图管理 + 双框架适配（Quarkus/Spring Boot） |
| **依赖 ddd-4-java** | 强依赖，复用其聚合根/事件/异常                                |
| **依赖 esc-api**    | 强依赖，事件存储访问                                      |
| **框架适配**          | quarkus/ + springboot/ 双实现                      |

---

## 二、模块拓扑

```
cqrs-4-java/
├── core/              ← CQRS 核心契约（Command/Executor/View/Result）
├── esc/               ← EventStore CQRS 集成
├── jackson/           ← Jackson 序列化
├── jaxb/              ← JAXB 序列化
├── jsonb/             ← JSON-B 序列化
├── quarkus/           ← Quarkus 框架适配（CDI/Arc）     ★ 参考实现
├── springboot/        ← Spring Boot 框架适配              ★ 参考实现
├── jacoco/
└── test/              ← 集成测试（KurrentDB / EventStoreDB）
```

---

## 三、CQRS 核心契约图谱

### 3.1 写侧（Command Side）

```
                       ┌────────────────────────────┐
                       │   Command (标记接口)        │  写命令
                       │  无方法，纯标签              │
                       └────────────┬───────────────┘
                                    │ extends
              ┌─────────────────────┴─────────────────────┐
              │                                           │
   ┌──────────▼──────────────┐              ┌─────────────▼──────────────┐
   │ AggregateCommand         │              │ (业务自定义命令)            │
   │ <ROOT_ID, ENTITY_ID>    │              │ CreatePersonCommand         │
   │ + entityIdPath          │              │ DeletePersonCommand         │
   │ + aggregateVersion      │              │ UpdateOrderCommand         │
   │ + aggregateType         │              └─────────────────────────────┘
   └──────────┬──────────────┘
              │ extends (jackson/jaxb/jsonb 提供)
   ┌──────────▼──────────────┐
   │ AbstractCommand         │  抽象基类
   │ + correlationId         │  链路追踪
   │ + causationId           │  因果关系
   │ + Builder 模式          │  流式构造
   └─────────────────────────┘

                       ┌────────────────────────────┐
                       │   CommandExecutor          │  SPI
                       │  <CONTEXT, RESULT, CMD>    │
                       │  + execute(ctx, cmd)       │
                       │  + getCommandTypes()       │
                       └────────────┬───────────────┘
                                    │ extends
                       ┌────────────▼───────────────┐
                       │ MultiCommandExecutor       │  组合多个执行器
                       │  + add(CE)                  │
                       │  + remove(CE)               │
                       └────────────────────────────┘
```

### 3.2 写侧执行流程

```
Controller 接收到 CreatePersonCommand
    ↓
查找对应的 CommandExecutor（按 EventType 匹配）
    ↓
CommandExecutor.execute(ctx, cmd)
    ↓
EventStorePersonRepository.add(person)
    ↓
person.apply(PersonCreatedEvent)        ← 聚合根产生事件
    ↓
EventStore.append(streamId, events)     ← 持久化到事件存储
    ↓
返回 Result<T>（SUCCESS / FAILURE）
```

### 3.3 读侧（Query Side / Projection）

```
                       ┌────────────────────────────┐
                       │   View (标记接口)           │  读模型
                       └────────────┬───────────────┘
                                    │ extends
                       ┌────────────▼───────────────┐
                       │   JpaView                  │  JPA 读模型
                       │  + create(streamId)        │  首次创建投影
                       │  + update(streamId, event) │  事件更新
                       │  + delete(streamId)        │  投影删除
                       │  + getEntityType()         │  实体类型
                       │  + getCron()               │  定时拉取表达式
                       │  + getLock()               │  并发锁
                       └────────────┬───────────────┘
                                    │ extends
                ┌───────────────────┴───────────────────┐
                ▼                                       ▼
   ┌──────────────────────────┐          ┌──────────────────────────┐
   │ AbstractPersonsView       │          │ StatisticView            │
   │ @CreateEvent              │          │ @CreateEvent             │
   │ @UpdateEvent              │          │ @DeleteEvent             │
   │ @DeleteEvent              │          │                          │
   └──────────────────────────┘          └──────────────────────────┘

                       ┌────────────────────────────┐
                       │   JpaEventHandler           │  事件处理接口
                       │  <VIEW, EVENT>              │
                       │  + create(view, event)      │
                       │  + update(view, event)      │
                       │  + delete(view, event)      │
                       └────────────────────────────┘
```

### 3.4 读侧投影流程

```
┌──────────────────────────────────────────────────────────┐
│                    Command Side                          │
│  POST /persons (CreatePersonCommand)                     │
│    ↓                                                      │
│  CommandExecutor.execute(ctx, cmd)                       │
│    ↓                                                      │
│  EventStorePersonRepository.add(person)                   │
│    ↓ apply(PersonCreatedEvent)                           │
│  EventStore.append(stream=Person-uuid, events)           │
└──────────────────────┬───────────────────────────────────┘
                       │ EventStore stream subscription
                       ▼
┌──────────────────────────────────────────────────────────┐
│                    Query Side                            │
│  QuarkusJpaViewManager / SpringJpaViewManager            │
│    ↓ 定时轮询 (Cron)                                      │
│  EventStore.readProjection(stream=projection-stream)     │
│    ↓ 分块处理                                              │
│  JpaView.update(event)                                   │
│    ↓ JPA persist                                          │
│  PERSON_LIST_ENTRY table                                 │
│    ↓                                                      │
│  GET /person-list → JpaView 投影查询                      │
└──────────────────────────────────────────────────────────┘
```

### 3.5 投影位置持久化（QryProjectionPosition）

```java

@Entity
@Table(name = "QUARKUS_QRY_PROJECTION_POS")
public class QryProjectionPosition {
    @Id
    @Column(name = "STREAM_ID", length = 250, updatable = false)
    private String streamId;        // 投影流 ID

    @Column(name = "NEXT_POS", updatable = true)
    private Long nextPos;           // 下次读取位置

    // 重启后从 nextPos 继续读取，不会丢消息
}
```

---

## 四、关键 SPI 抽象

### 4.1 Command / CommandExecutor

```java
// Command 是纯标记接口
public interface Command {
}

// AggregateCommand 携带聚合根上下文
public interface AggregateCommand<ROOT_ID extends AggregateRootId, ENTITY_ID extends EntityId>
        extends Command {
    EntityIdPath getEntityIdPath();

    AggregateVersion getAggregateVersion();

    EntityType getAggregateType();
}

// CommandExecutor 负责执行命令
public interface CommandExecutor<CONTEXT, RESULT, CMD extends Command> {
    Set<EventType> getCommandTypes();  // 声明我能处理哪些命令

    RESULT execute(CONTEXT ctx, CMD cmd) throws ...;
}

// MultiCommandExecutor 组合多个执行器
public class MultiCommandExecutor<CONTEXT, RESULT, CMD extends Command>
        implements CommandExecutor<CONTEXT, RESULT, CMD> {
    private final List<CommandExecutor<CONTEXT, RESULT, ?>> executors;

    public RESULT execute(CONTEXT ctx, CMD cmd) {
        return executors.stream()
                .filter(e -> e.getCommandTypes().contains(eventTypeOf(cmd)))
                .findFirst()
                .orElseThrow(...)
            .execute(ctx, cmd);
    }
}
```

### 4.2 View / JpaView

```java
// View 是纯标记接口
public interface View {
}

// JpaView 是 JPA 实现的读模型
public interface JpaView extends View {
    EntityId getEntityId();

    EntityType getEntityType();

    String getCron();          // 定时拉取表达式

    Lock getLock();            // 并发锁

    void create(StreamId streamId);

    void update(StreamId streamId, CommonEvent event);

    void delete(StreamId streamId);
}
```

### 4.3 Result / ResultType

```java
public final class Result<T> {
    private final ResultType type;   // SUCCESS / FAILURE
    private final T data;
    private final String message;
}

public enum ResultType {
    SUCCESS,
    FAILURE
}
```

---

## 五、双框架适配对照

| 维度            | Quarkus 适配                     | Spring Boot 适配                                |
|---------------|--------------------------------|-----------------------------------------------|
| 入口            | `PersonResource`（JAX-RS）       | `PersonController`（@RestController）           |
| Bean          | `@ApplicationScoped`           | `@Service` / `@RestController`                |
| 异常映射          | `ExceptionMapper`（JAX-RS）      | `@RestControllerAdvice`                       |
| 事务            | `QuarkusTransaction`           | `PlatformTransactionManager`                  |
| 视图管理器         | `QuarkusJpaViewManager`        | `SpringJpaViewManager`                        |
| 配置            | MicroProfile `@ConfigProperty` | `application.yml`                             |
| EventStore 配置 | `EventstoreConfig` (CDI)       | `EventstoreConfig` (@ConfigurationProperties) |
| 投影调度          | `@Scheduled` (Quarkus)         | `ScheduledTaskRegistrar` (Spring)             |
| 异常            | `AggregateNotFoundException`   | 同（继承自 ddd-4-java）                             |

### Spring Boot 视图管理器示例

```java
public class SpringJpaViewManager implements SchedulingConfigurer, ApplicationListener<ContextClosedEvent> {
    private final List<JpaView> rawViews;
    private final EventStore eventstore;
    private final ProjectionAdminEventStore admin;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        // 创建定时任务
        for (ViewExt view : views) {
            view.setCronTask(new CronTask(
                    () -> updateView(view),
                    view.getCron()
            ));
            taskRegistrar.addCronTask(view.getCronTask());
        }
    }

    private void updateView(ViewExt view) {
        // 加锁 → 读事件流 → 更新投影 → 提交
        tryLocked(view.getLock(), () -> {
            new Thread(() -> {
                try {
                    readStreamEvents(view);
                } catch (RuntimeException ex) {
                    LOG.error("Error reading events from stream", ex);
                }
            }).start();
        });
    }
}
```

---

## 六、对 ddd4j 的关键启示

### 6.1 cqrs-4-java 提供了什么（ddd4j 应补充）

| 价值                       | 具体实现                                             | ddd4j 当前状态          |
|--------------------------|--------------------------------------------------|---------------------|
| **Command 接口**           | `Command` / `AggregateCommand`                   | ❌ ddd4j 缺           |
| **CommandExecutor SPI**  | `CommandExecutor<CONTEXT, RESULT, CMD>`          | ❌ ddd4j 缺           |
| **MultiCommandExecutor** | 组合多个执行器                                          | ❌ ddd4j 缺           |
| **AbstractCommand**      | Builder + Jackson 兼容                             | ❌ ddd4j 缺           |
| **View 标记接口**            | `View`                                           | ❌ ddd4j 缺           |
| **JpaView 接口**           | 读侧投影标准                                           | ❌ ddd4j 缺           |
| **ViewManager 抽象**       | `QuarkusJpaViewManager` / `SpringJpaViewManager` | ❌ ddd4j 缺           |
| **ProjectionPosition**   | 投影位置持久化                                          | ❌ ddd4j 缺           |
| **事件处理器注解**              | `@CreateEvent` / `@UpdateEvent` / `@DeleteEvent` | ❌ ddd4j 缺           |
| **Result / ResultType**  | 命令执行结果                                           | ❌ ddd4j 缺           |
| **多序列化器**                | Jackson / JAXB / JSON-B                          | ⚠️ ddd4j 仅有 Jackson |
| **Quarkus 适配**           | CDI 集成                                           | ❌ ddd4j 缺           |
| **Spring Boot 适配**       | Spring Scheduling                                | ⚠️ ddd4j 仅有部分       |

### 6.2 ddd4j 应补充的 CQRS 能力清单

```
P0 必须：
  ⬜ Command / AggregateCommand 接口
  ⬜ CommandExecutor SPI
  ⬜ CommandHandler 注解 + 自动注册
  ⬜ MultiCommandExecutor 组合
  ⬜ Result<T> + ResultType

P1 应有：
  ⬜ View / JpaView 接口
  ⬜ ViewManager 抽象（Quarkus / Spring 各自实现）
  ⬜ ProjectionPosition（投影位置持久化）
  ⬜ 投影增量拉取 + 重试机制
  ⬜ @CreateEvent / @UpdateEvent / @DeleteEvent 注解

P2 可选：
  ⬜ 多序列化器支持（JAXB / JSON-B）
  ⬜ APT 代码生成（Command / View 模板）
  ⬜ 单元测试套件（KurrentDB / EventStoreDB 集成测试）
```

### 6.3 三个项目的依赖层级

```
                    ┌──────────────────────┐
                    │   ddd-cqrs-4-java    │  参考实现
                    │   -example           │  （业务侧）
                    └──────────┬───────────┘
                               │ 引用
              ┌────────────────┼────────────────┐
              ▼                ▼                ▼
     ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
     │ cqrs-4-java  │  │ ddd-4-java   │  │ esc-api      │
     │ (CQRS 框架)   │  │ (DDD 基类)   │  │ (EventStore) │
     └──────┬───────┘  └──────┬───────┘  └──────┬───────┘
            │                  │                  │
            └──────────────────┼──────────────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │      ddd4j           │  通用基础层
                    │  (本次主题项目)        │  框架无关
                    └──────────┬───────────┘
                               │
            ┌──────────────────┼──────────────────┐
            ▼                  ▼                  ▼
    ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
    │ ddd4j-runtime-spring │  │ ddd4j-runtime-quarkus│  │ddd4j-javalin │
    │ (Spring 适配) │  │ (Quarkus 适配)│  │(Javalin 适配)│
    └──────────────┘  └──────────────┘  └──────────────┘
```

---

## 七、集成到 ddd4j 的建议路径

### 7.1 短期：复用 ddd-4-java 基类

```java
// ddd4j-core/src/main/java/io/ddd4j/core/ddd/
public abstract class DddAggregateRoot<ID extends AggregateRootId>
        extends AbstractAggregateRoot<ID> {  // 直接继承 fuinorg

    protected LocalDateTime createTime;
    protected LocalDateTime updateTime;

    // ddd4j 扩展：审计字段（无 ORM 注解）
}
```

### 7.2 中期：补充 CQRS 抽象

```java
// ddd4j-core/src/main/java/io/ddd4j/core/cqrs/
public interface Command {
}

public interface AggregateCommand<ROOT_ID extends AggregateRootId, ENTITY_ID extends EntityId>
        extends Command {
    EntityIdPath getEntityIdPath();

    AggregateVersion getAggregateVersion();
}

public interface CommandExecutor<CONTEXT, RESULT, CMD extends Command> {
    Set<EventType> getCommandTypes();

    RESULT execute(CONTEXT ctx, CMD cmd);
}
```

### 7.3 长期：双框架视图管理器

```java
// ddd4j-runtime-quarkus/src/main/java/io/ddd4j/quarkus/view/
@ApplicationScoped
public class QuarkusJpaViewManager { ...
}

// ddd4j-runtime-spring/src/main/java/io/ddd4j/spring/view/
public class SpringJpaViewManager implements SchedulingConfigurer { ...
}
```

---

## 八、总结

`cqrs-4-java` 是 ddd4j 实现完整 CQRS 读侧投影的**最佳参考**。它以极简的接口设计（`Command` / `View` 纯标记接口 +
`CommandExecutor` / `JpaView` 单一职责 SPI）提供了：

- **写侧**：命令路由 + 多执行器组合 + 异常隧道
- **读侧**：JPA 投影 + 定时拉取 + 位置持久化
- **框架适配**：Quarkus CDI + Spring Boot 双实现

ddd4j 应在此基础上：

1. **继承** ddd-4-java 的 `AbstractAggregateRoot`
2. **补充** `Command` / `CommandExecutor` / `View` / `JpaView` 抽象
3. **实现** `ddd4j-runtime-spring` / `ddd4j-runtime-quarkus` 各自的视图管理器
4. **复用** `esc-api` 作为事件存储后端

这样 ddd4j 才能在事件溯源 + CQRS 维度上，达到与 fuinorg 系列同等的成熟度。
