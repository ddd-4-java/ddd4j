# ddd4j-mq-mqtt

Eclipse Paho MQTT 客户端、QoS 与持久会话。

## 实现与调用

从 [MqttAcknowledgment](src/main/java/io/ddd4j/mq/mqtt/MqttAcknowledgment.java)、[MqttMQClient](src/main/java/io/ddd4j/mq/mqtt/MqttMQClient.java)、[MqttMQProperties](src/main/java/io/ddd4j/mq/mqtt/MqttMQProperties.java) 进入。

修改 `MqttAcknowledgment` 时，核对公开入口 `deliveryTag`、`messageId`、`correlationId`、`isOpen` 的调用方。

修改 `MqttMQClient` 时，核对公开入口 `impl`、`defaultConcat`、`supportsBrokerTagFilter`、`initProducer` 的调用方。

## 回归

- [MqttAdapterContractTest](src/test/java/io/ddd4j/mq/mqtt/MqttAdapterContractTest.java) 中可从 `shouldTreatMessageIdAsPayloadConcernAndRejectNativeNack` 定位断言。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-mqtt -am -Dtest=MqttAdapterContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-mqtt -am verify
```
