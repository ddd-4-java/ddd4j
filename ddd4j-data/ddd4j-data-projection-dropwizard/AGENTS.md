# ddd4j-data-projection-dropwizard

维护运行时的投影视图和调度。检查创建、启动及停止顺序；修改异步处理时，等待投影实际完成后再读取进度。

## 实现与调用

从 [DropwizardProjectionScheduler](src/main/java/io/ddd4j/data/projection/dropwizard/DropwizardProjectionScheduler.java)、[DropwizardProjectionViewManager](src/main/java/io/ddd4j/data/projection/dropwizard/DropwizardProjectionViewManager.java) 进入。

修改 `DropwizardProjectionScheduler` 时，核对公开入口 `create`、`shutdown`、`schedule`、`run` 的调用方。

修改 `DropwizardProjectionViewManager` 时，核对公开入口 `create`、`start`、`stop`、`isRunning` 的调用方。

## 回归

- [DropwizardProjectionSchedulerIT](src/test/java/io/ddd4j/data/projection/dropwizard/DropwizardProjectionSchedulerIT.java) 中可从 `scheduler_schedule_应返回activeHandle_cancel后应inactive`、`viewManager_start_stop_应管理生命周期` 定位断言。

- [ProjectionDropwizardModuleIndependenceTest](src/test/java/io/ddd4j/data/projection/dropwizard/arch/ProjectionDropwizardModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-projection-dropwizard -am -Dtest=DropwizardProjectionSchedulerIT,ProjectionDropwizardModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-projection-dropwizard -am verify
```
