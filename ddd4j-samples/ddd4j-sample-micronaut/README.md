# Micronaut 默认订单样例

按本线 JDK 和 Maven Wrapper 使用[默认订单示例构建说明](../README.md#启动默认订单示例)，运行 `io.ddd4j.sample.micronaut.MicronautOrderApplication`。默认端口为 `8083`，可用 `--micronaut.server.port=...` 覆盖。

默认可以匿名创建订单、添加明细、支付和查询。订单入口为 `/api/orders`，支付请求使用 `POST /api/orders/{id}/pay`，并携带 `Idempotency-Key`。显式设置 `ddd4j.web.default-authentication-mode=REQUIRED` 可启用 Bearer 示例，令牌入口为 `/api/auth/tokens/{userId}`。

样例直接调用共享订单应用服务和内存仓储。普通接入无需配置命令执行器、事件存储、投影或消息队列。关闭原生 ApplicationContext 会关闭运行时并归还已有 SPI 注册。

HTTP 错误的 JSON 保留完整消息。HTTP 状态行使用简短单行消息，消息超过 4080 字符或包含换行时使用标准状态短语。
