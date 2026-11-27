# ddd4j-data-projection-micronaut

维护运行时的投影视图和调度。检查创建、启动及停止顺序；修改异步处理时，等待投影实际完成后再读取进度。

## 实现与调用

从 [MicronautProjectionScheduler](src/main/java/io/ddd4j/data/projection/micronaut/MicronautProjectionScheduler.java)、[MicronautProjectionViewManager](src/main/java/io/ddd4j/data/projection/micronaut/MicronautProjectionViewManager.java) 进入。

修改 `MicronautProjectionScheduler` 时，核对公开入口 `schedule`、`run`、`cancel`、`isActive` 的调用方。

修改 `MicronautProjectionViewManager` 时，核对公开入口 `start`、`stop`、`isRunning`、`triggerOnce` 的调用方。

## 回归

- [MicronautProjectionSchedulerIT](src/test/java/io/ddd4j/data/projection/micronaut/MicronautProjectionSchedulerIT.java) 中可从 `scheduler_schedule_应返回activeHandle_cancel后应inactive`、`viewManager_start_stop_应管理生命周期` 定位断言。

- [MicronautProjectionSchedulerRaceTest](src/test/java/io/ddd4j/data/projection/micronaut/MicronautProjectionSchedulerRaceTest.java) 中可从 `completedInitialSubmissionCannotReplaceRescheduledFuture` 定位断言。

- [ProjectionMicronautModuleIndependenceTest](src/test/java/io/ddd4j/data/projection/micronaut/arch/ProjectionMicronautModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-projection-micronaut -am -Dtest=MicronautProjectionSchedulerIT,MicronautProjectionSchedulerRaceTest,ProjectionMicronautModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-projection-micronaut -am verify
```
