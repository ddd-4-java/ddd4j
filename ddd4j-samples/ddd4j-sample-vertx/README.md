# Vert.x 默认商品交易样例

按[运行时类路径构建说明](../README.md#启动默认订单示例)构建 `ddd4j-sample-vertx`。使用当前分支的 JDK 从生产运行时类路径启动 `io.ddd4j.sample.vertx.VertxOrderApplication`。默认监听 `127.0.0.1:8084`，可用 `-Dsample.port=...` 修改。

默认启动原生应用时，Flyway 先迁移专用 MySQL 库。应用随后提供商品、规格、订单及成交明细的读写示例。[商品交易的数据库配置、四个接口与验证命令](../README.md#启动默认订单示例)。

同步业务在 Vert.x worker 上执行。应用关闭时停止接纳新业务，等待已接纳的操作结束，包括客户端已经断开的请求，然后关闭 HTTP、运行时和 Vert.x。启动失败同样释放已创建的资源。
