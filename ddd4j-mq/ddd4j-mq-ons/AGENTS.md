# ddd4j-mq-ons

阿里云 ONS 生产者生命周期和消费重试。

创建 broker 前先验证 group 与 topic，纯空白输入应被拒绝。生产者启动失败时清理本模块创建的实例；注入的 producer 保留调用者的所有权。消费失败的重试结果要映射为 ONS 协议动作。

实现入口见 [OnsMQClient](src/main/java/io/ddd4j/mq/ons/OnsMQClient.java)、[OnsAcknowledgment](src/main/java/io/ddd4j/mq/ons/OnsAcknowledgment.java)。

## 回归

优先运行 [OnsRollbackRetryTest](src/test/java/io/ddd4j/mq/ons/OnsRollbackRetryTest.java)、[OnsProducerLifecycleTest](src/test/java/io/ddd4j/mq/ons/OnsProducerLifecycleTest.java)、[ConsumerGroupParityTest](src/test/java/io/ddd4j/mq/ons/ConsumerGroupParityTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-ons -am -Dtest=OnsRollbackRetryTest,OnsProducerLifecycleTest,ConsumerGroupParityTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-ons -am verify
```
