# WebMVC 典型链路与异常处理

## 请求链路

- 入口：`Spring MVC`（DispatcherServlet）
- 线程上下文与本地化：`RequestContextFilter` 与 `LocaleResolver`（X-Header 支持）
- 拦截器：
    - 语言切换：`LocaleChangeInterceptor`（参数名：`lang`）
    - 主题切换：`ThemeChangeInterceptor`（参数名：`theme`）
    - MDC 链路：`Slf4jMDCInterceptor`（对接 `Sequence` 生成请求标识）
- 控制器基类：统一事件发布、国际化消息与响应封装

## 关键配置

- `ddd4j-cmpt/ddd4j-cmpt-webmvc/src/main/java/io/ddd4j/boot/cmpt/webmvc/DefaultWebMvcConfiguration.java:53-76`
- `ddd4j-core/src/main/java/io/ddd4j/boot/core/web/BaseController.java:21-35`

## 统一响应

- 成功：`ApiRestResponse.success(...)`
- 失败：`ApiRestResponse.fail(...)`
- 错误：`ApiRestResponse.error(...)`
- 参考：`ddd4j-core/src/main/java/io/ddd4j/boot/core/ApiRestResponse.java:118-166`

## 全局异常映射

- 位置：`ddd4j-cmpt/ddd4j-cmpt-webmvc/src/main/java/io/ddd4j/boot/cmpt/webmvc/webmvc/GlobalExceptionHandler.java`
- 典型规则：
    - `NoHandlerFoundException` → `404`：`GlobalExceptionHandler.java:72-81`
    - `ServletRequestBindingException` → `400`：`GlobalExceptionHandler.java:198-206`
    - `MethodArgumentTypeMismatchException` → `400`：`GlobalExceptionHandler.java:331-340`
    - `ConversionNotSupportedException`/`IOException`/`IndexOutOfBoundsException`/`IllegalArgumentException` → `500`：
      `GlobalExceptionHandler.java:439-517`
    - JDBC 异常聚合处理（`SQLClientInfoException` 等）：`GlobalExceptionHandler.java:616-625`

## 幂等控制

- 注解：`ddd4j-core/src/main/java/io/ddd4j/boot/core/annotation/ApiIdempotent.java`
- Key 生成：请求映射值 + 参数（可选）→ MD5：`ddd4j-core/src/main/java/io/ddd4j/boot/core/utils/IdempotentUtils.java`

## 使用建议

- 控制器继承 `BaseController`，使用 `success`/`fail`/`error` 统一返回
- 对外 API 需要幂等的，标注 `@ApiIdempotent` 并合理设置过期与重试策略
- 通过国际化键值输出用户可读消息，统一体验

