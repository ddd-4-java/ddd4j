# Task 4.1+4.2 Brief（合并派发）— event-store-jpa 模块：骨架 + JPA 实体 + Spring Data Repository

## 背景
阶段 4 开篇：EventStore SPI（3.2）+ 序列化器（3.3）+ core 回读修复（4.0）已就绪。本任务建 **Spring Data JPA 实现**模块（服务 Spring WebMVC/WebFlux 等 Spring 系运行时；Quarkus/Javalin/响应式各有独立模块）。

## 交付

### A. 模块骨架（4.1）
1. `ddd4j-data/ddd4j-data-event-store-jpa/pom.xml`：parent `io.ddd4j:ddd4j-data`（照 ddd4j-data-event-store 结构）；依赖：
   - `ddd4j-data-event-store` ${revision}
   - `org.springframework.data:spring-data-jpa`（BOM 管理，无版本——先验证 dependencyManagement 有此项，若无则用 ${spring-data.version} 类属性，报告注明选择）
   - `jakarta.persistence:jakarta.persistence-api`（BOM 3.2.0 已核实）
   - test：`spring-boot-starter-test`、`org.testcontainers:postgresql`（testcontainers-bom 2.0.5 已核实）、`org.postgresql:postgresql`（BOM 42.7.8，test 作用域供 Testcontainers 驱动）
2. `ddd4j-data/pom.xml` 注册 `<module>ddd4j-data-event-store-jpa</module>`（字母序在 ddd4j-data-event-store 之后、ddd4j-data-external 之前；Edit 工具）。

### B. JPA 实体（4.2 之一）
`src/main/java/io/ddd4j/data/eventstore/jpa/StoredEventEntity.java` 照 3.2 前计划 sketch，修正两处已知笔误：
- `@Table(name="ddd4j_stored_event", uniqueConstraints=@UniqueConstraint(name="uk_aggregate_version", columnNames={"aggregate_type","aggregate_id","version"}))`
- 字段：`@Id @GeneratedValue(IDENTITY) Long position`（getter 暴露，无 setter，其余字段私有+getter/setter 照 sketch：eventId(36)/aggregateType(128)/aggregateId(128)/version(Long)/eventType(256)/payload @Lob/correlationId(36)可空/causationId(36)可空/tenantId(64)可空/createdAt ZonedDateTime）。javadoc 引 ADR-0005。

### C. Spring Data Repository（4.2 之二）
`SpringDataStoredEventRepository extends JpaRepository<StoredEventEntity, Long>`（接口名带 SpringData 前缀避免与 SPI 概念混淆——计划 sketch 同名 StoredEventRepository 会被误认 EventStore 实现仓储，改名并记 brief correction）：
- `@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select coalesce(max(s.version), 0) from StoredEventEntity s where s.aggregateType = :type and s.aggregateId = :id") long findCurrentVersion(@Param("type") String, @Param("id") String)`（sketch 笔误 maxsum→max、实体名 StoredEventEntityEntity→StoredEventEntity 已修正）
- `findByAggregateTypeAndAggregateIdOrderByVersionAsc(String, String)`
- `findByAggregateTypeAndAggregateIdAndVersionBetweenOrderByVersionAsc(String, String, long, long)`
- `findByPositionGreaterThanEqualOrderByPositionAsc(long)`

### D. 模块 ArchUnit（全局约束：每个新模块独立 ArchUnit）
`src/test/java/io/ddd4j/data/eventstore/jpa/arch/EventStoreJpaModuleIndependenceTest.java`：@ArchTest 字段风格（照 EventStoreModuleIndependenceTest），≥2 条：
- `jpa_impl_deps_allowlist`：io.ddd4j.data.eventstore.jpa.. onlyDependOnClassesThat resideInAnyPackage(io.ddd4j.., java.., jakarta.., com.fasterxml.jackson.annotation..|databind..|core.., org.springframework.data.., org.springframework.stereotype.., org.springframework.transaction.annotation.., lombok..)
- `no_quarkus/no_micronaut in jpa module`（防误引运行时依赖）

## 门禁
`./mvnw -pl ddd4j-data/ddd4j-data-event-store-jpa -am install` BUILD SUCCESS（模块测试 = ArchUnit ≥2 绿；实体/Repository 无需 Spring 上下文，编译级验证即可——IT 是 4.4）。

## 提交
单 commit：`feat(data): ddd4j-data-event-store-jpa 模块（骨架+StoredEventEntity+SpringData Repository）`

## 为 4.3 预置的两个事实（本任务不实现，写进报告提醒）
- `EventId.valueOf(String)` 静态工厂已存在（EventId.java:44，blank→null）——4.3 sketch 里 `new EventId(String)` 不存在，须用 valueOf。
- `StringEntityId implements EntityId` **而非** AggregateRootId（标记接口）——4.3 的 toStoredEvent 需私有 record 适配器 `StringAggregateRootId(String) implements AggregateRootId`（getType/asString/asTypedString 三方法照 StringEntityId 实现）。

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-4.1-4.2-report.md`。Reply ≤15 lines.
