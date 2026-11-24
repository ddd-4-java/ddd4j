# Helidon 默认订单样例

使用当前分支的 JDK，按[运行时类路径构建说明](../README.md#启动默认订单示例)构建 `ddd4j-sample-helidon`。从生产运行时类路径启动 `io.ddd4j.sample.helidon.HelidonOrderApplication`。默认端口为 `8080`，可用 `-Dserver.port=...` 修改。

默认直接使用 `POST /api/orders` 创建订单，`POST /api/orders/{id}/lines` 添加明细，`GET /api/orders/{id}` 查询。列表支持 `buyerId`、`status`、`page` 和 `size`。通过 `POST /api/orders/{id}/pay` 支付时须携带 `Idempotency-Key`。

默认身份验证为 `OPTIONAL`。指定 `-Dddd4j.web.authentication-mode=REQUIRED` 可验证原 Bearer 入口，先通过 `POST /api/auth/tokens/{userId}` 签发令牌，再访问订单业务接口。

默认样例使用内存订单和同步查询，重启后数据清空。持久化事件和异步投影见 [Helidon CQRS 样例](../ddd4j-sample-helidon-cqrs/README.md)。同进程运行时可复用已注册的幂等缓存，调用方负责该缓存的生命周期。
