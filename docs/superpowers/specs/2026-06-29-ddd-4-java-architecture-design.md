# ddd-4-java 架构剖析设计

- **日期**：2026-06-29
- **作者**：ddd4j 架构团队
- **状态**：参考文档（fuinorg 上游依赖）

## 1. 目标与范围

剖析 ddd-4-java（fuinorg 出品）的架构，作为 ddd4j 的核心上游依赖参考。

| 维度 | 数据 |
|------|------|
| 核心价值 | 纯 DDD 构建块 + 注解驱动事件溯源 + 多序列化器支持 |
| 依赖策略 | 零 Spring / 零 ORM / 零 Web 容器，仅依赖 jakarta.validation / objects4j / slf4j |
| 可嵌入性 | 可被 Spring Boot / Quarkus / Javalin / 任何 Java 容器嵌入使用 |

## 2. 模块拓扑

```
ddd-4-java/
├── core/              ← 核心契约（纯 Java，零框架依赖）      ★ ddd4j 直接依赖
├── esc/               ← EventStore 仓储实现（依赖 esc-api）
├── jackson/           ← Jackson 序列化扩展                  ★ ddd4j 复用
├── jaxb/              ← JAXB 序列化扩展
├── jsonb/             ← JSON-B 序列化扩展
├── jsonb-testmodel/   ← JSON-B 测试模型
├── junit/             ← JUnit 5 扩展（ArchUnit 规则）
├── codegen/           ← APT 代码生成器（value object）     ★ ddd4j 可参考
└── jacoco/            ← 测试覆盖率聚合
```

## 3. 核心抽象

- `AbstractAggregateRoot<ID>`：聚合根基类，支持事件溯源
- `AbstractDomainEvent<ID>`：领域事件基类
- `AbstractEntity<ID>`：实体基类
- `AbstractValueObject`：值对象基类
- `EncryptedData`：加密数据值对象
