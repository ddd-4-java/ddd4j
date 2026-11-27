# ddd4j-sample-order-domain

商品与订单聚合、成交快照和仓储端口。

维护商品与订单两个聚合，SKU 和订单明细归各自聚合。恢复已有订单时要拥有独立的明细集合，拒绝跨租户子项。支付、取消、履约的状态迁移放在 TradeOrder；成交价与名称从下单快照读取，避免后续商品修改改变历史订单。

实现入口见 [TradeOrder](src/main/java/io/ddd4j/sample/order/domain/model/TradeOrder.java)、[GoodsSpu](src/main/java/io/ddd4j/sample/order/domain/model/GoodsSpu.java)、[TradeOrderItem](src/main/java/io/ddd4j/sample/order/domain/model/TradeOrderItem.java)。

GoodsSkuOffer 是从目录选购规格时使用的领域对象；四表的业务模型为 GoodsSpu、GoodsSku、TradeOrder、TradeOrderItem。调整报价对象时要沿下单路径核对实际保存的明细快照。

## 回归

优先运行 [CatalogArchitectureTest](../ddd4j-sample-order-start/src/test/java/io/ddd4j/sample/order/CatalogArchitectureTest.java)、[CatalogTradeTest](../ddd4j-sample-order-start/src/test/java/io/ddd4j/sample/order/CatalogTradeTest.java)。这些用例位于 ddd4j-sample-order-start，从仓库根目录选择该模块。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-samples/ddd4j-sample-order-start -am -Dtest=CatalogArchitectureTest,CatalogTradeTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-samples/ddd4j-sample-order-start -am verify
```

所列集成用例使用 Testcontainers。核对测试报告中对应测试类的执行数和跳过数；只运行单元用例时，单独记录仍缺的数据库或 broker 往返。
