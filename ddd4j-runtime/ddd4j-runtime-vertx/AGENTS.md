# ddd4j-runtime-vertx

Vert.x EventBus 的本地分发、命令和投影生命周期。

本地领域事件经 EventBus 发布，本地交付不要求网络 codec。改消息编码前先确定是否走跨进程链路；变更投影 timer 或 worker 后等待异步处理完成，再断言投影和关闭结果。

实现入口见 [Ddd4jVertxRuntime](src/main/java/io/ddd4j/vertx/Ddd4jVertxRuntime.java)、[VertxDomainEventPublisher](src/main/java/io/ddd4j/vertx/VertxDomainEventPublisher.java)。

保留的 `VertxProjectionScheduler.create(vertx, views, runner)` 工厂使用传入的 Vert.x。timer 触发后由 worker 执行投影，失败交给 Vert.x 异常处理器；关闭调度器会取消后续触发，调用方负责关闭 Vert.x。

## 回归

优先运行 [VertxDomainEventPublisherTest](src/test/java/io/ddd4j/vertx/VertxDomainEventPublisherTest.java)、[VertxCqrsRuntimeContractTest](src/test/java/io/ddd4j/vertx/cqrs/VertxCqrsRuntimeContractTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-vertx -am -Dtest=VertxDomainEventPublisherTest,VertxCqrsRuntimeContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-vertx -am verify
```

## 命令分发

维护 vertx 的命令分发。改动执行器注册或调用方式时，从本模块的命令总线追到 core 的 CommandRegistry 和返回对象，再验证对应框架的实际装配。

### 实现与调用

从 [VertxCommandBus](src/main/java/io/ddd4j/vertx/command/VertxCommandBus.java) 进入。

修改 `VertxCommandBus` 时，核对公开入口 `create` 的调用方。

#保留的 `VertxProjectionScheduler.create(vertx, views, runner)` 工厂使用传入的 Vert.x。timer 触发后由 worker 执行投影，失败交给 Vert.x 异常处理器；关闭调度器会取消后续触发，调用方负责关闭 Vert.x。

## 回归

- [VertxCommandBusIT](src/test/java/io/ddd4j/vertx/command/VertxCommandBusIT.java) 中可从 `execute_已在Vertx上下文线程同步执行_应路由到真实Handler并返回成功`、`装配期冲突_整批拒绝应自工厂传播` 定位断言。

- [CqrsVertxModuleIndependenceTest](src/test/java/io/ddd4j/vertx/command/arch/CqrsVertxModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-vertx -am -Dtest=VertxCommandBusIT,CqrsVertxModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-vertx -am verify
```

## 投影维护

维护运行时的投影视图和调度。检查创建、启动及停止顺序；修改异步处理时，等待投影实际完成后再读取进度。

### 实现与调用

从 [VertxProjectionScheduler](src/main/java/io/ddd4j/vertx/cqrs/VertxProjectionScheduler.java)、[VertxProjectionViewManager](src/main/java/io/ddd4j/vertx/cqrs/VertxProjectionViewManager.java) 进入。

修改 `VertxProjectionScheduler` 时，核对公开入口 `create`、`shutdown`、`schedule`、`run` 的调用方。

修改 `VertxProjectionViewManager` 时，核对公开入口 `create`、`start`、`stop`、`isRunning` 的调用方。

#保留的 `VertxProjectionScheduler.create(vertx, views, runner)` 工厂使用传入的 Vert.x。timer 触发后由 worker 执行投影，失败交给 Vert.x 异常处理器；关闭调度器会取消后续触发，调用方负责关闭 Vert.x。

## 回归

- [VertxProjectionSchedulerIT](src/test/java/io/ddd4j/vertx/cqrs/VertxProjectionSchedulerIT.java) 中可从 `scheduler_schedule_应返回activeHandle_cancel后应inactive`、`viewManager_start_stop_应管理生命周期` 定位断言。

- [ProjectionVertxModuleIndependenceTest](src/test/java/io/ddd4j/vertx/cqrs/arch/ProjectionVertxModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-vertx -am -Dtest=VertxProjectionSchedulerIT,ProjectionVertxModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-vertx -am verify
```
