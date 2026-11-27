# ddd4j-data-projection-vertx

维护运行时的投影视图和调度。检查创建、启动及停止顺序；修改异步处理时，等待投影实际完成后再读取进度。

## 实现与调用

从 [VertxProjectionScheduler](src/main/java/io/ddd4j/data/projection/vertx/VertxProjectionScheduler.java)、[VertxProjectionViewManager](src/main/java/io/ddd4j/data/projection/vertx/VertxProjectionViewManager.java) 进入。

修改 `VertxProjectionScheduler` 时，核对公开入口 `create`、`shutdown`、`schedule`、`run` 的调用方。

修改 `VertxProjectionViewManager` 时，核对公开入口 `create`、`start`、`stop`、`isRunning` 的调用方。

## 回归

- [VertxProjectionSchedulerIT](src/test/java/io/ddd4j/data/projection/vertx/VertxProjectionSchedulerIT.java) 中可从 `scheduler_schedule_应返回activeHandle_cancel后应inactive`、`viewManager_start_stop_应管理生命周期` 定位断言。

- [ProjectionVertxModuleIndependenceTest](src/test/java/io/ddd4j/data/projection/vertx/arch/ProjectionVertxModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-projection-vertx -am -Dtest=VertxProjectionSchedulerIT,ProjectionVertxModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-projection-vertx -am verify
```
