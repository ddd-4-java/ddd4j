# ddd4j-mq-activemq

JMS Client ID、原生消息属性和消费确认。

## 实现与调用

从 [ActiveMQAcknowledgment](src/main/java/io/ddd4j/mq/activemq/ActiveMQAcknowledgment.java)、[ActiveMQClient](src/main/java/io/ddd4j/mq/activemq/ActiveMQClient.java)、[ActiveMQProperties](src/main/java/io/ddd4j/mq/activemq/ActiveMQProperties.java) 进入。

修改 `ActiveMQAcknowledgment` 时，核对公开入口 `deliveryTag`、`messageId`、`correlationId`、`isOpen` 的调用方。

修改 `ActiveMQClient` 时，核对公开入口 `impl`、`lifecycle`、`startupStatus`、`initProducer` 的调用方。

## 回归

- [ArtemisContainerIntegrationTest](src/test/java/io/ddd4j/mq/activemq/ArtemisContainerIntegrationTest.java) 中可从 `nativeJmsCausalPropertiesShouldRoundTripThroughClient`、`failedHandlerIsRedeliveredAndPublishedMessageKeepsItsId` 定位断言。

- [ActivemqKitContractTest](src/test/java/io/ddd4j/mq/activemq/util/ActivemqKitContractTest.java) 中可从 `resolvesPhysicalDestinationAndJmsKind`、`extractsTextAndBytesPayload` 定位断言。

- [ActiveMQAdapterContractTest](src/test/java/io/ddd4j/mq/activemq/ActiveMQAdapterContractTest.java) 中可从 `shouldPublishCausalHeadersAsNativeJmsProperties`、`shouldConstructArtemisFactoryWithoutContainer` 定位断言。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-activemq -am -Dtest=ArtemisContainerIntegrationTest,ActivemqKitContractTest,ActiveMQAdapterContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-activemq -am verify
```
