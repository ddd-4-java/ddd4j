# ddd4j-mq-ons

阿里云 ONS 生产者生命周期和消费重试。

## 实现与调用

从 [OnsAcknowledgment](src/main/java/io/ddd4j/mq/ons/OnsAcknowledgment.java)、[OnsMQClient](src/main/java/io/ddd4j/mq/ons/OnsMQClient.java)、[OnsProperties](src/main/java/io/ddd4j/mq/ons/OnsProperties.java) 进入。

修改 `OnsAcknowledgment` 时，核对公开入口 `deliveryTag`、`messageId`、`correlationId`、`isOpen` 的调用方。

修改 `OnsMQClient` 时，核对公开入口 `impl`、`lifecycle`、`startupStatus`、`initProducer` 的调用方。

## 回归

- [ConsumerGroupParityTest](src/test/java/io/ddd4j/mq/ons/ConsumerGroupParityTest.java) 中可从 `whitespaceGroupIsRejectedBeforeCreatingBroker`、`whitespaceTopicIsRejectedBeforeCreatingBroker` 定位断言。

- [OnsProducerLifecycleTest](src/test/java/io/ddd4j/mq/ons/OnsProducerLifecycleTest.java) 中可从 `failedOwnedProducerStartShutsDownCreatedProducer`、`failedBorrowedProducerStartDoesNotShutDownProducer` 定位断言。

- [OnsAcknowledgmentContractTest](src/test/java/io/ddd4j/mq/ons/OnsAcknowledgmentContractTest.java) 中可从 `shouldMapNackToProtocolRetryAction` 定位断言。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-ons -am -Dtest=ConsumerGroupParityTest,OnsProducerLifecycleTest,OnsAcknowledgmentContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-ons -am verify
```
