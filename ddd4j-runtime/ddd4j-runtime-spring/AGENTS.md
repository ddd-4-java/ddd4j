# ddd4j-runtime-spring

Spring Bean 发现、命令事务与投影调度。

## 实现与调用

从 [SpringCommandBus](src/main/java/io/ddd4j/spring/command/SpringCommandBus.java)、[ApplicationService](src/main/java/io/ddd4j/spring/annotation/ApplicationService.java)、[DomainRepository](src/main/java/io/ddd4j/spring/annotation/DomainRepository.java) 进入。

修改 `SpringCommandBus` 时，核对公开入口 `afterSingletonsInstantiated`、`execute` 的调用方。

## 回归

- [SpringCommandBusTest](src/test/java/io/ddd4j/spring/command/SpringCommandBusTest.java) 中可从 `shouldRegisterExecutorsOnContextRefresh`、`shouldRouteCommandToCorrectExecutor` 定位断言。

- [MicrometerProjectionMetricsTest](src/test/java/io/ddd4j/spring/cqrs/MicrometerProjectionMetricsTest.java) 中可从 `constructor_nullRegistry_shouldThrowNPE`、`shouldRecordEventCounterAndDuration` 定位断言。

- [SignKitEmptyFormTest](src/test/java/io/ddd4j/spring/util/SignKitEmptyFormTest.java) 中可从 `multiValueMapTreatsNullAsEmpty`、`arrayMapTreatsNullAsEmpty` 定位断言。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-spring -am -Dtest=SpringCommandBusTest,MicrometerProjectionMetricsTest,SignKitEmptyFormTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-spring -am verify
```
