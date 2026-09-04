# Task 7.2 Brief — ddd4j-data-projection-jpa（4 持久化第 1 个：Spring Data JPA ProjectionPositionRepository 实现）

## 背景
- 阶段 6.10 已交付 ddd4j-data-projection SPI（ProjectionHandler/Registry/Dispatcher）。
- ddd4j-core/cqrs/readmodel/ 已有 ProjectionPositionRepository 接口（findByStreamId/save/resetToZero）+ ProjectionPosition 值接口 + DefaultProjectionPosition/InMemoryProjectionPositionRepository 实现 + ProjectionService/DefaultProjectionService 业务方法。
- 任务 4.1+4.2 模式：Spring Data JPA 通过 Spring Boot 父聚合器版本管理（**spring-boot-it.version 3.4.4 模块局部属性**——经验事实 4.1 任务证实 spring-boot 全仓无 BOM 管理）。

## 交付
### A. `ddd4j-data-projection-jpa`
- `pom.xml`：parent ddd4j-data；依赖 `ddd4j-data-projection` + `spring-context` + `spring-tx`（BOM 无版本）；test `spring-boot-starter-test` + `spring-boot-starter-data-jpa`（与 4.1+4.2 一致：模块局部 `<spring-boot-it.version>3.4.4</spring-boot-it.version>` 属性，javadoc 注明仅测试栈、BOM 故意不管理 Boot）
- `src/main/java/io/ddd4j/data/projection/jpa/ProjectionPositionEntity.java`：`@Entity @Table(name="ddd4j_projection_position")` + `@Id private String streamId`（String 是 core ProjectionPosition.getStreamId() 返回类型）；version `Long` 字段（Nullable=false 但 non-versionable——按 IT 用更新 SQL 显式 incr 即可）。
- `src/main/java/io/ddd4j/data/projection/jpa/SpringDataProjectionPositionRepository.java`：`@Repository extends JpaRepository<ProjectionPositionEntity, String>` + 显式 @Query update/incr；`implements io.ddd4j.data.projection.spi.ProjectionPositionRepository`（**包名修正：core 的接口在 ddd4j-core/cqrs/readmodel，本模块不重定义；用 4.1+4.2 同款引用风格**）
- `src/main/java/io/ddd4j/data/projection/jpa/TransactionalProjectionPositionRepository.java`（可选门面——包 `JpaTransactionManager` 包装 save/incr；若集成方已有 tx 则不需要此层）

### B. 模块 ArchUnit（≥3）
allowlist 含 org.springframework.. + org.hibernate.. + jakarta..；no_spring 含 ddd4j.data.projection.jpa.. 内部不可引 spring（允许其传递依赖以工作流存在）
注：与 ddd4j-data-cqrs-spring 不同——**投影持久化本质用 Spring Data JPA 必然依赖 Spring**，因此 no_spring 规则不适用本模块；改 no_quarkus / no_micronaut / no_vertx / no_dropwizard

### C. JPA IT ≥2
- `ProjectionPositionJpaIT`（`@SpringBootTest(classes=TestApp)` + H2）：①saveAndIncr 完整循环（先 save position=0 → incr 到 5 → 重启读回 5）；②resetToZero 回退到 0
- 跨 aggregateType/aggregateId 命名空间验证：两个 handler 名称 save 后独立递增

### D. ddd4j-data 注册
字母序：projection 后、projection-panache 前？实测 "projection-jpa" 与 "projection-panache" 比较——"j" < "p"——projection-jpa 在 projection-panache 之前。注册于 `ddd4j-data-projection` 之后、4 持久化子组首位。

## 门禁
`./mvnw -pl ddd4j-data/ddd4j-data-projection-jpa,ddd4j-data/ddd4j-data-projection,ddd4j-core -am install` BUILD SUCCESS；3 模块测试全绿。

## 提交
单 commit：`feat(data): ddd4j-data-projection-jpa——Spring Data JPA ProjectionPositionRepository 实现（含 H2 IT）`

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-7.2-report.md`。Reply ≤15 lines.
