# Vert.x 商品交易 CQRS

使用 `VertxCqrsApplication` 启动。默认端口为 8085，可通过 `-Dsample.port` 修改。配置专用 MySQL 后，Flyway 启动时迁移四张业务表。Vert.x
原生命令总线执行写入，原生事件总线在事务提交后接收支付确认事件，订单读侧从已提交快照重建。[配置、接口和验证命令](../README.md#商品交易专项入口)
