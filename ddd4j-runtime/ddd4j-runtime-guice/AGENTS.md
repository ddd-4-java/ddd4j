# ddd4j-runtime-guice

Guice 绑定、可选能力与 Javalin 运行时接线。

## 实现与调用

使用 `Ddd4jMybatisGuiceModule` 的应用需要在自己的 POM 中声明 `io.ddd4j:ddd4j-data-mybatisplus`。商品交易示例通过 `ddd4j-sample-order-jdbc` 引入普通 MyBatis 适配器。这两个适配器提供同名配置类，同时装配会导致方法签名冲突。

从 [Ddd4jGuiceModule](src/main/java/io/ddd4j/guice/Ddd4jGuiceModule.java)、[GuiceCommandBus](src/main/java/io/ddd4j/guice/command/GuiceCommandBus.java)、[GuiceI18nProvider](src/main/java/io/ddd4j/guice/i18n/GuiceI18nProvider.java) 进入。

修改 `Ddd4jGuiceModule` 时，核对公开入口 `basic`、`eventBus`、`projectionRunner`、`projectionService` 的调用方。

修改 `GuiceCommandBus` 时，核对公开入口 `execute` 的调用方。

## 回归

- [Ddd4jGuiceModuleTest](src/test/java/io/ddd4j/guice/Ddd4jGuiceModuleTest.java) 中可从 `projectionRunnerMustUseExplicitReaderBinding`、`ddd4jGuiceModuleWiresCoreServices` 定位断言。

- [GuiceCommandBusTest](src/test/java/io/ddd4j/guice/command/GuiceCommandBusTest.java) 中可从 `commandBusRoutesToRegisteredExecutor`、`commandBusThrowsForUnregisteredCommand` 定位断言。

- [GuiceInMemoryProjectionPositionRepositoryTest](src/test/java/io/ddd4j/guice/cqrs/GuiceInMemoryProjectionPositionRepositoryTest.java) 中可从 `saveAndFindByStreamId`、`findAllReturnsSnapshotOfStore` 定位断言。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-guice -am -Dtest=Ddd4jGuiceModuleTest,GuiceCommandBusTest,GuiceInMemoryProjectionPositionRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-guice -am verify
```

## 命令分发

维护 javalin 的命令分发。改动执行器注册或调用方式时，从本模块的命令总线追到 core 的 CommandRegistry 和返回对象，再验证对应框架的实际装配。

### 实现与调用

从 [JavalinCommandBus](src/main/java/io/ddd4j/data/cqrs/javalin/JavalinCommandBus.java) 进入。

修改 `JavalinCommandBus` 时，核对公开入口 `create` 的调用方。

### 回归

- [JavalinCommandBusIT](src/test/java/io/ddd4j/data/cqrs/javalin/JavalinCommandBusIT.java) 中可从 `execute_已注册命令_应路由到真实Handler并返回成功`、`装配期冲突_整批拒绝应自工厂传播` 定位断言。

- [CqrsJavalinModuleIndependenceTest](src/test/java/io/ddd4j/data/cqrs/javalin/arch/CqrsJavalinModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-guice -am -Dtest=JavalinCommandBusIT,CqrsJavalinModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-guice -am verify
```

## 投影维护

维护运行时的投影视图和调度。检查创建、启动及停止顺序；修改异步处理时，等待投影实际完成后再读取进度。

### 实现与调用

从 [JavalinProjectionScheduler](src/main/java/io/ddd4j/data/projection/javalin/JavalinProjectionScheduler.java)、[JavalinProjectionViewManager](src/main/java/io/ddd4j/data/projection/javalin/JavalinProjectionViewManager.java) 进入。

修改 `JavalinProjectionScheduler` 时，核对公开入口 `create`、`shutdown`、`schedule`、`run` 的调用方。

修改 `JavalinProjectionViewManager` 时，核对公开入口 `create`、`start`、`stop`、`isRunning` 的调用方。

### 回归

- [JavalinProjectionSchedulerIT](src/test/java/io/ddd4j/data/projection/javalin/JavalinProjectionSchedulerIT.java) 中可从 `scheduler_schedule_应返回activeHandle_cancel后应inactive`、`viewManager_start_stop_应管理生命周期` 定位断言。

- [ProjectionJavalinModuleIndependenceTest](src/test/java/io/ddd4j/data/projection/javalin/arch/ProjectionJavalinModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-guice -am -Dtest=JavalinProjectionSchedulerIT,ProjectionJavalinModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-guice -am verify
```
