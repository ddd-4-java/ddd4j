# Task 7.3 Brief — ddd4j-data-projection-panache（Quarkus Hibernate ORM Panache 投影持久化）

## 背景
- ddd4j-core 已含 ProjectionPositionRepository 接口（findByStreamId/save/resetToZero/findAll/deleteByStreamId）
- 任务 7.2 已交付 JPA 实体 `ProjectionPositionEntity`（`@Entity @Table(name="ddd4j_projection_position")` + `@Id String streamId` + `long nextEventNumber` + non-`@Version`）——本模块用 Quarkus Panache 同款表结构
- 5.1 经验：quarkus-bom 3.38.2 在 BOM 导入（:8019），模块级再导入（parent-chain 防御）；公有字段风格为 Panache 约定

## 交付

### A. pom + 注册
- `ddd4j-data/ddd4j-data-projection-panache/pom.xml`：parent ddd4j-data；依赖 `ddd4j-data-projection`（${revision}）+ `io.quarkus:quarkus-hibernate-orm-panache`（BOM 无版本，**模块级 quarkus-bom 再导入**按 5.1 经验）；test `io.quarkus:quarkus-junit5` + `io.quarkus:quarkus-jdbc-h2`（BOM 无版本）
- 注册：字母序 projection-jpa 之后（"j" < "p"）

### B. 实体 `PanacheProjectionPositionEntity`
- extends `io.quarkus.hibernate.orm.panache.PanacheEntityBase`
- `@Entity @Table(name="ddd4j_projection_position")`（同 jpa 模块表名 + uk）
- 公有字段：`@Id public String streamId` + `@Column(nullable=false) public long nextEventNumber`（Panache 公有字段约定）
- `upsert(String streamId, long nextEventNumber)`：`find("streamId", streamId).firstResult()` → null 则 new entity + persist，否则更新 + persist；javadoc 同 jpa 模块 non-versionable 设计说明
- `resetToZero(String streamId)`：upsert streamId + 0

### C. 适配器 `QuarkusProjectionPositionRepository`（implements core SPI）
- `@ApplicationScoped`，构造器 `@Inject PanacheProjectionPositionEntity repository`（ArC 注入）
- `findByStreamId`：repository.find("streamId", id) → Optional
- `findAll`：repository.listAll() → map
- `save`：repository.upsert
- `deleteByStreamId`：repository.delete
- `resetToZero`：repository.resetToZero

### D. ArchUnit（≥3）
`projection_panache_deps_allowlist`（io.quarkus../io.hibernate../jakarta../lombok..）；`no_spring_in_panache_module`；`no_vertx_in_panache_module`

### E. QuarkusTest IT ≥2
`@QuarkusTest`；H2 `jdbc:h2:mem:projit;MODE=PostgreSQL`；`ddl-auto=drop-and-create`；`devservices=false` + explicit jdbc.url（5.1 实证修正）
用例：①save→incr→reload cycle；②resetToZero→0（含缺失行插入零值语义，同 jpa 模块）

## 门禁
`./mvnw -pl ddd4j-data/ddd4j-data-projection-panache -am install` BUILD SUCCESS；报告模块精确计数 + quarkus-panache 实际解析版本（dependency:tree 实证，5.1 教训）

## 提交
单 commit：`feat(data): ddd4j-data-projection-panache——Quarkus Panache 投影持久化（含 H2 IT）`

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-7.3-report.md`。Reply ≤15 lines.
