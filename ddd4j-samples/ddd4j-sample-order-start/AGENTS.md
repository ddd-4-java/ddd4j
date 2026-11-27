# ddd4j-sample-order-start

商品交易装配、MySQL 跨层回归及架构规则验收。

维护共享启动装配和四表跨层验收。新建实例后使用同一数据库恢复订单，从四表重新投影；检查租户、订单状态和原始成交价。目录与工件边界由 CatalogArchitectureTest 核对。

实现入口见 [CatalogRuntime](src/main/java/io/ddd4j/sample/order/start/CatalogRuntime.java)、[TradeEventLog](src/main/java/io/ddd4j/sample/order/start/TradeEventLog.java)。

## 回归

优先运行 [CatalogTradeTest](src/test/java/io/ddd4j/sample/order/CatalogTradeTest.java)、[CatalogArchitectureTest](src/test/java/io/ddd4j/sample/order/CatalogArchitectureTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-samples/ddd4j-sample-order-start -am -Dtest=CatalogTradeTest,CatalogArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-samples/ddd4j-sample-order-start -am verify
```

所列集成用例使用 Testcontainers。核对测试报告中对应测试类的执行数和跳过数；只运行单元用例时，单独记录仍缺的数据库或 broker 往返。
