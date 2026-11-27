# ddd4j-web-quarkus

CDI 发现的 JAX-RS 请求过滤器与异常处理。

## 实现与调用

从 [Ddd4jQuarkusWebFilter](src/main/java/io/ddd4j/web/quarkus/Ddd4jQuarkusWebFilter.java)、[AccessLogFilter](src/main/java/io/ddd4j/web/quarkus/AccessLogFilter.java)、[Ddd4jQuarkusWebConfiguration](src/main/java/io/ddd4j/web/quarkus/Ddd4jQuarkusWebConfiguration.java) 进入。

修改 `Ddd4jQuarkusWebFilter` 时，核对公开入口 `request`、`response` 的调用方。

修改 `AccessLogFilter` 时，核对公开入口 `filter` 的调用方。

## 回归

- [Ddd4jQuarkusOtelIntegrationTest](src/test/java/io/ddd4j/web/quarkus/Ddd4jQuarkusOtelIntegrationTest.java) 中可从 `quarkusModuleCanUseWebOtelIntegration`、`quarkusModuleCanUseWebOtelIntegration_withTraceparent` 定位断言。

- [Ddd4jQuarkusWebContractTest](src/test/java/io/ddd4j/web/quarkus/Ddd4jQuarkusWebContractTest.java)。

```sh
./mvnw -pl ddd4j-web/ddd4j-web-quarkus -am -Dtest=Ddd4jQuarkusOtelIntegrationTest,Ddd4jQuarkusWebContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-web/ddd4j-web-quarkus -am verify
```
