# Task 5.3 Report — ddd4j-data-event-store-jdbi（JDBI 实现，Javalin/Vert.x）

**Status: DONE** | Commit: `66eeeabc` `feat(data): ddd4j-data-event-store-jdbi——JDBI 实现（SQL-first+H2 IT）` | Branch: feature/2.0.x (5 files, +635)

## Gate

`./mvnw -pl ddd4j-data/ddd4j-data-event-store-jdbi -am install` → **BUILD SUCCESS**

| Suite | Tests | Fail | Err | Skip |
|---|---|---|---|---|
| JdbiEventStoreIT (纯 JDBI+H2) | 5 | 0 | 0 | 0 |
| EventStoreJdbiModuleIndependenceTest (ArchUnit) | 3 | 0 | 0 | 0 |
| **模块合计** | **8** | **0** | **0** | **0** |

`-am` 链（ddd4j-kit/core/SPI/-jpa/-panache 等）全绿。

## 解析版本（dependency:list 实证）

- `org.jdbi:jdbi3-core:jar:3.45.4:compile` — 经模块 pom 显式 `<version>${jdbi.version}</version>`（BOM 只有属性 :375 无 dependencyManagement 条目，控制器预检一致）；本地 .m2 无缓存，从镜像拉取成功
- `com.h2database:h2:jar:2.4.240:test` — BOM 管理（`ddd4j-dependencies` :3905 `${h2.version}`），无需显式钉版
- `com.fasterxml.jackson.core:jackson-databind:jar:2.21.2` — BOM 链管理（SPI 传递 + jsr310 test 声明）

## 交付明细

### A. 模块（注册于 ddd4j-data/pom.xml，panache 之后、external 之前）
- `ddd4j-data/ddd4j-data-event-store-jdbi/pom.xml`：parent ddd4j-data；compile = `ddd4j-data-event-store` ${revision} + `jdbi3-core` ${jdbi.version}（**不含** jdbi3-sqlobject——SQL-first 无注解 SQL Object）；test = h2（BOM 2.4.240）+ jackson-datatype-jsr310；surefire 追加 `**/*IT.java` include。archunit/assertj/junit 经 ddd4j-dependencies 的全局 `<dependencies>` 继承（与 -jpa/-panache 同机制）。

### B. JdbiEventStore（io.ddd4j.data.eventstore.jdbi，232 行）
- 构造器 `(Jdbi, EventPayloadSerializer)` requireNonNull；javadoc 明确 Javalin/Vert.x 手动 new、非容器托管、Jdbi 可包连接池。
- `append`：`jdbi.useTransaction` 单事务——`select coalesce(max(version),0)` 查 actual→不等抛 `AggregateVersionConflictException`→逐事件 `createUpdate(insert …)` 绑 9 列（correlation/causation null 绑定）。
- `read` / `read(from,to)` / `readAll`：`jdbi.withHandle` + createQuery + `.map((rs, ctx) -> toStoredEvent(rs))`；**readAll SQL `limit :limit` 真分页**（对齐 4.3 遗留改进项——-jpa 是内存 stream().limit()）。
- `toStoredEvent(ResultSet)`：Class.forName+强转；**`EventId.valueOf`**（空安全，阶段 4 landmine 修复 #1）；**私有 record StringAggregateRootId**（landmine 修复 #2，照 -jpa/-panache 适配器）；`rs.getTimestamp("created_at").toInstant().atZone(ZoneId.systemDefault())`；payload=serializer.deserialize。
- tenant_id 列在表契约中但 insert 不绑定（与 -jpa 行为一致，列可空）。

### C. ArchUnit 3 条（EventStoreJdbiModuleIndependenceTest）
`jdbi_impl_deps_allowlist`（io.ddd4j../java../jakarta../jackson 三件套/org.jdbi../lombok..）、`no_spring_in_jdbi_module`、`no_quarkus_in_jdbi_module`。

### D. JdbiEventStoreIT（5 用例，≥4 达标）
`Jdbi.create(JdbcDataSource h2-mem)` 零容器；@BeforeAll DDL 与 -jpa StoredEventEntity **同列集含 tenant_id + uk_aggregate_version**（阶段 5.1 评审 parity 发现）；真实 `EventPayloadSerializer(JsonMapper.builder().findAndAddModules().build())`：
1. append+readBack 3 事件（version 递增、真实 Jackson 往返、correlationId/causationId null 空安全路径）
2. 乐观锁冲突（actualVersion=2）且事务回滚库中仍 2 条
3. readAll 跨聚合 position 递增 + SQL limit=2 真分页 + fromPosition 闭区间
4. read(from,to) 闭区间 + 空区间/不存在流返回空
5. uk_aggregate_version 直插违例 → UnableToExecuteStatementException（数据层双保险，兼证 tenant_id 可空）

## Deviations / Concerns

- **模块注册位置**：简报明示「panache 之后、external 之前」，已照办；注：严格字母序 jdbi 应排 jpa 之前，以简报显式指令为准（Maven reactor 按依赖排序，位置无功能影响）。
- ZonedDateTime 绑定走 JDBI 内建 JavaTime 参数工厂（IT 实测往返正常）；`limit :limit` 在 H2/PostgreSQL 均合法，SQL Server 类方言需集成方改 FETCH FIRST（表契约文档已在 javadoc 说明）。
- 建表 DDL 由集成方负责（本类零 DDL 副作用），IT 中 DDL 为 parity 基准。

## Files

- `ddd4j-data/ddd4j-data-event-store-jdbi/pom.xml`（新建）
- `ddd4j-data/ddd4j-data-event-store-jdbi/src/main/java/io/ddd4j/data/eventstore/jdbi/JdbiEventStore.java`（新建）
- `ddd4j-data/ddd4j-data-event-store-jdbi/src/test/java/io/ddd4j/data/eventstore/jdbi/JdbiEventStoreIT.java`（新建）
- `ddd4j-data/ddd4j-data-event-store-jdbi/src/test/java/io/ddd4j/data/eventstore/jdbi/arch/EventStoreJdbiModuleIndependenceTest.java`（新建）
- `ddd4j-data/pom.xml`（+1 行模块注册）
