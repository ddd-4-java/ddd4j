# Dropwizard 默认订单样例

按[运行时类路径构建说明](../README.md#启动默认订单示例)构建 `ddd4j-sample-dropwizard`。使用当前分支 JDK，运行 `io.ddd4j.sample.dropwizard.DropwizardOrderApplication`，参数为 `server ddd4j-samples/ddd4j-sample-dropwizard/src/main/resources/dropwizard-order.yml`。YAML 的应用和管理端口默认分别为 `8086` 和 `8087`。

默认通过 `POST /api/orders` 匿名创建订单，`POST /api/orders/{id}/lines` 添加明细，`GET /api/orders/{id}` 查询。`POST /api/orders/{id}/pay` 支付须携带 `Idempotency-Key`。

将 `ddd4jWeb.defaultAuthenticationMode` 设为 `REQUIRED` 可验证原 Bearer 入口。先通过 `POST /api/auth/tokens/{userId}` 签发令牌，再访问订单接口。将 `ddd4jWeb.idempotencyEnabled` 设为 `false` 会关闭 Web 层幂等拦截，支付操作仍需要业务幂等键。

默认样例使用内存订单，重启后数据清空。事件持久化和异步投影见 [Dropwizard CQRS 样例](../ddd4j-sample-dropwizard-cqrs/README.md)。
