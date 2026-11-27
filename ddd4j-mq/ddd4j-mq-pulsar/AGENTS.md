# ddd4j-mq-pulsar

Pulsar 分区键、消息属性与消费确认。

分区策略决定原生消息的 key。改重投时同时检查监听失败和显式不要求重排队两条路径；容器测试从实际订阅端核对带 tag 消息和因果属性。

实现入口见 [PulsarMQClient](src/main/java/io/ddd4j/mq/pulsar/PulsarMQClient.java)、[PulsarAcknowledgment](src/main/java/io/ddd4j/mq/pulsar/PulsarAcknowledgment.java)。

## 回归

优先运行 [PulsarPartitionKeyContractTest](src/test/java/io/ddd4j/mq/pulsar/PulsarPartitionKeyContractTest.java)、[PulsarContainerIntegrationTest](src/test/java/io/ddd4j/mq/pulsar/PulsarContainerIntegrationTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-pulsar -am -Dtest=PulsarPartitionKeyContractTest,PulsarContainerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-pulsar -am verify
```

所列集成用例使用 Testcontainers。核对测试报告中对应测试类的执行数和跳过数；只运行单元用例时，单独记录仍缺的数据库或 broker 往返。
