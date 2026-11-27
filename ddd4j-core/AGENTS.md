# ddd4j-core

聚合、仓储、命令总线、事件存储及 API 响应的公共契约。

先确定调用者依赖哪个公共承诺。仓储的最小读写入口是 findById 和 save；命令分发由 CommandBus 选处理器；事件读写使用 EventStore 的版本和位置语义。改返回对象时检查 R 的字符串失败重载、空数据和序列化，并沿实际调用者迁移，避免引入第二套响应协议。

实现入口见 [Repository](src/main/java/io/ddd4j/core/ddd/repository/Repository.java)、[AggregateRoot](src/main/java/io/ddd4j/core/ddd/model/AggregateRoot.java)、[EventStore](src/main/java/io/ddd4j/core/cqrs/eventstore/EventStore.java)、[CommandBus](src/main/java/io/ddd4j/core/cqrs/command/CommandBus.java)、[R](src/main/java/io/ddd4j/core/api/R.java)。

修改命令分发或事件存储前，分别核对 [0004-command-bus-design.md](../docs/adr/0004-command-bus-design.md) 和 [0005-event-store-spi.md](../docs/adr/0005-event-store-spi.md)。

| 修改对象 | 从消费方验证的行为 |
|---|---|
| 聚合与仓储 | 保存后按标识取回；检查重建后的业务状态以及可选仓储操作的失败语义。 |
| 命令总线 | 用已有 handler 分发一次命令，再检查未注册命令、重复注册和返回值。 |
| 事件存储 | 区分聚合版本与全局位置，过期版本追加须整批失败。 |
| API 响应 | 断言业务码、消息和数据，特别覆盖失败重载的字符串与 null 入参。 |

## 回归

优先运行 [DefaultCommandBusCompatibilityTest](src/test/java/io/ddd4j/core/cqrs/command/DefaultCommandBusCompatibilityTest.java)、[EventStoreContractTest](src/test/java/io/ddd4j/core/cqrs/eventstore/EventStoreContractTest.java)、[RTest](src/test/java/io/ddd4j/core/api/RTest.java)。

从仓库根目录可先执行以下定向回归，再用第二条命令检查整个模块。

```sh
./mvnw -pl ddd4j-core -am -Dtest=DefaultCommandBusCompatibilityTest,EventStoreContractTest,RTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl ddd4j-core -am verify
```
