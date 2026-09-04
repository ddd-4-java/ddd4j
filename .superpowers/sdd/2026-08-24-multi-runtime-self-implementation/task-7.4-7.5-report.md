# Task 7.4+7.5 Report — ddd4j-data-projection-jdbi + ddd4j-data-projection-r2dbc

**Status: COMPLETE** — 2 commits on feature/2.0.x (base 5059bdb1), all gates green.

## Commits

| Commit | Subject | Contents |
|---|---|---|
| `1a4de83e` | feat(data): ddd4j-data-projection-jdbi——JDBI 投影持久化（SQL-first + H2 IT） | pom + JdbiProjectionPositionRepository + ProjectionPositionJdbiIT + ArchUnit + ddd4j-data/pom.xml 注册 |
| `72c4e8d6` | feat(data): ddd4j-data-projection-r2dbc——R2DBC 投影持久化（响应式 + reactor-test） | pom + R2dbcProjectionPositionRepository + ProjectionPositionR2dbcIT + ArchUnit + ddd4j-data/pom.xml 注册 |

提交消息与 brief 逐字一致；每个 commit 恰含 5 个文件（模块 4 文件 + 父 pom 1 行注册）。
ddd4j-data/pom.xml 注册顺序按派发指示：jpa < jdbi < panache < r2dbc。

## Gate 计数（3 次安装全 BUILD SUCCESS）

| Gate | Command | Result | Test counts |
|---|---|---|---|
| A | `./mvnw -pl ddd4j-data/ddd4j-data-projection-jdbi,ddd4j-data/ddd4j-data-projection,ddd4j-core -am install` | SUCCESS | projection-jdbi：**8**（IT 5 + ArchUnit 3），0 失败 |
| B | `./mvnw -pl ddd4j-data/ddd4j-data-projection-r2dbc,ddd4j-data/ddd4j-data-projection,ddd4j-core -am install` | SUCCESS | projection-r2dbc：**8**（IT 5 + ArchUnit 3），0 失败 |
| 合并 | brief 原命令（jdbi + r2dbc + projection + core，-am） | SUCCESS（reactor 9 模块全绿） | 复跑通过，jacoco 覆盖分析正常 |

## H2 MERGE INTO 语法实证（5.4 教训落地）

动手前用 BOM 同版 H2 2.4.240（`org.h2.tools.Shell`，jdbc:h2:mem 常规模式）实测：

- `MERGE INTO ddd4j_projection_position (stream_id, next_event_number) KEY (stream_id) VALUES (...)`
  → 两次执行各返回 Update count: 1，表内 1 行、计数原位变化（真 upsert）。**采用**。
- `INSERT INTO t (k, v) VALUES ('a', 1) ON CONFLICT (k) DO UPDATE SET v = 2`
  → `JdbcSQLSyntaxErrorException ... [*]on conflict ... [42000-240]`。**弃用**（brief 预判
  「PostgreSQL 语法，H2 用 MERGE INTO——实测后适配」成立）。

JDBI 侧绑定 `:streamId/:next` 命名参数；R2DBC 侧 `$1/$2`（与 event-store-r2dbc 5.4 实证
的 r2dbc-h2 绑参约定一致），IT 全绿即两条 MERGE 通路均实证可用。

## created_at 实证类型

**N/A——本表无时间戳列**。`ddd4j_projection_position` 与 -jpa/-panache DDL parity（
`stream_id VARCHAR(250) NOT NULL PRIMARY KEY, next_event_number BIGINT NOT NULL`），brief B.3
的「timestamp with time zone（如有）」条件不成立（"如有"=无），5.4 的 OffsetDateTime→
toZonedDateTime 行类型课题在本表不出现；已在 R2dbcProjectionPositionRepository 类注释中
注明该结论与出处。

## 交付细节

### A. ddd4j-data-projection-jdbi
- `JdbiProjectionPositionRepository`：`(Jdbi)` 构造器；findByStreamId / findAll /
  save（单语句 MERGE INTO 原子 upsert，无先查后写竞态）/ deleteByStreamId（缺行静默）/
  resetToZero=save(streamId, 0)（与 core InMemory 同语义，含缺行插零位行）。
- SQL-first：仅 Statement/Handle 原语，无 SQL Object 注解；行重建为不可变
  DefaultProjectionPosition（不暴露原始行）。
- IT 5 用例：saveIncrReload（跨仓储实例重读证持久性）/ resetToZero 推进后回退 /
  resetToZero 缺行插零 / 两 handler 命名空间隔离（upsert 互不串扰）/ save upsert+delete 归空。
- ArchUnit 3：allowlist（io.ddd4j/java/jakarta/org.jdbi/lombok——不含 Jackson，比
  event-store-jdbi 更紧，本模块无 payload 序列化需求）+ no_spring + no_quarkus。

### B. ddd4j-data-projection-r2dbc
- `R2dbcProjectionPositionRepository`：构造参数取 **ConnectionFactory**（brief 给的
  ConnectionPool 二选一——pool is-a ConnectionFactory，故选 SPI 接口，模块保持零
  r2dbc-pool 依赖，池化实例直接可传）。
- **双面 API**（设计决策，记录供评审）：core SPI 是同步接口而本模块服务响应式运行时——
  `*Reactive` 公共方法（Mono/Flux，连接 usingWhen 托管）供 WebFlux/Vert.x 零阻塞直组；
  同步桥接方法（SPI 实现）内部 block()，每方法恰一条原子语句故桥接无一致性代价。
- save=MERGE INTO $1/$2 单语句；resetToZero=upsert(streamId,0)；delete 缺行静默。
- IT 5 用例（StepVerifier 信号序列断言 + 同步桥接面覆盖）：saveIncrReadBack 响应式
  完整循环 / resetToZero 响应式回退 / resetToZero 缺行插零 / 两 handler 命名空间隔离
  （同步 SPI 面）/ save upsert+delete 归空。
- ArchUnit 3：allowlist（io.ddd4j/java/jakarta/reactor/io.r2dbc/lombok）+ no_spring + no_quarkus。

## 依赖面核查（控制器预检项全部按预案执行）

- jdbi3-core 显式 `${jdbi.version}`（3.45.4，BOM 仅属性无 dependencyManagement）。
- r2dbc-spi / r2dbc-h2 / reactor-core / reactor-test 均走 BOM 管理版（r2dbc-spi 1.0.0.RELEASE:553、
  r2dbc-h2 1.1.0.RELEASE:8052、reactor-bom 2025.0.6:6461）；h2 走 ${h2.version}=2.4.240。
- effective-model 1458 条 WARNING 为全仓既有 BOM 噪音：新模块 projection-jdbi 与既有
  模块 event-store-jdbi 同为 107 条（help:effective-model 实测），非本任务引入。

## Self-review 结论

- 两模块均不重定义 core 投影契约（接口取自 io.ddd4j.core.cqrs.readmodel）。
- DDL parity：与 jpa/panache 表结构逐列一致（VARCHAR(250) PK + BIGINT NOT NULL）。
- 「重启读回」模拟：每用例新建仓储实例重读（零缓存证明持久性），非实例态断言。
- 无 gold-plating：未加 brief 之外的 incrementBy 等扩展（jpa 模块有、panache 模块无——
  本两模块跟随 panache 的最小 SPI 面）。
- 工作树 clean，无 target/ 泄漏入提交。

## Concerns（不阻塞）

1. **MERGE INTO 为 H2 方言**：PostgreSQL 生产运行时需换 `INSERT ... ON CONFLICT` 变体
   ——已在两处 Javadoc 注明「集成方按需替换」，后续若加多方言支持可提独立任务。
2. **同步桥接 block()**：R2dbc 仓储在响应式线程上调用同步 SPI 方法会抛
   IllegalStateException（block() 禁止在 Netty EventLoop 等非阻塞线程调用）——
   core ProjectionDispatcher 是同步语义，属预期使用边界，已由「每方法单条原子语句」
   设计将桥接代价压到最低；后续若 core 出响应式投影 SPI 可直接复用 *Reactive 面。
3. **jpa 与 jdbi 模块的 deleteByStreamId 语义差**：jpa 走 Spring Data deleteById、
   本两模块缺行静默——SPI 契约本身未定义缺行行为，无实际影响，仅记录。
