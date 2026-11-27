# ddd4j-runtime-dropwizard

Dropwizard Bundle 的资源注册、就绪检查与关闭。

## 实现与调用

从 [Ddd4jBundle](src/main/java/io/ddd4j/dropwizard/Ddd4jBundle.java)、[Ddd4jDropwizardReadinessHealthCheck](src/main/java/io/ddd4j/dropwizard/Ddd4jDropwizardReadinessHealthCheck.java)、[Ddd4jDropwizardRuntime](src/main/java/io/ddd4j/dropwizard/Ddd4jDropwizardRuntime.java) 进入。

修改 `Ddd4jBundle` 时，核对公开入口 `initialize`、`run` 的调用方。

## 回归

- [Ddd4jDropwizardReadinessHealthCheckTest](src/test/java/io/ddd4j/dropwizard/Ddd4jDropwizardReadinessHealthCheckTest.java) 中可从 `shouldMapReadyAndUnavailableReportsWithoutExposingFailureDetails` 定位断言。

- [Ddd4jDropwizardRuntimeTest](src/test/java/io/ddd4j/dropwizard/Ddd4jDropwizardRuntimeTest.java)。

```sh
./mvnw -pl ddd4j-runtime/ddd4j-runtime-dropwizard -am -Dtest=Ddd4jDropwizardReadinessHealthCheckTest,Ddd4jDropwizardRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-runtime/ddd4j-runtime-dropwizard -am verify
```
