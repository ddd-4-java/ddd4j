# ddd4j-mq-core

消息契约、交付策略、客户端启停与可靠投递接口。

改变初始化顺序时让必需的 listener 启动失败一次，确认之前创建的 publisher 及资源被回收。保留原构造器的默认要求。持久化投递的失败语义在 core 定义，再由各 broker 映射为协议动作。

实现入口见 [MQClient](src/main/java/io/ddd4j/mq/MQClient.java)、[MQDeliveryPolicy](src/main/java/io/ddd4j/mq/delivery/MQDeliveryPolicy.java)。

## 回归

优先运行 [MQClientInitializationContractTest](src/test/java/io/ddd4j/mq/MQClientInitializationContractTest.java)、[MQClientPersistenceContractTest](src/test/java/io/ddd4j/mq/MQClientPersistenceContractTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-core -am -Dtest=MQClientInitializationContractTest,MQClientPersistenceContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-core -am verify
```
