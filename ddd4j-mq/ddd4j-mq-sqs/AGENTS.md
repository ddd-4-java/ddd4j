# ddd4j-mq-sqs

SQS 消息属性、接收、删除和失败后的再次交付。

## 实现与调用

从 [SqsAcknowledgment](src/main/java/io/ddd4j/mq/sqs/SqsAcknowledgment.java)、[SqsMQClient](src/main/java/io/ddd4j/mq/sqs/SqsMQClient.java)、[SqsProperties](src/main/java/io/ddd4j/mq/sqs/SqsProperties.java) 进入。

修改 `SqsAcknowledgment` 时，核对公开入口 `deliveryTag`、`messageId`、`correlationId`、`isOpen` 的调用方。

修改 `SqsMQClient` 时，核对公开入口 `impl`、`supportsBrokerTagFilter`、`initProducer`、`initConsumer` 的调用方。

## 回归

- [SqsAdapterContractTest](src/test/java/io/ddd4j/mq/sqs/SqsAdapterContractTest.java) 中可从 `shouldPreferStableMessageIdAndResetVisibilityForRedelivery` 定位断言。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-sqs -am -Dtest=SqsAdapterContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-sqs -am verify
```
