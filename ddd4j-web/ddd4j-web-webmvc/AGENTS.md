# ddd4j-web-webmvc

Spring MVC 拦截器、请求版本与完成回调。

## 实现与调用

从 [Ddd4jWebMvcInterceptor](src/main/java/io/ddd4j/web/webmvc/Ddd4jWebMvcInterceptor.java)、[DefaultWebMvcConfigurer](src/main/java/io/ddd4j/web/webmvc/DefaultWebMvcConfigurer.java)、[WebSocketService](src/main/java/io/ddd4j/web/webmvc/utils/WebSocketService.java) 进入。

修改 `Ddd4jWebMvcInterceptor` 时，核对公开入口 `preHandle`、`afterCompletion` 的调用方。

修改 `DefaultWebMvcConfigurer` 时，核对公开入口 `configureDefaultServletHandling`、`configureMessageConverters`、`deserialize`、`extendMessageConverters` 的调用方。

## 回归

- [Ddd4jWebMvcInterceptorTest](src/test/java/io/ddd4j/web/webmvc/Ddd4jWebMvcInterceptorTest.java) 中可从 `shouldBindAndClearPublicRequestContext` 定位断言。

- [Ddd4jWebMvcOtelIntegrationTest](src/test/java/io/ddd4j/web/webmvc/Ddd4jWebMvcOtelIntegrationTest.java) 中可从 `webMvcModuleCanUseWebOtelIntegration`、`webMvcModuleCanUseWebOtelIntegration_withTraceparent` 定位断言。

- [GlobalExceptionHandlerTest](src/test/java/io/ddd4j/web/webmvc/error/GlobalExceptionHandlerTest.java) 中可从 `httpRequestMethodNotSupportedException_shouldReturn405`、`jsonParseException_shouldReturnParsingError` 定位断言。

```sh
./mvnw -pl ddd4j-web/ddd4j-web-webmvc -am -Dtest=Ddd4jWebMvcInterceptorTest,Ddd4jWebMvcOtelIntegrationTest,GlobalExceptionHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-web/ddd4j-web-webmvc -am verify
```
