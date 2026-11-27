# ddd4j-web-webflux

WebFlux 过滤器、exchange 属性与 Reactor Context。

请求信息经 Reactor Context 传播。解析 API 版本时 header 优先、缺少 header 才看路径，并放入 exchange 属性。关闭可选追踪时，公开请求仍要可用，业务异常仍按原状态返回。

实现入口见 [ApiVersionWebFilter](src/main/java/io/ddd4j/web/webflux/ApiVersionWebFilter.java)、[Ddd4jWebFluxContext](src/main/java/io/ddd4j/web/webflux/Ddd4jWebFluxContext.java)。

## 回归

优先运行 [ApiVersionWebFilterTest](src/test/java/io/ddd4j/web/webflux/ApiVersionWebFilterTest.java)、[Ddd4jWebFluxHttpContractTest](src/test/java/io/ddd4j/web/webflux/Ddd4jWebFluxHttpContractTest.java)、[Ddd4jWebFluxOptionalTracingTest](src/test/java/io/ddd4j/web/webflux/Ddd4jWebFluxOptionalTracingTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-web/ddd4j-web-webflux -am -Dtest=ApiVersionWebFilterTest,Ddd4jWebFluxHttpContractTest,Ddd4jWebFluxOptionalTracingTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-web/ddd4j-web-webflux -am verify
```
