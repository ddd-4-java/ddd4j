# ddd4j-mq-core

消息契约、交付策略、客户端启停与可靠投递接口。

## 实现与调用

从 [MQClient](src/main/java/io/ddd4j/mq/MQClient.java)、[MQDeliveryPolicy](src/main/java/io/ddd4j/mq/delivery/MQDeliveryPolicy.java)、[MQOutboxDispatcher](src/main/java/io/ddd4j/mq/delivery/MQOutboxDispatcher.java) 进入。

修改 `MQDeliveryPolicy` 时，核对公开入口 `productionDefault`、`nextAvailableAt`、`exhausted`、`leaseDuration` 的调用方。

## 回归

- [MQClientTagsSelectorTest](src/test/java/io/ddd4j/mq/MQClientTagsSelectorTest.java) 中可从 `wildcardShouldReturnNull`、`singleTagShouldEqualMatch` 定位断言。

- [MQDeliveryPolicyTest](src/test/java/io/ddd4j/mq/delivery/MQDeliveryPolicyTest.java) 中可从 `productionDefault_shouldExposeTheDocumentedReliabilityDefaults`、`nextAvailableAt_shouldApplyCappedExponentialBackoffAndJitter` 定位断言。

- [MQOutboxDispatcherTest](src/test/java/io/ddd4j/mq/delivery/MQOutboxDispatcherTest.java) 中可从 `dispatch_shouldPublishOutsideStoreAndConfirmWithLeaseOwner`、`dispatch_shouldRescheduleFailedMessageBeforeMaximumAttempts` 定位断言。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-core -am -Dtest=MQClientTagsSelectorTest,MQDeliveryPolicyTest,MQOutboxDispatcherTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-core -am verify
```
