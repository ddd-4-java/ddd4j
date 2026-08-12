# cqrs-4-java 架构剖析设计

- **日期**：2026-07-02
- **作者**：ddd4j 架构团队
- **状态**：参考文档（fuinorg 上游依赖）

## 1. 目标与范围

剖析 cqrs-4-java（fuinorg 出品）的架构，作为 ddd4j CQRS 能力的上游依赖参考。

| 维度 | 数据 |
|------|------|
| 核心价值 | 命令总线 + 读侧投影 + 视图管理 + 双框架适配（Quarkus/Spring Boot） |
| 依赖 ddd-4-java | 强依赖，复用其聚合根/事件/异常 |
| 依赖 esc-api | 强依赖，事件存储访问 |
| 框架适配 | quarkus/ + springboot/ 双实现 |

## 2. 模块拓扑

```
cqrs-4-java/
├── core/              ← CQRS 核心契约（Command/Executor/View/Result）
├── esc/               ← EventStore CQRS 集成
├── jackson/           ← Jackson 序列化
├── jaxb/              ← JAXB 序列化
├── jsonb/             ← JSON-B 序列化
├── quarkus/           ← Quarkus 框架适配（CDI/Arc）     ★ 参考实现
├── springboot/        ← Spring Boot 框架适配              ★ 参考实现
├── jacoco/
└── test/              ← 集成测试（KurrentDB / EventStoreDB）
```

## 3. 核心抽象

- `Command`：命令标记接口
- `CommandExecutor<CMD>`：命令执行器基类
- `View`：查询视图基类
- `ViewManager`：视图管理器 SPI
- `ProjectionPosition`：投影位置 SPI
- `EventChunk`：事件块
