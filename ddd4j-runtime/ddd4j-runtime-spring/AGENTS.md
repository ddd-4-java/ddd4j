# ddd4j-runtime-spring

Spring Bean 发现、命令事务与投影调度。

## 实现与调用

从 [SpringCommandBus](src/main/java/io/ddd4j/spring/command/SpringCommandBus.java)、[ApplicationService](src/main/java/io/ddd4j/spring/annotation/ApplicationService.java)、[DomainRepository](src/main/java/io/ddd4j/spring/annotation/DomainRepository.java) 进入。

修改 `SpringCommandBus` 时，核对公开入口 `afterSingletonsInstantiated`、`execute` 的调用方。

实现入口见 [SpringCommandBus](src/main/java/io/ddd4j/spring/command/SpringCommandBus.java)、[SpringProjectionScheduler](src/main/java/io/ddd4j/spring/projection/SpringProjectionScheduler.java)。

命令总线统一为 `io.ddd4j.spring.command.SpringCommandBus`。显式启用使用 `@Import(SpringCommandConfig.class)`，传入 CommandRegistry 的应用通过同一类型的双参数构造器装配。

## 回归

- [SpringCommandBusTest](src/test/java/io/ddd4j/spring/command/SpringCommandBusTest.java) 中可从 `shouldRegisterExecutorsOnContextRefresh`、`shouldRouteCommandToCorrectExecutor` 定位断言。

- [MicrometerProjectionMetricsTest](src/test/java/io/ddd4j/spring/cqrs/MicrometerProjectionMetricsTest.java) 中可从 `constructor_nullRegistry_shouldThrowNPE`、`shouldRecordEventCounterAndDuration` 定位断言。

- [SignKitEmptyFormTest](src/test/java/io/ddd4j/spring/util/SignKitEmptyFormTest.java) 中可从 `multiValueMapTreatsNullAsEmpty`、`arrayMapTreatsNullAsEmpty` 定位断言。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-spring -am -Dtest=SpringCommandBusTest,MicrometerProjectionMetricsTest,SignKitEmptyFormTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-spring -am verify
```

## 命令分发

维护 spring 的命令分发。改动执行器注册或调用方式时，从本模块的命令总线追到 core 的 CommandRegistry 和返回对象，再验证对应框架的实际装配。

### 实现与调用

从 [SpringCommandBus](src/main/java/io/ddd4j/data/cqrs/spring/SpringCommandBus.java) 进入。

修改 `SpringCommandBus` 时，核对公开入口 `execute` 的调用方。

### 回归

- [SpringCommandBusIT](src/test/java/io/ddd4j/data/cqrs/spring/SpringCommandBusIT.java) 中可从 `execute_已注册命令_应路由到真实Handler并返回成功`、`装配期冲突_整批拒绝应传播为容器启动失败` 定位断言。

- [CqrsSpringModuleIndependenceTest](src/test/java/io/ddd4j/data/cqrs/spring/arch/CqrsSpringModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-spring -am -Dtest=SpringCommandBusIT,CqrsSpringModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-spring -am verify
```

## 投影维护

维护运行时的投影视图和调度。检查创建、启动及停止顺序；修改异步处理时，等待投影实际完成后再读取进度。

### 实现与调用

从 [SpringProjectionScheduler](src/main/java/io/ddd4j/data/projection/spring/SpringProjectionScheduler.java)、[SpringProjectionViewManager](src/main/java/io/ddd4j/data/projection/spring/SpringProjectionViewManager.java) 进入。

修改 `SpringProjectionScheduler` 时，核对公开入口 `schedule`、`start`、`stop`、`isRunning` 的调用方。

修改 `SpringProjectionViewManager` 时，核对公开入口 `start`、`stop`、`isRunning`、`isAutoStartup` 的调用方。

### 回归

- [SpringProjectionSchedulerIT](src/test/java/io/ddd4j/data/projection/spring/SpringProjectionSchedulerIT.java) 中可从 `scheduler_schedule_应返回activeHandle_cancel后应inactive`、`viewManager_start_stop_应管理生命周期` 定位断言。

- [ProjectionSpringModuleIndependenceTest](src/test/java/io/ddd4j/data/projection/spring/arch/ProjectionSpringModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-spring -am -Dtest=SpringProjectionSchedulerIT,ProjectionSpringModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-spring -am verify
```
