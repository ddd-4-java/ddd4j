# ddd4j-mq-spring

Spring 监听 Bean 发现、消息转换和客户端生命周期。

## 实现与调用

从 [MQListenerBeanPostProcessor](src/main/java/io/ddd4j/mq/spring/registry/MQListenerBeanPostProcessor.java)、[Ddd4jMQPropertiesConfiguration](src/main/java/io/ddd4j/mq/spring/config/Ddd4jMQPropertiesConfiguration.java)、[Ddd4jMQRegistrarConfiguration](src/main/java/io/ddd4j/mq/spring/config/Ddd4jMQRegistrarConfiguration.java) 进入。

修改 `MQListenerBeanPostProcessor` 时，核对公开入口 `postProcessAfterInitialization` 的调用方。

修改 `Ddd4jMQPropertiesConfiguration` 时，核对公开入口 `ddd4jMQProperties`、`mqEventSerialization`、`mqEventStorer` 的调用方。

## 回归

- [SpringMqBridgeContractTest](src/test/java/io/ddd4j/mq/spring/registry/SpringMqBridgeContractTest.java) 中可从 `shouldDiscoverListenerWithoutDefiningBrokerMessageSemantics` 定位断言。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-spring -am -Dtest=SpringMqBridgeContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-spring -am verify
```
