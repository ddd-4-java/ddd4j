# ddd-cqrs-4-java-example 架构剖析

> **项目地址**：`/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd-cqrs-4-java-example`
> **协议**：Apache 2.0
> **规模**：127 文件 / 2,006 符号 / 3,543 边（codegraph 索引）
> **在 ddd4j 中的角色**：**双框架参考实现**——Quarkus 与 Spring Boot 平行展示完整 DDD/CQRS/ES 微服务架构

---

## 一、项目定位

`ddd-cqrs-4-java-example`（fuinorg 出品）是 `ddd-4-java` + `cqrs-4-java` 的**官方参考实现**，使用同一个领域模型（Person）平行演示：

- **Quarkus** 框架下的命令侧 + 查询侧
- **Spring Boot** 框架下的命令侧 + 查询侧

是 ddd4j 实现 `ddd4j-quarkus` 和 `ddd4j-spring` 适配层的**最佳参考实现**。

| 维度 | 数据 |
|------|------|
| **核心价值** | 完整微服务架构的两种实现范式 |
| **领域模型** | Person 聚合根（创建/删除/查询） |
| **事件存储** | EventStoreDB / KurrentDB（通过 esc-api） |
| **写侧技术** | 命令路由 → 聚合根 → EventStore |
| **读侧技术** | 定时投影 → JPA 视图 → REST 查询 |
| **框架对比** | Quarkus（CGI） vs Spring Boot（MVC） |

---

## 二、模块拓扑

```
ddd-cqrs-4-java-example/
├── spring-boot/                  ← Spring Boot 实现
│   ├── shared/       ← 共享契约（事件/命令/ID）
│   ├── command/      ← 写侧服务（Person 微服务）
│   └── query/        ← 读侧服务（PersonList 微服务）
├── quarkus/                     ← Quarkus 实现
│   ├── shared/
│   ├── command/
│   └── query/
├── demo/                        ← 旧版 demo（被 spring-boot/quarkus 取代）
└── docker-compose.yml           ← EventStoreDB 一键启动
```

### 服务拆分（每种框架都是 4 个 Maven 子模块）

```
shared   - 跨进程共享的领域契约（Command / Event / ID / Config）
command  - 写侧微服务（接收命令 → 产生事件 → 写入 EventStore）
query    - 读侧微服务（订阅 EventStore → 投影到 JPA → REST 查询）
```

---

## 三、Person 聚合根全景

```
                  ┌────────────────────────────────┐
                  │  Person (Aggregate Root)       │
                  │  extends AbstractAggregateRoot │
                  │  - id: PersonId                │
                  │  - name: PersonName            │
                  │  - deleted: boolean            │
                  ├────────────────────────────────┤
                  │  + Person(id, name, service)   │ ← 构造校验唯一性
                  │  + delete()                    │ ← 业务方法
                  │  + getId()/getType()           │
                  ├────────────────────────────────┤
                  │  @ApplyEvent                   │
                  │  applyEvent(PersonCreatedEvent)│ ← 状态重建
                  │  @ApplyEvent                   │
                  │  applyEvent(PersonDeletedEvent)│
                  └──────────┬─────────────────────┘
                             │
                             │ 序列化
                             ▼
        ┌──────────────────────────────────────┐
        │ PersonCreatedEvent extends           │
        │  AbstractDomainEvent<PersonId>       │
        │  - entityId: PersonId                │
        │  - name: PersonName                  │
        │  + Builder 模式                       │
        └──────────────────────────────────────┘

读侧投影 (CQRS Query Side)：
  PersonListView implements JpaView
    @CreateEvent(PersonCreatedEvent) → PersonListEntry insert
    @UpdateEvent(PersonDeletedEvent) → PersonListEntry soft delete
    @DeleteEvent(...)             → PersonListEntry delete

  QryProjectionPosition (JPA 实体)
    - 记录每个投影流的下一个读取位置
    - 持久化到 QUARKUS_QRY_PROJECTION_POS / SPRING_QRY_PROJECTION_POS

  QryScheduler (Quarkus) / SpringJpaViewManager
    - 定时拉取 EventStore 增量事件
    - 事务隔离 PROPAGATION_REQUIRES_NEW
    - 锁定 view.getLock() 防止并发
```

---

## 四、关键设计模式

### 4.1 构造器注入服务

```java
public Person(@NotNull final PersonId id,
              @NotNull final PersonName name,
              final CreatePersonService service)
        throws DuplicatePersonNameException {
    super();

    // VERIFY PRECONDITIONS
    Contract.requireArgNotNull("id", id);
    Contract.requireArgNotNull("name", name);

    // VERIFY BUSINESS RULES

    // Rule 1: The name of the person must be unique
    final Optional<PersonId> otherId = service.loadPersonIdByName(name);
    if (otherId.isPresent()) {
        throw new DuplicatePersonNameException(otherId.get(), name);
    }

    // CREATE EVENT
    apply(new PersonCreatedEvent.Builder()
        .id(id).name(name)
        .version(getNextVersion() + 1)
        .build());
}

/**
 * Service for the constructor.
 */
public static interface CreatePersonService {

    /**
     * Loads the person's identifier for a given name.
     *
     * @param name Person's name.
     * @return Office identifier or empty if not found.
     */
    public Optional<PersonId> loadPersonIdByName(@NotNull PersonName name);
}
```

**设计精髓**：业务规则校验通过构造器参数注入的 `CreatePersonService` 完成，避免在聚合根中直接依赖仓储。

### 4.2 事件溯源 + CQRS 投影

写侧 EventStore + 读侧 JPA View 独立演进，**读写模型完全分离**。

### 4.3 Jandex 反射优化

```java
// Quarkus 场景：启动时扫描索引，避免运行时 Class.forName
// 通过 Jandex 索引快速定位 EntityId 子类
public class JandexEntityIdFactory implements EntityIdFactory { }
```

### 4.4 Builder + Jackson 兼容

事件和命令都使用 Builder 模式构造，同时支持 Jackson 反序列化（`@JsonCreator`）。

### 4.5 共享 shared 模块

```
shared/ 放 ID/Event/Command
  ├── PersonId           ← UUID + 类型
  ├── PersonName         ← String 值对象
  ├── PersonCreatedEvent ← 业务事件
  ├── PersonDeletedEvent
  ├── CreatePersonCommand
  └── DeletePersonCommand
command/ 和 query/ 模块都依赖 shared
```

### 4.6 投影位置持久化

```java
@Entity
@Table(name = "QUARKUS_QRY_PROJECTION_POS")
public class QryProjectionPosition {

    @Id
    @Column(name = "STREAM_ID", nullable = false, length = 250, updatable = false)
    @NotNull
    private String streamId;

    @Column(name = "NEXT_POS", nullable = false, updatable = true)
    @NotNull
    private Long nextPos;

    /**
     * JPA constructor.
     */
    protected QryProjectionPosition() {
        super();
    }

    /**
     * Constructor with mandatory data.
     *
     * @param streamId Unique stream identifier.
     * @param nextPos Next position from the stream to read.
     */
    public QryProjectionPosition(@NotNull final StreamId streamId, @NotNull final Long nextPos) {
        super();
        Contract.requireArgNotNull("streamId", streamId);
        Contract.requireArgNotNull("nextPos", nextPos);
        this.streamId = streamId.asString();
        this.nextPos = nextPos;
    }

    @NotNull
    public StreamId getStreamId() {
        return new SimpleStreamId(streamId);
    }
}
```

`QryProjectionPosition` 记录 offset，**重启后从 nextPos 继续读取，不会丢消息**。

---

## 五、对 ddd4j 的关键启示

### 5.1 共享契约模块化

`shared/` 模块放 ID/Event/Command，被 `command/` 和 `query/` 同时依赖。这是**微服务间契约共享**的标准做法。

**ddd4j 应借鉴**：
```
ddd4j-core-api           ← 全局共享契约
  ├── io.ddd4j.core.identity.PersonId
  ├── io.ddd4j.core.event.PersonCreatedEvent
  └── io.ddd4j.core.command.CreatePersonCommand

ddd4j-service-order/     ← 业务服务模块
  ├── ddd4j-service-order-command/   ← 写侧
  └── ddd4j-service-order-query/     ← 读侧
```

### 5.2 读写完全分离的微服务架构

```
┌─────────────────┐     POST /persons     ┌──────────────────┐
│  Client / UI    │ ─────────────────────→ │ command-service  │
└─────────────────┘                        │ (Quarkus/Spring) │
                                           │  - Person Ctrl   │
                                           │  - Executes Cmd  │
                                           │  - Writes Events │
                                           └────────┬─────────┘
                                                    │
                                                    │ EventStore.append
                                                    ▼
                                           ┌────────────────┐
                                           │  EventStoreDB  │
                                           │  (KurrentDB)   │
                                           └────────┬───────┘
                                                    │ readProjection
                                                    ▼
                                           ┌────────────────┐
                                           │  query-service │
                                           │  - JPA View    │
                                           │  - REST Query  │
                                           └────────────────┘
```

**ddd4j 应提供**：
- `ddd4j-service-parent` 模板（聚合父 POM）
- `ddd4j-service-shared` 模板（契约模块）
- `ddd4j-service-command` 模板（写侧服务）
- `ddd4j-service-query` 模板（读侧服务）

### 5.3 双框架平行实现是最佳验证

通过同一领域模型在 Quarkus 和 Spring Boot 下平行实现，验证了 ddd-4-java + cqrs-4-java 的**框架无关性**。

**ddd4j 应做**：
- `ddd4j-spring` 与 `ddd4j-quarkus`（及 `ddd4j-javalin`）下用同一套共享契约
- 提供 Person/Vendor 等参考聚合根的可运行示例

### 5.4 docker-compose 简化本地开发

```yaml
# docker-compose.yml
version: '3'
services:
  eventstore:
    image: eventstore/eventstore:latest
    ports:
      - "2113:2113"
```

**ddd4j 应提供**：`ddd4j-samples/docker-compose.yml` 一键启动 EventStoreDB + KurrentDB。

---

## 六、代码结构详解

### 6.1 Quarkus 写侧（command）

```
quarkus/command/src/main/java/.../command/
├── api/                          ← REST 接口
│   ├── PersonResource.java       ← JAX-RS 端点
│   ├── AggregateAlreadyExistsExceptionMapper.java
│   ├── AggregateDeletedExceptionMapper.java
│   ├── AggregateNotFoundExceptionMapper.java
│   ├── AggregateVersionConflictExceptionMapper.java
│   ├── AggregateVersionNotFoundExceptionMapper.java
│   ├── CommandExecutionFailedExceptionMapper.java
│   └── ConstraintViolationExceptionMapper.java
├── app/
│   └── CmdApp.java               ← Quarkus 启动入口
└── domain/                       ← 领域层
    ├── Person.java               ← 聚合根
    ├── PersonRepository.java     ← 仓储接口
    ├── PersonRepositoryFactory.java
    ├── EventStorePersonRepository.java
    └── DuplicatePersonNameException.java
```

### 6.2 Quarkus 读侧（query）

```
quarkus/query/src/main/java/.../query/
├── app/
│   ├── QryApp.java               ← Quarkus 启动入口
│   ├── QryScheduler.java         ← 定时拉取
│   └── QryCheckForViewUpdatesEvent.java
└── views/                        ← 读模型视图
    ├── common/
    │   ├── QryProjectionPosition.java        ← 投影位置
    │   ├── QryProjectionPositionRepository.java
    │   └── QuarkusViewManager.java
    ├── personlist/
    │   ├── PersonListEntry.java              ← 投影实体
    │   ├── PersonListView.java               ← JpaView 实现
    │   ├── PersonListEventDispatcher.java
    │   ├── PersonListEventChunkHandler.java
    │   ├── PersonListProjector.java
    │   ├── PersonCreatedEventHandler.java
    │   ├── PersonDeletedEventHandler.java
    │   └── PersonListResource.java
    └── statistic/
        ├── Statistic.java
        ├── StatisticView.java
        └── QryStatisticResource.java
```

### 6.3 Quarkus shared

```
quarkus/shared/src/main/java/.../shared/
├── Config.java
├── CreatePersonCommand.java
├── DeletePersonCommand.java
├── EventStoreFactory.java        ← EventStore CDI 工厂
├── HttpClientFactory.java
├── JsonbFactory.java
├── PersonCreatedEvent.java
├── PersonDeletedEvent.java
├── PersonId.java
├── PersonName.java
├── ProjectionAdminEventStoreFactory.java
├── SerDeserializerRegistryFactory.java
└── SharedUtils.java
```

### 6.4 Spring Boot 写侧（command）

```
spring-boot/command/src/main/java/.../command/
├── controller/
│   └── PersonController.java     ← @RestController 端点
├── domain/
│   ├── CreatePersonCommand.java
│   ├── DeletePersonCommand.java
│   ├── DuplicatePersonNameException.java
│   ├── EventStorePersonRepository.java
│   ├── Person.java
│   ├── PersonRepository.java
│   └── package-info.java
└── app/
    └── CmdApplication.java       ← Spring Boot 启动入口
```

### 6.5 Spring Boot 读侧（query）

```
spring-boot/query/src/main/java/.../query/
├── app/
│   ├── QryApplication.java       ← Spring Boot 启动入口
│   └── QryBeanConfig.java
└── views/
    ├── personlist/
    │   ├── Person.java
    │   ├── PersonCreatedEventHandler.java
    │   ├── PersonDeletedEventHandler.java
    │   ├── PersonListController.java
    │   ├── PersonListEntry.java
    │   ├── PersonListView.java
    │   └── package-info.java
    └── statistics/
        ├── EntityType.java
        ├── QryStatisticController.java
        ├── Statistic.java
        ├── StatisticEntity.java
        ├── StatisticView.java
        └── package-info.java
```

### 6.6 Spring Boot shared

```
spring-boot/shared/src/main/java/.../shared/
├── Config.java
├── GlobalExceptionHandler.java   ← @RestControllerAdvice
├── PersonCreatedEvent.java
├── PersonDeletedEvent.java
├── PersonId.java
├── PersonName.java
├── SharedConfig.java
├── SharedJacksonModule.java
└── package-info.java
```

---

## 七、双框架命令端点对照

| 维度 | Quarkus | Spring Boot |
|------|---------|-------------|
| 端点注解 | `@Path("/persons")` | `@RequestMapping("/persons")` |
| HTTP 方法 | `@POST` / `@GET` | `@PostMapping` / `@GetMapping` |
| 依赖注入 | `@Inject` | `@Autowired` |
| 启动类 | `CmdApp.java` (Quarkus) | `CmdApplication.java` (Spring Boot) |
| 异常处理 | `ExceptionMapper` (JAX-RS) | `@RestControllerAdvice` |
| 仓储 | `PersonRepositoryFactory` | `PersonRepository` Bean |
| 包结构 | `api/ + domain/ + app/` | `controller/ + domain/ + app/` |

### Quarkus 端点示例

```java
@Path("/persons")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PersonResource {
    @Inject
    private PersonRepository repository;

    @POST
    public Response create(CreatePersonCommand command) {
        // 调用 CommandExecutor
        ...
    }
}
```

### Spring Boot 端点示例

```java
@RestController
@RequestMapping("/persons")
public class PersonController {
    @Autowired
    private PersonRepository repository;

    @PostMapping
    public Result create(@RequestBody CreatePersonCommand command) {
        // 调用 CommandExecutor
        ...
    }
}
```

---

## 八、集成到 ddd4j 的建议

### 8.1 参考示例作为 ddd4j 模板

```bash
# ddd4j-samples 借鉴 ddd-cqrs-4-java-example 的结构
ddd4j-samples/
├── spring-boot/
│   ├── shared/
│   ├── command/
│   └── query/
├── quarkus/
│   ├── shared/
│   ├── command/
│   └── query/
└── docker-compose.yml
```

### 8.2 复制核心模式

- **Person 聚合根** → 改为 `Order` / `Product` 等业务示例
- **QryProjectionPosition** → 抽取到 `ddd4j-core` 作为公共 SPI
- **SpringJpaViewManager** → 抽取到 `ddd4j-spring` 作为默认视图管理器
- **QuarkusViewManager** → 抽取到 `ddd4j-quarkus` 作为默认视图管理器

### 8.3 共享契约模块的复用

`shared/` 模块的 ID/Event/Command 设计可以直接作为 ddd4j 业务项目的模板，文档中明确建议：

```
业务项目结构（推荐）：

my-service/
├── my-service-shared/         ← 跨进程共享契约
│   ├── domain/identity/       ← ID 值对象
│   ├── domain/event/          ← 领域事件
│   └── domain/command/        ← 命令对象
├── my-service-command/        ← 写侧服务
│   ├── api/                   ← REST 接口
│   └── domain/                ← 聚合根/仓储
└── my-service-query/          ← 读侧服务
    ├── app/                   ← 启动 + 调度
    └── views/                 ← 投影 + 视图
```

---

## 九、总结

`ddd-cqrs-4-java-example` 是 ddd4j 实现**完整微服务架构**的最完整参考。它以 Person 聚合根为载体，完整演示了：

- **写侧**：Command → CommandExecutor → AggregateRoot → EventStore
- **读侧**：定时拉取 → 事件分块 → JpaView 投影 → REST 查询
- **双框架**：Quarkus（CDI）+ Spring Boot（MVC）平行实现
- **工程化**：docker-compose + KurrentDB 集成测试

ddd4j 应：
1. **复制其工程结构**到 `ddd4j-samples/`
2. **抽取核心模式**（投影位置、视图管理器、事件分块）到 ddd4j 各层
3. **复用其共享契约设计**作为业务项目模板
4. **借鉴其双框架对照**为 ddd4j-spring / ddd4j-quarkus / ddd4j-javalin 提供平行参考实现
