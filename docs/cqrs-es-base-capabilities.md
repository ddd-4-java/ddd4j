# CQRS/ES 基础能力使用说明

`ddd4j-core` 提供 Saga、UnitOfWork、QueryBus 和 EventSourcingRepository。以下描述的是三线共同的行为契约；各线的 JDK、语言写法和依赖配置由各自 POM 管理。

## Saga：顺序执行与逆序补偿

`Saga` 按注册顺序执行步骤。前向步骤失败后停止后续步骤，对已执行步骤逆序补偿；单个补偿失败仍继续其他补偿。调用方应检查 `SagaState`，对未能补偿的流程人工处理。Saga 实例一次性使用。

```java
SagaState state = new Saga()
        .step(new SagaStep("reserve-inventory", this::reserve, this::unreserve))
        .step(new SagaStep("charge-payment", this::charge, this::refund))
        .execute();
```

## UnitOfWork：显式回调边界

`UnitOfWork.begin()` 创建独立边界。`execute` 成功时提交，工作抛异常时回滚并传播原始异常；未提交边界在 `close` 时回滚。终态不能再次提交或回滚。它只协调回调，不自动开启 JDBC/JPA 事务；具体资源必须由应用或适配器接入。

## QueryBus：按查询类型分发

`DefaultQueryBus` 在构造时注册 `QueryHandler`，`ask(query)` 按查询对象的实际类型选择处理器。缺失或重复处理器会报错，不会隐式转入命令路径。框架不检查处理器是否产生写入副作用；只读约束由处理器实现和测试保证。JDK 8 示例可使用 `Arrays.asList(...)` 构造处理器集合。

## EventSourcingRepository：追加与重放

`DefaultEventSourcingRepository` 使用 `EventStore` SPI 追加未提交事件，并通过历史事件重建聚合。`add` 从预期版本 0 开始；`update` 使用已跟踪版本；`read(id, version)` 重放到指定历史版本。并发冲突的可靠性还取决于所选 `EventStore` 是否原子检查预期版本。聚合必须支持重放实例化和事件处理。

契约测试位于 `ddd4j-core/src/test/java/io/ddd4j/core/cqrs/` 及 `ddd4j-core/src/test/java/io/ddd4j/core/ddd/repository/`。版本专属依赖与构建参数不写入本共同指南。
