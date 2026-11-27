# ddd4j-web-helidon

Helidon MP 的 JAX-RS 过滤器与异常映射。

公开路径从配置进入 JAX-RS 请求过滤器，解析时保留对条目周围空白的处理。调整异常映射器时通过 HTTP 契约检查实际状态和正文，直接调用 mapper 无法发现注册缺失。

实现入口见 [Ddd4jHelidonRequestFilter](src/main/java/io/ddd4j/web/helidon/Ddd4jHelidonRequestFilter.java)、[Ddd4jHelidonExceptionMapper](src/main/java/io/ddd4j/web/helidon/Ddd4jHelidonExceptionMapper.java)。

## 回归

优先运行 [Ddd4jHelidonWebConfigurationContractTest](src/test/java/io/ddd4j/web/helidon/Ddd4jHelidonWebConfigurationContractTest.java)、[Ddd4jHelidonWebContractTest](src/test/java/io/ddd4j/web/helidon/Ddd4jHelidonWebContractTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-web/ddd4j-web-helidon -am -Dtest=Ddd4jHelidonWebConfigurationContractTest,Ddd4jHelidonWebContractTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-web/ddd4j-web-helidon -am verify
```
