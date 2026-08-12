# ddd4j Quarkus 适配评估设计

- **日期**：2026-07-01
- **作者**：ddd4j 架构团队
- **状态**：已实施
- **Codegraph 快照**：823 文件 / 13,671 节点（2026-07-01 同步）

## 1. 目标与范围

基于 codegraph 对 ddd4j 2.0.x 的深度探索，评估 Quarkus 适配的完整性和深度。

**状态校准**：当前 ddd4j 仓库内的 Quarkus 通用适配模块是 `ddd4j-runtime-quarkus`，不是旧文档中的 `ddd4j-quarkus` / `ddd4j-quarkus-core`。Quarkus 专属脚手架仍归外部 `ddd4j-quarkus` 项目。

## 2. 核心架构

```
ddd4j (通用基础层)
├── ddd4j-annotation      ← DDD 注解层（零框架依赖）
├── ddd4j-core            ← 核心契约层（纯 Java SPI）
├── ddd4j-ddd-rules       ← ArchUnit 架构守护（Clean/COLA 规则）
├── ddd4j-kit             ← 工具箱
├── ddd4j-data            ← 数据层 SPI
├── ddd4j-mq              ← 消息队列 SPI
├── ddd4j-web             ← Web 层 SPI
├── ddd4j-auth            ← 认证授权 SPI
├── ddd4j-cache           ← 缓存 SPI
├── ddd4j-runtime         ← 多框架运行时绑定聚合
│   ├── ddd4j-runtime-spring      ← Spring 框架运行时绑定
│   ├── ddd4j-runtime-guice       ← Guice 框架运行时绑定
│   └── ddd4j-runtime-quarkus     ← Quarkus CDI 运行时绑定
└── ddd4j-extensions      ← 扩展模块
```

## 3. 核心契约层关键接口

| 接口/类 | 包路径 | 职责 |
|---------|--------|------|
| `DddAggregateRoot<ID>` | `io.ddd4j.core.ddd.aggregate` | 聚合根基类（继承 fuinorg AbstractAggregateRoot） |
| `DddDomainEvent<ID>` | `io.ddd4j.core.ddd.event` | 领域事件基类 |
| `DddCommandExecutor<CMD>` | `io.ddd4j.core.ddd.command` | 命令执行器基类 |
| `DddView` | `io.ddd4j.core.ddd.query` | 查询视图基类 |
| `ViewManager` | `io.ddd4j.core.cqrs.readmodel` | 视图管理器 SPI |
| `ProjectionPosition` | `io.ddd4j.core.cqrs.readmodel` | 投影位置 SPI |

## 4. Quarkus 适配要点

- `ddd4j-runtime-quarkus`：CDI 运行时绑定，通过 `CdiDomainEventPublisher` 发布领域事件
- `DddInitializer`：Quarkus 启动期初始化器
- `ddd4j-web-quarkus`：Quarkus Web 适配，`QuarkusAggregateController` 提供通用聚合 REST 骨架
