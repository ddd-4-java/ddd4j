# ddd4j-sample-javalin

通过 Javalin 的原生入口运行共享交易。普通写入完成后可直接读取订单。

启动入口见 [JavalinSample](src/main/java/io/ddd4j/sample/javalin/JavalinSample.java)。

维护路由时从 [CatalogTradeRoutes](src/main/java/io/ddd4j/sample/javalin/CatalogTradeRoutes.java) 进入。

提交 `POST /api/orders/{orderNo}` 后，从 `GET /api/orders/{orderNo}` 读取已保存的明细。两件单价 1250 分的商品应得到 2500 分总额；用另一租户查询同一订单号应得到 404。改成交价算法进入共享领域与应用模块，HTTP 层保留租户及入参映射。

## 从实际入口验收

框架启动参数见 [Javalin 示例说明](README.md)。数据库变量和测试数据见 [共享 MySQL 配置](../README.md)。用专用测试库启动服务，将 BASE_URL 设置为实际监听地址。

```sh
python3 ddd4j-samples/scripts/check_catalog_trade.py "$BASE_URL" --mode default
```

探针会写入测试商品和订单。模块回归见 [JavalinEntryContractTest](src/test/java/io/ddd4j/sample/javalin/JavalinEntryContractTest.java)。

```sh
./mvnw -pl ddd4j-samples/ddd4j-sample-javalin -am verify
```
