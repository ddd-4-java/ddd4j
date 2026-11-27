# ddd4j-data-cqrs-micronaut

维护 micronaut 的命令分发。改动执行器注册或调用方式时，从本模块的命令总线追到 core 的 CommandRegistry 和返回对象，再验证对应框架的实际装配。

## 实现与调用

从 [MicronautCommandBus](src/main/java/io/ddd4j/data/cqrs/micronaut/MicronautCommandBus.java) 进入。

## 回归

- [MicronautCommandBusIT](src/test/java/io/ddd4j/data/cqrs/micronaut/MicronautCommandBusIT.java) 中可从 `execute_已注册命令_应路由到真实Handler并返回成功`、`装配期冲突_整批拒绝应自构造器传播` 定位断言。

- [CqrsMicronautModuleIndependenceTest](src/test/java/io/ddd4j/data/cqrs/micronaut/arch/CqrsMicronautModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-cqrs-micronaut -am -Dtest=MicronautCommandBusIT,CqrsMicronautModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-cqrs-micronaut -am verify
```
