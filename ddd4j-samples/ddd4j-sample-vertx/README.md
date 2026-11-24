# Vert.x 默认订单样例

按[运行时类路径构建说明](../README.md#启动默认订单示例)构建 `ddd4j-sample-vertx`。使用当前分支的 JDK 从生产运行时类路径启动 `io.ddd4j.sample.vertx.VertxOrderApplication`。默认监听 `127.0.0.1:8084`，可用 `-Dsample.port=...` 修改。

默认直接使用 `POST /api/orders` 创建订单，`POST /api/orders/{id}/lines` 添加明细，`GET /api/orders/{id}` 查询。通过 `POST /api/orders/{id}/pay` 支付时须携带 `Idempotency-Key`。

旧的 `VertxOrderRoutes.router(Vertx, service)` 仍要求 Bearer，并保留 `POST /api/auth/tokens/{userId}`。默认主入口允许匿名订单。订单保存在内存中，重启后清空。持久事件和异步投影见 [Vert.x CQRS 样例](../ddd4j-sample-vertx-cqrs/README.md)。

同步业务在 Vert.x worker 上执行。应用关闭时停止接纳新业务，等待已接纳的操作结束，包括客户端已经断开的请求，然后关闭 HTTP、运行时和 Vert.x。启动失败同样释放已创建的资源。
