# ddd4j-web-webmvc

Spring MVC 拦截器、请求版本与完成回调。

preHandle 处理版本和上下文，完成阶段处理幂等结果。完成回调失败也需恢复外层请求上下文。版本来源按 header、路径分别测；Servlet 线程复用问题要在前一请求抛错后发起第二个请求。

实现入口见 [Ddd4jWebMvcInterceptor](src/main/java/io/ddd4j/web/webmvc/Ddd4jWebMvcInterceptor.java)、[ApiVersionWebInterceptor](src/main/java/io/ddd4j/web/webmvc/interceptor/ApiVersionWebInterceptor.java)。

## 回归

优先运行 [Ddd4jWebMvcInterceptorTest](src/test/java/io/ddd4j/web/webmvc/Ddd4jWebMvcInterceptorTest.java)、[ApiVersionWebInterceptorTest](src/test/java/io/ddd4j/web/webmvc/ApiVersionWebInterceptorTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-web/ddd4j-web-webmvc -am -Dtest=Ddd4jWebMvcInterceptorTest,ApiVersionWebInterceptorTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-web/ddd4j-web-webmvc -am verify
```
