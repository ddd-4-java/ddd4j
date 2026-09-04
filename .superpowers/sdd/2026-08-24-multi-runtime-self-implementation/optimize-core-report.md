# ddd4j-core 架构优化报告

## 执行摘要

本次优化覆盖 ddd4j-core 域 + 4 个 CQRS sample（dropwizard/helidon/micronaut/vertx），完成审查报告 P0/P1/P2 清单中的 5 项任务。

## 任务完成情况

### T1: EventStore SPI + StoredEvent + InMemoryEventStore

**状态**: 完成 | **Commit**: `2f8b1cbd`

- 新增包 `io.ddd4j.core.cqrs.eventstore`，含 3 个源文件 + 2 个测试文件
- `EventStore` 接口：`append`/`read`/`readAll` 三方法，与 sample 拷贝完全一致
- `StoredEvent` record：`(String aggregateId, long version, Object event, long position, Instant timestamp)`
- `InMemoryEventStore`：synchronized append + 乐观版本校验 + `ConcurrentSkipListMap` 实现 O(limit) readAll（优化 M19）
- `EventStoreContractTest`：11 个抽象契约测试，供所有持久化实现复用
- `InMemoryEventStoreTest`：继承契约测试

### T2: ProjectionRunner.runAll() 异常隔离

**状态**: 完成 | **Commit**: `b87bff7f`

- `runAll()` 中每个 view 独立 try-catch，捕获 `RuntimeException` 后用 SLF4J 记录 view name + 异常
- 添加 `@Slf4j` 注解
- 测试更新：原"异常传播中断后续"断言改为"异常隔离，后续 view 仍执行"
- 24 个 ProjectionRunner 测试全部通过

### T3: AggregateRoot 补充 apply + loadFromHistory

**状态**: 完成 | **Commit**: `10edb556`

- `apply(DomainEvent)`：ClassValue 二级缓存路由到 `on<EventTypeSimpleName>` 方法，无 handler 静默忽略
- `loadFromHistory(List<DomainEvent>)`：顺序 apply 重建聚合状态
- apply 不注册事件到未提交缓冲区（仅用于回放）
- 7 个测试：路由命中、无 handler 忽略、loadFromHistory 顺序重建、null/empty 安全

### T4: RepositoryRegistry.clear()

**状态**: 完成 | **Commit**: `9b18ed21`

- `clear()` 方法：清除 INSTANCES、QUERY_INSTANCES 映射及 BaseContext 注册项
- 测试验证 clear 后所有仓储查找抛 BizRuntimeException
- 10 个 RepositoryRegistry 测试全部通过

### T5: 4 个 sample 统一使用 core InMemoryEventStore

**状态**: 完成 | **Commit**: `57598fb6`

- 删除 4 份本地 InMemoryEventStore.java（git rm）
- 更新 12 个文件的 import 指向 `io.ddd4j.core.cqrs.eventstore.{InMemoryEventStore,StoredEvent}`
- Micronaut sample 补充 `@Singleton` bean 定义（类移出扫描包后需显式注册）
- 4 个 sample 测试全部通过

## 测试结果

| 模块 | 测试数 | 通过 | 失败 | 错误 |
|------|--------|------|------|------|
| ddd4j-core | 256 | 256 | 0 | 0 |
| ddd4j-sample-dropwizard-cqrs | 3 | 3 | 0 | 0 |
| ddd4j-sample-helidon-cqrs | 3 | 3 | 0 | 0 |
| ddd4j-sample-micronaut-cqrs | 1 | 1 | 0 | 0 |
| ddd4j-sample-vertx-cqrs | 3 | 3 | 0 | 0 |

## Commit 列表

```
9b18ed21 feat(core): add RepositoryRegistry.clear() for test cleanup
57598fb6 refactor(sample): replace 4 local InMemoryEventStore copies with core version
10edb556 feat(core): add apply(DomainEvent) + loadFromHistory to AggregateRoot
b87bff7f fix(core): ProjectionRunner.runAll() exception isolation per view
2f8b1cbd feat(core): add EventStore SPI + StoredEvent + InMemoryEventStore with NavigableMap index
```

## 验证检查

- `grep -rn "org.fuin" ddd4j-core/src ddd4j-samples --include="*.java"` → 零命中
- `git status` → ddd4j-core 和 4 个 sample 文件全部已提交；仅有 `ddd4j-runtime-guice` 的未暂存变更（并行 agent 的工作，非本域）

## 遗留问题

1. **EventSourcingRepository 实现**：T1 仅补了 EventStore 基础设施，`EventSourcingRepository` 接口的默认实现（从 EventStore 拉事件 → loadFromHistory）待下一轮完成
2. **sample 本地 CommandBus/ViewManager/ProjectionView**：本轮未动（任务说明明确为下一轮工作）
3. **并行 agent 冲突**：T4 commit 曾被并行 agent 的 rebase 覆盖，已重新提交
