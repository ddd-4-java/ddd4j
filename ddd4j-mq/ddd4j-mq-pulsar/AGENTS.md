# ddd4j-mq-pulsar

Pulsar 分区键、消息属性与消费确认。

## 实现与调用

从 [PulsarAcknowledgment](src/main/java/io/ddd4j/mq/pulsar/PulsarAcknowledgment.java)、[PulsarMQClient](src/main/java/io/ddd4j/mq/pulsar/PulsarMQClient.java)、[PulsarProperties](src/main/java/io/ddd4j/mq/pulsar/PulsarProperties.java) 进入。

修改 `PulsarAcknowledgment` 时，核对公开入口 `deliveryTag`、`messageId`、`correlationId`、`isOpen` 的调用方。

修改 `PulsarMQClient` 时，核对公开入口 `impl`、`partitionKey`、`initProducer`、`initConsumer` 的调用方。

## 回归

- [PulsarAdapterContractTest](src/test/java/io/ddd4j/mq/pulsar/PulsarAdapterContractTest.java) 中可从 `shouldPreferStableMessageIdAndNegativeAcknowledgeOnRetry` 定位断言。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-pulsar -am -Dtest=PulsarAdapterContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-pulsar -am verify
```
