# ddd4j-web-webflux

WebFlux 过滤器、exchange 属性与 Reactor Context。

## 实现与调用

从 [ApiVersionWebFilter](src/main/java/io/ddd4j/web/webflux/ApiVersionWebFilter.java)、[Ddd4jWebFluxContext](src/main/java/io/ddd4j/web/webflux/Ddd4jWebFluxContext.java)、[DefaultWebFluxConfigurer](src/main/java/io/ddd4j/web/webflux/DefaultWebFluxConfigurer.java) 进入。

修改 `ApiVersionWebFilter` 时，核对公开入口 `isEnabled`、`filter` 的调用方。

修改 `Ddd4jWebFluxContext` 时，核对公开入口 `currentRequest`、`currentSubject` 的调用方。

## 回归

- [ApiVersionWebFilterTest](src/test/java/io/ddd4j/web/webflux/ApiVersionWebFilterTest.java) 中可从 `filterStoresResolvedHeaderVersionIntoExchangeAttribute`、`filterStoresPathVersionWhenHeaderAbsent` 定位断言。

- [Ddd4jWebFluxHttpContractTest](src/test/java/io/ddd4j/web/webflux/Ddd4jWebFluxHttpContractTest.java)。

- [DefaultWebFluxConfigurerContractTest](src/test/java/io/ddd4j/web/webflux/DefaultWebFluxConfigurerContractTest.java) 中可从 `registersWebJarsResolverForVersionlessPaths` 定位断言。

```sh
./mvnw -pl ddd4j-web/ddd4j-web-webflux -am -Dtest=ApiVersionWebFilterTest,Ddd4jWebFluxHttpContractTest,DefaultWebFluxConfigurerContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-web/ddd4j-web-webflux -am verify
```
