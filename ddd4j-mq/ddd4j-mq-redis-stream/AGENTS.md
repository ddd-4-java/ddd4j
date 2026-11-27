# ddd4j-mq-redis-stream

Redis Stream 记录、消费组与 XACK。

手工确认映射到 XACK。并发 nack 与正在执行的 XACK 之间必须按最终确认结果决定是否重排队；只断言本地布尔状态会漏掉竞态。变更记录编码后，用既有公开字段做 JSON 往返。

实现入口见 [RedisStreamMQClient](src/main/java/io/ddd4j/mq/redisstream/RedisStreamMQClient.java)、[RedisStreamAcknowledgment](src/main/java/io/ddd4j/mq/redisstream/RedisStreamAcknowledgment.java)。

## 回归

优先运行 [RedisStreamBrokerAdapterTest](src/test/java/io/ddd4j/mq/redisstream/RedisStreamBrokerAdapterTest.java)、[RedisStreamRecordJsonContractTest](src/test/java/io/ddd4j/mq/redisstream/RedisStreamRecordJsonContractTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-redis-stream -am -Dtest=RedisStreamBrokerAdapterTest,RedisStreamRecordJsonContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-redis-stream -am verify
```
