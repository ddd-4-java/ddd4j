# ddd4j-mq-tdmq

腾讯 TDMQ 订阅、协议确认和资源释放。

## 实现与调用

从 [TdmqAcknowledgment](src/main/java/io/ddd4j/mq/tdmq/TdmqAcknowledgment.java)、[TdmqMQClient](src/main/java/io/ddd4j/mq/tdmq/TdmqMQClient.java)、[TdmqProperties](src/main/java/io/ddd4j/mq/tdmq/TdmqProperties.java) 进入。

修改 `TdmqAcknowledgment` 时，核对公开入口 `deliveryTag`、`messageId`、`correlationId`、`isOpen` 的调用方。

修改 `TdmqMQClient` 时，核对公开入口 `impl`、`initProducer`、`initConsumer`、`start` 的调用方。

## 回归

- [TdmqAcknowledgmentContractTest](src/test/java/io/ddd4j/mq/tdmq/TdmqAcknowledgmentContractTest.java) 中可从 `shouldForwardRequeueIntentToProtocolAdapter` 定位断言。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-tdmq -am -Dtest=TdmqAcknowledgmentContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-tdmq -am verify
```
