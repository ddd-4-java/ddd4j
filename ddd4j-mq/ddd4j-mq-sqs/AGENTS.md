# ddd4j-mq-sqs

SQS 消息属性、接收、删除和失败后的再次交付。

发送端使用冻结的因果属性，成功后记录日志不应再次序列化消息。消费流程在成功后删除；先让 listener 失败，再观察同一消息重新交付，成功后才消失。

实现入口见 [SqsMQClient](src/main/java/io/ddd4j/mq/sqs/SqsMQClient.java)、[SqsAcknowledgment](src/main/java/io/ddd4j/mq/sqs/SqsAcknowledgment.java)。

## 回归

优先运行 [SqsAdapterContractTest](src/test/java/io/ddd4j/mq/sqs/SqsAdapterContractTest.java)、[SqsBrokerIntegrationTest](src/test/java/io/ddd4j/mq/sqs/SqsBrokerIntegrationTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-sqs -am -Dtest=SqsAdapterContractTest,SqsBrokerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-sqs -am verify
```

所列集成用例使用 Testcontainers。核对测试报告中对应测试类的执行数和跳过数；只运行单元用例时，单独记录仍缺的数据库或 broker 往返。
