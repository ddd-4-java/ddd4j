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
