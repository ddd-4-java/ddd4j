# ddd4j-runtime-quarkus

Quarkus CDI 命令路由与上下文生命周期。

## 实现与调用

从 [Ddd4jQuarkusRuntime](src/main/java/io/ddd4j/quarkus/Ddd4jQuarkusRuntime.java)、[CdiI18nProvider](src/main/java/io/ddd4j/quarkus/i18n/CdiI18nProvider.java)、[CdiSubjectProvider](src/main/java/io/ddd4j/quarkus/subject/CdiSubjectProvider.java) 进入。

修改 `Ddd4jQuarkusRuntime` 时，核对公开入口 `start`、`readiness`、`close` 的调用方。

## 回归

- [Ddd4jQuarkusRuntimeTest](src/test/java/io/ddd4j/quarkus/Ddd4jQuarkusRuntimeTest.java)。

- [Ddd4jQuarkusReadinessHealthCheckTest](src/test/java/io/ddd4j/quarkus/health/Ddd4jQuarkusReadinessHealthCheckTest.java) 中可从 `shouldMapReadyAndUnavailableReportsWithoutExposingFailureDetails` 定位断言。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-quarkus -am -Dtest=Ddd4jQuarkusRuntimeTest,Ddd4jQuarkusReadinessHealthCheckTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-quarkus -am verify
```
