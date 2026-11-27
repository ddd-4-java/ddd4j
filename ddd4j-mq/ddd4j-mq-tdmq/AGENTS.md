# ddd4j-mq-tdmq

腾讯 TDMQ 订阅、协议确认和资源释放。

竞争的 ack 和 nack 最多触发一次成功的协议操作。回调失败要保留重试机会。关闭后拒绝新的生产或消费初始化，同时避免同一订阅被重复关闭。

实现入口见 [TdmqMQClient](src/main/java/io/ddd4j/mq/tdmq/TdmqMQClient.java)、[TdmqAcknowledgment](src/main/java/io/ddd4j/mq/tdmq/TdmqAcknowledgment.java)。

## 回归

优先运行 [TdmqAcknowledgmentContractTest](src/test/java/io/ddd4j/mq/tdmq/TdmqAcknowledgmentContractTest.java)、[TdmqClientLifecycleTest](src/test/java/io/ddd4j/mq/tdmq/TdmqClientLifecycleTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-tdmq -am -Dtest=TdmqAcknowledgmentContractTest,TdmqClientLifecycleTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-tdmq -am verify
```
