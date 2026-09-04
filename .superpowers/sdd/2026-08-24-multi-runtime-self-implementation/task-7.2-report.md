# Task 7.2 Report — ddd4j-data-projection-jpa（Spring Data JPA ProjectionPositionRepository 实现）

## Status: DONE

- **Commit**: `a0042865` `feat(data): ddd4j-data-projection-jpa——Spring Data JPA ProjectionPositionRepository 实现（含 H2 IT）`（单 commit，9 files / +626）
- **Gate**: `./mvnw -pl ddd4j-data/ddd4j-data-projection-jpa,ddd4j-data/ddd4j-data-projection,ddd4j-core -am install` → **BUILD SUCCESS**（7 模块 reactor 全绿）
- **测试数**：新模块 **10**（`ProjectionPositionJpaIT` 5 + `ProjectionJpaModuleIndependenceTest` 5 条 Arch 规则）；`ddd4j-data-projection` 21（5+9+7）；`ddd4j-core` 全绿（随 reactor）

## 交付物

| 文件 | 说明 |
| --- | --- |
| `ddd4j-data/ddd4j-data-projection-jpa/pom.xml` | 4.1+4.2 同款：模块局部 `<spring-boot-it.version>3.4.4</spring-boot-it.version>`（仅测试栈，BOM 刻意不管理 Boot，javadoc 注明）；主依赖 spring-context/spring-tx（BOM 6.2.19 无版本）+ spring-data-jpa/spring-data-commons 3.5.13 显式钉 + jakarta.persistence-api 3.1.0 钉（BOM 2025.1/3.2.0 与 6.2 线冲突，理由同 4.1 注释）；test：starter-data-jpa/starter-test 3.4.4 + hibernate-core 6.6.40.Final（test 钉，BOM 链生效版面向 Fw7 冲突）+ h2（BOM 管）；surefire 追加 `**/*IT.java` |
| `ProjectionPositionEntity.java` | `@Entity @Table(name="ddd4j_projection_position")`，`@Id String streamId`（= core `getStreamId()` 返回类型）+ 位置计数列 `next_event_number`（nullable=false，**non-versionable**，无 `@Version`） |
| `SpringDataProjectionPositionRepository.java` | `extends JpaRepository<ProjectionPositionEntity,String>` 持久化原语：显式 `@Modifying(clearAutomatically=true) @Query` 数据库端原子 `incrementBy`/`resetToZero` 更新 SQL |
| `JpaProjectionPositionRepository.java` | `@Repository` 适配器，`implements io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository`（**core 接口原样引用，不重定义**）；实体↔`DefaultProjectionPosition` 映射、upsert save、缺行 reset 插 0（与 core InMemory 语义一致）、扩展 `incrementBy` |
| `ProjectionPositionJpaIT.java` | ①save 0→incr 到 5→`em.clear()` 重启读回 5；②resetToZero 回退 0；③缺行 reset 插 0；④双 handler 名（`order-summary`/`inventory-snapshot`）独立递增互不串扰 + findAll；⑤save upsert + deleteByStreamId |
| `ProjectionJpaModuleIndependenceTest.java` | 5 条 Arch 规则（≥3）：allowlist（io.ddd4j.. / java.. / jakarta.. / org.springframework.. / org.hibernate.. / lombok..）+ no_quarkus / no_micronaut / no_vertx / no_dropwizard（no_spring 不适用本模块——JPA 投影持久化本质依赖 Spring） |

## 注册槽位证据

`ddd4j-data/pom.xml` L42：`<module>ddd4j-data-projection</module>` 之后追加 `<module>ddd4j-data-projection-jpa</module>`（列表末位＝4 持久化子组首位；"j"<"p"，后续 7.3 projection-panache 排其后，字母序成立）。

## 设计决策 / 与 brief 的偏差

1. **两件套拆分而非单接口**：brief 原文让 `SpringDataProjectionPositionRepository` 同时 `extends JpaRepository` 且 `implements` core SPI——不可编译（`JpaRepository#findAll` 固定返回 `List<实体>`，与 SPI 的 `List<ProjectionPosition>` 同名方法返回类型不可协变）。按 4.1 的 `SpringDataStoredEventRepository`+`JpaEventStore` 同款分层拆为 SpringData 仓储（原语）+ Jpa 适配器（SPI 语义），javadoc 已注明。
2. **计数列命名 `next_event_number`**：brief 行文称 "version Long 字段"，实指位置计数（core 契约 `getNextEventNumber()`，0-based、resetToZero→0）；按 core 语义命名，non-@Version。
3. **可选类 `TransactionalProjectionPositionRepository` 未建**（brief 标可选）：适配器用声明式 `@Transactional`，集成方已有事务时直接加入，无需额外门面。
4. **测试栈 Boot banner 显示 v3.5.10**：starter 钉 3.4.4，个别 spring-boot 传递 jar 被 BOM 链管理到 3.5.x（4.1/4.2 同状），二者同在 Spring 6.2 线；`dependency:tree` 实证关键版本：spring-context/tx 6.2.19、spring-data-jpa 3.5.13、jakarta.persistence-api 3.1.0、hibernate-core 6.6.40.Final(test)、starter 3.4.4(test)。

## Concerns

- 无阻塞。上述偏差 1/2 为编译正确性与 core 契约语义驱动的最小偏离，均有 javadoc 说明。
