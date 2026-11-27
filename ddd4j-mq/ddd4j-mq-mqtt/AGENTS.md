# ddd4j-mq-mqtt

Eclipse Paho MQTT 客户端、QoS 与持久会话。

Paho 客户端处理 QoS 消息时，handler 抛错后不能提前确认。用持久会话和 QoS 1 验证重投；不受支持的确认操作失败后仍应允许有效 ack。注入客户端的关闭权归调用方。

实现入口见 [MqttMQClient](src/main/java/io/ddd4j/mq/mqtt/MqttMQClient.java)、[MqttAcknowledgment](src/main/java/io/ddd4j/mq/mqtt/MqttAcknowledgment.java)。

## 回归

优先运行 [MqttAdapterContractTest](src/test/java/io/ddd4j/mq/mqtt/MqttAdapterContractTest.java)、[MqttContainerIntegrationTest](src/test/java/io/ddd4j/mq/mqtt/MqttContainerIntegrationTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-mqtt -am -Dtest=MqttAdapterContractTest,MqttContainerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-mqtt -am verify
```

所列集成用例使用 Testcontainers。核对测试报告中对应测试类的执行数和跳过数；只运行单元用例时，单独记录仍缺的数据库或 broker 往返。
