# ddd-4-java 架构剖析

## 一、项目定位

`ddd-4-java`（fuinorg 出品）是 Java 领域 DDD 战术模式的基础类库，提供**纯 Java 零框架依赖**的聚合根、实体、值对象、领域事件、加密数据、异常等核心抽象。它是 ddd4j 站在巨人肩膀上的"巨人"之一。

| 维度 | 数据 |
|------|------|
| **核心价值** | 纯 DDD 构建块 + 注解驱动事件溯源 + 多序列化器支持 |
| **依赖策略** | 零 Spring / 零 ORM / 零 Web 容器，仅依赖 jakarta.validation / objects4j / slf4j |
| **可嵌入性** | 可被 Spring Boot / Quarkus / Javalin / 任何 Java 容器嵌入使用 |

---

## 二、模块拓扑

```
ddd-4-java/
├── core/              ← 核心契约（纯 Java，零框架依赖）      ★ ddd4j 直接依赖
├── esc/               ← EventStore 仓储实现（依赖 esc-api）
├── jackson/           ← Jackson 序列化扩展                  ★ ddd4j 复用
├── jaxb/              ← JAXB 序列化扩展
├── jsonb/             ← JSON-B 序列化扩展
├── jsonb-testmodel/   ← JSON-B 测试模型
├── junit/             ← JUnit 5 扩展（ArchUnit 规则）
├── codegen/           ← APT 代码生成器（value object）     ★ ddd4j 可参考
└── jacoco/            ← 测试覆盖率聚合
```

### 依赖纯净度（core 模块）

```xml
<!-- core/pom.xml 全部依赖 -->
<dependency>
    <groupId>org.fuin</groupId>
   <artifactId>utils4j</artifactId>
</dependency>
<dependency>
    <groupId>org.fuin.objects4j</groupId>
    <artifactId>objects4j-common</artifactId>
</dependency>
<dependency>
    <groupId>org.fuin.objects4j</groupId>
    <artifactId>objects4j-core</artifactId>
</dependency>
<dependency>
    <groupId>org.fuin.objects4j</groupId>
    <artifactId>objects4j-ui</artifactId>
</dependency>
<dependency>
    <groupId>jakarta.validation</groupId>
    <artifactId>jakarta.validation-api</artifactId>
</dependency>
<dependency>
    <groupId>jakarta.annotation</groupId>
    <artifactId>jakarta.annotation-api</artifactId>
</dependency>
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
</dependency>
<dependency>
    <groupId>io.smallrye</groupId>
    <artifactId>jandex</artifactId>
    <optional>true</optional>     <!-- Quarkus 友好 -->
</dependency>
<dependency>
    <groupId>com.google.code.findbugs</groupId>
    <artifactId>jsr305</artifactId>
</dependency>
```

**结论**：`core` 模块真正做到了**零框架依赖**，这正是 ddd4j 通用基础层想要达到的纯净度目标。

---

## 三、核心契约图谱

### 3.1 聚合根基类体系

```
                            ┌────────────────────────┐
                            │   AggregateRoot<ID>    │  接口
                            │  + getId(): ID         │
                            │  + getType(): EntityType│
                            │  + getVersion(): int    │
                            │  + getUncommittedChanges│
                            └───────────┬────────────┘
                                        │ extends
                            ┌───────────▼────────────┐
                            │ AbstractAggregateRoot  │  模板基类
                            │  + apply(event)        │  收集事件
                            │  + loadFromHistory()   │  重放事件
                            │  + callAnnotatedHandler│  @ApplyEvent 反射
                            └───────────┬────────────┘
                                        │ extends (用户业务聚合根)
                            ┌───────────▼────────────┐
                            │ Person / Vendor / ...  │  业务聚合根
                            │  + @ApplyEvent 方法     │  状态变更回调
                            └────────────────────────┘
```

### 3.2 领域事件与标识符体系

```
┌──────────────────────────────────────────────────────────┐
│                    Event 基接口                            │
│  + getEventId(): EventId                                 │
│  + getEventTimestamp(): ZonedDateTime                    │
│  + getCorrelationId(): EventId    ← 链路追踪              │
│  + getCausationId(): EventId      ← 因果关系              │
└────────────────────────┬─────────────────────────────────┘
                         │ extends
              ┌──────────┴──────────┐
              ▼                     ▼
   ┌──────────────────┐  ┌────────────────────┐
   │   DomainEvent<T> │  │  AbstractEvent     │  抽象基类
   │  业务领域事件      │  │  + Builder 模式     │
   │  + entityIdPath  │  │  + Jackson 兼容     │
   └──────────────────┘  └────────────────────┘
              │ extends
              ▼
   ┌──────────────────────────────────────┐
   │ PersonCreatedEvent / VendorCreated…  │  具体业务事件
   │  + entityId: PersonId / VendorId    │
   │  + name: PersonName                  │
   │  + Builder().id().name().build()    │
   └──────────────────────────────────────┘

标识符体系（强类型值对象）：
  EntityId (接口)
    ├── AggregateRootId ← 聚合根标识
    │     └── AggregateRootUuid (UUID 风格基类)
    │           ├── PersonId (UUID + 类型)
    │           ├── VendorId
    │           └── OrderId
    └── EntityId (普通实体标识)
          ├── PersonName
          ├── VendorRef
          └── OrderLineId

版本控制：
  AggregateVersion (值对象，包装 int，0..n)
  @BusinessKey 注解 → 业务唯一键
```

### 3.3 异常体系

```
AbstractAggregateException
├── AggregateAlreadyExistsException       ← 创建时已存在
├── AggregateDeletedException             ← 已删除（软删）
└── AggregateNotFoundException             ← 未找到

AbstractVersionedAggregateException
├── AggregateVersionConflictException      ← 乐观锁冲突
└── AggregateVersionNotFoundException      ← 指定版本不存在

加密/合规异常：
├── DecryptionFailedException
├── DuplicateEncryptionKeyIdException
├── EncryptionKeyIdUnknownException
└── EncryptionKeyVersionUnknownException
```

### 3.4 实体路径（EntityIdPath）

```java
// 标识聚合根→子实体的层级路径
EntityIdPath path = new EntityIdPath(orderId, orderLineId);
// 类似文件系统路径：Order/OrderLine
path.first()   // OrderId
path.last()    // OrderLineId
path.iterator() // 顺序遍历
```

---

## 四、关键技术决策

### 4.1 注解驱动事件溯源

```java
public class Person extends AbstractAggregateRoot<PersonId> {
    private PersonId id;
    private PersonName name;
    private boolean deleted;

    // 业务方法：只负责产生事件
    public void delete() throws AggregateDeletedException {
        if (deleted) throw new AggregateDeletedException(...);
        apply(new PersonDeletedEvent.Builder()
            .id(id).name(name)
            .version(getNextVersion() + 1)
            .build());
    }

    // 事件应用方法：只负责修改状态
    @ApplyEvent
    public void applyEvent(PersonCreatedEvent event) {
        this.id = event.getEntityId();
        this.name = event.getName();
    }

    @ApplyEvent
    public void applyEvent(PersonDeletedEvent event) {
        this.deleted = true;
    }
}
```

**核心机制**：
- `apply(event)` 把事件加入 `uncommitedChanges` 列表
- `loadFromHistory(events)` 通过反射调用 `@ApplyEvent` 标注的方法
- `markChangesAsCommitted()` 清空事件列表并增加 version
- **业务方法与状态变更完全分离**——业务方法只产生意图，事件应用方法只反映事实

### 4.2 多序列化器平行支持

```
core/DomainEvent (接口)
    ├── jackson/AbstractDomainEvent     (Jackson 序列化)
    │     + @JsonCreator / @JsonProperty
    │     + Ddd4JacksonModule (注册序列化器)
    ├── jaxb/AbstractDomainEvent        (JAXB 序列化)
    │     + @XmlRootElement / @XmlAttribute
    └── jsonb/AbstractDomainEvent       (JSON-B 序列化)
          + @JsonbCreator / @JsonbProperty
```

业务侧可按需引入对应模块，无需关心底层。

### 4.3 EncryptedData + GDPR 合规

```java
// GDPR 要求"被遗忘权"——通过加密实现假删除
public class EncryptedData {
    private String keyId;          // 密钥 ID
    private String keyVersion;     // 密钥版本
    private String contentType;    // 内容类型
    private String dataType;       // 数据类型
    private String encryptedData;  // 加密后的密文
}

// 删除用户 → 销毁密钥 → 历史事件中的个人数据永远无法解密
```

### 4.4 Jandex 字节码索引

```java
// Quarkus 场景：启动时扫描索引，避免运行时 Class.forName
public class JandexEntityIdFactory implements EntityIdFactory {
    // 通过 Jandex 索引快速定位 EntityId 子类
}
```

### 4.5 APT 代码生成器（codegen）

```java
// 注解驱动自动生成值对象
@StringVO(min = 3, max = 50, label = "Person Name")
public class PersonName {
    // APT 自动生成：
    // - valueOf(String)
    // - isValid(String)
    // - equals/hashCode
    // - toString
    // - @JsonCreator / @JsonValue
}
```

---

## 五、对 ddd4j 的关键启示

### 5.1 ddd-4-java 提供了什么（ddd4j 应当参考或复用）

| 价值 | 具体实现 | ddd4j 当前状态 |
|------|---------|---------------|
| 纯 DDD 聚合根基类 | `AbstractAggregateRoot` | ✅ `DddAggregateRoot` 已实现 |
| 领域事件基类 | `DomainEvent` / `AbstractDomainEvent` | ✅ `DddDomainEvent` 已实现 |
| 事件溯源仓储 | `EventStoreRepository` | ✅ `DddEventStoreRepository` 已实现 |
| 强类型 ID 值对象 | `EntityId` / `AggregateRootUuid` | ⚠️ ddd4j 暂无，依赖 `PersonId` 直接继承 |
| 加密数据 | `EncryptedData` | ❌ ddd4j 缺 |
| APT 代码生成 | `codegen` ValueObject | ❌ ddd4j 缺 |
| Jandex 集成 | `JandexEntityIdFactory` | ❌ ddd4j 缺 |
| 异常体系 | `AbstractAggregateException` 层级 | ⚠️ ddd4j 简单 `ServiceException` |

### 5.2 ddd-4-java 缺什么（ddd4j 补充的优势）

| 缺失 | ddd4j 方案 |
|------|-----------|
| **没有进程内事件发布器** | ✅ `DomainEventPublisher` SPI（三框架适配） |
| **没有 MQ 集成** | ✅ `MQEventPublisher` / `MQBrokerAdapter` SPI（12 种 MQ） |
| **没有 Web 适配** | ✅ `BaseAggregateController` / `BaseClientAggregateController` |
| **没有 CQRS 读侧** | ⚠️ ddd4j 应参考 `cqrs-4-java` 补充 |
| **没有 CommandHandler 自动注册** | ⚠️ ddd4j 应补充 |
| **没有 MultiCommandExecutor** | ⚠️ ddd4j 应补充 |
| **没有视图投影机制** | ⚠️ ddd4j 应参考补充 |

### 5.3 两条潜在风险

1. **`esc-api` 强制依赖**：`EventStoreRepository` 必须依赖 `org.fuin:esc-api`，对不用 EventStoreDB/KurrentDB 的项目不友好。
   - **ddd4j 建议**：提供仓储 SPI 接口，让用户自己选 EventStore 实现

2. **Jandex 扫描内置**：`JandexEntityIdFactory` 强制走 Jandex 索引（Quarkus 友好但非 Quarkus 场景是负担）。
   - **ddd4j 建议**：将 Jandex 设为可选 SPI，按环境启用

---

## 六、在 ddd4j 中的使用方式

### 6.1 直接依赖 ddd-4-java

```xml
<!-- ddd4j-core/pom.xml -->
<dependency>
    <groupId>org.fuin.ddd4j</groupId>
    <artifactId>ddd-4-java-core</artifactId>
    <optional>true</optional>
</dependency>
<dependency>
    <groupId>org.fuin.ddd4j</groupId>
    <artifactId>ddd-4-java-jackson</artifactId>
    <optional>true</optional>
</dependency>
```

### 6.2 ddd4j 包装 ddd-4-java（双轨 DDD）

```java
// ddd4j 的 DddAggregateRoot 继承 fuinorg 的 AbstractAggregateRoot
public abstract class DddAggregateRoot<ID extends AggregateRootId>
    extends AbstractAggregateRoot<ID> {
    
    protected LocalDateTime createTime;  // ddd4j 扩展的审计字段
    protected LocalDateTime updateTime;
    
    // 业务方法 + @ApplyEvent 全部由父类提供
}
```

### 6.3 业务项目使用

```java
// 业务聚合根（ddd4j 风格）
public class Person extends DddAggregateRoot<PersonId> {
    private PersonId id;
    private PersonName name;
    
    public Person(PersonId id, PersonName name) {
        super();
        apply(new PersonCreatedEvent.Builder()
            .id(id).name(name)
            .version(getNextVersion() + 1)
            .build());
    }
    
    @ApplyEvent  // 来自 fuinorg 父类
    public void applyEvent(PersonCreatedEvent event) {
        this.id = event.getEntityId();
        this.name = event.getName();
    }
}
```

---

## 七、总结

`ddd-4-java` 是 ddd4j 通用基础层的**核心灵感来源**和**直接依赖**。它以极致的纯净度（零框架依赖）提供了 DDD 战术模式的所有构建块，并以注解驱动 + 反射机制将样板代码降到最低。ddd4j 在它之上：

- **继承**其纯 DDD 基类（`DddAggregateRoot`）
- **复用**其事件溯源机制（`DddEventStoreRepository`）
- **补充**其缺失的进程内事件发布、MQ 集成、Web 适配、视图投影
- **桥接**到 Spring / Quarkus / Javalin 三种容器框架

这种"站在巨人肩膀上"的策略，让 ddd4j 既能享受 fuinorg 多年沉淀的 DDD 模式实现，又能提供符合中国开发者习惯的中文文档、SaaS 多租户、多 MQ 适配等本地化能力。
