# ddd4j-sample-order-adapter

普通交易与 CQRS 的 HTTP 适配。

HTTP 请求在这里转换成共享命令或查询。普通读取可直接返回订单，CQRS 读取由显式投影驱动。改路径、请求字段或状态映射时，同时检查框架路由及 HTTP 探针；数据库事务与 Mapper 归 JDBC 模块。

实现入口见 [CatalogApi](src/main/java/io/ddd4j/sample/order/adapter/http/CatalogApi.java)、[CatalogCqrs](src/main/java/io/ddd4j/sample/order/adapter/http/CatalogCqrs.java)。

## 回归

优先运行 [CatalogArchitectureTest](../ddd4j-sample-order-start/src/test/java/io/ddd4j/sample/order/CatalogArchitectureTest.java)、[CatalogTradeTest](../ddd4j-sample-order-start/src/test/java/io/ddd4j/sample/order/CatalogTradeTest.java)。这些用例位于 ddd4j-sample-order-start，从仓库根目录选择该模块。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-samples/ddd4j-sample-order-start -am -Dtest=CatalogArchitectureTest,CatalogTradeTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-samples/ddd4j-sample-order-start -am verify
```

所列集成用例使用 Testcontainers。核对测试报告中对应测试类的执行数和跳过数；只运行单元用例时，单独记录仍缺的数据库或 broker 往返。
