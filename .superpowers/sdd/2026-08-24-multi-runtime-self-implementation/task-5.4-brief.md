# Task 5.4 Brief — AsyncEventStore SPI + event-store-r2dbc（响应式实现）

## BOM 实证（控制器预检）
reactor-bom 2025.0.6 已导入（:6464）、r2dbc-h2 1.1.0.RELEASE（:8055）、r2dbc-spi（:8104，${r2dbc-spi.version}=1.0.0.RELEASE）⇒ 相关依赖全部无版本声明。

## 交付

### A. SPI 模块新增 `AsyncEventStore`（io.ddd4j.data.eventstore，进 ddd4j-data-event-store）
```java
public interface AsyncEventStore {
    Mono<Void> append(String aggregateType, AggregateRootId aggregateId,
                      Flux<? extends DomainEvent<?>> events, long expectedVersion);
    Flux<StoredEvent> read(String aggregateType, AggregateRootId aggregateId);
    Flux<StoredEvent> read(String aggregateType, AggregateRootId aggregateId, long fromVersion, long toVersion);
    Flux<StoredEvent> readAll(long fromPosition, int limit);
}
```
- SPI 模块 pom 加 `io.projectreactor:reactor-core`（BOM 无版本）；**EventStoreModuleIndependenceTest allowlist 增 `io.projectreactor..`**（规则 javadoc 注明依据 ADR-0005 单轨决策）。
- javadoc：引 ADR-0005（对照 esc-api CompletableFuture 双轨全量复制的批评）、实现指向 r2dbc 模块。

### B. 新模块 `ddd4j-data/ddd4j-data-event-store-r2dbc`
pom：parent ddd4j-data；依赖 `ddd4j-data-event-store` ${revision} + `io.r2dbc:r2dbc-spi` + `io.projectreactor:reactor-core`（均 BOM）；test `io.r2dbc:r2dbc-h2`（BOM）+ `io.projectreactor:reactor-test`。注册字母序（external 之前、jdbi 之后）。测试 surefire includes 照 jdbi 模块含 `**/*IT.java`。

### C. `R2dbcEventStore`（io.ddd4j.data.eventstore.r2dbc）——**预授权偏离**
计划 sketch 用 Spring `DatabaseClient`——**偏离为纯 `io.r2dbc.spi.Connection` API**（理由：①模块零 Spring，同时服务 WebFlux 与 Vert.x 响应式——对齐 ADR-0003；②Connection.beginTransaction/commitTransaction/rollbackTransaction 提供真事务，无需 Spring tx 管理器；③ArchUnit 可立 no_spring 规则）。偏离记报告。
- `implements AsyncEventStore`；构造器 `(ConnectionFactory, EventPayloadSerializer)`。
- **append**：`connectionFactory.create()` → `beginTransaction` → SELECT coalesce(max(version),0)（bind :type/:id，row.get(0,Long)）→ 版本不符 `Mono/Flux.error(AggregateVersionConflictException)` + rollback → `Flux.fromIterable(eventsList).concatMap(逐条 INSERT 9 列 bind)`（**可空列用 `bindNull(idx, String.class)`**——r2dbc 不接受 null bind）→ `commitTransaction` → `close`；异常路径 rollback+close（`onErrorResume`/`doFinally`）。`events.collectList()` 先物化再入链（避免流重订阅）。
- **read/read(from,to)/readAll**：`create()` → SELECT（readAll 带 SQL `LIMIT $1`——r2dbc H2 支持 limit 参数）→ `flux` 映射行 → `toStoredEvent(io.r2dbc.spi.Row)` → close 收尾。行映射**地雷修复**同前（`EventId.valueOf`；私有 record `StringAggregateRootId`；`Class.forName`+ISE）；`created_at` 列类型**实证后适配**（r2dbc-h2 对 timestamp with time zone 多半回 `OffsetDateTime`→`toZonedDateTime()`；报告记录实际类型）。
- javadoc：事务边界、uk 兜底、对照 ADR-0005。

### D. ArchUnit `EventStoreR2dbcModuleIndependenceTest` ≥3
allowlist：io.ddd4j../java../jakarta..(若用)/jackson 三件/io.projectreactor../io.r2dbc../lombok..；**`no_spring_in_r2dbc_module`**（偏离的红利）；`no_quarkus_in_r2dbc_module`。

### E. IT `R2dbcEventStoreIT`（r2dbc-h2 内存）
`ConnectionFactories.get("r2dbc:h2:mem:///esit;DB_CLOSE_DELAY=-1")`；@BeforeAll 建表（DDL 与 jdbi IT **同 parity 含 tenant_id+uk**，created_at 用 `timestamp with time zone`）；真实序列化器。`StepVerifier` 用例 ≥4：①append+readBack（version 递增+真实 Jackson 往返）；②乐观锁冲突（库不变）；③readAll position 递增+limit；④read(from,to) 闭区间。

## 门禁
`./mvnw -pl ddd4j-data/ddd4j-data-event-store-r2dbc,ddd4j-data/ddd4j-data-event-store -am install` BUILD SUCCESS；报告双模块精确计数（SPI 模块 ArchUnit 应仍 3 规则绿+新增允许项）+ created_at 实际行类型实证。

## 提交
单 commit：`feat(data): AsyncEventStore SPI + r2dbc 实现（纯 io.r2dbc.spi 响应式事务）`

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-5.4-report.md`。Reply ≤15 lines.
