# ddd4j-core

核心仓储、聚合根与命令执行接口。CommandBus.execute 返回 Result，API 响应 R 由独立接口提供。修改返回值前检查调用方的泛型与异常分支。

## 实现与调用

从 [CommandBus](src/main/java/io/ddd4j/core/cqrs/command/CommandBus.java)、[Repository](src/main/java/io/ddd4j/core/ddd/repository/Repository.java)、[R](src/main/java/io/ddd4j/core/api/R.java) 进入。

## 回归

- [RTest](src/test/java/io/ddd4j/core/api/RTest.java) 中可从 `fail_withString_shouldTreatArgumentAsMessage`、`fail_withNullObject_shouldKeepFailureMetadata` 定位断言。

- [EventStoreContractTest](src/test/java/io/ddd4j/core/cqrs/eventstore/EventStoreContractTest.java) 中可从 `appendAndReadShouldPreserveTypedDomainEvents`、`appendWithStaleVersionShouldExposeTypedConflict` 定位断言。

- [AggregateRootApplyTest](src/test/java/io/ddd4j/core/ddd/model/AggregateRootApplyTest.java) 中可从 `applyInvokesAnnotatedHandlerAndRegistersEvent`、`applyThrowsWhenNoHandlerRegistered` 定位断言。

```sh
./mvnw -pl ddd4j-core -am -Dtest=RTest,EventStoreContractTest,AggregateRootApplyTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-core -am verify
```

## 投影维护

共享投影分发层。维护 ProjectionHandler 的发现、注册与 ProjectionDispatcher 的路由；位置持久化及运行时调度分别由具体适配模块负责。

### 实现与调用

从 [ProjectionDispatcher](src/main/java/io/ddd4j/core/cqrs/readmodel/handler/ProjectionDispatcher.java)、[ProjectionHandlerRegistry](src/main/java/io/ddd4j/core/cqrs/readmodel/handler/ProjectionHandlerRegistry.java)、[ProjectionHandler](src/main/java/io/ddd4j/core/cqrs/readmodel/handler/ProjectionHandler.java) 进入。

修改 `ProjectionDispatcher` 时，核对公开入口 `chunkByEvent`、`dispatchOne`、`position`、`chunk` 的调用方。

修改 `ProjectionHandlerRegistry` 时，核对公开入口 `register`、`all`、`findHandler` 的调用方。

### 回归

- [ProjectionDispatcherTest](src/test/java/io/ddd4j/core/cqrs/readmodel/handler/ProjectionDispatcherTest.java) 中可从 `chunkReaderReceivesImmutableEventTypeNames`、`chunkByEventEmitsOnlyRequestedTypeAndCompletesWhenExhausted` 定位断言。

- [ProjectionHandlerRegistryTest](src/test/java/io/ddd4j/core/cqrs/readmodel/handler/ProjectionHandlerRegistryTest.java) 中可从 `registeredHandlerIsFoundByExactEventType`、`findHandlerReturnsEmptyForUnregisteredEventType` 定位断言。

- [ProjectionModuleIndependenceTest](src/test/java/io/ddd4j/core/cqrs/readmodel/handler/arch/ProjectionModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-core -am -Dtest=ProjectionDispatcherTest,ProjectionHandlerRegistryTest,ProjectionModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-core -am verify
```
