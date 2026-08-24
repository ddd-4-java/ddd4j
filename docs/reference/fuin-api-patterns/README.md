# fuin API 模式参考（高精度参考 + 完全自研）

**目标**：以参考文档形式提炼 fuin 三个仓库（ddd-4-java / cqrs-4-java / ddd-cqrs-4-java-example）的核心 API 设计，作为 ddd4j 自研 ES/CQRS 的 API 形态借鉴。

**原则**：**只读不写**（reference-only），**不复用代码**（no code reuse），**不集成进 ddd4j monorepo**。

| # | 文档 | 关键 API | ddd4j 落地 |
|---|------|---------|-----------|
| 01 | aggregate-root.md | AbstractAggregateRoot.apply/loadFromHistory | ddd4j-core AggregateRoot 扩展 |
| 02 | entity-id-path.md | EntityIdPath.first/last/parent/child | ddd4j-core EntityIdPath 补 validate |
| 03 | domain-event.md | DomainEvent 接口 + 元数据 | ddd4j-core DomainEvent 已对齐 |
| 04 | event-sourcing-repository.md | EventStoreRepository 接口 | ddd4j-data-event-store 新增 |
| 05 | event-store.md | EventStore append/read/slice | ddd4j-data-event-store 新增 |
| 06 | cqrs-command.md | CommandExecutor/MultiCommandExecutor | ddd4j-data-cqrs 新增 |
| 07 | cqrs-projection.md | QryProjectionService/SpringJpaViewManager | ddd4j-data-projection 新增 |
| 08 | architecture-test.md | ArchUnit 模块边界规则 | ddd4j-core + ddd4j-data ArchUnit |
