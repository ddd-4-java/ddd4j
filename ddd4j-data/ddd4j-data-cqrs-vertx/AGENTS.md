# ddd4j-data-cqrs-vertx

维护 vertx 的命令分发。改动执行器注册或调用方式时，从本模块的命令总线追到 core 的 CommandRegistry 和返回对象，再验证对应框架的实际装配。

## 实现与调用

从 [VertxCommandBus](src/main/java/io/ddd4j/data/cqrs/vertx/VertxCommandBus.java) 进入。

修改 `VertxCommandBus` 时，核对公开入口 `create` 的调用方。

## 回归

- [VertxCommandBusIT](src/test/java/io/ddd4j/data/cqrs/vertx/VertxCommandBusIT.java) 中可从 `execute_已在Vertx上下文线程同步执行_应路由到真实Handler并返回成功`、`装配期冲突_整批拒绝应自工厂传播` 定位断言。

- [CqrsVertxModuleIndependenceTest](src/test/java/io/ddd4j/data/cqrs/vertx/arch/CqrsVertxModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-cqrs-vertx -am -Dtest=VertxCommandBusIT,CqrsVertxModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-cqrs-vertx -am verify
```
