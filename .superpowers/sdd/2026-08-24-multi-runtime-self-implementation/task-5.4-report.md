# Task 5.4 Report — AsyncEventStore SPI + ddd4j-data-event-store-r2dbc

## Status: DONE

- Commit: `c6b3b938` `feat(data): AsyncEventStore SPI + r2dbc 实现（纯 io.r2dbc.spi 响应式事务）`（8 files, +894/−4）
- Gate: `./mvnw -pl ddd4j-data/ddd4j-data-event-store-r2dbc,ddd4j-data/ddd4j-data-event-store -am install` → **BUILD SUCCESS**（全链 7 模块 SUCCESS）

## 交付清单

### A. SPI（ddd4j-data-event-store）
- `AsyncEventStore`（io.ddd4j.data.eventstore）：四方法 Reactor 版（`Mono<Void> append(Flux, expectedVersion)` + 3 个 `Flux<StoredEvent>` 读）。javadoc 引 ADR-0005：对照 esc-api CompletableFuture 双轨全量复制（约 20 签名×2 漂移风险）与 `DelegatingAsyncEventStore` 线程池包同步的批评，明确单轨响应式决策；实现指向 r2dbc 模块。
- pom 增 `io.projectreactor:reactor-core`（无版本，reactor-bom 2025.0.6 导入 :6464）。
- `EventStoreModuleIndependenceTest` allowlist 增 **`reactor..`**（非 brief 草写的 `io.projectreactor..`——那是 Maven groupId，reactor-core 的 Java 根包是 `reactor.`，`io.projectreactor..` 匹配不到任何类，40 条假阴性/直接红）。规则 javadoc 注明 ADR-0005 单轨依据。

### B. 新模块 ddd4j-data-event-store-r2dbc
- pom：parent ddd4j-data；deps `ddd4j-data-event-store ${revision}` + `io.r2dbc:r2dbc-spi`（:8104）+ `reactor-core`（BOM，均无版本）；test `io.r2dbc:r2dbc-h2`（:8055）+ `reactor-test` + `jackson-datatype-jsr310`；surefire includes 含 `**/*IT.java`（照 jdbi 模块）。
- 注册于 ddd4j-data/pom.xml：jdbi 之后、external 之前。

### C. R2dbcEventStore（预授权偏离的落地）
**偏离记录**：计划 sketch 用 Spring `DatabaseClient`；实现为**纯 `io.r2dbc.spi.Connection` API**。理由：①模块零 Spring，同时服务 WebFlux 与 Vert.x 响应式（ADR-0003）；②`Connection.beginTransaction/commitTransaction/rollbackTransaction` 提供真响应式事务，无需 Spring tx 管理器；③ArchUnit 可立 `no_spring` 硬规则（红利已兑现，规则绿）。
- 构造器 `(ConnectionFactory, EventPayloadSerializer)`。
- append：`events.collectList()` 先物化 → `Mono.usingWhen(create)` → begin → `select coalesce(max(version),0)`（bind 位置 `$1/$2`，`row.get(0, Long.class)`）→ 不符 `Mono.error(AggregateVersionConflictException)` → `Flux.fromIterable.concatMap` 逐条 INSERT 9 列（correlation/causation 可空经 **`bindNull(idx, String.class)`**）→ commit → close；异常/取消路径 `rollbackAndClose`（rollback 容错 `onErrorResume` 不吞原始异常）。
- read/read(from,to)/readAll：`Flux.usingWhen` + `query(sql, binder)` 复用；readAll SQL `limit $2` 真分页下推。
- toStoredEvent 双地雷修复：`EventId.valueOf`（空安全）+ 私有 record `StringAggregateRootId` + `Class.forName`→ISE（与 -jpa/-jdbi 对齐）。

### D. ArchUnit（EventStoreR2dbcModuleIndependenceTest，3 规则）
`r2dbc_impl_deps_allowlist`（io.ddd4j../java../jakarta../jackson 三件/**reactor..**/io.r2dbc../lombok..）+ `no_spring_in_r2dbc_module`（偏离红利）+ `no_quarkus_in_r2dbc_module`。

### E. R2dbcEventStoreIT（r2dbc-h2 内存，5 用例 ≥4）
`ConnectionFactories.get("r2dbc:h2:mem:///esit;DB_CLOSE_DELAY=-1")`；@BeforeAll DDL 与 jdbi IT 逐列 parity（含 `tenant_id`、`uk_aggregate_version`、`timestamp with time zone`）；真实 `EventPayloadSerializer`（findAndAddModules）。全 StepVerifier 断言：
1. append+readBack：3 事件 version 1→3、eventId/聚合定位往返、真实 Jackson payload 等值、position 正、无因果 → correlation/causation null。
2. 乐观锁冲突：`verifyErrorSatisfies` 四字段断言 + **库中仍 2 条**（响应式事务真回滚实证）。
3. readAll：跨聚合 position 单调无重复 + `limit=2` SQL 真分页 + fromPosition 含端点。
4. read(from,to)：闭区间/单点/空区间/流不存在空流。
5. （超额）uk 数据层兜底：绕过语义层直插重复 version，第二条 `verifyError(R2dbcException.class)`，仅 1 行落库。

## created_at 行类型实证（临时探针 IT 跑后删除）

r2dbc-h2 1.1.0.RELEASE（H2 经 BOM 链解析）对 `timestamp with time zone` 列：
- **回读类型 = `java.time.OffsetDateTime`** → 适配 `row.get("created_at", OffsetDateTime.class).toZonedDateTime()`（与 brief 预判一致）。
- 附带实证：`clob` 列 `row.get("payload", String.class)` 直接得 `java.lang.String`（无需 Clob 流式读）；绑定侧 `ZonedDateTime.now().toOffsetDateTime()` 可直接写 `timestamp with time zone`；`bindNull(idx, String.class)` 可用。

## 门禁精确计数

| 模块 | Tests run | 明细 |
|---|---|---|
| ddd4j-data-event-store | **15**（0 失败） | arch 3（3 规则仍绿，allowlist 含新增 reactor.. 项）+ StoredEventTest 7 + EventPayloadSerializerTest 5 |
| ddd4j-data-event-store-r2dbc | **8**（0 失败） | arch 3 + R2dbcEventStoreIT 5 |

上游 -am 链（ddd4j/dependencies/annotation/kit/core/data）全 SUCCESS。

## Self-review 备注

- brief 草写的 allowlist 项 `io.projectreactor..` 实为 Maven groupId；真实包根 `reactor..`（两处 ArchUnit 均按实证修正并在规则 javadoc 记录映射关系）。
- append 事务区间内逐条 concatMap 严格保序（r2dbc 无 JDBC batch 语义，9 列逐条 bind）。
- `created_at` 每事件取一次 now（jdbi 是每 append 一次），语义等价，IT 不受影响。
- 未提交物：`docs/superpowers/plans/*.md` 为任务前已存在的未跟踪计划文件，非本任务产物。

## Concerns

无阻塞。后续可选项：r2dbc-pool 池化接入示例、MySQL/PostgreSQL r2dbc 方言 IT（当前仅 H2，与其他实现模块的本地必跑轨一致）。
