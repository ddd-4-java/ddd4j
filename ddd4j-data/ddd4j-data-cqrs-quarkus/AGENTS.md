# ddd4j-data-cqrs-quarkus

维护 quarkus 的命令分发。改动执行器注册或调用方式时，从本模块的命令总线追到 core 的 CommandRegistry 和返回对象，再验证对应框架的实际装配。

## 实现与调用

从 [QuarkusCommandBus](src/main/java/io/ddd4j/data/cqrs/quarkus/QuarkusCommandBus.java) 进入。

## 回归

- [QuarkusCommandBusIT](src/test/java/io/ddd4j/data/cqrs/quarkus/QuarkusCommandBusIT.java) 中可从 `execute_已注册命令_应路由到真实Handler并返回成功`、`装配期冲突_整批拒绝应传播为Bean创建失败` 定位断言。

- [QuarkusCommandBusPlatformContractTest](src/test/java/io/ddd4j/data/cqrs/quarkus/QuarkusCommandBusPlatformContractTest.java) 中可从 `declaresTheCdiNamespaceAndProxyConstructorsForThisJdk` 定位断言。

- [CqrsQuarkusModuleIndependenceTest](src/test/java/io/ddd4j/data/cqrs/quarkus/arch/CqrsQuarkusModuleIndependenceTest.java)。

```sh
./mvnw -pl ddd4j-data/ddd4j-data-cqrs-quarkus -am -Dtest=QuarkusCommandBusIT,QuarkusCommandBusPlatformContractTest,CqrsQuarkusModuleIndependenceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-data/ddd4j-data-cqrs-quarkus -am verify
```
