# ddd4j-runtime-quarkus

Quarkus CDI 命令路由与上下文生命周期。

## 实现与调用

从 [Ddd4jQuarkusRuntime](src/main/java/io/ddd4j/quarkus/Ddd4jQuarkusRuntime.java)、[CdiI18nProvider](src/main/java/io/ddd4j/quarkus/i18n/CdiI18nProvider.java)、[CdiSubjectProvider](src/main/java/io/ddd4j/quarkus/subject/CdiSubjectProvider.java) 进入。

修改 `Ddd4jQuarkusRuntime` 时，核对公开入口 `start`、`readiness`、`close` 的调用方。

## 回归

- [QuarkusCommandBusTest](src/test/java/io/ddd4j/quarkus/command/QuarkusCommandBusTest.java) 中可从 `executeShouldRouteToRegisteredExecutor`、`executeShouldThrowWhenCommandIsNull` 定位断言。

- [Ddd4jQuarkusRuntimeTest](src/test/java/io/ddd4j/quarkus/Ddd4jQuarkusRuntimeTest.java)。

- [CdiDomainEventPublisherContractTest](src/test/java/io/ddd4j/quarkus/event/CdiDomainEventPublisherContractTest.java) 中可从 `domainAndPlainObjectsFireOnceWhileNullIsIgnored` 定位断言。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-quarkus -am -Dtest=QuarkusCommandBusTest,Ddd4jQuarkusRuntimeTest,CdiDomainEventPublisherContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-quarkus -am verify
```

## 命令分发

维护 quarkus 的命令分发。改动执行器注册或调用方式时，从本模块的命令总线追到 core 的 CommandRegistry 和返回对象，再验证对应框架的实际装配。

### 实现与调用

从 [QuarkusCommandBus](src/main/java/io/ddd4j/quarkus/command/QuarkusCommandBus.java) 进入。

### 回归

- [QuarkusCommandRegistrationIT](src/test/java/io/ddd4j/quarkus/command/QuarkusCommandRegistrationIT.java) 中可从 `execute_已注册命令_应路由到真实Handler并返回成功`、`装配期冲突_整批拒绝应传播为Bean创建失败` 定位断言。

- [QuarkusCommandBusPlatformContractTest](src/test/java/io/ddd4j/quarkus/command/QuarkusCommandBusPlatformContractTest.java) 中可从 `declaresTheCdiNamespaceAndProxyConstructorsForThisJdk` 定位断言。

- [CqrsQuarkusModuleIndependenceTest](src/test/java/io/ddd4j/quarkus/command/arch/CqrsQuarkusModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-quarkus -am -Dtest=QuarkusCommandBusIT,QuarkusCommandRegistrationIT,QuarkusCommandBusPlatformContractTest,CqrsQuarkusModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-quarkus -am verify
```

## 投影维护

维护运行时的投影视图和调度。检查创建、启动及停止顺序；修改异步处理时，等待投影实际完成后再读取进度。

### 实现与调用

从 [QuarkusNumericProjectionScheduler](src/main/java/io/ddd4j/quarkus/cqrs/QuarkusNumericProjectionScheduler.java)、[QuarkusProjectionViewManager](src/main/java/io/ddd4j/quarkus/cqrs/QuarkusProjectionViewManager.java) 进入。

修改 `QuarkusNumericProjectionScheduler` 时，核对公开入口 `schedule`、`run`、`cancel`、`isActive` 的调用方。

修改 `QuarkusProjectionViewManager` 时，核对公开入口 `start`、`stop`、`isRunning`、`triggerOnce` 的调用方。

### 回归

- [QuarkusNumericProjectionSchedulerIT](src/test/java/io/ddd4j/quarkus/cqrs/QuarkusNumericProjectionSchedulerIT.java) 中可从 `startupEvent_应启动视图管理器`、`shutdownEvent_应停止视图管理器` 定位断言。

- [ProjectionQuarkusModuleIndependenceTest](src/test/java/io/ddd4j/quarkus/cqrs/arch/ProjectionQuarkusModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-quarkus -am -Dtest=QuarkusNumericProjectionSchedulerIT,ProjectionQuarkusModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-quarkus -am verify
```
