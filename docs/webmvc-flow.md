# WebMVC 请求链路与异常处理

## 请求链路

`ddd4j-web/ddd4j-web-webmvc` 的 `DefaultWebMvcConfiguration` 装配请求上下文过滤器、区域解析器、验证器、Bearer 认证器、访问策略、幂等生命周期和异常转换器。`ApiVersionWebMvcConfiguration` 启用时，版本拦截器先解析 API 版本，再由 `Ddd4jWebMvcInterceptor` 创建请求上下文。

```text
请求 → API 版本解析 → 请求上下文/追踪 → 公开路径或 Bearer 认证 → 幂等处理 → 控制器
                                                        ↓
响应 ← 异常转换和实际 HTTP 状态 ← 控制器结果/异常 ← 上下文与幂等资源收尾
```

`ddd4j.web.public-paths` 定义公开路径；其余路径默认要求认证。`ddd4j.web.trust-forwarded-headers` 控制是否信任转发头解析客户端地址。请求上下文携带请求 ID、追踪 ID、客户端信息和解析后的 API 版本；请求完成时拦截器清理上下文与幂等资源。

## 异常与响应

`Ddd4jWebMvcExceptionHandler` 使用共享的 `WebExceptionTranslator` 将异常映射为真实 HTTP 状态和统一的 `R` 响应。业务控制器应按当前模块 API 返回结果，不使用旧版 `ApiRestResponse`、`ApiCodeValue` 或已移除的 `ddd4j-cmpt` 路径。

相关实现：`ddd4j-web/ddd4j-web-webmvc/src/main/java/io/ddd4j/web/webmvc/DefaultWebMvcConfiguration.java`、`Ddd4jWebMvcInterceptor.java` 和 `Ddd4jWebMvcExceptionHandler.java`。三线行为契约一致，Spring 与其他三方依赖配置分别由各线维护。
