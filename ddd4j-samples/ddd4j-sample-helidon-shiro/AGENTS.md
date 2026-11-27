# ddd4j-sample-helidon-shiro

通过 Helidon MP 的原生入口运行共享交易。使用 Shiro 登录并检查交易权限。

启动入口见 [HelidonShiroApplication](src/main/java/io/ddd4j/sample/helidon/shiro/HelidonShiroApplication.java)。

维护路由时从 [CatalogTradeResource](src/main/java/io/ddd4j/sample/helidon/shiro/CatalogTradeResource.java) 进入。登录、请求绑定及清理由 [NativeAuthFilter](src/main/java/io/ddd4j/sample/helidon/shiro/NativeAuthFilter.java) 所在的鉴权装配配合完成。

交易接口前缀为 `/orders/catalog`，同时读取 `X-Tenant-Id`。本入口的认证请求头为 `Authorization: Bearer <登录凭证>`。改鉴权时连续发送匿名、只读、获准写入和注销后重用旧凭证四组请求，核对拒绝写入的订单没有落库。

## 从实际入口验收

框架启动参数见 [Helidon MP 示例说明](README.md)。数据库变量和测试数据见 [共享 MySQL 配置](../README.md)。用专用测试库启动服务，将 BASE_URL 设置为实际监听地址。

向 `POST /auth/login` 提交 JSON 字段 `username`、`password`，从响应的 `data.token` 取值。将所得凭证赋给 `LOGIN_TOKEN`。

```sh
CATALOG_TEST_TOKEN="Bearer $LOGIN_TOKEN" python3 ddd4j-samples/scripts/check_catalog_trade.py "$BASE_URL" --mode auth --auth-header Authorization --check-legacy-routes
```

探针会写入测试商品和订单。模块回归见 [NativeAuthHttpTest](src/test/java/io/ddd4j/sample/helidon/shiro/NativeAuthHttpTest.java)。

```sh
./mvnw -pl ddd4j-samples/ddd4j-sample-helidon-shiro -am verify
```
