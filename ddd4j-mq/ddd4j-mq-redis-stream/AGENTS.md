# ddd4j-mq-redis-stream

Redis Stream 记录、消费组与 XACK。

## 实现与调用

从 [RedisStreamAcknowledgment](src/main/java/io/ddd4j/mq/redisstream/RedisStreamAcknowledgment.java)、[RedisStreamMQClient](src/main/java/io/ddd4j/mq/redisstream/RedisStreamMQClient.java)、[RedisMQClient](src/main/java/io/ddd4j/mq/redisstream/RedisMQClient.java) 进入。

修改 `RedisStreamAcknowledgment` 时，核对公开入口 `deliveryTag`、`messageId`、`correlationId`、`isOpen` 的调用方。

修改 `RedisStreamMQClient` 时，核对公开入口 `impl`、`defaultConcat`、`initProducer`、`initConsumer` 的调用方。

## 回归

- [RedisStreamBrokerAdapterTest](src/test/java/io/ddd4j/mq/redisstream/RedisStreamBrokerAdapterTest.java) 中可从 `manualAckShouldMapToXack`、`redissonOperationsShouldMapStreamCommands` 定位断言。

- [RedisStreamMQClientTest](src/test/java/io/ddd4j/mq/redisstream/RedisStreamMQClientTest.java) 中可从 `impl_shouldReturnRedisStream`、`defaultConcat_shouldReturnColon` 定位断言。

```sh
./mvnw -pl ddd4j-mq/ddd4j-mq-redis-stream -am -Dtest=RedisStreamBrokerAdapterTest,RedisStreamMQClientTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-mq/ddd4j-mq-redis-stream -am verify
```
