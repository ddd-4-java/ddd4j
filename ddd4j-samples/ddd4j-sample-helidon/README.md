# Helidon 默认商品交易样例

使用当前分支的 JDK，按[运行时类路径构建说明](../README.md#启动默认订单示例)构建 `ddd4j-sample-helidon`。从生产运行时类路径启动 `io.ddd4j.sample.helidon.HelidonOrderApplication`。默认端口为 `8080`，可用 `-Dserver.port=...` 修改。

默认启动原生应用时，Flyway 先迁移专用 MySQL 库。应用随后提供商品、规格、订单及成交明细的读写示例。[商品交易的数据库配置、四个接口与验证命令](../README.md#启动默认订单示例)。
