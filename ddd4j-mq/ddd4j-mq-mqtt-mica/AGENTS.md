# ddd4j-mq-mqtt-mica

mica-mqtt AIO 连接就绪、订阅与确认回调。

## 实现与调用

从 [MicaMqttAcknowledgment](src/main/java/io/ddd4j/mq/mqttmica/MicaMqttAcknowledgment.java)、[MicaMqttMQClient](src/main/java/io/ddd4j/mq/mqttmica/MicaMqttMQClient.java)、[MicaMqttProperties](src/main/java/io/ddd4j/mq/mqttmica/MicaMqttProperties.java) 进入。

修改 `MicaMqttAcknowledgment` 时，核对公开入口 `deliveryTag`、`messageId`、`correlationId`、`isOpen` 的调用方。

修改 `MicaMqttMQClient` 时，核对公开入口 `impl`、`defaultConcat`、`supportsBrokerTagFilter`、`initProducer` 的调用方。

## 回归

- [MicaMqttAcknowledgmentContractTest](src/test/java/io/ddd4j/mq/mqttmica/MicaMqttAcknowledgmentContractTest.java) 中可从 `shouldMarkNackAsHandledBecauseMicaHasNoNativeRedelivery` 定位断言。

- [MicaMqttMessageHeaderTest](src/test/java/io/ddd4j/mq/mqttmica/MicaMqttMessageHeaderTest.java) 中可从 `shouldPreferStableUserPropertyAndReadLegacyProperty` 定位断言。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-mqtt-mica -am -Dtest=MicaMqttAcknowledgmentContractTest,MicaMqttMessageHeaderTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-mqtt-mica -am verify
```
