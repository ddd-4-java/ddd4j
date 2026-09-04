# Task 4.4 Report — JpaEventStore 集成测试（双轨）+ 4.3 评审并入项

- Commit: `df5646c4` `test(data): JpaEventStore 集成测试——H2 全量契约 + PG 容器 CI 轨（Docker 缺席自动跳过）`（branch `feature/2.0.x`，7 files，+613/−10）
- Gate: `./mvnw -pl ddd4j-data/ddd4j-data-event-store-jpa -am install` → **BUILD SUCCESS**（reactor 8 模块全绿）

## 精确测试计数（surefire，本模块）

| 测试类 | 轨道 | Run | Fail | Err | Skip |
| --- | --- | --- | --- | --- | --- |
| `EventStoreJpaModuleIndependenceTest` | ArchUnit（既有） | 3 | 0 | 0 | 0 |
| `JpaEventStoreTest` | Mockito（既有＋D 并入改） | 4 | 0 | 0 | 0 |
| `JpaEventStoreH2IT` | **H2 全量契约（本地真跑）** | 5 | 0 | 0 | 0 |
| `JpaEventStorePostgresIT` | **PG 容器 CI 轨** | 2 | 0 | 0 | **2** |
| 模块合计 |  | **14** | 0 | 0 | **2** |

PG 轨 surefire 呈现（如实记录）：`Tests run: 2, Failures: 0, Errors: 0, Skipped: 2`；XML `tests="2" skipped="2"`，两用例名均在报告中、无失败。跳过原因＝`@Testcontainers(disabledWithoutDocker = true)` 且本机 Docker 缺席（log 中 Testcontainers 报 `Could not find a valid Docker environment`，`/var/run/docker.sock` 不存在）。**未伪造 PG 绿。**

## 交付明细

### A. 测试栈依赖（模块 pom，Edit 工具）
- 新属性 `<spring-boot-it.version>3.4.4</spring-boot-it.version>`（注释：仅测试栈，BOM 刻意不管理 Boot；Boot 3.4＝Spring 6.2 线）。
- 新增 test 依赖：`spring-boot-starter-data-jpa`、`spring-boot-starter-test`（均 `${spring-boot-it.version}`）、`com.h2database:h2`（**BOM 已管理 → 2.4.240**，未另行钉版）、`jackson-datatype-jsr310`（真实 Jackson 往返 ZonedDateTime，BOM 管理 2.21.2）。
- Testcontainers：改为 2.x 新命名 `org.testcontainers:testcontainers-postgresql` ＋新增 `testcontainers-junit-jupiter`（均 BOM 管理 2.0.3，全链一致 core/jdbc/database-commons 2.0.3）。原 1.x 旧命名 `org.testcontainers:postgresql` 在本仓库 BOM 组合下解析为 1.20.6、而 core 被 BOM 管到 2.0.3，混版有运行时风险，故统一到 BOM 管理的新命名（意图不变：PG 容器＋JUnit 集成）。
- surefire：父配置只含 `**/*Test.java`（不含 IT），模块级追加 `<include>**/*IT.java</include>` 使双轨随 test 阶段执行。

### B. H2 全量契约轨（本地真跑）
- `TestApp`：`@SpringBootConfiguration + @EnableAutoConfiguration + @EntityScan(io.ddd4j.data.eventstore.jpa) + @ComponentScan`＋`@Bean EventPayloadSerializer`（`JsonMapper.builder().findAndAddModules().build()`——**真实序列化，零 mock**，jsr310 由 findAndAddModules 注册）。
- `application-test.yml`：`jdbc:h2:mem:esit;MODE=PostgreSQL`、`ddl-auto=create-drop`、`open-in-view=false`；不声明 driver-class-name（按 URL 推断，PG 轨覆盖 URL 后自动切 PG 驱动）。
- `JpaEventStoreH2IT` 5 用例（`@BeforeEach repository.deleteAll()` 隔离；position 断言只做单调/相对序，不做绝对值）：
  1. append+readBack：3 事件→read 3 条、version 1/2/3、payload 真实 Jackson 往返（`OccurredEvent.fact` 等值）、eventId/aggregateId 一致、position 正数、correlation/causation null（空安全路径）。
  2. 乐观锁：expected=0 二次 append → `AggregateVersionConflictException`（actualVersion=2），库中仍 2 条。
  3. readAll：跨两聚合追加→position 严格递增且跨聚合按追加序、limit=2 截断、fromPosition 含端点/越过后返回尾部。
  4. read(from,to)：闭区间 [2,3]→[2,3]、[1,1]→[1]。
  5. uk_aggregate_version：同一 `@Transactional` session 直插两条同 (type,id,version=1)→第二条 `DataIntegrityViolationException`（数据层双保险实证）。

### C. PG 容器 CI 轨（本地自动跳过）
- `@Testcontainers(disabledWithoutDocker = true)`＋`@Container static PostgreSQLContainer<>("postgres:16-alpine")`＋`@DynamicPropertySource` 覆盖 url/username/password（其余装配与 H2 轨共用 TestApp/test profile）。
- 2 用例：append/readBack 全往返（watch② 验证点）＋两线程同 expected 并发 append 恰一成功（watch① 验证点；败者接受 `AggregateVersionConflictException` 或 uk 兜底 `DataIntegrityViolationException`，断言 successes==1 且流内恰 1 条）。
- javadoc 记录 watch①② 验证点与两条**预授权回退**（去 @Lock 依赖 uk 约束 / payload 改 `@JdbcTypeCode(SqlTypes.LONGVARCHAR)` 或 text）；**本地 Docker 缺席未执行任何回退**，CI 首跑定夺。

### D. 4.3 评审并入项
1. `JpaEventStore.toStoredEvent`：`Objects.requireNonNullElse(getPosition(), 0L)` →
   ```java
   Objects.requireNonNull(entity.getPosition(),
           "position must not be null in read path (transient entities unsupported)")
   ```
   javadoc 同步改为 fail-loud 语义（瞬态实体进读路径＝编程错误，抛 NPE）。
2. `JpaEventStoreTest.read_应重建StoredEvent且correlationId空安全`：经反射对手工实体设 `position=42`（实体无 setter），断言 `single.position()` **isEqualTo(42L)**（原 isZero 删除）。
3. `JpaEventStoreTest` 用例 b：`third` 改经 `SampleEvent(Event respondTo)` 因果构造器（`super(EntityIdPath, respondTo)`）派生自 `first`，captor 新增断言 correlationId/causationId `containsExactly(null, null, first.getEventId().asString())`。

## H2 轨意外与处理（简要说明中有事实记录）

1. **BOM 版本错配（两次上下文加载失败，已修）**——这是本任务最大意外：
   - 父 BOM 将 `spring-data-jpa` 管到 **4.0.6**（spring-data-bom 2025.1，面向 Framework 7），而 spring-framework 管在 **6.2.19** → 运行时 `NoSuchMethodError: RuntimeBeanReference.<init>(String, Class)`（4.0.6 调 Framework 7 API）。模块级显式钉 `spring-data-jpa`/`spring-data-commons` **3.5.13**（Spring 6.2 线，.m2 现有）。注意：这是**主作用域**版本对齐（原 4.0.6 与本仓库 6.2 框架线 ABI 不兼容，属既有 BOM 地雷，仅在本模块修正）。
   - BOM 将 `jakarta.persistence-api` 管到 **3.2.0**，与 Hibernate 6.6 在 Spring EMF 代理上冲突（`getSchemaManager()` 两接口返回类型不兼容）→ 模块级钉 **3.1.0**。
   - BOM 将 `hibernate-core` 管到 7.2.6（Framework 7 线）→ 模块 test 作用域钉 **6.6.40.Final**（Boot 3.4/3.5 线）。
   - Boot starter 传递的 spring-boot core 三件被 BOM 管到 3.5.10（starter 本身 3.4.4）——同为 Spring 6.2 线，实测兼容，未再强钉。
2. **测试自身一 bug**：`StoredEvent` 无 equals，`containsExactly` 按对象恒等比较失败 → 改按 position 比对（非产品缺陷）。
3. **read 路径未加 `@Transactional(readOnly=true)`**：H2（MODE=PostgreSQL）CLOB 在无事务读路径下未出问题，预授权最小修复**未启用**。

## Concerns / Follow-up
- 父 BOM 的 spring-data 4.0.x ＋ framework 6.2 错配是仓库级问题（其它用 spring-data 的模块同样悬着运行时地雷），建议后续 ADR/任务统一对齐版本线。
- PG 轨两用例本地未执行（Docker 缺席），CI 首跑如触发 watch①② 预授权回退，按 brief 回退并记录。
- `docs/superpowers/plans/*.md` 两个未跟踪文件非本任务产物，未纳入提交。

## 文件清单（均绝对路径）
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-data/ddd4j-data-event-store-jpa/pom.xml`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-data/ddd4j-data-event-store-jpa/src/main/java/io/ddd4j/data/eventstore/jpa/JpaEventStore.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-data/ddd4j-data-event-store-jpa/src/test/java/io/ddd4j/data/eventstore/jpa/JpaEventStoreTest.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-data/ddd4j-data-event-store-jpa/src/test/java/io/ddd4j/data/eventstore/jpa/TestApp.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-data/ddd4j-data-event-store-jpa/src/test/java/io/ddd4j/data/eventstore/jpa/JpaEventStoreH2IT.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-data/ddd4j-data-event-store-jpa/src/test/java/io/ddd4j/data/eventstore/jpa/JpaEventStorePostgresIT.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-data/ddd4j-data-event-store-jpa/src/test/resources/application-test.yml`

## Micro-fix（2026-08-24，pom 注释失实纠偏）

**前提核验**：`ddd4j-dependencies/pom.xml` 确有 `hibernate.version=6.6.40.Final`（行 302）与 `hibernate-platform:${hibernate.version}` import（行 4045-4052，位于 dependencyManagement 内）。原注释"BOM 管理的 7.2.x 面向 Framework 7"对 BOM 声明的描述失实——**但去钉实测证明钉并非冗余**。

**解析实测（关键反转）**：移除模块级 hibernate-core 6.6.40.Final(test) 钉后，`dependency:tree` 显示本模块测试类路径解析为 **hibernate-core:7.2.6.Final(test)**——即 BOM 链实际生效的管理版并非 hibernate-platform:6.6.40.Final（有效 POM 中 hibernate-core 管理版 = 7.2.6.Final；同源还有 hibernate-agroal 7.2.6，早于行 4045 声明的其它 import 在 Maven first-wins 下胜出，quarkus-bom 3.38.2 嫌疑排除——其管 7.4.5）。后果：H2 IT 5 例全挂（Hibernate 7.x 需 JPA API 3.2，与本模块 jakarta.persistence-api 3.1.0 钉冲突：`jakarta/persistence/PersistenceUnitTransactionType` NoClassDefFound），PG 2 例照常跳过，运行数 14 不变。

**处置（走 brief 预授权回退路径，注释按实测改写而非照抄模板）**：恢复 hibernate-core 6.6.40.Final(test) 钉，注释改写为实测事实——"此钉为实测必要（非冗余）：BOM 链实际生效管理版是 7.2.6.Final（dependency:tree 实测），与 spring-orm 6.2.x / jakarta.persistence-api 3.1.0 冲突；去钉 H2 IT 全挂"。模板话术"与 BOM 同版冗余钉"与实测矛盾，未采用。

**门禁复跑**：`./mvnw -pl ddd4j-data/ddd4j-data-event-store-jpa -am install` → **BUILD SUCCESS**；模块运行数 **14**（ArchUnit 3 + Mockito 4 + H2 5 + PG 2 skipped），与 df5646c4 基线一致；hibernate-core 解析版回到 **6.6.40.Final**。spring-data-jpa/commons 3.5.13 与 jakarta.persistence-api 3.1.0 两个真钉未动。

**提交**：`build(data): 纠正 event-store-jpa hibernate 注释失实，钉经实测保留（BOM 链实管 7.2.6，去钉 H2 IT 全挂）`。偏离说明：brief 指定标题"移除冗余 hibernate 钉（BOM 已管 6.6.40.Final）"，但其前提（BOM 实管 6.6.40.Final）已被 dependency:tree 实测证伪，且终态未移除钉，按原标题提交将复刻同等级失实信息，故改用如实标题。
