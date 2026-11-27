# ddd4j-data-cqrs-javalin

维护 javalin 的命令分发。改动执行器注册或调用方式时，从本模块的命令总线追到 core 的 CommandRegistry 和返回对象，再验证对应框架的实际装配。

## 实现与调用

从 [JavalinCommandBus](src/main/java/io/ddd4j/data/cqrs/javalin/JavalinCommandBus.java) 进入。

修改 `JavalinCommandBus` 时，核对公开入口 `create` 的调用方。

## 回归

- [JavalinCommandBusIT](src/test/java/io/ddd4j/data/cqrs/javalin/JavalinCommandBusIT.java) 中可从 `execute_已注册命令_应路由到真实Handler并返回成功`、`装配期冲突_整批拒绝应自工厂传播` 定位断言。

- [CqrsJavalinModuleIndependenceTest](src/test/java/io/ddd4j/data/cqrs/javalin/arch/CqrsJavalinModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-cqrs-javalin -am -Dtest=JavalinCommandBusIT,CqrsJavalinModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-cqrs-javalin -am verify
```
