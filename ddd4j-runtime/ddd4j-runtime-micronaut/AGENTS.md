# ddd4j-runtime-micronaut

Micronaut Bean 工厂、投影调度及启停。

## 实现与调用

从 [Ddd4jMicronautFactory](src/main/java/io/ddd4j/micronaut/Ddd4jMicronautFactory.java)、[Ddd4jMicronautRuntime](src/main/java/io/ddd4j/micronaut/Ddd4jMicronautRuntime.java)、[MicronautDomainEventPublisher](src/main/java/io/ddd4j/micronaut/MicronautDomainEventPublisher.java) 进入。

修改 `Ddd4jMicronautRuntime` 时，核对公开入口 `start`、`readiness`、`close` 的调用方。

## 回归

- [MicronautCqrsRuntimeContractTest](src/test/java/io/ddd4j/micronaut/cqrs/MicronautCqrsRuntimeContractTest.java)。

- [Ddd4jMicronautReadinessHealthIndicatorTest](src/test/java/io/ddd4j/micronaut/health/Ddd4jMicronautReadinessHealthIndicatorTest.java) 中可从 `shouldMapReadyAndUnavailableReportsWithoutExposingFailureDetails` 定位断言。

- [Ddd4jMicronautRuntimeTest](src/test/java/io/ddd4j/micronaut/Ddd4jMicronautRuntimeTest.java) 中可从 `defaultConstructorStartsWithNoReadinessContributors` 定位断言。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-micronaut -am -Dtest=MicronautCqrsRuntimeContractTest,Ddd4jMicronautReadinessHealthIndicatorTest,Ddd4jMicronautRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-micronaut -am verify
```
