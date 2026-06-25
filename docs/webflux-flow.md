# WebFlux 响应式链路与异常处理

## 响应式链路
- 入口：`WebFlux`（`HttpHandler`/`DispatcherHandler`）
- 本地化与上下文：`LocaleResolver` 与响应式 `ServerWebExchange` 上下文
- 拦截器与过滤：按组件默认配置注册，使用响应式链路处理 MDC、语言与主题
- 控制器返回 `Mono/Flux`，统一包装 `ApiRestResponse` 响应

## 关键配置与异常处理
- 全局异常：`ddd4j-cmpt/ddd4j-cmpt-webflux/src/main/java/io/ddd4j/boot/cmpt/webflux/handler/GlobalExceptionHandler.java`
- 典型规则：
  - `HttpRequestMethodNotSupportedException` → `405`：`GlobalExceptionHandler.java:63-73`
  - `MissingMatrixVariableException` → `400`：`GlobalExceptionHandler.java:104-115`
  - `HttpMessageNotReadableException` → `400`：`GlobalExceptionHandler.java:210-219`
  - `BindException`/`WebExchangeBindException` → `400`，输出字段错误列表：`GlobalExceptionHandler.java:255-267`
  - `MethodArgumentConversionNotSupportedException` → `400`：`GlobalExceptionHandler.java:329-339`
  - 服务器错误（`NullPointerException`/`IndexOutOfBoundsException` 等）→ `500`：`GlobalExceptionHandler.java:416-469`
  - JDBC 异常聚合处理：`GlobalExceptionHandler.java:567-584`、`620-629`

## 响应统一
- `ApiCode` + `ApiRestResponse`：同 MVC，响应式返回封装一致
- 参考：`ddd4j-core/src/main/java/io/ddd4j/boot/core/ApiCode.java`、`ApiRestResponse.java`

## 使用建议
- 控制器方法返回 `Mono<ApiRestResponse<T>>` 或 `Flux<ApiRestResponse<T>>`，避免裸类型返回
- 统一在异常处理类中映射错误，保证响应码与国际化消息一致
- 在需要追踪的链路中启用 MDC，便于日志聚合

