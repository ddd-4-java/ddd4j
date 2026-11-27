# ddd4j-mq-mqtt-mica

mica-mqtt AIO 连接就绪、订阅与确认回调。

订阅需要等原生 AIO 客户端连接就绪。修改 nack 时区分是否要求 requeue：不支持的请求应失败且保留确认机会。协议头兼容性另查稳定 user property 与旧字段的读取顺序。

实现入口见 [MicaMqttMQClient](src/main/java/io/ddd4j/mq/mqttmica/MicaMqttMQClient.java)、[MicaMqttAcknowledgment](src/main/java/io/ddd4j/mq/mqttmica/MicaMqttAcknowledgment.java)。

## 回归

优先运行 [MicaMqttConnectionReadinessTest](src/test/java/io/ddd4j/mq/mqttmica/MicaMqttConnectionReadinessTest.java)、[MicaMqttAcknowledgmentContractTest](src/test/java/io/ddd4j/mq/mqttmica/MicaMqttAcknowledgmentContractTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-mqtt-mica -am -Dtest=MicaMqttConnectionReadinessTest,MicaMqttAcknowledgmentContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-mqtt-mica -am verify
```
