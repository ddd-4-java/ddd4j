# ddd4j-mq-spring

Spring 监听 Bean 发现、消息转换和客户端生命周期。

从监听 Bean 的发现追到客户端初始化。必需客户端失败应传出 context refresh；销毁路径按创建顺序逆序关闭且每个客户端仅一次。消息的 ack、重投和协议头归具体 broker。

实现入口见 [MQListenerBeanPostProcessor](src/main/java/io/ddd4j/mq/spring/registry/MQListenerBeanPostProcessor.java)。

## 回归

优先运行 [MQListenerLifecycleContractTest](src/test/java/io/ddd4j/mq/spring/registry/MQListenerLifecycleContractTest.java)、[SpringMqBridgeContractTest](src/test/java/io/ddd4j/mq/spring/registry/SpringMqBridgeContractTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-spring -am -Dtest=MQListenerLifecycleContractTest,SpringMqBridgeContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-spring -am verify
```
