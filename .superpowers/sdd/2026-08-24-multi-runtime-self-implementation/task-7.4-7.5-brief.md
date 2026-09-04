# Task 7.4+7.5 Brief（合并派发）— projection-jdbi + projection-r2dbc

## 背景
- ddd4j-core 已含 ProjectionPositionRepository/ProjectionPosition/DefaultProjectionPosition（不重定义）
- 任务 7.2/7.3 已交付 JPA/Panache 持久化——本任务补 JDBI + R2DBC 两套（覆盖 Javalin/Vert.x + 响应式场景）
- jdbi.version=3.45.4 属性（:375）无 dependencyManagement；r2dbc-spi/r2dbc-h2 均有管理（:553/:545）

## 交付

### A. ddd4j-data-projection-jdbi
1. `pom.xml`：parent ddd4j-data；依赖 `ddd4j-data-projection` + `org.jdbi:jdbi3-core` ${jdbi.version}；test `com.h2database:h2`（BOM）。
2. `JdbiProjectionPositionRepository`：`(Jdbi jdbi)` 构造器；findByStreamId `SELECT stream_id, next_event_number FROM ddd4j_projection_position WHERE stream_id = :id`；findAll `SELECT * FROM ddd4j_projection_position`；save `INSERT ... ON CONFLICT (stream_id) DO UPDATE SET next_event_number = :next`（H2 用 MERGE INTO）；deleteByStreamId `DELETE WHERE stream_id = :id`；resetToZero 用 save(streamId, 0)。
3. DDL IT：`CREATE TABLE ddd4j_projection_position (stream_id VARCHAR(250) NOT NULL PRIMARY KEY, next_event_number BIGINT NOT NULL)`——同 jpa/panache 模块表结构。
4. H2 IT ≥2：①save→incr→reload；②resetToZero→0；③upsert 命名空间隔离。
5. ArchUnit 3：allowlist jdbi../jakarta..；no_spring + no_quarkus。

### B. ddd4j-data-projection-r2dbc
1. `pom.xml`：parent ddd4j-data；依赖 `ddd4j-data-projection` + `io.r2dbc:r2dbc-spi` + `io.projectreactor:reactor-core`（BOM）；test `io.r2dbc:r2dbc-h2`（BOM）+ `io.projectreactor:reactor-test`。
2. `R2dbcProjectionPositionRepository`：`(ConnectionPool pool)` 或 `(ConnectionFactory cf)`；findByStreamId `SELECT ... WHERE stream_id = $1`；save `INSERT ... ON CONFLICT (stream_id) DO UPDATE SET next_event_number = $2`（PostgreSQL 语法，H2 用 MERGE INTO——实测后适配）；deleteByStreamId `DELETE WHERE stream_id = $1`；resetToZero save(id, 0)。**级联类型**：created_at 行类型实证（OffsetDateTime→toZonedDateTime，如 5.4 Task）。
3. DDL IT：表结构同 jpa，但用 `timestamp with time zone`（如有）。
4. reactor-test StepVerifier ≥2：①save→incr→readBack；②resetToZero。
5. ArchUnit 3：allowlist reactor../io.r2dbc../jakarta..；no_spring + no_quarkus。

## 门禁
两个模块独立安装：`./mvnw -pl ddd4j-data/ddd4j-data-projection-jdbi,ddd4j-data/ddd4j-data-projection-r2dbc,ddd4j-data/ddd4j-data-projection,ddd4j-core -am install` BUILD SUCCESS；报告各模块测试计数。

## 提交
两 commit：
- `feat(data): ddd4j-data-projection-jdbi——JDBI 投影持久化（SQL-first + H2 IT）`
- `feat(data): ddd4j-data-projection-r2dbc——R2DBC 投影持久化（响应式 + reactor-test）`

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-7.4-7.5-report.md`。Reply ≤15 lines.
