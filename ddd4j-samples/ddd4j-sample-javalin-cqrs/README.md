# Javalin CQRS 示例

本入口使用Guice 命令总线和成交快照投影。商品、规格、订单和订单明细保存在 [共享 MySQL 四表](../ddd4j-sample-order-jdbc/src/main/resources/db/catalog-trade/V1_0_0__catalog_trade.sql)。COLA 用例和 DDD4J 基类由共享模块提供。

按[示例总览](../README.md)配置专用 MySQL 库和三个连接环境变量。应用首次启动自动初始化四表，可使用 Maven 构建模块。

```sh
./mvnw -pl ddd4j-samples/ddd4j-sample-javalin-cqrs -am package
```

JAR 与运行时依赖的启动方式见[独立运行时命令](../README.md#javalinmicronauthelidonvertx-和-dropwizard)。

业务 HTTP 前缀为 `/orders/catalog`，请求携带 `X-Tenant-Id`。

## 交易与投影

1. 调用 `POST /orders/catalog/goods/{code}` 建档，再调用 `POST /orders/catalog/orders/{code}` 下单。
2. 调用 `POST /orders/catalog/orders/{orderNo}/payment`，提交以分计的整数 `paidAmountCent`。
3. 调用 `POST /orders/catalog/projections/orders/{code}` 从已提交的四表快照重建订单视图。
4. 使用同一租户调用 `GET /orders/catalog/orders/{code}` 读取投影。应用重启后可再次从订单快照重建。
