# ddd4j-data-cqrs-spring

维护 spring 的命令分发。改动执行器注册或调用方式时，从本模块的命令总线追到 core 的 CommandRegistry 和返回对象，再验证对应框架的实际装配。

## 实现与调用

从 [SpringCommandBus](src/main/java/io/ddd4j/data/cqrs/spring/SpringCommandBus.java) 进入。

修改 `SpringCommandBus` 时，核对公开入口 `execute` 的调用方。

## 回归

- [SpringCommandBusIT](src/test/java/io/ddd4j/data/cqrs/spring/SpringCommandBusIT.java) 中可从 `execute_已注册命令_应路由到真实Handler并返回成功`、`装配期冲突_整批拒绝应传播为容器启动失败` 定位断言。

- [CqrsSpringModuleIndependenceTest](src/test/java/io/ddd4j/data/cqrs/spring/arch/CqrsSpringModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-cqrs-spring -am -Dtest=SpringCommandBusIT,CqrsSpringModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-cqrs-spring -am verify
```
