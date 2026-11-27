# ddd4j-data-projection-spring

维护运行时的投影视图和调度。检查创建、启动及停止顺序；修改异步处理时，等待投影实际完成后再读取进度。

## 实现与调用

从 [SpringProjectionScheduler](src/main/java/io/ddd4j/data/projection/spring/SpringProjectionScheduler.java)、[SpringProjectionViewManager](src/main/java/io/ddd4j/data/projection/spring/SpringProjectionViewManager.java) 进入。

修改 `SpringProjectionScheduler` 时，核对公开入口 `schedule`、`start`、`stop`、`isRunning` 的调用方。

修改 `SpringProjectionViewManager` 时，核对公开入口 `start`、`stop`、`isRunning`、`isAutoStartup` 的调用方。

## 回归

- [SpringProjectionSchedulerIT](src/test/java/io/ddd4j/data/projection/spring/SpringProjectionSchedulerIT.java) 中可从 `scheduler_schedule_应返回activeHandle_cancel后应inactive`、`viewManager_start_stop_应管理生命周期` 定位断言。

- [ProjectionSpringModuleIndependenceTest](src/test/java/io/ddd4j/data/projection/spring/arch/ProjectionSpringModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-projection-spring -am -Dtest=SpringProjectionSchedulerIT,ProjectionSpringModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-projection-spring -am verify
```
