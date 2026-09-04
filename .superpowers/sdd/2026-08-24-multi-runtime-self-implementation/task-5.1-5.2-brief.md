# Task 5.1+5.2 Brief（合并派发）— event-store-panache：Quarkus Hibernate ORM Panache 实现

## 背景与 BOM 实证（控制器预检）
- `io.quarkus.platform:quarkus-bom` **已在 ddd4j-dependencies 导入**（:8022-8024，${quarkus.version}=3.38.2，本地 .m2 有 3.38.2）⇒ quarkus 依赖**无版本声明**。
- **阶段 4 教训（强制）**：写 pom 后必须 `./mvnw -pl <module> dependency:tree -Dincludes=org.hibernate.orm,io.quarkus:quarkus-hibernate-orm-panache,jakarta.persistence -am -q` 实证 hibernate/jakarta.persistence 实际解析版本并在报告记录——本模块**独立解析**（Quarkus 全家桶自带），**不得**继承 -jpa 模块的任何钉。
- Panache 实体为**公有字段风格**（Panache 约定），与 -jpa 模块私有字段风格并存是刻意的。

## 交付

### A. 模块骨架（5.1）
`ddd4j-data/ddd4j-data-event-store-panache/pom.xml`：parent ddd4j-data；依赖：
- `ddd4j-data-event-store` ${revision}
- `io.quarkus:quarkus-hibernate-orm-panache`（BOM，无版本）
- test：`io.quarkus:quarkus-junit5`、`io.quarkus:quarkus-jdbc-h2`（均 BOM）
`ddd4j-data/pom.xml` 注册（字母序 ddd4j-data-event-store-jpa 之后、ddd4j-data-external 之前）。

### B. PanacheStoredEventEntity（io.ddd4j.data.eventstore.panache）
照计划 sketch：extends `io.quarkus.hibernate.orm.panache.PanacheEntityBase`；`@Entity @Table(name="ddd4j_stored_event", uniqueConstraints=uk_aggregate_version 同 -jpa 模块)`；公有字段 position(@Id @GeneratedValue IDENTITY Long)/eventId/aggregateType/aggregateId/version/eventType/payload(@Lob)/correlationId/causationId/createdAt；静态方法 `findCurrentVersion(String type,String id)`（Panache find("aggregateType=?1 and aggregateId=?2",...).firstResult("version") 空安全 0L）与 `findByAggregate(String,String)`（list(... order by version)）。javadoc 引 ADR-0005 + 注明公有字段为 Panache 约定。

### C. PanacheEventStore
照 sketch + 阶段 4 两枚地雷修复：
- `@ApplicationScoped`，`@Inject EventPayloadSerializer`（构造器注入）。
- `@Transactional`（**jakarta.transaction.Transactional**——Quarkus，勿 import Spring 的）append：findCurrentVersion→冲突抛 AggregateVersionConflictException→逐事件 version++ 公有字段赋值→`entity.persist()`。
- read/read(from,to)/readAll 私有 toStoredEvent：`Class.forName`+强转；**`EventId.valueOf`**（非 new EventId(String)）；**私有 record `StringAggregateRootId implements AggregateRootId`**（照 4.3 JpaEventStore 内同名适配器抄三方法）；payload=serializer.deserialize。

### D. 模块 ArchUnit
`panache/arch/EventStorePanacheModuleIndependenceTest`（@ArchTest 字段风格）≥3 条：
- `panache_impl_deps_allowlist`：io.ddd4j.data.eventstore.panache.. onlyDependOnClassesThat resideInAnyPackage(io.ddd4j.., java.., jakarta.., com.fasterxml.jackson.core|databind|annotation.., io.quarkus.., org.hibernate..（若实体注解用到）, lombok..)
- `no_spring_in_panache_module`（Quarkus 模块严禁 Spring——org.springframework..）
- `no_micronaut_in_panache_module`

### E. H2 @QuarkusTest IT（5.2 验证，Docker 无关）
`PanacheEventStoreIT`：`@QuarkusTest`；`src/test/resources/application.properties`：`quarkus.datasource.db-kind=h2`、`quarkus.hibernate-orm.database.generation=drop-and-create`、`quarkus.datasource.devservices.enabled=false`、`quarkus.hibernate-orm.log.sql=false`（H2 内存默认）。用例 ≥3：①append+readBack 3 事件（version 1/2/3、真实 EventPayloadSerializer 往返——`@Inject` 即可，需 @RegisterRestClient 不必；直接 new EventPayloadSerializer(findAndAddModules mapper) 亦可但优先注入）；②乐观锁冲突（库中条数不变）；③read(from,to) 区间。fixture 复用 -jpa 模块测试模式（自有 record AggregateRootId）。
注意：@QuarkusTest 需要入口——空 `@QuarkusMain` 不必，test-profile 直接可用；若需要 application 类则 test 源放一个最小的。

## 门禁
`./mvnw -pl ddd4j-data/ddd4j-data-event-store-panache -am install` BUILD SUCCESS；报告精确计数（ArchUnit 3 + IT ≥3）+ dependency:tree 实证结果（hibernate/persistence/quarkus-panache 实际版本）。

## 提交
单 commit：`feat(data): ddd4j-data-event-store-panache——Quarkus Panache 实现（实体+EventStore+H2 IT）`

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-5.1-5.2-report.md`。Reply ≤15 lines.
