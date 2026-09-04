# Task 5.3 Brief — event-store-jdbi：JDBI 实现（Javalin/Vert.x 服务，SQL-first）

## BOM 实证（控制器预检）
`jdbi.version=3.45.4` 属性存在（ddd4j-dependencies:375）但**无 dependencyManagement 条目** ⇒ 模块 pom 里 jdbi3-core 用 `<version>${jdbi.version}</version>`（属性可继承）。本地 .m2 无 jdbi——构建时从镜像拉取，报告记录解析版本。

## 交付
### A. 模块 `ddd4j-data/ddd4j-data-event-store-jdbi`
pom：parent ddd4j-data；依赖 `ddd4j-data-event-store` ${revision} + `org.jdbi:jdbi3-core` ${jdbi.version}；test：`com.h2database:h2`（BOM 或显式 2.4.240，报告注明）。注册于 ddd4j-data/pom.xml（字母序 panache 之后、external 之前）。

### B. `JdbiEventStore`（io.ddd4j.data.eventstore.jdbi）
照计划 sketch（SQL-first，无注解 SQL Object）：
- 构造器 `(Jdbi jdbi, EventPayloadSerializer serializer)`，requireNonNull；javadoc：集成方装配（Javalin/Vert.x 手动 new，非容器托管）。
- `append`：`jdbi.useTransaction(handle -> {...})`——`select coalesce(max(version),0)` 查 actual→冲突抛 AggregateVersionConflictException→逐事件 `createUpdate("insert into ddd4j_stored_event (...) values (...)")` 绑定 9 列（含可空 correlation/causation 的 null 绑定）。
- `read/read(from,to)/readAll`：`jdbi.withHandle` + createQuery + `.map((rs, ctx) -> toStoredEvent(rs))`；readAll SQL 带 `limit :limit`（**真 SQL 分页，非内存 limit——对齐 4.3 遗留改进项**）。
- 私有 `toStoredEvent(ResultSet)`：Class.forName+强转；**`EventId.valueOf`**；**私有 record StringAggregateRootId**（照 4.3 适配器抄）；`rs.getTimestamp("created_at").toInstant().atZone(ZoneId.systemDefault())`；payload=serializer.deserialize。

### C. ArchUnit `EventStoreJdbiModuleIndependenceTest` ≥3 条
- `jdbi_impl_deps_allowlist`：io.ddd4j.data.eventstore.jdbi.. onlyDependOn io.ddd4j../java../jakarta..(若用)/com.fasterxml.jackson.core|databind|annotation../org.jdbi../lombok..
- `no_spring_in_jdbi_module`、`no_quarkus_in_jdbi_module`（本模块服务 Javalin/Vert.x，零运行时框架）

### D. H2 IT `JdbiEventStoreIT`（纯 JDBI，无容器）
`Jdbi.create(JdbcDataSource 或 DriverManagerDataSource h2-mem)`；@BeforeAll 执行 DDL 脚本（CREATE TABLE ddd4j_stored_event 与 -jpa 模块**同列集含 tenant_id 列 + uk_aggregate_version**——对齐阶段 5.1 评审 parity 发现）+ uk 约束；序列化器真实 new（findAndAddModules）。用例 ≥4：①append+readBack 3 事件（version 递增、真实 Jackson 往返）；②乐观锁冲突且库中条数不变；③readAll position 递增+limit 真分页（多插几条验证 SQL limit）；④read(from,to) 闭区间；⑤（可选）uk 直接违例→DataAccessException。

## 门禁
`./mvnw -pl ddd4j-data/ddd4j-data-event-store-jdbi -am install` BUILD SUCCESS；报告精确计数 + jdbi3-core 实际解析版本。

## 提交
单 commit：`feat(data): ddd4j-data-event-store-jdbi——JDBI 实现（SQL-first+H2 IT）`

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-5.3-report.md`。Reply ≤15 lines.
