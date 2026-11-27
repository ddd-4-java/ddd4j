# ddd4j-sample-vertx-satoken

通过 Vert.x 的原生入口运行共享交易。使用 Sa-Token 登录并检查交易权限。

启动入口见 [VertxSaTokenApplication](src/main/java/io/ddd4j/sample/vertx/satoken/VertxSaTokenApplication.java)。

交易接口前缀为 `/orders/catalog`，同时读取 `X-Tenant-Id`。本入口的认证请求头为 `Authorization: Bearer <登录凭证>`。改鉴权时连续发送匿名、只读、获准写入和注销后重用旧凭证四组请求，核对拒绝写入的订单没有落库。

## 从实际入口验收

框架启动参数见 [Vert.x 示例说明](README.md)。数据库变量和测试数据见 [共享 MySQL 配置](../README.md)。用专用测试库启动服务，将 BASE_URL 设置为实际监听地址。

用写账号向 `POST /auth/login` 提交 JSON 字段 `username` 和 `password`。将所得凭证赋给 `LOGIN_TOKEN`。

```sh
CATALOG_TEST_TOKEN="Bearer $LOGIN_TOKEN" python3 ddd4j-samples/scripts/check_catalog_trade.py "$BASE_URL" --mode auth --auth-header Authorization --check-legacy-routes
```

探针会写入测试商品和订单。模块回归见 [NativeAuthHttpTest](src/test/java/io/ddd4j/sample/vertx/satoken/NativeAuthHttpTest.java)。

```sh
./mvnw -pl ddd4j-samples/ddd4j-sample-vertx-satoken -am verify
```
