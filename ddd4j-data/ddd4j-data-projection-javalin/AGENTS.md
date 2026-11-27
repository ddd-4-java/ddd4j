# ddd4j-data-projection-javalin

维护运行时的投影视图和调度。检查创建、启动及停止顺序；修改异步处理时，等待投影实际完成后再读取进度。

## 实现与调用

从 [JavalinProjectionScheduler](src/main/java/io/ddd4j/data/projection/javalin/JavalinProjectionScheduler.java)、[JavalinProjectionViewManager](src/main/java/io/ddd4j/data/projection/javalin/JavalinProjectionViewManager.java) 进入。

修改 `JavalinProjectionScheduler` 时，核对公开入口 `create`、`shutdown`、`schedule`、`run` 的调用方。

修改 `JavalinProjectionViewManager` 时，核对公开入口 `create`、`start`、`stop`、`isRunning` 的调用方。

## 回归

- [JavalinProjectionSchedulerIT](src/test/java/io/ddd4j/data/projection/javalin/JavalinProjectionSchedulerIT.java) 中可从 `scheduler_schedule_应返回activeHandle_cancel后应inactive`、`viewManager_start_stop_应管理生命周期` 定位断言。

- [ProjectionJavalinModuleIndependenceTest](src/test/java/io/ddd4j/data/projection/javalin/arch/ProjectionJavalinModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-projection-javalin -am -Dtest=JavalinProjectionSchedulerIT,ProjectionJavalinModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-projection-javalin -am verify
```
