# ddd4j-web-javalin

Javalin 处理器、失败事件及请求作用域。

原生路由可能改写当前方法；发布请求失败事件要保留入站 HTTP method。改 before/after 或异常处理时沿请求结束回调检查 OTel scope，确保下一请求不会继承前一请求的上下文。

实现入口见 [Ddd4jJavalinWeb](src/main/java/io/ddd4j/web/javalin/Ddd4jJavalinWeb.java)、[BaseHandler](src/main/java/io/ddd4j/web/javalin/BaseHandler.java)。

默认审计 IP 读取 Servlet 连接对端地址。测试应配置与对端不同的 Javalin IP 解析结果，确保自定义解析器不会覆盖审计值。

## 回归

优先运行 [BaseHandlerFailureEventTest](src/test/java/io/ddd4j/web/javalin/BaseHandlerFailureEventTest.java)、[Ddd4jJavalinOtelScopeTest](src/test/java/io/ddd4j/web/javalin/Ddd4jJavalinOtelScopeTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-web/ddd4j-web-javalin -am -Dtest=BaseHandlerFailureEventTest,Ddd4jJavalinOtelScopeTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-web/ddd4j-web-javalin -am verify
```
