# WebMVC 典型链路与异常处理设计

- **日期**：2026-06-30
- **作者**：ddd4j 架构团队
- **状态**：已实施

## 1. 目标与范围

定义 ddd4j-web-webmvc 的请求链路、异常处理、统一响应和幂等控制规范。

## 2. 请求链路

- **入口**：`Spring MVC`（DispatcherServlet）
- **线程上下文与本地化**：`RequestContextFilter` 与 `LocaleResolver`（X-Header 支持）
- **拦截器**：
  - 语言切换：`LocaleChangeInterceptor`（参数名：`lang`）
  - MDC 链路：`MdcInterceptor`（使用 `X-Request-Id` 或自动生成请求标识）
- **控制器基类**：统一事件发布、国际化消息与响应封装

## 3. 统一响应

- 成功：`ApiRestResponse.success(...)`
- 失败：`ApiRestResponse.fail(...)`
- 错误：`ApiRestResponse.error(...)`

## 4. 全局异常映射

**全局异常处理器**：`ddd4j-web/ddd4j-web-webmvc/src/main/java/io/ddd4j/web/webmvc/handler/GlobalExceptionHandler.java`

典型异常映射规则：

| 异常类型 | HTTP 状态码 | 说明 |
|---------|-----------|------|
| `NoHandlerFoundException` | 404 | 资源不存在 |
| `ServletRequestBindingException` | 400 | 请求绑定异常 |
| `MethodArgumentTypeMismatchException` | 400 | 参数类型不匹配 |
| `ConversionNotSupportedException` / `IOException` / `IndexOutOfBoundsException` / `IllegalArgumentException` | 500 | 服务器内部错误 |
| JDBC 异常（`SQLClientInfoException` 等） | 500 | 数据库异常聚合处理 |

## 5. 幂等控制

- **注解**：`@ApiIdempotent`
- **Key 生成**：请求映射值 + 参数（可选）→ MD5

## 6. 使用建议

- 控制器继承 `BaseController`，使用 `success`/`fail`/`error` 统一返回
- 对外 API 需要幂等的，标注 `@ApiIdempotent` 并合理设置过期与重试策略
- 通过国际化键值输出用户可读消息，统一体验
