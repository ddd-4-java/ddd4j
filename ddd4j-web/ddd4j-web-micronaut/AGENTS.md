# ddd4j-web-micronaut

Micronaut HTTP 上下文、地址解析与错误响应。

HTTP 上下文同时承载请求与主体。改远端地址来源时使用 Micronaut 请求对象校验；改异常翻译时检查实际返回的状态与响应体。上下文构造应及早拒绝缺失的 request 或 subject。

实现入口见 [Ddd4jMicronautContext](src/main/java/io/ddd4j/web/micronaut/Ddd4jMicronautContext.java)、[Ddd4jMicronautExceptionHandler](src/main/java/io/ddd4j/web/micronaut/Ddd4jMicronautExceptionHandler.java)。

## 回归

优先运行 [Ddd4jMicronautRemoteAddressContractTest](src/test/java/io/ddd4j/web/micronaut/Ddd4jMicronautRemoteAddressContractTest.java)、[Ddd4jMicronautExceptionHandlerTest](src/test/java/io/ddd4j/web/micronaut/Ddd4jMicronautExceptionHandlerTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-web/ddd4j-web-micronaut -am -Dtest=Ddd4jMicronautRemoteAddressContractTest,Ddd4jMicronautExceptionHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-web/ddd4j-web-micronaut -am verify
```
