# ddd4j-sample-micronaut-cqrs

Micronaut CQRS 集成示例：演示在 Micronaut 运行时下完整的 CQRS 流程。

## 架构

- **写侧**：Command -> CommandHandler -> Aggregate -> InMemoryEventStore
- **读侧**：InMemoryEventStore -> ViewManager -> ProjectionView -> OrderSummaryView（内存读模型）

## 关键组件

| 组件 | 说明 |
|------|------|
| `CreateOrderCommand` | 创建订单命令 |
| `CreateOrderCommandHandler` | 命令执行器（@Singleton + @CommandHandler） |
| `EventSourcingOrderRepository` | 事件溯源仓储（@Singleton，基于 InMemoryEventStore） |
| `OrderSummaryView` | 投影视图（@Singleton，内存读模型） |
| `InMemoryEventStore` | 内存事件存储（ConcurrentHashMap，按聚合 ID 存储事件） |
| `CommandBus` | 内存命令总线（命令类型 -> 处理器路由） |
| `ViewManager` | 内存视图管理器（定时触发投影，维护读模型） |
| `OrderController` | REST 控制器（Micronaut @Controller） |

## 实现说明

本示例使用 **内存实现** 代替框架 CQRS 模块（`ddd4j-data-event-store`、
`ddd4j-data-cqrs-*`、`ddd4j-data-projection-*`），因为这些模块在 `feature/3.0.x`
分支上尚未完成 3.0.x 迁移。当框架模块完成后，本示例将切换到框架提供的
`PlainEventStore`、`PlainProjectionPositionRepository` 等实现。

## 运行测试

```bash
mvn test -pl ddd4j-samples/ddd4j-sample-micronaut-cqrs
```

## REST 端点

- `POST /orders` - 创建订单（写侧）
- `GET /orders/{id}` - 查询订单摘要（读侧）
