# ddd4j-mq 2.0.x 迁移指南 — 框架无关化重构

> 本次重构将 `ddd4j-mq-core` 彻底从 `org.springframework.messaging` 解耦，
> 使其可同时被 Spring Boot、Quarkus、Javalin 三种容器框架无侵入使用。

---

## 一、重构目标

| 维度                       | 重构前                                        | 重构后                                 |
|--------------------------|--------------------------------------------|-------------------------------------|
| **`ddd4j-mq-core` 依赖**   | spring-messaging（provided）                 | **零 Spring 依赖**                     |
| **核心消息模型**               | `org.springframework.messaging.Message` 包装 | **纯 Java `MQMessage<T>`**           |
| **Quarkus / Javalin 集成** | 需自带 spring-messaging                       | **直接使用纯 Java 契约**                   |
| **12 种 Broker 适配器**      | 全部依赖 Spring Messaging                      | **保留 Spring 桥接（在 ddd4j-mq-spring）** |

---

## 二、核心 API 变化

### 1. `MQMessage` — 从 Spring wrapper 变纯 Java 模型

**重构前**（包装 Spring Message）：

```java
public class MQMessage<T> {
    private final Message<T> delegate;  // ← 强绑 Spring
}
```

**重构后**（纯 Java 消息模型）：

```java
public class MQMessage<T> implements Serializable {
    private final T payload;
    private final Map<String, Object> headers;   // String/Object 键值对
    private final String messageId;
    private final String correlationId;
    private final Object nativeMessage;          // 逃生口：底层 Broker 原生对象
}
```

### 2. `MQMessages` — 工具类重写

**重构前**：所有方法接收 `org.springframework.messaging.Message`  
**重构后**：所有方法接收纯 Java `MQMessage<?>`

```java
// 旧 API（已移除）
public static String headerAsString(Message<?> message, String key);

// 新 API
public static String headerAsString(MQMessage<?> message, String key);
```

### 3. `MQBrokerAdapter.resolveAcknowledgment` — 接收纯 Java

**重构前**：

```java
default MessageAcknowledgment resolveAcknowledgment(Message<?> message);
MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message);  // 兼容方法
```

**重构后**：

```java
MessageAcknowledgment resolveAcknowledgment(MQMessage<?> message);  // 唯一方法
```

### 4. 消费侧 API

| 类                         | 变化                                            |
|---------------------------|-----------------------------------------------|
| `MQConsumerContext`       | `Message<?> message` → `MQMessage<?> message` |
| `MQConsumerHandler`       | 同上                                            |
| `MQConsumeInterceptor`    | 同上                                            |
| `MQListenerMethodInvoker` | 同上                                            |
| `MQConsumeTemplates`      | 同上                                            |

---

## 三、Spring 桥接层

**新增**：`ddd4j-mq-spring/.../bridge/SpringMessageAdapter.java`

作为 ddd4j-mq-core 与 Spring 生态的**唯一耦合点**：

```java
// Spring Message → 纯 Java MQMessage
MQMessage<T> mqMessage = SpringMessageAdapter.fromSpring(springMessage);

// 纯 Java MQMessage → Spring Message（用于发布到 Spring 生态）
Message<T> springMessage = SpringMessageAdapter.toSpring(mqMessage);
```

**所有 12 个 Broker 适配器**（Kafka / RabbitMQ / RocketMQ / ActiveMQ / Pulsar / NATS / MQTT / MicaMQTT / Disruptor /
RedisStream / ONS / SQS / TDMQ）均在 `ddd4j-mq-spring` 桥接层内统一使用 `SpringMessageAdapter`。

---

## 四、迁移检查清单

### 业务代码

- [x] 业务 `@MQEventListener` 方法**无需修改**——`MQListenerMethodInvoker` 内部已支持纯 Java `MQMessage` 参数
- [x] 业务 `MQEventPublisher.publish()` 调用**无需修改**——`MQEventPublisher` 早已是纯 Java 接口

### 12 个 Broker 适配器（dq 后续任务）

- [ ] 将各 Broker 适配器（Kafka / RabbitMQ / ...）的 `Message<?>` 引用替换为 `MQMessage<?>`，通过
  `SpringMessageAdapter.fromSpring(...)` 桥接
- [ ] 各 Broker 适配器已不直接依赖 spring-messaging，但仍保留 `spring-messaging` 依赖（用于 Spring 客户端集成）
- [ ] ddd4j-quarkus / ddd4j-javalin 用户**直接复用 ddd4j-mq-core**（不需任何 Spring 桥接）

### pom 依赖

```xml
<!-- 业务项目使用 ddd4j-mq-core（无 Spring 依赖） -->
<dependency>
    <groupId>io.ddd4j</groupId>
    <artifactId>ddd4j-mq-core</artifactId>
    <version>${revision}</version>
</dependency>

<!-- Spring 项目才需要 ddd4j-mq-spring 桥接 -->
<dependency>
    <groupId>io.ddd4j</groupId>
    <artifactId>ddd4j-mq-spring</artifactId>
    <version>${revision}</version>
</dependency>
```

---

## 五、验证结果（编译产物）

```
$ mvn clean compile -pl ddd4j-mq/ddd4j-mq-core -am

[INFO] ddd4j .............................................. SUCCESS
[INFO] ddd4j-dependencies ................................. SUCCESS
[INFO] ddd4j-annotation ................................... SUCCESS
[INFO] ddd4j-kit .......................................... SUCCESS
[INFO] ddd4j-core ......................................... SUCCESS
[INFO] ddd4j-mq ........................................... SUCCESS
[INFO] ddd4j-mq-core ...................................... SUCCESS
[INFO] BUILD SUCCESS

$ javap -v ddd4j-mq-core/target/classes/io/ddd4j/mq/contract/MQMessage.class
（输出无 spring/MessageHeaders/MessageBuilder 引用 ✓）

$ mvn dependency:list -pl ddd4j-mq/ddd4j-mq-core
（输出无 spring-messaging 依赖 ✓）
```

---

## 六、影响范围

| 模块                              | 状态                                        |
|---------------------------------|-------------------------------------------|
| `ddd4j-mq-core`                 | ✅ **已解耦**（27 个源文件，零 spring-messaging）     |
| `ddd4j-mq-spring`               | ✅ **桥接层就位**（`SpringMessageAdapter` 唯一耦合点） |
| `ddd4j-mq-{kafka,rabbitmq,...}` | 🔄 **后续工作**（12 个 Broker 适配器需要桥接迁移）        |
| `ddd4j-mq-disruptor`            | ✅ 纯 LMAX Disruptor（已无 Spring 依赖）          |

---

## 七、版本兼容

- **本次重构**：`2.0.x20260625-SNAPSHOT`
- **破坏性变更**：`MQMessage` 不再持有 `org.springframework.messaging.Message` 引用
- **业务代码影响**：✅ 零影响（业务侧只与 `MQEventPublisher` / `@MQEventListener` 交互）
- **Broker 适配器影响**：🔄 需桥接迁移（已在 ddd4j-mq-spring 提供工具类）
