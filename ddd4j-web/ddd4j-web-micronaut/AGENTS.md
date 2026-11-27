# ddd4j-web-micronaut

Micronaut HTTP 上下文、地址解析与错误响应。

## 实现与调用

从 [Ddd4jMicronautContext](src/main/java/io/ddd4j/web/micronaut/Ddd4jMicronautContext.java)、[Ddd4jMicronautExceptionHandler](src/main/java/io/ddd4j/web/micronaut/Ddd4jMicronautExceptionHandler.java)、[Ddd4jMicronautWebConfiguration](src/main/java/io/ddd4j/web/micronaut/Ddd4jMicronautWebConfiguration.java) 进入。

修改 `Ddd4jMicronautContext` 时，核对公开入口 `current`、`updateThreadContext`、`restoreThreadContext`、`requestContext` 的调用方。

修改 `Ddd4jMicronautExceptionHandler` 时，核对公开入口 `handle` 的调用方。

## 回归

- [Ddd4jMicronautWebConfigurationTest](src/test/java/io/ddd4j/web/micronaut/Ddd4jMicronautWebConfigurationTest.java) 中可从 `defaults`、`setters` 定位断言。

- [Ddd4jMicronautOtelIntegrationTest](src/test/java/io/ddd4j/web/micronaut/Ddd4jMicronautOtelIntegrationTest.java) 中可从 `micronautModuleCanUseWebOtelIntegration`、`micronautModuleCanUseWebOtelIntegration_withTraceparent` 定位断言。

- [Ddd4jMicronautWebContractTest](src/test/java/io/ddd4j/web/micronaut/Ddd4jMicronautWebContractTest.java)。

```sh
./mvnw -pl ddd4j-web/ddd4j-web-micronaut -am -Dtest=Ddd4jMicronautWebConfigurationTest,Ddd4jMicronautOtelIntegrationTest,Ddd4jMicronautWebContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-web/ddd4j-web-micronaut -am verify
```
