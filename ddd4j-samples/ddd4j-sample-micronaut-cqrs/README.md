# ddd4j-sample-micronaut-cqrs

Micronaut CQRS 集成示例：演示在 Micronaut 运行时下完整的 CQRS 流程。

## 架构

- **写侧**：Command -> CommandHandler -> Aggregate -> EventStore (JPA)
- **读侧**：EventStore -> ProjectionView -> QueryModel (JPA)

## 关键组件

| 组件 | 说明 |
|------|------|
| `CreateOrderCommand` | 创建订单命令 |
| `CreateOrderCommandHandler` | 命令执行器（@Singleton + @CommandHandler） |
| `EventSourcingOrderRepository` | 事件溯源仓储（@Singleton） |
| `OrderSummaryView` | 投影视图（@Singleton + ProjectionView） |
| `PlainEventStore` | JPA EventStore（@Singleton，EntityManager） |
| `PlainProjectionPositionRepository` | JPA 投影位置仓储 |
| `OrderController` | REST 控制器（Micronaut @Controller） |

## 运行测试

```bash
mvn test -pl ddd4j-samples/ddd4j-sample-micronaut-cqrs
```

## REST 端点

- `POST /orders` - 创建订单（写侧）
- `GET /orders/{id}` - 查询订单摘要（读侧）
