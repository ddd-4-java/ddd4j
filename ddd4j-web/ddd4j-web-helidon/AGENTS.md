# ddd4j-web-helidon

Helidon MP 的 JAX-RS 过滤器与异常映射。

## 实现与调用

从 [Ddd4jHelidonExceptionMapper](src/main/java/io/ddd4j/web/helidon/Ddd4jHelidonExceptionMapper.java)、[Ddd4jHelidonRequestFilter](src/main/java/io/ddd4j/web/helidon/Ddd4jHelidonRequestFilter.java)、[Ddd4jHelidonIllegalStateExceptionMapper](src/main/java/io/ddd4j/web/helidon/Ddd4jHelidonIllegalStateExceptionMapper.java) 进入。

修改 `Ddd4jHelidonExceptionMapper` 时，核对公开入口 `toResponse` 的调用方。

修改 `Ddd4jHelidonRequestFilter` 时，核对公开入口 `filter` 的调用方。

## 回归

- [Ddd4jHelidonWebContractTest](src/test/java/io/ddd4j/web/helidon/Ddd4jHelidonWebContractTest.java)。

- [Ddd4jHelidonOtelIntegrationTest](src/test/java/io/ddd4j/web/helidon/Ddd4jHelidonOtelIntegrationTest.java) 中可从 `helidonModuleCanUseWebOtelIntegration`、`helidonModuleCanUseWebOtelIntegration_withTraceparent` 定位断言。

```sh
./mvnw -pl ddd4j-web/ddd4j-web-helidon -am -Dtest=Ddd4jHelidonWebContractTest,Ddd4jHelidonOtelIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-web/ddd4j-web-helidon -am verify
```
