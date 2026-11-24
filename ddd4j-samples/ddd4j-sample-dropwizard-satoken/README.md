# Dropwizard SaToken

原生入口 `io.ddd4j.sample.dropwizard.satoken.DropwizardSaTokenApplication server dropwizard-satoken.yml`。Ddd4jBundle 注册原生 SubjectProvider，Web Bundle 执行 Bearer 认证和异常映射；Servlet Filter 在同步 Jersey 请求外围绑定并在 finally 恢复原生上下文。实际订单业务复用共享 Order application/local 模块。

`POST /auth/login` 使用 JSON `{"username":"alice","password":"sample-password"}`；演示账号 `alice` 有 `order:read`/`order:write`，`reader` 只有读取权限。令牌用于 `Authorization: Bearer ...`，写请求另带 `Idempotency-Key`。`POST /auth/logout` 使原生会话失效。

业务路由：`POST /orders`、`GET /orders/{id}`、`POST /orders/{id}/lines`、`POST /orders/{id}/pay`。凭证错误返回 401、读者写操作返回 403；无效业务输入经现有 Web mapper 返回错误。无调试专用 HTTP 接口。

Order 与原生会话都为 demo-memory，固定账号仅演示。Managed 复用调用方已注册的 Sa-Token DAO，仅在空槽位创建并管理内存 DAO。关闭时恢复仍属于样例的 SDK 注册；此处不是同 JVM 多应用会话隔离框架。同步请求包括客户端断连后的已接纳业务，在 native Jetty graceful deadline 内完成；断连不等于事务回滚。未提供异步 Jersey/SSE 上下文传播。
