# ddd4j-sample-order-application

商品与交易用例、事务后的事件发布及读侧重建。

place 在事务里读取商品并保存订单，事务成功返回后发布 TradeOrderPlaced。支付和状态变更先保存结果，再发布拉取的事件；重复确认支付走已有结果，不重复发布。投影重建按租户与订单串行，防止较早的读取最后发布并覆盖新快照。

实现入口见 [CatalogService](src/main/java/io/ddd4j/sample/order/app/CatalogService.java)、[CatalogOrderView](src/main/java/io/ddd4j/sample/order/app/projection/CatalogOrderView.java)。

## 回归

优先运行 [CatalogTradeTest](../ddd4j-sample-order-start/src/test/java/io/ddd4j/sample/order/CatalogTradeTest.java)、[CatalogArchitectureTest](../ddd4j-sample-order-start/src/test/java/io/ddd4j/sample/order/CatalogArchitectureTest.java)。这些用例位于 ddd4j-sample-order-start，从仓库根目录选择该模块。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-samples/ddd4j-sample-order-start -am -Dtest=CatalogTradeTest,CatalogArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-samples/ddd4j-sample-order-start -am verify
```

所列集成用例使用 Testcontainers。核对测试报告中对应测试类的执行数和跳过数；只运行单元用例时，单独记录仍缺的数据库或 broker 往返。
