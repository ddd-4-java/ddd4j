# Task 4.1+4.2 Report — ddd4j-data-event-store-jpa（骨架 + StoredEventEntity + SpringDataStoredEventRepository）

- **Status**: DONE
- **Commit**: `19b7599a`（feature/2.0.x，基于 93c1116f）— 5 files, +300 lines
- **Gate**: `./mvnw -pl ddd4j-data/ddd4j-data-event-store-jpa -am install` → **BUILD SUCCESS**；模块测试 `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`（EventStoreJpaModuleIndependenceTest 的 3 条 @ArchTest 全绿）

## 交付物

| 文件 | 说明 |
| --- | --- |
| `ddd4j-data/ddd4j-data-event-store-jpa/pom.xml` | 模块骨架；parent `io.ddd4j:ddd4j-data` |
| `ddd4j-data/pom.xml` | 注册 `<module>ddd4j-data-event-store-jpa</module>`（字母序 event-store 之后、external 之前，Edit 工具） |
| `.../eventstore/jpa/StoredEventEntity.java` | JPA 实体，javadoc 引 ADR-0005（docs/adr/0005-event-store-spi.md） |
| `.../eventstore/jpa/SpringDataStoredEventRepository.java` | Spring Data Repository（4 个查询方法） |
| `.../eventstore/jpa/arch/EventStoreJpaModuleIndependenceTest.java` | 3 条 ArchUnit 规则 |

## Brief corrections（已按 brief 执行）

1. **仓储改名**：`StoredEventRepository` → `SpringDataStoredEventRepository`（避免与 EventStore SPI 领域概念混淆）。
2. **sketch 笔误修正**：`findCurrentVersion` 的 JPQL `maxsum`→`max`、实体名 `StoredEventEntityEntity`→`StoredEventEntity`（计划文档当前版本已含修正，实现与其一致：`select coalesce(max(s.version), 0) from StoredEventEntity s where ...`）。
3. 实体：`@Table(name="ddd4j_stored_event", uniqueConstraints=@UniqueConstraint(name="uk_aggregate_version", columnNames={"aggregate_type","aggregate_id","version"}))`；`@Id @GeneratedValue(IDENTITY) Long position` 仅 getter 无 setter；其余字段私有+getter/setter（eventId 36/aggregateType 128/aggregateId 128/version Long/eventType 256/payload @Lob/correlationId 36 可空/causationId 36 可空/tenantId 64 可空/createdAt ZonedDateTime）。

## spring-data-jpa 版本管理核实结果（brief 要求自查项）

- `ddd4j-dependencies/pom.xml:9240-9247` 以 `import` scope 导入 **`org.springframework.data:spring-data-bom:2025.1.6`**（属性 `spring-data-bom.version`，pom.xml:626）→ 模块 pom **无版本声明**可用。
- 实测解析版本：`org.springframework.data:spring-data-jpa:jar:4.0.6:compile`（`dependency:list` 确认）。选择：BOM 管理无版本（brief 首选项），未引入 `${spring-data.version}` 类属性。

## 偏差：spring-boot-starter-test 未纳入（需 4.4 决策）

- Brief 列了 test 依赖 `spring-boot-starter-test`，但 **`org.springframework.boot` 在 ddd4j-dependencies/pom.xml 出现 0 次**（仅 resilience4j-spring-boot2/3 字样），且无 spring-boot BOM 导入（root pom 无 dependencyManagement，全部集中在 ddd4j-dependencies）→ 无版本声明会直接导致 gate 失败（missing version）。
- 处置：**暂不声明**。父级 ddd4j-dependencies 的全局 `<dependencies>` 已向所有子模块提供 junit-jupiter／assertj-core／mockito／archunit-junit5／slf4j-simple（test scope），本任务 ArchUnit 测试全部覆盖。
- **给 4.4 的决策点**：若集成测试确需 spring-boot 测试设施（@DataJpaTest 等），需先在 ddd4j-dependencies 增加 spring-boot BOM 管理或给出显式版本属性；testcontainers:postgresql（2.0.5）与 postgresql 驱动（42.7.8）已按 brief 以 test scope 就位。
- 附加发现：`ddd4j-data-jpa` 模块自带 enforcer 禁止 `org.springframework:*`（"Spring Data JPA 集成应由独立适配模块提供"）——本模块正是该独立适配模块，与 brief 依赖清单（不含 ddd4j-data-jpa，只依赖 ddd4j-data-event-store SPI）一致。

## ArchUnit 规则（3 条，≥2 达标）

1. `jpa_impl_deps_allowlist`：`io.ddd4j.data.eventstore.jpa..` onlyDependOnClassesThat resideInAnyPackage(`io.ddd4j..`, `java..`, `jakarta..`, `com.fasterxml.jackson.{annotation,core,databind}..`, `org.springframework.data..`, `org.springframework.stereotype..`, `org.springframework.transaction.annotation..`, `lombok..`)。
2. `no_quarkus_in_jpa_module`：不得依赖 `io.quarkus..`（归 panache 模块）。
3. `no_micronaut_in_jpa_module`：不得依赖 `io.micronaut..`。

## 为 Task 4.3 预置的两个事实（brief 要求写明，勿在 4.3 重踩）

1. **`EventId.valueOf(String)` 静态工厂已存在**（EventId.java:44，blank→null）——4.3 sketch 里的 `new EventId(String)` 构造器**不存在**，必须用 `valueOf`。
2. **`StringEntityId implements EntityId` 而非 AggregateRootId**（后者是标记接口）——4.3 的 `toStoredEvent` 需私有 record 适配器 `StringAggregateRootId(String) implements AggregateRootId`，`getType`/`asString`/`asTypedString` 三方法照 StringEntityId 实现。

## 备注

- 门禁为编译级验证（实体/Repository 无 Spring 上下文测试），IT 归 Task 4.4——符合 brief。
- 工作区仅余 2 个未跟踪的计划文档（本次不纳入提交）。
