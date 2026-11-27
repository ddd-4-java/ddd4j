# ddd4j-mq-nats

NATS JetStream 发布确认、原生 header 与重投。

## 实现与调用

从 [NatsAcknowledgment](src/main/java/io/ddd4j/mq/nats/NatsAcknowledgment.java)、[NatsMQClient](src/main/java/io/ddd4j/mq/nats/NatsMQClient.java)、[NatsProperties](src/main/java/io/ddd4j/mq/nats/NatsProperties.java) 进入。

修改 `NatsAcknowledgment` 时，核对公开入口 `deliveryTag`、`messageId`、`correlationId`、`isOpen` 的调用方。

修改 `NatsMQClient` 时，核对公开入口 `impl`、`supportsBrokerTagFilter`、`initProducer`、`initConsumer` 的调用方。

## 回归

- [NatsAcknowledgmentContractTest](src/test/java/io/ddd4j/mq/nats/NatsAcknowledgmentContractTest.java) 中可从 `shouldMapNackToJetStreamNakForRedelivery` 定位断言。

- [NatsMessageHeaderTest](src/test/java/io/ddd4j/mq/nats/NatsMessageHeaderTest.java) 中可从 `shouldWriteStableHeaderAndPreferItWhenReading` 定位断言。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-nats -am -Dtest=NatsAcknowledgmentContractTest,NatsMessageHeaderTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-nats -am verify
```
