# ddd4j-runtime-helidon

Helidon MP 的 CDI 扩展、配置与显式投影。

CDI 扩展负责持有与释放运行时引用。显式投影启动失败时回滚已取得的引用；关闭多个资源时保留首个失败并继续释放余项。改发现或配置时通过服务器启动测试确认 CDI 注册生效。

实现入口见 [Ddd4jHelidonExtension](src/main/java/io/ddd4j/helidon/Ddd4jHelidonExtension.java)、[Ddd4jHelidonRuntime](src/main/java/io/ddd4j/helidon/Ddd4jHelidonRuntime.java)。

## 回归

优先运行 [Ddd4jHelidonExtensionTest](src/test/java/io/ddd4j/helidon/Ddd4jHelidonExtensionTest.java)、[HelidonServerBootTest](src/test/java/io/ddd4j/helidon/HelidonServerBootTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-helidon -am -Dtest=Ddd4jHelidonExtensionTest,HelidonServerBootTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-helidon -am verify
```

## 命令分发

维护 helidon 的命令分发。改动执行器注册或调用方式时，从本模块的命令总线追到 core 的 CommandRegistry 和返回对象，再验证对应框架的实际装配。

### 实现与调用

从 [HelidonCommandBus](src/main/java/io/ddd4j/helidon/command/HelidonCommandBus.java) 进入。

修改 `HelidonCommandBus` 时，核对公开入口 `discover` 的调用方。

### 回归

- [HelidonCommandBusIT](src/test/java/io/ddd4j/helidon/command/HelidonCommandBusIT.java) 中可从 `execute_已注册命令_应路由到真实Handler并返回成功`、`装配期冲突_整批拒绝应自构造器传播` 定位断言。

- [CqrsHelidonModuleIndependenceTest](src/test/java/io/ddd4j/helidon/command/arch/CqrsHelidonModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-helidon -am -Dtest=HelidonCommandBusIT,CqrsHelidonModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-helidon -am verify
```

## 投影维护

维护运行时的投影视图和调度。检查创建、启动及停止顺序；修改异步处理时，等待投影实际完成后再读取进度。

### 实现与调用

从 [HelidonProjectionScheduler](src/main/java/io/ddd4j/helidon/cqrs/HelidonProjectionScheduler.java)、[HelidonProjectionViewManager](src/main/java/io/ddd4j/helidon/cqrs/HelidonProjectionViewManager.java) 进入。

修改 `HelidonProjectionScheduler` 时，核对公开入口 `shutdown`、`schedule`、`run`、`cancel` 的调用方。

修改 `HelidonProjectionViewManager` 时，核对公开入口 `start`、`stop`、`isRunning`、`triggerOnce` 的调用方。

### 回归

- [HelidonProjectionSchedulerIT](src/test/java/io/ddd4j/helidon/cqrs/HelidonProjectionSchedulerIT.java) 中可从 `scheduler_schedule_应返回activeHandle_cancel后应inactive`、`viewManager_start_stop_应管理生命周期` 定位断言。

- [ProjectionHelidonModuleIndependenceTest](src/test/java/io/ddd4j/helidon/cqrs/arch/ProjectionHelidonModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-helidon -am -Dtest=HelidonProjectionSchedulerIT,ProjectionHelidonModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-helidon -am verify
```
