# ddd4j-mq-rabbitmq

AMQP 持久化消息、publisher confirm 和消费 nack。

发布端的持久化属性与 broker confirm 是两个独立步骤。改成功返回时机时检查是否已等待 confirm；消费失败要求重投时核对 native nack 的 requeue 参数。

实现入口见 [RabbitMQClient](src/main/java/io/ddd4j/mq/rabbitmq/RabbitMQClient.java)、[RabbitAcknowledgment](src/main/java/io/ddd4j/mq/rabbitmq/RabbitAcknowledgment.java)。

## 回归

优先运行 [RabbitMQAdapterContractTest](src/test/java/io/ddd4j/mq/rabbitmq/RabbitMQAdapterContractTest.java)、[RabbitMQContainerIntegrationTest](src/test/java/io/ddd4j/mq/rabbitmq/RabbitMQContainerIntegrationTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-rabbitmq -am -Dtest=RabbitMQAdapterContractTest,RabbitMQContainerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-rabbitmq -am verify
```

所列集成用例使用 Testcontainers。核对测试报告中对应测试类的执行数和跳过数；只运行单元用例时，单独记录仍缺的数据库或 broker 往返。
