# ddd4j-data-projection

共享投影分发层。维护 ProjectionHandler 的发现、注册与 ProjectionDispatcher 的路由；位置持久化及运行时调度分别由具体适配模块负责。

## 实现与调用

从 [ProjectionDispatcher](src/main/java/io/ddd4j/data/projection/ProjectionDispatcher.java)、[ProjectionHandlerRegistry](src/main/java/io/ddd4j/data/projection/ProjectionHandlerRegistry.java)、[ProjectionHandler](src/main/java/io/ddd4j/data/projection/ProjectionHandler.java) 进入。

修改 `ProjectionDispatcher` 时，核对公开入口 `chunkByEvent`、`dispatchOne`、`position`、`chunk` 的调用方。

修改 `ProjectionHandlerRegistry` 时，核对公开入口 `register`、`all`、`findHandler` 的调用方。

## 回归

- [ProjectionDispatcherTest](src/test/java/io/ddd4j/data/projection/ProjectionDispatcherTest.java) 中可从 `chunkReaderReceivesImmutableEventTypeNames`、`chunkByEventEmitsOnlyRequestedTypeAndCompletesWhenExhausted` 定位断言。

- [ProjectionHandlerRegistryTest](src/test/java/io/ddd4j/data/projection/ProjectionHandlerRegistryTest.java) 中可从 `registeredHandlerIsFoundByExactEventType`、`findHandlerReturnsEmptyForUnregisteredEventType` 定位断言。

- [ProjectionModuleIndependenceTest](src/test/java/io/ddd4j/data/projection/arch/ProjectionModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-projection -am -Dtest=ProjectionDispatcherTest,ProjectionHandlerRegistryTest,ProjectionModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-projection -am verify
```
