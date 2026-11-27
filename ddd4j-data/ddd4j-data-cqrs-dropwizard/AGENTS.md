# ddd4j-data-cqrs-dropwizard

维护 dropwizard 的命令分发。改动执行器注册或调用方式时，从本模块的命令总线追到 core 的 CommandRegistry 和返回对象，再验证对应框架的实际装配。

## 实现与调用

从 [DropwizardCommandBus](src/main/java/io/ddd4j/data/cqrs/dropwizard/DropwizardCommandBus.java) 进入。

修改 `DropwizardCommandBus` 时，核对公开入口 `create` 的调用方。

## 回归

- [DropwizardCommandBusIT](src/test/java/io/ddd4j/data/cqrs/dropwizard/DropwizardCommandBusIT.java) 中可从 `execute_已注册命令_应路由到真实Handler并返回成功`、`装配期冲突_整批拒绝应自工厂传播` 定位断言。

- [ElRuntimeTest](src/test/java/io/ddd4j/data/cqrs/dropwizard/ElRuntimeTest.java) 中可从 `expressionFactoryShouldLoadFromCurrentPlatform` 定位断言。

- [CqrsDropwizardModuleIndependenceTest](src/test/java/io/ddd4j/data/cqrs/dropwizard/arch/CqrsDropwizardModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-cqrs-dropwizard -am -Dtest=DropwizardCommandBusIT,ElRuntimeTest,CqrsDropwizardModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-cqrs-dropwizard -am verify
```
