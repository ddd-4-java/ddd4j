# ddd4j-mq-nats

NATS JetStream 发布确认、原生 header 与重投。

把需要重投的 nack 映射为 JetStream 的 nak。调整 correlation 或消息标识时分别检查发布的原生 header 和消费侧优先读取的字段；用 JetStream 验证真正收到发布确认。

实现入口见 [NatsMQClient](src/main/java/io/ddd4j/mq/nats/NatsMQClient.java)、[NatsAcknowledgment](src/main/java/io/ddd4j/mq/nats/NatsAcknowledgment.java)。

## 回归

优先运行 [NatsAcknowledgmentContractTest](src/test/java/io/ddd4j/mq/nats/NatsAcknowledgmentContractTest.java)、[NatsJetStreamIntegrationTest](src/test/java/io/ddd4j/mq/nats/NatsJetStreamIntegrationTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-nats -am -Dtest=NatsAcknowledgmentContractTest,NatsJetStreamIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-nats -am verify
```

所列集成用例使用 Testcontainers。核对测试报告中对应测试类的执行数和跳过数；只运行单元用例时，单独记录仍缺的数据库或 broker 往返。
