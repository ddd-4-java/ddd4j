# ddd4j-data-projection-helidon

维护运行时的投影视图和调度。检查创建、启动及停止顺序；修改异步处理时，等待投影实际完成后再读取进度。

## 实现与调用

从 [HelidonProjectionScheduler](src/main/java/io/ddd4j/data/projection/helidon/HelidonProjectionScheduler.java)、[HelidonProjectionViewManager](src/main/java/io/ddd4j/data/projection/helidon/HelidonProjectionViewManager.java) 进入。

修改 `HelidonProjectionScheduler` 时，核对公开入口 `shutdown`、`schedule`、`run`、`cancel` 的调用方。

修改 `HelidonProjectionViewManager` 时，核对公开入口 `start`、`stop`、`isRunning`、`triggerOnce` 的调用方。

## 回归

- [HelidonProjectionSchedulerIT](src/test/java/io/ddd4j/data/projection/helidon/HelidonProjectionSchedulerIT.java) 中可从 `scheduler_schedule_应返回activeHandle_cancel后应inactive`、`viewManager_start_stop_应管理生命周期` 定位断言。

- [ProjectionHelidonModuleIndependenceTest](src/test/java/io/ddd4j/data/projection/helidon/arch/ProjectionHelidonModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-projection-helidon -am -Dtest=HelidonProjectionSchedulerIT,ProjectionHelidonModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-projection-helidon -am verify
```
