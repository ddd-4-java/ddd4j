# ddd4j-data-projection-quarkus

维护运行时的投影视图和调度。检查创建、启动及停止顺序；修改异步处理时，等待投影实际完成后再读取进度。

## 实现与调用

从 [QuarkusProjectionScheduler](src/main/java/io/ddd4j/data/projection/quarkus/QuarkusProjectionScheduler.java)、[QuarkusProjectionViewManager](src/main/java/io/ddd4j/data/projection/quarkus/QuarkusProjectionViewManager.java) 进入。

修改 `QuarkusProjectionScheduler` 时，核对公开入口 `schedule`、`run`、`cancel`、`isActive` 的调用方。

修改 `QuarkusProjectionViewManager` 时，核对公开入口 `start`、`stop`、`isRunning`、`triggerOnce` 的调用方。

## 回归

- [QuarkusProjectionSchedulerIT](src/test/java/io/ddd4j/data/projection/quarkus/QuarkusProjectionSchedulerIT.java) 中可从 `startupEvent_应启动视图管理器`、`shutdownEvent_应停止视图管理器` 定位断言。

- [ProjectionQuarkusModuleIndependenceTest](src/test/java/io/ddd4j/data/projection/quarkus/arch/ProjectionQuarkusModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-projection-quarkus -am -Dtest=QuarkusProjectionSchedulerIT,ProjectionQuarkusModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-projection-quarkus -am verify
```
