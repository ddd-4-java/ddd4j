# WebFlux 响应式链路与异常处理设计

- **日期**：2026-06-29
- **作者**：ddd4j 架构团队
- **状态**：已实施

## 1. 目标与范围

定义 ddd4j-web-webflux 的响应式请求链路、异常处理和统一响应规范。

## 2. 响应式链路

- **入口**：`WebFlux`（`HttpHandler` / `DispatcherHandler`）
- **本地化与上下文**：`LocaleResolver` 与响应式 `ServerWebExchange` 上下文
- **拦截器与过滤**：按组件默认配置注册，使用响应式链路处理 MDC、语言与主题
- **控制器返回**：`Mono/Flux`，统一包装 `ApiRestResponse` 响应

## 3. 关键配置与异常处理

**全局异常处理器**：`ddd4j-web/ddd4j-web-webflux/src/main/java/io/ddd4j/web/webflux/handler/GlobalExceptionHandler.java`

典型异常映射规则：

| 异常类型 | HTTP 状态码 | 说明 |
|---------|-----------|------|
| `HttpRequestMethodNotSupportedException` | 405 | 请求方法不支持 |
| `MissingMatrixVariableException` | 400 | 缺少矩阵变量 |
| `HttpMessageNotReadableException` | 400 | 请求体不可读 |
| `BindException` / `WebExchangeBindException` | 400 | 绑定异常，输出字段错误列表 |
| `MethodArgumentConversionNotSupportedException` | 400 | 参数转换不支持 |
| `NullPointerException` / `IndexOutOfBoundsException` 等 | 500 | 服务器内部错误 |
| JDBC 异常 | 500 | 数据库异常聚合处理 |

## 4. 响应统一

- `ApiCode` + `ApiRestResponse`：同 MVC，响应式返回封装一致
- 成功：`ApiRestResponse.success(...)`
- 失败：`ApiRestResponse.fail(...)`
- 错误：`ApiRestResponse.error(...)`

## 5. 使用建议

- 控制器方法返回 `Mono<ApiRestResponse<T>>` 或 `Flux<ApiRestResponse<T>>`，避免裸类型返回
- 统一在异常处理类中映射错误，保证响应码与国际化消息一致
- 在需要追踪的链路中启用 MDC，便于日志聚合
