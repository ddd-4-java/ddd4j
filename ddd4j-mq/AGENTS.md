# ddd4j-mq

按故障选入口：消息协议与初始化在 mq-core，Spring Bean 发现和销毁在 mq-spring。原生确认、分区键、重投和客户端所有权由具体 broker 模块实现。修改公共协议后至少选一个真实 broker 做往返。

## 按变更选择模块

| 工件 | 修改范围 |
|---|---|
| [ddd4j-mq-core](ddd4j-mq-core/AGENTS.md) | 消息契约、交付策略、客户端启停与可靠投递接口 |
| [ddd4j-mq-activemq](ddd4j-mq-activemq/AGENTS.md) | JMS Client ID、原生消息属性和消费确认 |
| [ddd4j-mq-disruptor](ddd4j-mq-disruptor/AGENTS.md) | 进程内 ring buffer 发布、路由和消费 |
| [ddd4j-mq-kafka](ddd4j-mq-kafka/AGENTS.md) | Kafka producer、consumer、offset 提交与消息头 |
| [ddd4j-mq-mqtt](ddd4j-mq-mqtt/AGENTS.md) | Eclipse Paho MQTT 客户端、QoS 与持久会话 |
| [ddd4j-mq-mqtt-mica](ddd4j-mq-mqtt-mica/AGENTS.md) | mica-mqtt AIO 连接就绪、订阅与确认回调 |
| [ddd4j-mq-nats](ddd4j-mq-nats/AGENTS.md) | NATS JetStream 发布确认、原生 header 与重投 |
| [ddd4j-mq-ons](ddd4j-mq-ons/AGENTS.md) | 阿里云 ONS 生产者生命周期和消费重试 |
| [ddd4j-mq-pulsar](ddd4j-mq-pulsar/AGENTS.md) | Pulsar 分区键、消息属性与消费确认 |
| [ddd4j-mq-rabbitmq](ddd4j-mq-rabbitmq/AGENTS.md) | AMQP 持久化消息、publisher confirm 和消费 nack |
| [ddd4j-mq-redis-stream](ddd4j-mq-redis-stream/AGENTS.md) | Redis Stream 记录、消费组与 XACK |
| [ddd4j-mq-rocketmq](ddd4j-mq-rocketmq/AGENTS.md) | RocketMQ 命名空间、标签、路由键和重新消费 |
| [ddd4j-mq-sqs](ddd4j-mq-sqs/AGENTS.md) | SQS 消息属性、接收、删除和失败后的再次交付 |
| [ddd4j-mq-tdmq](ddd4j-mq-tdmq/AGENTS.md) | 腾讯 TDMQ 订阅、协议确认和资源释放 |
| [ddd4j-mq-spring](ddd4j-mq-spring/AGENTS.md) | Spring 监听 Bean 发现、消息转换和客户端生命周期 |

## 验证范围

从仓库根目录执行整组验证时使用下列命令。只改一个实现时，进入该模块指引选择对应的行为测试。

```sh
./mvnw -pl ddd4j-mq -am verify
```
