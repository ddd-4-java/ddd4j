# ddd4j-web-webflux

WebFlux 过滤器、exchange 属性与 Reactor Context。

## 实现与调用

从 [Ddd4jWebFluxContext](src/main/java/io/ddd4j/web/webflux/Ddd4jWebFluxContext.java)、[DefaultWebFluxConfigurer](src/main/java/io/ddd4j/web/webflux/DefaultWebFluxConfigurer.java)、[Ddd4jWebFluxFilter](src/main/java/io/ddd4j/web/webflux/Ddd4jWebFluxFilter.java) 进入。

修改 `Ddd4jWebFluxContext` 时，核对公开入口 `currentRequest`、`currentSubject` 的调用方。

修改 `DefaultWebFluxConfigurer` 时，核对公开入口 `addResourceHandlers` 的调用方。

## 回归

- [Ddd4jWebFluxHttpContractTest](src/test/java/io/ddd4j/web/webflux/Ddd4jWebFluxHttpContractTest.java)。

- [Ddd4jWebFluxContractTest](src/test/java/io/ddd4j/web/webflux/Ddd4jWebFluxContractTest.java) 中可从 `shouldPropagateRequestThroughReactorContext`、`shouldUseTranslatedHttpStatusAndResponseBody` 定位断言。

- [Ddd4jWebFluxOtelIntegrationTest](src/test/java/io/ddd4j/web/webflux/Ddd4jWebFluxOtelIntegrationTest.java) 中可从 `webfluxModuleCanUseWebOtelIntegration`、`webfluxModuleCanUseWebOtelIntegration_withTraceparent` 定位断言。

```sh
./mvnw -pl ddd4j-web/ddd4j-web-webflux -am -Dtest=Ddd4jWebFluxHttpContractTest,Ddd4jWebFluxContractTest,Ddd4jWebFluxOtelIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-web/ddd4j-web-webflux -am verify
```
