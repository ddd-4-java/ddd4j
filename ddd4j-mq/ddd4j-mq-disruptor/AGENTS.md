# ddd4j-mq-disruptor

进程内 ring buffer 发布、路由和消费。

事件携带原始 topic 时避免再次拼接路由；事件缺少 namespace 时使用配置值。修改 ring buffer 发布或消费者匹配时，断言目标订阅者恰好收到一次，防止同进程重复交付。

实现入口见 [DisruptorMQClient](src/main/java/io/ddd4j/mq/disruptor/DisruptorMQClient.java)。

## 回归

优先运行 [DisruptorAdapterContractTest](src/test/java/io/ddd4j/mq/disruptor/DisruptorAdapterContractTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-disruptor -am -Dtest=DisruptorAdapterContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-disruptor -am verify
```
