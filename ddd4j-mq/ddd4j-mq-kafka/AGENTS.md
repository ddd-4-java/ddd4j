# ddd4j-mq-kafka

Kafka producer、consumer、offset 提交与消息头。

消费确认的完成点是 offset 提交成功。提交失败后要允许重试，并按消费路径恢复读取位置。显式 listener group 与默认前缀分别测试。注入的 producer 由创建者管理，关闭客户端不能顺带关闭它。

实现入口见 [KafkaMQClient](src/main/java/io/ddd4j/mq/kafka/KafkaMQClient.java)、[KafkaMessageAcknowledgment](src/main/java/io/ddd4j/mq/kafka/KafkaMessageAcknowledgment.java)。

## 回归

优先运行 [KafkaAcknowledgmentContractTest](src/test/java/io/ddd4j/mq/kafka/KafkaAcknowledgmentContractTest.java)、[KafkaMQClientDurabilityContractTest](src/test/java/io/ddd4j/mq/kafka/KafkaMQClientDurabilityContractTest.java)、[KafkaMQClientLifecycleContractTest](src/test/java/io/ddd4j/mq/kafka/KafkaMQClientLifecycleContractTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-kafka -am -Dtest=KafkaAcknowledgmentContractTest,KafkaMQClientDurabilityContractTest,KafkaMQClientLifecycleContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-kafka -am verify
```
