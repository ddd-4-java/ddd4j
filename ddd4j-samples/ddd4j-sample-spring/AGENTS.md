# ddd4j-sample-spring

通过 Spring 的原生入口运行共享交易。普通写入完成后可直接读取订单。

启动入口见 [SpringSampleApplication](src/main/java/io/ddd4j/sample/spring/SpringSampleApplication.java)。

维护路由时从 [CatalogTradeController](src/main/java/io/ddd4j/sample/spring/CatalogTradeController.java) 进入。

提交 `POST /api/orders/{orderNo}` 后，从 `GET /api/orders/{orderNo}` 读取已保存的明细。两件单价 1250 分的商品应得到 2500 分总额；用另一租户查询同一订单号应得到 404。改成交价算法进入共享领域与应用模块，HTTP 层保留租户及入参映射。

默认 profile 为 `catalog-trade`。MVC 与 WebFlux 使用同一份交易存储；改响应式装配时还要覆盖 [SampleReactiveConfiguration.java](src/main/java/io/ddd4j/sample/spring/SampleReactiveConfiguration.java)，以实际启用的 Web 模式发请求。

## 从实际入口验收

框架启动参数见 [Spring 示例说明](README.md)。数据库变量和测试数据见 [共享 MySQL 配置](../README.md)。用专用测试库启动服务，将 BASE_URL 设置为实际监听地址。

```sh
python3 ddd4j-samples/scripts/check_catalog_trade.py "$BASE_URL" --mode default
```

探针会写入测试商品和订单。模块回归见 [SpringOrderHttpTest](src/test/java/io/ddd4j/sample/spring/SpringOrderHttpTest.java)。

```sh
./mvnw -pl ddd4j-samples/ddd4j-sample-spring -am verify
```
