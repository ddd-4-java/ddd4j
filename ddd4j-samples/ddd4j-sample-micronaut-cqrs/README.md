# ddd4j-sample-micronaut-cqrs

## 当前商品交易入口

此模块以 CQRS 方式运行共享商品交易。先按[示例总览](../README.md#启动默认订单示例)配置 MySQL。以下命令从仓库根目录运行，使用实际
Maven 版本替换 `<version>`。

```bash
module=ddd4j-samples/ddd4j-sample-micronaut-cqrs
./mvnw -pl "$module" -am -DskipTests -Dmaven.javadoc.skip=true -DincludeScope=runtime -Dmdep.outputFile=target/runtime-classpath.txt package dependency:build-classpath
java -cp "$module/target/ddd4j-sample-micronaut-cqrs-<version>.jar:$(cat "$module/target/runtime-classpath.txt")" io.ddd4j.sample.micronaut.cqrs.MicronautCqrsApplication
```

业务请求携带 `X-Tenant-Id`。商品地址为 `/orders/catalog/goods/{spuCode}`，订单地址为 `/orders/catalog/orders/{orderNo}`
。支付、取消和履约分别使用订单子路径 `/payment`、`/cancel`、`/fulfill`。

写操作经命令总线执行。调用 `POST /orders/catalog/projections/orders/{orderNo}` 构建订单投影。

<details>
<summary>商品交易迁移前的说明</summary>

Micronaut CQRS 样例提供默认内存流程和显式 PostgreSQL 流程。

## 启动

使用当前分支对应的 JDK，按[运行时类路径构建说明](../README.md#启动默认订单示例)打包本模块，然后运行
`io.ddd4j.sample.micronaut.cqrs.MicronautCqrsApplication`。

## 默认内存入口

- `POST /orders` 通过命令总线写入内存事件。
- `GET /orders/{id}` 返回由定时投影生成的订单摘要，保留 `createdAt` 字段。
- Micronaut Context 负责停止内存投影调度器。

## S04 PostgreSQL 入口

设置 `ddd4j.cqrs.projection.enabled=true`、`sample.jdbc.url`、`sample.jdbc.user`、`sample.jdbc.password`。默认
`sample.jpa.schema=validate`
。启动前为专用样例库准备 [Order JDBC 迁移](../ddd4j-sample-order-jdbc/src/main/resources/db/migration/)及 JPA
EventStore、ProjectionPosition 对应表。测试中的 `S04DatabaseFixture` 只用于初始化隔离数据库。

- `POST /sample/orders` 创建订单，`POST /sample/orders/lines` 增加明细，`POST /sample/orders/{id}/payment?key=...` 支付。
- `GET /sample/orders/{id}` 和 `POST /sample/orders/query` 仅读取独立六字段投影。单条记录尚未投影时返回 404。
- `POST /sample/projections/orders/run` 立即执行与后台相同的投影。默认每 5 秒运行，`sample.projection.cron` 接受原生六字段数字
  cron。

订单与 Outbox 在同一 JDBC 本地事务内提交。单实例中继先在 JPA ResourceLocal 事务中追加事件，再确认 Outbox。确认失败后用原
Outbox ID 去重。查询 UPSERT 提交后独立提交检查点，检查点失败时重放同一批次。

事件读取按全局位置分页，仅把 `Order` 聚合事件送入投影。游标始终取完整源批次末尾的 `position + 1`，可越过位置空洞和仅包含可反序列化的非
`Order` 事件的批次。JPA 会先解码整页，无法加载的事件类型或损坏的载荷仍会触发读取失败，检查点保持原位。订单投影根据已提交写状态刷新当前查询快照。持久模式适用于独占此
Outbox 的单实例进程，Context 负责停止投影调度并关闭 JPA 工厂。

## 运行测试

```bash
./mvnw -pl ddd4j-samples/ddd4j-sample-micronaut-cqrs -am test
```

</details>
