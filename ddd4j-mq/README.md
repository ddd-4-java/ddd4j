# ddd4j-mq 架构设计

> 本文档描述 ddd4j 消息队列体系的架构、模块边界与设计约定。

---

## 1. 设计目标

1. **ddd4j-mq 只做适配层（Port）**：零 Broker SDK，可独立单测。
2. **Spring Messaging 作为统一消息模型**：`org.springframework.messaging.Message` 替代自定义 `MQMessage`。
3. **实现交给成熟组件**：Spring Framework 集成库（`spring-rabbit`、`spring-kafka` 等）及各 `ddd4j-mq-*` 模块。
4. **以 RabbitMQ Channel 确认语义为基准**，统一多 Broker 的 ack 模型。
5. **保留 ddd4j 优秀 DX**：`MQEvent`、`@MQEventListener`、租户上下文注入、JSON 序列化约定。

---

## 2. ddd4j-mq 是什么、不是什么

### 2.1 是什么

- **领域消息契约层**：`MQEvent`、`MQDestination`、发布/消费 Port。
- **消费确认抽象层**：`MessageAcknowledgment` 及 `AckDisposition` 高层语义。
- **监听器编排层**：解析 `@MQEventListener`，委托 SPI `MQBrokerAdapter` 注册消费端点。
- **与 DDD 配套的横切能力挂载点**：租户 `ThreadContext`、序列化、可选消息流水拦截器。

### 2.2 不是什么

- **不是** Rabbit/Kafka/Rocket 客户端实现（禁止在本模块引入 `amqp-client`、`kafka-clients` 等）。
- **不是** Spring Cloud Stream 集成（归属 `ddd4j-cloud-mq-stream-*`）。
- **不是** 任务调度器或本地内存队列。

---

## 3. 总体分层

```
┌─────────────────────────────────────────────────────────────────┐
│ 业务应用（单体 / 微服务）                                         │
│  - 领域事件类 extends MQEvent                                     │
│  - @MQEventListener 或 Spring Cloud Stream Consumer<Message<T>>  │
├─────────────────────────────────────────────────────────────────┤
│  ddd4j-cloud-mq-stream          Spring Cloud Stream 桥接      │
│  ddd4j-cloud-mq-stream-*        + 各 binder                   │
├─────────────────────────────────────────────────────────────────┤
│ ddd4j（Spring Framework 生态）                                    │
│  ddd4j-mq                    纯契约 + SPI（本模块）              │
│  ddd4j-mq-rabbitmq           spring-rabbit                      │
│  ddd4j-mq-kafka              spring-kafka                       │
│  ddd4j-mq-pulsar             spring-pulsar                      │
│  ddd4j-mq-redis-stream       spring-data-redis                  │
│  ddd4j-mq-activemq           spring-jms                         │
│  ddd4j-mq-nats / -mqtt / -mqtt-mica / -ons / -tdmq / -sqs      │
│  ddd4j-mq-disruptor          com.lmax:disruptor（本地）          │
├─────────────────────────────────────────────────────────────────┤
│ 开源实现层（不自研协议客户端）                                     │
│ RabbitTemplate · KafkaTemplate · PulsarTemplate · MqttClient…    │
└─────────────────────────────────────────────────────────────────┘
```

### 3.1 依赖规则

| 模块                        | 允许依赖                                        | 禁止依赖                                |
|---------------------------|---------------------------------------------|-------------------------------------|
| `ddd4j-mq`                | `ddd4j-core`、`spring-messaging`             | 一切 Broker SDK、`spring-cloud-stream` |
| `ddd4j-mq-{broker}`       | `ddd4j-mq` + 对应 Broker 客户端                  | `ddd4j-cloud-*`                     |
| `ddd4j-cloud-mq-stream-*` | `ddd4j-mq` + `spring-cloud-stream` + binder | —                                   |

### 3.2 应用如何选择依赖

**Spring Boot 单体 / 非 Cloud 微服务：**

```xml
<dependency>
    <groupId>io.ddd4j</groupId>
    <artifactId>ddd4j-mq-rabbitmq</artifactId>
</dependency>
```

**Spring Cloud 微服务（需要 StreamBridge / 函数式绑定）：**

```xml
<dependency>
    <groupId>io.ddd4j.cloud</groupId>
    <artifactId>ddd4j-cloud-mq-stream-rabbit</artifactId>
</dependency>
```

---

## 4. Spring Messaging 统一消息模型

### 4.1 设计原则

**`org.springframework.messaging.Message` 替代自定义 `MQMessage` 作为唯一消息类型。**

- `spring-messaging` 是 Spring Framework 模块（非 Spring Boot），提供 `Message`、`MessageHeaders`、`MessageBuilder` 等消息领域标准抽象
- 核心 SPI 层和所有实现模块统一使用 `Message<?>` 作为消息载体
- 原生消息逃生口通过 `MessageHeaders` 自定义 key 传递，不破坏类型体系

### 4.2 类型映射

| 概念        | Message / MessageHeaders                     |
|-----------|----------------------------------------------|
| 消息载体      | `org.springframework.messaging.Message<T>`   |
| 载荷        | `Message.getPayload()`                       |
| 消息头       | `Message.getHeaders()` (MessageHeaders)      |
| 消息 ID     | `MessageHeaders.getId()`                     |
| 关联 ID     | `MessageHeaders.get("ddd4j.correlation.id")` |
| 原生消息      | `MessageHeaders.get("ddd4j.native.message")` |
| Header 读取 | `MQMessages.headerAsString(message, key)`    |

### 4.3 依赖策略

```xml
<!-- ddd4j-mq/pom.xml -->
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-messaging</artifactId>
</dependency>
```

---

## 5. Header 约定

### 5.1 标准 Header Key（ddd4j 定义）

| Key                           | 类型           | 说明             |
|-------------------------------|--------------|----------------|
| `ddd4j.native.message`        | Object       | Broker 原生消息逃生口 |
| `ddd4j.correlation.id`        | String       | 关联 ID          |
| `ddd4j.tenant.id`             | String       | 租户 ID          |
| `ddd4j.broker.type`           | MQBrokerType | Broker 类型枚举    |
| `ddd4j.destination.topic`     | String       | 目标 topic       |
| `ddd4j.destination.tag`       | String       | 目标 tag         |
| `ddd4j.destination.namespace` | String       | 目标 namespace   |

### 5.2 Broker 原生 Header Key（各实现模块定义）

| 模块           | Key                                                                 | 说明                          |
|--------------|---------------------------------------------------------------------|-----------------------------|
| rabbitmq     | `amqp.channel`                                                      | RabbitMQ Channel            |
| rabbitmq     | `amqp.deliveryTag`                                                  | 投递标签                        |
| activemq     | `jms.session`                                                       | JMS Session                 |
| activemq     | `jms.message`                                                       | JMS Message                 |
| kafka        | `kafka.acknowledgment`                                              | Spring Kafka Acknowledgment |
| kafka        | `kafka.topic` / `kafka.partition` / `kafka.offset`                  | Kafka 元数据                   |
| mqtt         | `mqtt.receivedTopic` / `mqtt.qos`                                   | MQTT 元数据                    |
| mica-mqtt    | `mica.mqtt.topic` / `mica.mqtt.qos`                                 | Mica MQTT 元数据               |
| nats         | `nats.subject`                                                      | NATS 主题                     |
| redis-stream | `redis.stream.key` / `redis.stream.group` / `redis.stream.recordId` | Redis Stream 元数据            |
| pulsar       | `pulsar.consumer` / `pulsar.message`                                | Pulsar 元数据                  |

---

## 6. MQMessages 工具类

`MQMessages` 是 `Message` 的静态工具类，提供便捷操作：

```java
public final class MQMessages {
    // ── Header Keys ──
    public static final String HEADER_NATIVE_MESSAGE = "ddd4j.native.message";
    public static final String HEADER_CORRELATION_ID = "ddd4j.correlation.id";
    public static final String HEADER_TENANT_ID = "ddd4j.tenant.id";

    // ── 原生消息逃生口 ──
    public static <N> N nativeMessage(Message<?> message, Class<N> type);

    // ── 便捷读取 ──
    public static String headerAsString(Message<?> message, String key);
    public static Object header(Message<?> message, String key);

    // ── 构建方法 ──
    public static <T> Message<T> of(T payload, Map<String, Object> headers);
    public static <T> Message<T> of(T payload, Map<String, Object> headers, Object nativeMessage);

    // ── Header 提取 ──
    public static String extractMessageId(Message<?> message);
    public static String extractCorrelationId(Message<?> message);
    public static String extractTenantId(Message<?> message);
}
```

---

## 7. 两种消息语义

| 类型               | 用途         | 可靠性                   | 典型能力         |
|------------------|------------|-----------------------|--------------|
| **Domain Event** | 跨模块/跨服务通知  | at-least-once，幂等可接受重复 | 普通队列 + 业务幂等  |
| **Job Message**  | 一个工作单元一条消息 | 必须 ack/nack/retry/DLQ | 手动确认、重试退避、死信 |

---

## 8. 模块规划

### 8.1 `ddd4j-mq` 包结构

```
io.ddd4j.mq
├── ack/
│   ├── MessageAcknowledgment        # 完整确认端口
│   ├── AckDisposition               # 业务层推荐枚举
│   └── MQConsumeTemplates           # 消费模板
├── config/
│   └── Ddd4jMQProperties            # 主前缀 ddd4j.mq
├── consume/
│   ├── MQConsumerContext            # tenantId、ack、Message
│   ├── MQConsumerHandler            # 消费处理函数
│   └── MQConsumeInterceptor         # 幂等/流水等拦截链
├── contract/
│   ├── MQMessage                    # Message 适配器（兼容旧 API）
│   ├── MQMessages                   # 工具类：nativeMessage / header 便捷方法
│   └── MQDestination                # namespace / topic / tag
├── publish/
│   └── MQEventPublisher             # 发布端口
├── registry/
│   ├── MQListenerDefinition         # 监听器定义
│   ├── MQBrokerType                 # Broker 类型枚举
│   └── MQListenerRegistrar          # 监听器注册器
└── spi/
    └── MQBrokerAdapter              # 各 mq-* 模块实现
```

### 8.2 `ddd4j-mq-{broker}` 统一内部骨架

```
ddd4j-mq-{broker}/
├── autoconfigure/Ddd4j{Broker}AutoConfiguration
├── publish/{Broker}MQEventPublisher
├── consumer/{Broker}MQConsumerEndpointRegistrar
├── ack/{Broker}MessageAcknowledgment
└── spi/{Broker}MQBrokerAdapter
```

**命名约定**：

- 类名：`{Xxx}MQBrokerAdapter`、`{Xxx}MQEventPublisher`、`{Xxx}MQConsumerEndpointRegistrar`
- 包名：`io.ddd4j.mq.{broker}.spi`、`io.ddd4j.mq.{broker}.publisher`、`io.ddd4j.mq.{broker}.consumer`
- 例外：Kafka 模块使用 `io.ddd4j.mq.kafka.mq` 作为统一包

### 8.3 Broker 模块映射

| #  | 中间件              | ddd4j 模块                | 客户端依赖                            |
|----|------------------|-------------------------|----------------------------------|
| 1  | RabbitMQ         | `ddd4j-mq-rabbitmq`     | `spring-rabbit`                  |
| 2  | Apache Kafka     | `ddd4j-mq-kafka`        | `spring-kafka`                   |
| 4  | Apache Pulsar    | `ddd4j-mq-pulsar`       | `spring-pulsar`                  |
| 5  | Redis Stream     | `ddd4j-mq-redis-stream` | `spring-data-redis`              |
| 6  | ActiveMQ Artemis | `ddd4j-mq-activemq`     | `spring-jms`                     |
| 7  | NATS JetStream   | `ddd4j-mq-nats`         | `jnats`                          |
| —  | Eclipse MQTT     | `ddd4j-mq-mqtt`         | `spring-integration-mqtt` + Paho |
| —  | mica-mqtt        | `ddd4j-mq-mqtt-mica`    | `mica-mqtt-client`               |
| 8  | 阿里云 ONS          | `ddd4j-mq-ons`          | `ons-client`                     |
| 9  | 腾讯云 TDMQ         | `ddd4j-mq-tdmq`         | `tdmq-client`                    |
| 10 | AWS SQS          | `ddd4j-mq-sqs`          | `aws-java-sdk-sqs`               |
| —  | Disruptor        | `ddd4j-mq-disruptor`    | `com.lmax:disruptor`             |

---

## 9. 核心 SPI：`MQBrokerAdapter`

```java
public interface MQBrokerAdapter {
    MQBrokerType brokerType();
    MQEventPublisher createPublisher(Ddd4jMQProperties props);
    void registerConsumer(MQListenerDefinition def, MQConsumerHandler handler);
    MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message);
    boolean supports(MQBrokerType configured);
}
```

启动时根据 `ddd4j.mq.broker` 从 `List<MQBrokerAdapter>` 中选取唯一实现。

### 实现模块一览

| 模块           | BrokerAdapter 类名             | 包路径                           |
|--------------|------------------------------|-------------------------------|
| activemq     | `ActiveMQBrokerAdapter`      | `io.ddd4j.mq.activemq.spi`    |
| disruptor    | `DisruptorMQBrokerAdapter`   | `io.ddd4j.mq.disruptor.spi`   |
| kafka        | `KafkaMQBrokerAdapter`       | `io.ddd4j.mq.kafka.mq`        |
| mqtt         | `MqttMQBrokerAdapter`        | `io.ddd4j.mq.mqtt.spi`        |
| mqtt-mica    | `MicaMqttMQBrokerAdapter`    | `io.ddd4j.mq.mqtt.mica.spi`   |
| nats         | `NatsMQBrokerAdapter`        | `io.ddd4j.mq.nats.spi`        |
| ons          | `OnsMQBrokerAdapter`         | `io.ddd4j.mq.ons.spi`         |
| pulsar       | `PulsarMQBrokerAdapter`      | `io.ddd4j.mq.pulsar.spi`      |
| rabbitmq     | `RabbitMQBrokerAdapter`      | `io.ddd4j.mq.rabbit.spi`      |
| redis-stream | `RedisStreamMQBrokerAdapter` | `io.ddd4j.mq.redisstream.spi` |
| sqs          | `SqsMQBrokerAdapter`         | `io.ddd4j.mq.sqs.spi`         |
| tdmq         | `TdmqMQBrokerAdapter`        | `io.ddd4j.mq.tdmq.spi`        |

---

## 10. 发布侧设计

### 10.1 Spring Bean 发布

```java
public interface MQEventPublisher {
    void publish(MQEvent event);
}
```

`MQEvent.publish(topic, tag, tenantId)` 委托 Spring 容器中的 `MQEventPublisher` Bean。

### 10.2 目的地值对象

```java
public record MQDestination(String topic, String tag, String namespace) {
    public String physicalDestination() { return namespace + "." + topic; }
}
```

### 10.3 双轨事件发布策略

#### 设计动机

ddd4j-mq 的核心用途是推送 DDD 领域事件。在 Spring Boot 项目中，除了发布到远程 MQ Broker，还需要支持：

- **本地事件**：通过 `ApplicationEventPublisher` 发布到 Spring 事件系统，实现进程内解耦
- **混合模式**：同时发布到 MQ 和 Spring Event，支持本地监听 + 远程通知

#### 架构设计（三个改造点）

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           事件发布策略架构                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  改造点 1：统一事件总线（架构层）                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     EventPublisher (策略接口)                        │   │
│  │  ┌─────────────────┐  ┌──────────────────┐  ┌──────────────────┐  │   │
│  │  │ MQEventPublisher│  │SpringEventPublish│  │ HybridEventPublis│  │   │
│  │  │ Impl (MQ Broker)│  │erImpl (本地事件)  │  │herImpl (混合模式) │  │   │
│  │  └─────────────────┘  └──────────────────┘  └──────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                       │                                     │
│  改造点 2：发布端（MQEvent.publish()）                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     MQEvent.publish()                               │   │
│  │  ┌─────────────────────────────────────────────────────────────┐   │   │
│  │  │ 1. 查找 EventPublisher Bean                                 │   │   │
│  │  │ 2. 根据配置选择发布模式（MQ / SPRING_EVENT / BOTH）         │   │   │
│  │  │ 3. 委托给对应的 EventPublisher 实现                         │   │   │
│  │  └─────────────────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                       │                                     │
│  改造点 3：消费端（@MQEventListener + @EventListener）                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     消费监听方式                                     │   │
│  │  ┌─────────────────┐  ┌──────────────────┐  ┌──────────────────┐  │   │
│  │  │ @MQEventListener│  │ @EventListener   │  │ 两者同时使用      │  │   │
│  │  │ (MQ 远程消费)    │  │ (本地事件消费)    │  │ (混合消费)       │  │   │
│  │  └─────────────────┘  └──────────────────┘  └──────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 改造点 1：统一事件总线（EventPublisher 策略接口）

```java
/**
 * 事件发布策略接口。
 */
public interface EventPublisher {
    void publish(MQEvent event);
    PublishMode getPublishMode();
    
    enum PublishMode {
        MQ,           // 只发布到 MQ Broker（默认）
        SPRING_EVENT, // 只发布到 Spring Event
        BOTH          // 同时发布到 MQ 和 Spring Event
    }
}

/**
 * MQ 发布策略（默认）。
 */
@Component
@ConditionalOnProperty("ddd4j.mq.enabled")
public class MQEventPublisherImpl implements EventPublisher {
    private final MQEventPublisher mqEventPublisher;
    
    @Override
    public void publish(MQEvent event) {
        mqEventPublisher.publish(event);
    }
    
    @Override
    public PublishMode getPublishMode() { return PublishMode.MQ; }
}

/**
 * Spring Event 发布策略。
 */
@Component
public class SpringEventPublisherImpl implements EventPublisher {
    private final ApplicationEventPublisher publisher;
    
    @Override
    public void publish(MQEvent event) {
        publisher.publishEvent(event);
    }
    
    @Override
    public PublishMode getPublishMode() { return PublishMode.SPRING_EVENT; }
}

/**
 * 混合发布策略（MQ + Spring Event）。
 */
@Component
@ConditionalOnProperty("ddd4j.mq.publish-mode", havingValue = "BOTH")
public class HybridEventPublisherImpl implements EventPublisher {
    private final MQEventPublisherImpl mqPublisher;
    private final SpringEventPublisherImpl springPublisher;
    
    @Override
    public void publish(MQEvent event) {
        mqPublisher.publish(event);
        springPublisher.publish(event);
    }
    
    @Override
    public PublishMode getPublishMode() { return PublishMode.BOTH; }
}
```

#### 改造点 2：发布端（MQEvent.publish() 支持双轨发布）

```java
@Data
public class MQEvent implements Serializable {
    // ... 现有字段 ...
    
    /**
     * 发布事件，委托 Spring 容器中的 EventPublisher Bean。
     */
    public void publish(String topic, String tag, String tenantId) {
        // ... 现有逻辑 ...
        
        // 优先使用 EventPublisher（新方式）
        if (!publishViaEventPublisher()) {
            // 回退到 MQEventPublisher（向后兼容）
            if (!publishViaMQEventPublisher()) {
                throw new IllegalStateException(
                    "EventPublisher or MQEventPublisher bean not found");
            }
        }
    }
    
    private boolean publishViaEventPublisher() {
        ApplicationContext context = SpringContext.getApplicationContext();
        if (context == null) return false;
        
        Map<String, EventPublisher> publishers = context.getBeansOfType(EventPublisher.class);
        if (publishers.isEmpty()) return false;
        
        publishers.values().iterator().next().publish(this);
        return true;
    }
    
    private boolean publishViaMQEventPublisher() {
        // 旧方式：直接查找 MQEventPublisher（向后兼容）
        ApplicationContext context = SpringContext.getApplicationContext();
        if (context == null) return false;
        
        Map<String, MQEventPublisher> publishers = context.getBeansOfType(MQEventPublisher.class);
        if (publishers.isEmpty()) return false;
        
        publishers.values().iterator().next().publish(this);
        return true;
    }
}
```

#### 改造点 3：消费端（支持多种监听方式）

```java
// 方式 1：MQ 监听（现有方式，远程消费者）
@MQEventListener(topic = "order")
public void onOrderCreatedMQ(OrderCreatedEvent event) {
    // 处理 MQ 消息（远程消费者）
}

// 方式 2：Spring Event 监听（新增方式，本地消费者）
@EventListener
public void onOrderCreatedSpring(OrderCreatedEvent event) {
    // 处理 Spring Event（本地消费者）
}

// 方式 3：混合监听（同时支持两种方式）
@MQEventListener(topic = "order")
@EventListener
public void onOrderCreated(OrderCreatedEvent event) {
    // 同时处理 MQ 和 Spring Event
}
```

#### 发布模式配置

```yaml
ddd4j:
  mq:
    enabled: true
    broker: rabbit
    publish-mode: BOTH  # MQ | SPRING_EVENT | BOTH
```

| 模式             | 行为                      | 适用场景           |
|----------------|-------------------------|----------------|
| `MQ`           | 只发布到 MQ Broker（默认）      | 纯 MQ 项目，保持向后兼容 |
| `SPRING_EVENT` | 只发布到 Spring Event       | 纯本地项目，无需 MQ    |
| `BOTH`         | 同时发布到 MQ 和 Spring Event | 本地监听 + 远程通知    |

#### 完整使用示例

```java
// 1. 发布事件（自动根据配置选择发布方式）
OrderCreatedEvent event = new OrderCreatedEvent(order);
event.publish();

// 2. 消费方式 A：MQ 监听（远程消费者）
@MQEventListener(topic = "order", group = "order-service")
public void onOrderCreatedMQ(OrderCreatedEvent event) {
    // 处理 MQ 消息（远程消费者）
    log.info("Received MQ event: {}", event.getOrderId());
}

// 3. 消费方式 B：Spring Event 监听（本地消费者）
@EventListener
public void onOrderCreatedSpring(OrderCreatedEvent event) {
    // 处理 Spring Event（本地消费者）
    log.info("Received Spring event: {}", event.getOrderId());
}

// 4. 消费方式 C：混合监听（同时支持两种方式）
@MQEventListener(topic = "order", group = "order-service")
@EventListener
public void onOrderCreated(OrderCreatedEvent event) {
    // 同时处理 MQ 和 Spring Event
    log.info("Received event: {}", event.getOrderId());
}
```

#### 架构优势

| 优势       | 说明                             |
|----------|--------------------------------|
| **向后兼容** | 旧代码无需修改，自动回退到 MQEventPublisher |
| **灵活切换** | 通过配置切换发布模式，无需改代码               |
| **本地解耦** | 支持 Spring Event 实现进程内解耦        |
| **混合部署** | 支持同时发布到 MQ 和 Spring Event      |
| **统一接口** | EventPublisher 作为统一抽象，易于扩展     |

---

## 11. 消费侧设计

### 11.1 `@MQEventListener` 方法签名规范

```java
// ✅ 推荐：直接使用 payload 类型（最简洁）
@MQEventListener(topic = "order")
public void onOrder(OrderEvent event) { ... }

// ✅ 需要 headers 时：声明 Message 参数
@MQEventListener(topic = "order")
public void onOrder(Message<OrderEvent> message) {
    OrderEvent event = message.getPayload();
    String tenantId = message.getHeaders().get("ddd4j.tenant.id", String.class);
}

// ✅ 需要手动 ack 时：声明 Acknowledgment 参数
@MQEventListener(topic = "order")
public void onOrder(OrderEvent event, MessageAcknowledgment ack) {
    try {
        process(event);
        ack.ack();
    } catch (Exception e) {
        ack.nack(true); // requeue
    }
}

// ✅ 需要完整上下文时：声明 MQConsumerContext 参数
@MQEventListener(topic = "order")
public void onOrder(OrderEvent event, MQConsumerContext context) {
    Message<?> message = context.getMessage();
    MessageHeaders headers = message.getHeaders();
    // ...
}
```

### 11.2 返回值规范

| 返回值              | 行为                                                      |
|------------------|---------------------------------------------------------|
| `void`           | 自动 ack（auto 模式）或由 ack-mode 控制                           |
| `AckDisposition` | 显式控制确认（ACK / DISCARD / REQUEUE / REJECT_TO_DLQ / DEFER） |
| `Boolean`        | `true` → ACK，`false` → REQUEUE                          |

### 11.3 绑定命名约定

```
@MQEventListener(topic="order.paid", tags="notify", group="billing-service")
  ↓
函数 Bean 名:              orderPaidNotify
物理 destination:          {namespace}.order.paid
消费组 group:              billing-service
路由键 / tag（rabbit）:    notify
```

---

## 12. `MessageAcknowledgment` — 以 RabbitMQ `Channel` 为基准

### 12.1 设计原则

- 以 AMQP 0-9-1 `com.rabbitmq.client.Channel` 的确认 API 为**完整参考模型**。
- 其他 Broker 通过 Adapter **尽力映射**；不支持的操作抛出 `UnsupportedAckOperationException`。
- 业务层优先使用 `AckDisposition` + `MQConsumeTemplates`。

### 12.2 统一接口

```java
public interface MessageAcknowledgment {
    // ── 元数据 ──
    long deliveryTag();
    String messageId();
    String correlationId();
    boolean isOpen();
    boolean isAcknowledged();
    MQBrokerType brokerType();

    // ── 确认成功 ──
    void ack();
    void ack(boolean multiple);

    // ── 否定确认 ──
    void nack(boolean requeue);
    void nack(boolean multiple, boolean requeue);
    void reject(boolean requeue);
    void recover(boolean requeue);

    // ── 便捷方法 ──
    default void ackSingle() { ack(false); }
    default void discard() { nack(false); }
    default void requeue() { nack(true); }

    // ── 原生逃逸 ──
    <T> Optional<T> unwrap(Class<T> nativeType);
}
```

### 12.3 高层 `AckDisposition`

```java
public enum AckDisposition {
    ACK,              // 成功 → ack(false)
    DISCARD,          // 幂等跳过/终态 → ack(false)
    REQUEUE,          // 瞬时失败 → nack(requeue=true)
    REJECT_TO_DLQ,    // 永久失败 → nack(requeue=false)
    DEFER             // 处理中/锁占用 → nack(requeue=true)
}
```

### 12.4 消费模板

```java
public final class MQConsumeTemplates {
    public static void execute(
            Message<?> message,
            MessageAcknowledgment ack,
            IntSupplier preCheck,              // 0=继续, 1=DISCARD, 2=DEFER
            Supplier<AckDisposition> business);
}
```

### 12.5 多 Broker 映射矩阵

| MessageAcknowledgment | Rabbit               | Kafka          | Pulsar              | Redis Stream       |
|-----------------------|----------------------|----------------|---------------------|--------------------|
| `ack(false)`          | basicAck             | offset commit  | acknowledge         | XACK               |
| `ack(true)`           | basicAck multiple    | commit 至当前     | cumulative ack      | 批量 XACK            |
| `nack(false, true)`   | basicNack requeue    | 不 commit + 重平衡 | negativeAcknowledge | 不 XACK             |
| `nack(false, false)`  | basicNack no requeue | commit + DLT   | ack + DLQ           | XACK + dead stream |
| `recover(requeue)`    | basicRecover         | 不支持            | 不支持                 | XPENDING reclaim   |

---

## 13. 配置体系

### 13.1 配置前缀

| 前缀           | 说明                             |
|--------------|--------------------------------|
| `ddd4j.mq.*` | **唯一主前缀**（`Ddd4jMQProperties`） |

### 13.2 主要配置项

| 属性                  | 类型      | 默认      | 说明                           |
|---------------------|---------|---------|------------------------------|
| `enabled`           | boolean | false   | 是否启用 MQ                      |
| `broker`            | string  | none    | 当前 Broker 类型                 |
| `namespace`         | string  | ""      | 环境/租户级前缀                     |
| `default-topic`     | string  | DEFAULT | `MQEvent.publish()` 默认 topic |
| `consumer.ack-mode` | string  | manual  | manual / auto                |
| `serialization`     | string  | json    | 序列化器                         |

### 13.3 配置示例

**RabbitMQ：**

```yaml
ddd4j:
  mq:
    enabled: true
    broker: rabbit
    namespace: app
    consumer:
      ack-mode: manual

spring:
  rabbitmq:
    host: localhost
    port: 5672
```

**mica-mqtt：**

```yaml
ddd4j:
  mq:
    enabled: true
    broker: mqtt-mica
    namespace: app
    consumer:
      ack-mode: manual
    mica:
      qos: 1

mqtt:
  client:
    enabled: true
    ip: 127.0.0.1
    port: 1883
    client-id: ddd4j-mica-mqtt-001
```

---

## 14. 模块依赖简图

```
ddd4j-core (MQEvent, @MQEventListener)
        │
        ▼
ddd4j-mq  ◄────────────────────────────┐
        │                               │
        ├──► ddd4j-mq-rabbitmq         │
        ├──► ddd4j-mq-kafka            │
        ├──► ddd4j-mq-pulsar           │
        ├──► ddd4j-mq-redis-stream     │
        ├──► ddd4j-mq-activemq         │
        ├──► ddd4j-mq-nats             │
        ├──► ddd4j-mq-mqtt             │
        ├──► ddd4j-mq-mqtt-mica        │
        ├──► ddd4j-mq-disruptor        │
        └──► …                          │
                                        │
ddd4j-cloud-mq-stream ───────────────┘
        │
        ├──► ddd4j-cloud-mq-stream-rabbit
        ├──► ddd4j-cloud-mq-stream-kafka
        └──► …
```

---

*文档版本：2.0 | 维护：ddd4j 团队*
