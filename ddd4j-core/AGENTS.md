# ddd4j-core

核心仓储、聚合根与命令执行接口。CommandBus.execute 和命令执行器统一返回 io.ddd4j.core.api.R。调用方通过 isOk() 判断业务结果，使用 getData() 和 getMsg() 读取载荷与消息。旧 Result 的方法签名已移除，升级框架时需重新编译执行器及客户端。

## 实现与调用

从 [CommandBus](src/main/java/io/ddd4j/core/cqrs/command/CommandBus.java)、[Repository](src/main/java/io/ddd4j/core/ddd/repository/Repository.java)、[R](src/main/java/io/ddd4j/core/api/R.java) 进入。

## 回归

- [RTest](src/test/java/io/ddd4j/core/api/RTest.java) 中可从 `ok_shouldReturnSuccessCodeAndNullData`、`ok_withData_shouldCarryPayload` 定位断言。

- [AggregateRootEventsTest](src/test/java/io/ddd4j/core/ddd/model/AggregateRootEventsTest.java) 中可从 `registerEvent_shouldExposeEventViaDomainEvents`、`domainEvents_shouldBeImmutableView` 定位断言。

- [AggregateRootTest](src/test/java/io/ddd4j/core/ddd/model/AggregateRootTest.java) 中可从 `shouldBufferPullAndClearDomainEvents`、`shouldRejectNullDomainEvent` 定位断言。

```sh
./mvnw -pl ddd4j-core -am -Dtest=RTest,AggregateRootEventsTest,AggregateRootTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-core -am verify
```
