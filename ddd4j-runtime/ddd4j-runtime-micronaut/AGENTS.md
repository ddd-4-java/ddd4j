# ddd4j-runtime-micronaut

Micronaut Bean 工厂、投影调度及启停。

## 实现与调用

从 [Ddd4jMicronautFactory](src/main/java/io/ddd4j/micronaut/Ddd4jMicronautFactory.java)、[Ddd4jMicronautRuntime](src/main/java/io/ddd4j/micronaut/Ddd4jMicronautRuntime.java)、[MicronautDomainEventPublisher](src/main/java/io/ddd4j/micronaut/MicronautDomainEventPublisher.java) 进入。

修改 `Ddd4jMicronautRuntime` 时，核对公开入口 `start`、`readiness`、`close` 的调用方。

## 回归

- [MicronautCqrsRuntimeContractTest](src/test/java/io/ddd4j/micronaut/cqrs/MicronautCqrsRuntimeContractTest.java)。

- [Ddd4jMicronautReadinessHealthIndicatorTest](src/test/java/io/ddd4j/micronaut/health/Ddd4jMicronautReadinessHealthIndicatorTest.java) 中可从 `shouldMapReadyAndUnavailableReportsWithoutExposingFailureDetails` 定位断言。

- [Ddd4jMicronautRuntimeTest](src/test/java/io/ddd4j/micronaut/Ddd4jMicronautRuntimeTest.java) 中可从 `defaultConstructorStartsWithNoReadinessContributors` 定位断言。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-micronaut -am -Dtest=MicronautCqrsRuntimeContractTest,Ddd4jMicronautReadinessHealthIndicatorTest,Ddd4jMicronautRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-micronaut -am verify
```

## 命令分发

维护 micronaut 的命令分发。改动执行器注册或调用方式时，从本模块的命令总线追到 core 的 CommandRegistry 和返回对象，再验证对应框架的实际装配。

### 实现与调用

从 [MicronautCommandBus](src/main/java/io/ddd4j/micronaut/command/MicronautCommandBus.java) 进入。

### 回归

- [MicronautCommandBusIT](src/test/java/io/ddd4j/micronaut/command/MicronautCommandBusIT.java) 中可从 `execute_已注册命令_应路由到真实Handler并返回成功`、`装配期冲突_整批拒绝应自构造器传播` 定位断言。

- [CqrsMicronautModuleIndependenceTest](src/test/java/io/ddd4j/micronaut/command/arch/CqrsMicronautModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-micronaut -am -Dtest=MicronautCommandBusIT,CqrsMicronautModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-micronaut -am verify
```

## 投影维护

维护运行时的投影视图和调度。检查创建、启动及停止顺序；修改异步处理时，等待投影实际完成后再读取进度。

### 实现与调用

从 [MicronautProjectionScheduler](src/main/java/io/ddd4j/micronaut/cqrs/MicronautProjectionScheduler.java)、[MicronautProjectionViewManager](src/main/java/io/ddd4j/micronaut/cqrs/MicronautProjectionViewManager.java) 进入。

修改 `MicronautProjectionScheduler` 时，核对公开入口 `schedule`、`run`、`cancel`、`isActive` 的调用方。

修改 `MicronautProjectionViewManager` 时，核对公开入口 `start`、`stop`、`isRunning`、`triggerOnce` 的调用方。

### 回归

- [MicronautProjectionSchedulerIT](src/test/java/io/ddd4j/micronaut/cqrs/MicronautProjectionSchedulerIT.java) 中可从 `scheduler_schedule_应返回activeHandle_cancel后应inactive`、`viewManager_start_stop_应管理生命周期` 定位断言。

- [MicronautProjectionSchedulerRaceTest](src/test/java/io/ddd4j/micronaut/cqrs/MicronautProjectionSchedulerRaceTest.java) 中可从 `completedInitialSubmissionCannotReplaceRescheduledFuture` 定位断言。

- [ProjectionMicronautModuleIndependenceTest](src/test/java/io/ddd4j/micronaut/cqrs/arch/ProjectionMicronautModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-micronaut -am -Dtest=MicronautProjectionSchedulerIT,MicronautProjectionSchedulerRaceTest,ProjectionMicronautModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-micronaut -am verify
```

## 从自动投影迁移

已有应用在 Micronaut 配置中设置 `ddd4j.cqrs.projection.enabled=true` 才会装配并启动投影。注册一个读取真实事件的 `EventChunkReader` 和一个持久化的 `ProjectionPositionRepository`。容器在开始调度前检查这两个 Bean；缺少、重复、使用空读取器或内存位置仓储都会使启动失败。仅使用命令总线的应用可保持默认关闭。
