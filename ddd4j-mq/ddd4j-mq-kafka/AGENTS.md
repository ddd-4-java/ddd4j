# ddd4j-mq-kafka

Kafka producer、consumer、offset 提交与消息头。

## 实现与调用

从 [KafkaMQClient](src/main/java/io/ddd4j/mq/kafka/KafkaMQClient.java)、[KafkaMessageAcknowledgment](src/main/java/io/ddd4j/mq/kafka/KafkaMessageAcknowledgment.java)、[KafkaMQProperties](src/main/java/io/ddd4j/mq/kafka/KafkaMQProperties.java) 进入。

修改 `KafkaMQClient` 时，核对公开入口 `impl`、`defaultConcat`、`initProducer`、`partitionKey` 的调用方。

修改 `KafkaMessageAcknowledgment` 时，核对公开入口 `deliveryTag`、`messageId`、`correlationId`、`isOpen` 的调用方。

## 回归

- [KafkaAcknowledgmentContractTest](src/test/java/io/ddd4j/mq/kafka/KafkaAcknowledgmentContractTest.java) 中可从 `shouldReadStableMessageIdAndCommitOffsetOnAck` 定位断言。

- [KafkaMQClientRoundTripTest](src/test/java/io/ddd4j/mq/kafka/KafkaMQClientRoundTripTest.java) 中可从 `produceConsumeRoundTrip` 定位断言。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-kafka -am -Dtest=KafkaAcknowledgmentContractTest,KafkaMQClientRoundTripTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-kafka -am verify
```
