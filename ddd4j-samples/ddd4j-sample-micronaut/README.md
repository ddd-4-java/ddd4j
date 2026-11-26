# Micronaut 默认商品交易样例

按本线 JDK 和 Maven Wrapper 使用[默认订单示例构建说明](../README.md#启动默认订单示例)，运行 `io.ddd4j.sample.micronaut.MicronautOrderApplication`。默认端口为 `8083`，可用 `--micronaut.server.port=...` 覆盖。

默认启动原生应用时，Flyway 先迁移专用 MySQL 库。应用随后提供商品、规格、订单及成交明细的读写示例。[商品交易的数据库配置、四个接口与验证命令](../README.md#启动默认订单示例)。
