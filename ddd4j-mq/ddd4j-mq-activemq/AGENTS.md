# ddd4j-mq-activemq

JMS Client ID、原生消息属性和消费确认。

客户端工厂设置 clientId 时只取一次配置前缀。改 JMS 映射时从消费消息的原生 properties 核对因果头；用 Artemis 容器触发一次 handler 失败，观察重新投递。

实现入口见 [ActiveMQClient](src/main/java/io/ddd4j/mq/activemq/ActiveMQClient.java)、[ActiveMQAcknowledgment](src/main/java/io/ddd4j/mq/activemq/ActiveMQAcknowledgment.java)。

## 回归

优先运行 [ActiveMQClientIdConfigurationTest](src/test/java/io/ddd4j/mq/activemq/ActiveMQClientIdConfigurationTest.java)、[ArtemisContainerIntegrationTest](src/test/java/io/ddd4j/mq/activemq/ArtemisContainerIntegrationTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-activemq -am -Dtest=ActiveMQClientIdConfigurationTest,ArtemisContainerIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-activemq -am verify
```

所列集成用例使用 Testcontainers。核对测试报告中对应测试类的执行数和跳过数；只运行单元用例时，单独记录仍缺的数据库或 broker 往返。
