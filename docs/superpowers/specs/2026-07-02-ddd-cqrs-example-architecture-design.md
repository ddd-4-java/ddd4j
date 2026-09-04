# ddd-cqrs-4-java-example 架构剖析设计

- **日期**：2026-07-02
- **作者**：ddd4j 架构团队
- **状态**：参考文档（fuinorg 官方示例）

## 1. 目标与范围

剖析 ddd-cqrs-4-java-example（fuinorg 出品）的架构，作为 ddd4j 实现 Quarkus 和 Spring Boot 适配层的最佳参考实现。

| 维度 | 数据 |
|------|------|
| 核心价值 | 完整微服务架构的两种实现范式 |
| 领域模型 | Person 聚合根（创建/删除/查询） |
| 事件存储 | EventStoreDB / KurrentDB（通过 esc-api） |
| 写侧技术 | 命令路由 → 聚合根 → EventStore |
| 读侧技术 | 定时投影 → JPA 视图 → REST 查询 |
| 框架对比 | Quarkus（CDI） vs Spring Boot（MVC） |

## 2. 项目定位

`ddd-cqrs-4-java-example` 使用同一个领域模型（Person）平行演示：

- **Quarkus** 框架下的命令侧 + 查询侧
- **Spring Boot** 框架下的命令侧 + 查询侧

是 ddd4j 实现 `ddd4j-quarkus` 和 `ddd4j-runtime-spring` 适配层的最佳参考实现。

## 3. 架构要点

- 命令侧：`Command` → `CommandBus` → `CommandExecutor` → `AggregateRoot` → `EventStore`
- 查询侧：`EventStore` → `Projection` → `View` → `REST Query`
- 双框架并行：同一领域模型，不同框架适配
