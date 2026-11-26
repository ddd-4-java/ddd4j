# Javalin Shiro 示例

本入口使用Javalin 与原生 Shiro 会话。商品、规格、订单和订单明细保存在 [共享 MySQL 四表](../ddd4j-sample-order-jdbc/src/main/resources/db/catalog-trade/V1_0_0__catalog_trade.sql)。COLA 用例和 DDD4J 基类由共享模块提供。

按[示例总览](../README.md)配置专用 MySQL 库和三个连接环境变量。应用首次启动自动初始化四表，可使用 Maven 构建模块。

```sh
./mvnw -pl ddd4j-samples/ddd4j-sample-javalin-shiro -am package
```

JAR 与运行时依赖的启动方式见[独立运行时命令](../README.md#javalinmicronauthelidonvertx-和-dropwizard)。

业务 HTTP 前缀为 `/orders/catalog`，请求携带 `X-Tenant-Id`。

## 原生鉴权

通过该入口的 `/auth/login` 登录，使用返回的令牌调用 `/auth/me` 和商品交易路由。未登录请求会被拒绝，只有具备对应权限的账号可以建档、下单或写入订单状态。可通过 `/auth/logout` 验证令牌生命周期。账号、令牌头和权限映射由对应模块的原生鉴权配置定义。
