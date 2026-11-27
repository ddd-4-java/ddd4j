# ddd4j-web-quarkus

CDI 发现的 JAX-RS 请求过滤器与异常处理。

过滤器须由 CDI 发现。认证失败会提前结束请求，幂等完成也可能抛错；两条路径都要关闭请求作用域。改过滤器优先级时在真实容器确认过滤器执行过，再核对响应。

实现入口见 [Ddd4jQuarkusWebFilter](src/main/java/io/ddd4j/web/quarkus/Ddd4jQuarkusWebFilter.java)。

## 回归

优先运行 [Ddd4jQuarkusFilterCdiContractTest](src/test/java/io/ddd4j/web/quarkus/Ddd4jQuarkusFilterCdiContractTest.java)、[Ddd4jQuarkusOtelScopeTest](src/test/java/io/ddd4j/web/quarkus/Ddd4jQuarkusOtelScopeTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-web/ddd4j-web-quarkus -am -Dtest=Ddd4jQuarkusFilterCdiContractTest,Ddd4jQuarkusOtelScopeTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-web/ddd4j-web-quarkus -am verify
```
