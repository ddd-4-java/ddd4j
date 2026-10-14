# WebFlux 请求链路与异常处理

## 请求链路

`ddd4j-web/ddd4j-web-webflux` 的 `DefaultWebFluxConfiguration` 装配响应式请求上下文过滤器、区域解析器、Bearer 认证器、访问策略和幂等生命周期。启用 `ApiVersionWebFluxConfiguration` 时，`ApiVersionWebFilter` 在 `Ddd4jWebFluxFilter` 前解析版本；后者建立请求上下文并执行认证和幂等处理。

```text
请求 → API 版本过滤器 → 请求上下文过滤器 → 公开路径或 Bearer 认证 → 幂等处理 → Handler
                                                                  ↓
响应 ← 实际 HTTP 状态与 R 错误体 ← 结果/异常 ← 响应式资源收尾
```

公开路径和转发头信任分别由 `ddd4j.web.public-paths`、`ddd4j.web.trust-forwarded-headers` 控制。业务 Handler 可按自己的响应式 API 返回 `Mono` 或 `Flux`；不要把旧版 `ApiRestResponse` 当作当前模块的必需类型。

## 异常与响应

`GlobalErrorWebExceptionHandler` 通过共享的异常转换规则设置真实 HTTP 状态，并序列化统一的 `R` 错误体。当前实现位于 `ddd4j-web/ddd4j-web-webflux/src/main/java/io/ddd4j/web/webflux/error/`。三线维持同一业务错误语义；Jackson、Spring 和 Reactor 的实际依赖与适配由各线分别维护。
