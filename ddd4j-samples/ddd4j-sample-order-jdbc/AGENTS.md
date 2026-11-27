# ddd4j-sample-order-jdbc

四表初始化、MyBatis 事务和 PO 映射。

先由 CatalogDatabase 建立 MyBatis 会话并将租户传入仓储，仓储把四表 PO 恢复成聚合。改字段时从 XML 的 resultMap、写 SQL 一直对照到 PO 和构造入参。重复 SKU 需要让父商品一起回滚；带锁 JOIN 在真实 MySQL 验证。

实现入口见 [CatalogDatabase](src/main/java/io/ddd4j/sample/order/infrastructure/CatalogDatabase.java)、[MybatisRepositories](src/main/java/io/ddd4j/sample/order/infrastructure/persistence/MybatisRepositories.java)、[CatalogMapper](src/main/java/io/ddd4j/sample/order/infrastructure/persistence/CatalogMapper.java)。

字段映射在 [CatalogMapper.xml](src/main/resources/io/ddd4j/sample/order/infrastructure/persistence/CatalogMapper.xml)；四表定义在 [V1_0_0__catalog_trade.sql](src/main/resources/db/catalog-trade/V1_0_0__catalog_trade.sql)。

首次启动经 MyBatis ScriptRunner 执行原始 DDL，在内存中把 CREATE TABLE 转成 CREATE TABLE IF NOT EXISTS，按脚本顺序补齐缺表。这个入口不会升级已经存在的表。写入使用 openSession(false)，业务回调成功后才 commit；租户条件由 Mapper SQL 显式绑定。

## 回归

优先运行 [CatalogTradeTest](../ddd4j-sample-order-start/src/test/java/io/ddd4j/sample/order/CatalogTradeTest.java)。这些用例位于 ddd4j-sample-order-start，从仓库根目录选择该模块。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-samples/ddd4j-sample-order-start -am -Dtest=CatalogTradeTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-samples/ddd4j-sample-order-start -am verify
```

所列集成用例使用 Testcontainers。核对测试报告中对应测试类的执行数和跳过数；只运行单元用例时，单独记录仍缺的数据库或 broker 往返。
