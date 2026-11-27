# ddd4j-mq-rocketmq

RocketMQ 命名空间、标签、路由键和重新消费。

## 实现与调用

从 [RocketAcknowledgment](src/main/java/io/ddd4j/mq/rocketmq/RocketAcknowledgment.java)、[RocketMQClient](src/main/java/io/ddd4j/mq/rocketmq/RocketMQClient.java)、[RocketMQProperties](src/main/java/io/ddd4j/mq/rocketmq/RocketMQProperties.java) 进入。

修改 `RocketAcknowledgment` 时，核对公开入口 `deliveryTag`、`messageId`、`correlationId`、`isOpen` 的调用方。

修改 `RocketMQClient` 时，核对公开入口 `impl`、`initProducer`、`partitionKey`、`initConsumer` 的调用方。

## 回归

- [RocketAcknowledgmentContractTest](src/test/java/io/ddd4j/mq/rocketmq/RocketAcknowledgmentContractTest.java) 中可从 `shouldRequestBrokerReconsumeOnNack` 定位断言。

- [RocketMessageHeaderTest](src/test/java/io/ddd4j/mq/rocketmq/RocketMessageHeaderTest.java) 中可从 `shouldPreferStableHeaderAndReadLegacyHeader` 定位断言。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-rocketmq -am -Dtest=RocketAcknowledgmentContractTest,RocketMessageHeaderTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-rocketmq -am verify
```
