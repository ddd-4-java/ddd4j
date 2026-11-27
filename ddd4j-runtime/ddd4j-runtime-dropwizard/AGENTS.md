# ddd4j-runtime-dropwizard

Dropwizard Bundle 的资源注册、就绪检查与关闭。

配置显式读侧时，缺少 reader 或 repository 应在分配资源、修改全局状态前失败。健康检查对外区分就绪与不可用；检查报告不要泄露底层异常详情。

实现入口见 [Ddd4jBundle](src/main/java/io/ddd4j/dropwizard/Ddd4jBundle.java)、[Ddd4jDropwizardReadinessHealthCheck](src/main/java/io/ddd4j/dropwizard/Ddd4jDropwizardReadinessHealthCheck.java)。

## 回归

优先运行 [Ddd4jBundleCollectionContractTest](src/test/java/io/ddd4j/dropwizard/Ddd4jBundleCollectionContractTest.java)、[Ddd4jDropwizardReadinessHealthCheckTest](src/test/java/io/ddd4j/dropwizard/Ddd4jDropwizardReadinessHealthCheckTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-dropwizard -am -Dtest=Ddd4jBundleCollectionContractTest,Ddd4jDropwizardReadinessHealthCheckTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-dropwizard -am verify
```

## 命令分发

维护 dropwizard 的命令分发。改动执行器注册或调用方式时，从本模块的命令总线追到 core 的 CommandRegistry 和返回对象，再验证对应框架的实际装配。

### 实现与调用

从 [DropwizardCommandBus](src/main/java/io/ddd4j/dropwizard/command/DropwizardCommandBus.java) 进入。

修改 `DropwizardCommandBus` 时，核对公开入口 `create` 的调用方。

### 回归

- [DropwizardCommandBusIT](src/test/java/io/ddd4j/dropwizard/command/DropwizardCommandBusIT.java) 中可从 `execute_已注册命令_应路由到真实Handler并返回成功`、`装配期冲突_整批拒绝应自工厂传播` 定位断言。

- [ElRuntimeTest](src/test/java/io/ddd4j/dropwizard/command/ElRuntimeTest.java) 中可从 `expressionFactoryShouldLoadFromCurrentPlatform` 定位断言。

- [CqrsDropwizardModuleIndependenceTest](src/test/java/io/ddd4j/dropwizard/command/arch/CqrsDropwizardModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-dropwizard -am -Dtest=DropwizardCommandBusIT,ElRuntimeTest,CqrsDropwizardModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-dropwizard -am verify
```

## 投影维护

维护运行时的投影视图和调度。检查创建、启动及停止顺序；修改异步处理时，等待投影实际完成后再读取进度。

### 实现与调用

从 [DropwizardProjectionScheduler](src/main/java/io/ddd4j/dropwizard/cqrs/DropwizardProjectionScheduler.java)、[DropwizardProjectionViewManager](src/main/java/io/ddd4j/dropwizard/cqrs/DropwizardProjectionViewManager.java) 进入。

修改 `DropwizardProjectionScheduler` 时，核对公开入口 `create`、`shutdown`、`schedule`、`run` 的调用方。

修改 `DropwizardProjectionViewManager` 时，核对公开入口 `create`、`start`、`stop`、`isRunning` 的调用方。

### 回归

- [DropwizardProjectionSchedulerIT](src/test/java/io/ddd4j/dropwizard/cqrs/DropwizardProjectionSchedulerIT.java) 中可从 `scheduler_schedule_应返回activeHandle_cancel后应inactive`、`viewManager_start_stop_应管理生命周期` 定位断言。

- [ProjectionDropwizardModuleIndependenceTest](src/test/java/io/ddd4j/dropwizard/cqrs/arch/ProjectionDropwizardModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-dropwizard -am -Dtest=DropwizardProjectionSchedulerIT,ProjectionDropwizardModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-dropwizard -am verify
```
