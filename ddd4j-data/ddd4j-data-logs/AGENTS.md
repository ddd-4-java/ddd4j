# ddd4j-data-logs

API 操作日志切面、请求上下文和日志提供者。

切面需要方法调用信息，URI 和 IP 来自外部提供的请求上下文。改取值顺序时覆盖 Servlet 请求与原生上下文同时存在、以及只有原生上下文两种情况。HTTP 桥接断言放在 web-webmvc，单独构建本 JAR 无法运行它。

实现入口见 [ApiOperationLogAspect](src/main/java/io/ddd4j/data/logs/aspect/ApiOperationLogAspect.java)、[ApiOperationLogRequestContext](src/main/java/io/ddd4j/data/logs/ApiOperationLogRequestContext.java)。

## 回归

优先运行 [ApiOperationLogHttpBridgeTest](../../ddd4j-web/ddd4j-web-webmvc/src/test/java/io/ddd4j/web/webmvc/logs/ApiOperationLogHttpBridgeTest.java)。这些用例位于 ddd4j-web-webmvc，从仓库根目录选择该模块。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-web/ddd4j-web-webmvc -am -Dtest=ApiOperationLogHttpBridgeTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-web/ddd4j-web-webmvc -am verify
```
