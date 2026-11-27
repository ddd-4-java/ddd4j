# ddd4j-mq-rabbitmq

AMQP 持久化消息、publisher confirm 和消费 nack。

## 实现与调用

从 [RabbitAcknowledgment](src/main/java/io/ddd4j/mq/rabbitmq/RabbitAcknowledgment.java)、[RabbitMQClient](src/main/java/io/ddd4j/mq/rabbitmq/RabbitMQClient.java)、[RabbitMQProperties](src/main/java/io/ddd4j/mq/rabbitmq/RabbitMQProperties.java) 进入。

修改 `RabbitAcknowledgment` 时，核对公开入口 `deliveryTag`、`messageId`、`correlationId`、`isOpen` 的调用方。

修改 `RabbitMQClient` 时，核对公开入口 `impl`、`initProducer`、`initConsumer` 的调用方。

## 回归

- [RabbitMQAdapterContractTest](src/test/java/io/ddd4j/mq/rabbitmq/RabbitMQAdapterContractTest.java) 中可从 `shouldPreferStableMessageIdAndNackForRedelivery` 定位断言。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-rabbitmq -am -Dtest=RabbitMQAdapterContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-rabbitmq -am verify
```
