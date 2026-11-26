# Javalin 默认示例

本入口使用Javalin 与 Guice 发布领域事件。商品、规格、订单和订单明细保存在 [共享 MySQL 四表](../ddd4j-sample-order-jdbc/src/main/resources/db/catalog-trade/V1_0_0__catalog_trade.sql)。COLA 用例和 DDD4J 基类由共享模块提供。

按[示例总览](../README.md)配置专用 MySQL 库和三个连接环境变量。应用首次启动自动初始化四表，可使用 Maven 构建模块。

```sh
./mvnw -pl ddd4j-samples/ddd4j-sample-javalin -am package
```

JAR 与运行时依赖的启动方式见[独立运行时命令](../README.md#javalinmicronauthelidonvertx-和-dropwizard)。

业务 HTTP 前缀为 `/api`，请求携带 `X-Tenant-Id`。

## 验证交易

`check_catalog_trade.py` 每次使用独立租户，检查建档、下单、付款、履约、读取成交快照和跨租户隔离。默认端口为 7000。

```sh
python3 ddd4j-samples/scripts/check_catalog_trade.py http://127.0.0.1:7000
```
