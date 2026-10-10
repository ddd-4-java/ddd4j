# CQRS/ES 基础能力使用说明（Saga / UnitOfWork / QueryBus / EventSourcingRepository）

> 对应规格：`openspec/changes/complete-cqrs-es-base-capabilities`（Saga 编排、UnitOfWork 边界、QueryBus
> 读写分离、EventSourcingRepository 语义）。
> 全部位于 `ddd4j-core`，纯 Java 8、零新增依赖、零 Spring。

## 1. Saga 编排（`io.ddd4j.core.cqrs.saga`）

跨聚合流程的顺序执行与失败补偿：步骤按注册顺序前向执行；任一步骤失败即停止前向，已执行步骤的补偿按 **注册逆序**
执行；单个补偿失败不阻断其余补偿，最终收敛到终态 `COMPLETED`／`COMPENSATED`／`FAILED`。实例一次性使用。

```java
SagaState state = new Saga()
        .step(new SagaStep("reserve-inventory", this::reserve, this::unreserve))
        .step(new SagaStep("charge-payment",    this::charge,  this::refund))
        .execute();

if (state == SagaState.FAILED) {
    // 至少一个补偿自身失败，需人工介入；saga.failure() 为前向失败异常
}
```

## 2. UnitOfWork 事务边界（`io.ddd4j.core.cqrs.uow`）

显式事务边界：边界内成功 → 提交回调生效；失败 → 回滚回调生效且 **原始异常原样传播**。终态（`COMMITTED`/`ROLLED_BACK`）之后重复
commit/rollback 抛 `IllegalStateException`。不绑定具体事务资源（JPA/JDBC 交运行时容器适配）。

```java
try (UnitOfWork uow = UnitOfWork.begin()) {          // close 时仍未提交则自动回滚
    uow.registerCommit(() -> outbox.flush());
    uow.registerRollback(() -> outbox.discard());
    Result r = uow.execute(() -> handler.handle(cmd)); // 失败：回滚 + 原异常传播
}
```

## 3. QueryBus 只读分发（`io.ddd4j.core.cqrs.query`）

读写分离的只读侧入口，形态对齐 `CommandBus`：仅暴露 `ask`，按查询对象类型路由到 `QueryHandler`；查询路径无写副作用；未注册类型抛
`IllegalStateException`（拒绝，不隐式落写路径）。写路径一律走 `CommandBus`，两侧不得混用。

```java
QueryBus bus = new DefaultQueryBus(List.of(new CountOrdersHandler()));
Integer count = bus.ask(new CountOrdersQuery());
```

## 4. EventSourcingRepository 语义（`io.ddd4j.core.ddd.repository`）

`DefaultEventSourcingRepository` 基于既有 `EventStore` SPI：

- **append 追加**：`add` 以期望版本 0 追加未提交事件；`update` 以本实例跟踪版本追加；不改写、不删除既有事件。
- **replay 重放**：`read(id)` 读取全部事件按版本升序 `loadFromHistory` 重建；`read(id, version)` 重放至历史版本（闭合区间）。
- **乐观并发冲突**：期望版本与流实际版本不一致时抛 `AggregateVersionConflictException`（跨实例/跨节点并发由 `EventStore`
  乐观锁兜底，后提交者必然冲突）。

```java
EventSourcingRepository<Order, String> repo =
        new DefaultEventSourcingRepository<>("Order", Order.class, OrderId::new, eventStore);

Order order = new Order("order-1");
order.place(...);
repo.add(order);                       // 追加版本 1..n

Order loaded = repo.read("order-1");   // 重放重建（无未提交事件）
loaded.pay(...);
repo.update(loaded);                   // 追加新事件，既有事件保持原样
```

聚合约定：无参构造（重放实例化）+ 事件处理器（`on<事件简单名>` 或 `@EventHandler`），业务方法用 `apply(事件)`
同时完成状态派发与未提交事件入队（2.0.x 语义）。

## 行为守护

四项能力的契约测试：`SagaMustTest`（4）、`UnitOfWorkMustTest`（4）、`QueryBusMustTest`（3）、
`DefaultEventSourcingRepositoryMustTest`（5），位于 `ddd4j-core/src/test`。
