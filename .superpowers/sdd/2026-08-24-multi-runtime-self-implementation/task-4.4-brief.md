# Task 4.4 Brief — JpaEventStore 集成测试（双轨：H2 全量契约 + PG 容器 CI 轨）

## 环境事实（控制器已预检）
- **Docker 本地不可用** ⇒ Testcontainers 本地必跳。诚实原则：不伪造 PG 绿。
- 本地 .m2 有 `spring-boot-starter-data-jpa`/`-test` **3.4.4**（Boot 3.4 = Spring 6.2 线，与 ddd4j 2.0.x 匹配；4.0.4 属 Boot4/Spring7 线勿用）。

## 交付

### A. 测试栈依赖（模块 pom，Edit 工具，test 作用域，显式版本 3.4.4）
`spring-boot-starter-data-jpa`(test，带 Hibernate) + `spring-boot-starter-test`(test) + `com.h2database:h2`(test，BOM 若无管理则 2.3.232 或 .m2 现有版本，报告注明) + 既有 testcontainers-postgresql/postgresql 保留。属性 `<spring-boot-it.version>3.4.4</spring-boot-it.version>` 局部声明（javadoc 注明：仅测试栈，BOM 刻意不管理 Boot）。

### B. H2 全量契约轨（本地必跑）`JpaEventStoreH2IT`
`@SpringBootTest(classes=TestApp)` + `@ActiveProfiles("test")`，`TestApp`=@SpringBootConfiguration+@EnableAutoConfiguration+@EntityScan(io.ddd4j.data.eventstore.jpa)+组件扫描 JpaEventStore + @Bean EventPayloadSerializer（mapper `findAndAddModules` 构建——真实序列化，勿 mock）。`application-test.yml`：H2 内存库 `jdbc:h2:mem:esit;MODE=PostgreSQL`、ddl-auto=create-drop、`spring.jpa.open-in-view=false`。
用例（复用 4.3 fixture 模式但**真实库**）：
1. append+readBack：3 事件追加→read 返回 3、version 1/2/3、payload 反序列化字段等值（真实 Jackson 往返）、aggregateId.asString 一致
2. 乐观锁：二次 append(expected=0) 抛 AggregateVersionConflictException 且库中仍 3 条
3. readAll(fromPosition,limit)：跨聚合追加后按 position 递增、limit 生效
4. read(from,to) 版本区间
5. uk_aggregate_version 约束：同 session 直接 save 两条同 (type,id,version)→DataIntegrityViolation/ConstraintViolation（证数据库层双保险）

### C. PG 容器 CI 轨（本地自动跳过）`JpaEventStorePostgresIT`
`@Testcontainers(disabledWithoutDocker = true)` + `@SpringBootTest`（属性注入 container JDBC URL/username/password 覆盖 yml）。用例精简 2 条：append/readBack 全往返 + 并发乐观锁（两线程同 expected 并发 append，恰一成功——**watch①** 的真实验证点）。javadoc 标注：**watch①（聚合 JPQL×FOR UPDATE）与 watch②（@Lob OID 大对象需事务）在此轨验证**；若 PG 拒绝聚合加锁，预授权回退=去掉 @Lock 依赖 uk 约束为唯一串行化点（改 SpringDataStoredEventRepository 并记报告）；若 @Lob 报 auto-commit 大对象错，预授权回退=payload 改 `@JdbcTypeCode(SqlTypes.LONGVARCHAR)`（或 columnDefinition="text"）。本地因 Docker 缺席无法验证回退——**不执行回退**，仅轨内代码+注释就绪，CI 首跑定夺。

### D. 4.3 评审并入项（同 commit）
1. JpaEventStore.toStoredEvent 的 `Objects.requireNonNullElse(getPosition(), 0L)` 改 **fail-loud**：`Objects.requireNonNull(entity.getPosition(), "position must not be null in read path (transient entities unsupported)")`；4.3 的 JpaEventStoreTest 手工实体经反射设 position=42（或新增包内可见构造路径），断言改真值（不再 isZero）。
2. JpaEventStoreTest 用例 b 补 correlationId/causationId **非空分支** captor 断言（事件带 respondTo 构造出因果链，断言实体两字段=对应 asString）。

## 门禁
`./mvnw -pl ddd4j-data/ddd4j-data-event-store-jpa -am install` BUILD SUCCESS；模块测试报告精确计数（H2 轨 5 + 既有 3 ArchUnit + 4 Mockito(含改) + PG 轨 skipped 数——surefire 对 disabledWithoutDocker 的呈现如实记录）。**若 H2 轨暴露 watch② 同类问题（H2 CLOB 也可能挑 tx）**：read 路径加 @Transactional(readOnly=true) 为最小修复（预授权）。

## 提交
单 commit：`test(data): JpaEventStore 集成测试——H2 全量契约 + PG 容器 CI 轨（Docker 缺席自动跳过）`

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-4.4-report.md`。Reply ≤15 lines.
