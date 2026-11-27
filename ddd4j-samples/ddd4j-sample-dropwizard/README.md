# Dropwizard 默认商品交易样例

按[运行时类路径构建说明](../README.md#启动默认订单示例)构建 `ddd4j-sample-dropwizard`。使用当前分支 JDK，运行
`io.ddd4j.sample.dropwizard.DropwizardOrderApplication`，参数为
`server ddd4j-samples/ddd4j-sample-dropwizard/src/main/resources/dropwizard-order.yml`。YAML 的应用和管理端口默认分别为
`8086` 和 `8087`。

默认启动原生应用时，Flyway 先迁移专用 MySQL
库。应用随后提供商品、规格、订单及成交明细的读写示例。[商品交易的数据库配置、四个接口与验证命令](../README.md#启动默认订单示例)。
