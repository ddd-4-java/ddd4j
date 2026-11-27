# ddd4j-sample-micronaut-cqrs

通过 Micronaut 的原生入口运行共享交易。写入和查询投影分两次完成。

启动入口见 [MicronautCqrsApplication](src/main/java/io/ddd4j/sample/micronaut/cqrs/MicronautCqrsApplication.java)。

维护路由时从 [CatalogTradeController](src/main/java/io/ddd4j/sample/micronaut/cqrs/CatalogTradeController.java) 进入。

命令经容器注入的 `CommandBus` 派发。新订单第一次 GET 返回 404；调用 `POST /orders/catalog/projections/orders/{orderNo}` 后读取快照。支付提交后，旧投影仍显示上次状态，显式重建才反映已支付。用同一库重启，再重建一次，比较租户、金额与明细。

## 从实际入口验收

框架启动参数见 [Micronaut 示例说明](README.md)。数据库变量和测试数据见 [共享 MySQL 配置](../README.md)。用专用测试库启动服务，将 BASE_URL 设置为实际监听地址。

```sh
python3 ddd4j-samples/scripts/check_catalog_trade.py "$BASE_URL" --mode cqrs --check-legacy-routes
```

探针会写入测试商品和订单。模块回归见 [MicronautCatalogCqrsHttpTest](src/test/java/io/ddd4j/sample/micronaut/cqrs/MicronautCatalogCqrsHttpTest.java)。

```sh
./mvnw -pl ddd4j-samples/ddd4j-sample-micronaut-cqrs -am verify
```
