# ddd4j-runtime-helidon

Helidon MP 的 CDI 扩展、配置与显式投影。

## 实现与调用

从 [Ddd4jHelidonExtension](src/main/java/io/ddd4j/helidon/Ddd4jHelidonExtension.java)、[Ddd4jHelidonRuntime](src/main/java/io/ddd4j/helidon/Ddd4jHelidonRuntime.java)、[Ddd4jHelidonConfig](src/main/java/io/ddd4j/helidon/Ddd4jHelidonConfig.java) 进入。

修改 `Ddd4jHelidonRuntime` 时，核对公开入口 `start`、`readiness`、`close` 的调用方。

## 回归

- [Ddd4jHelidonRuntimeTest](src/test/java/io/ddd4j/helidon/Ddd4jHelidonRuntimeTest.java)。

- [Ddd4jHelidonReadinessHealthCheckTest](src/test/java/io/ddd4j/helidon/health/Ddd4jHelidonReadinessHealthCheckTest.java) 中可从 `shouldMapReadyAndUnavailableReportsWithoutExposingFailureDetails` 定位断言。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-helidon -am -Dtest=Ddd4jHelidonRuntimeTest,Ddd4jHelidonReadinessHealthCheckTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-helidon -am verify
```
