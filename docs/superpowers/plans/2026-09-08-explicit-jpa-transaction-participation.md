# JPA 显式事务参与 Implementation Plan

状态：Task 1–5 实施与定向验证已完成；Task 5 独立最终审查待主控执行。三线最终完整回归中 1.0.x/2.0.x 被 Javalin Shiro sample 的 HTTP 生命周期失败阻塞，3.0.x 通过；整体全绿门禁未完成。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 禁止 worktree、分支切换和自动提交；在现有三个 checkout 中顺序实施。

**Goal:** 在保持现有独立事务 API 的同时，让业务显式选择 JPA 外部事务参与，并以真实事务验证提交、回滚及上下文保护。

**Architecture:** 事务选择保持在现有 JpaEventStore 内部，不新增公共事务管理 SPI 或 JAR。RESOURCE_LOCAL 与 Managed 使用不同命名工厂；四种读写共用参与策略，但保留各线既有仓储及独立事务行为。

**Tech Stack:** JDK8/17/21、Maven3/4、javax/jakarta JPA、Hibernate、JUnit、H2、PostgreSQL、Spring 事务、Quarkus JTA。

**Spec:** `../specs/2026-09-08-explicit-jpa-transaction-participation-design.md`（已批准，唯一事实源）。

## Global Constraints

- 保留现有构造器的独立事务默认行为。
- 增加显式参与外部事务的入口，不自动侦测后切换事务归属。
- 使用现有 data/runtime 模块，不新增独立 JAR，不建立通用事务管理子系统。
- 三条发布线提供相同公共 API 与行为；仅保留 JDK、javax/jakarta、框架依赖差异。
- 不迁移或修改 cloud-agents，不切换分支、不使用 worktree。
- 不修改全局 position 算法、生产数据库结构、消息确认策略或 Micronaut 断流相关代码。
- 版本只能在各线 ddd4j-dependencies/pom.xml 定义；具体模块引用管理项或属性，description 保持一行。
- 不跳过 Enforcer；未运行或 skipped 的测试不得标为通过。
- 当前工作区已有大量未提交修复，必须保留。仅修改任务分配文件，不提交、推送、发布或清理其他任务文件。
- 三个根为 `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j`、同级 `ddd4j-v2.0.x`、`ddd4j-v3.0.x`。
- 下面的相对路径均在三个根分别应用，除明确限定的 Quarkus 测试。

## 验证命令约定

1.0 用 `JAVA_HOME=$(/usr/libexec/java_home -v 1.8) mvn`；2.0 改为 JDK17；3.0 改为 JDK21 和 `./mvnw`。
每轮日志写入不同 `/tmp/ddd4j-tx-*` 文件，记录退出码、测试数量、失败原因，禁止只检查最后一行。

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 1.8) mvn -B -ntp -Pparity-verification \
  -pl ddd4j-data/ddd4j-data-event-store-jpa -am \
  -Dtest=JpaEventStoreTest -Dsurefire.failIfNoSpecifiedTests=false test
```

### Task 1: RESOURCE_LOCAL 显式参与入口

**Execution context:** 规格位于本计划同级 `../specs/2026-09-08-explicit-jpa-transaction-participation-design.md`，用户已批准。
工作根为 `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j` 及同级 `ddd4j-v2.0.x`、`ddd4j-v3.0.x`；
分别使用 JDK8 / Maven、JDK17 / Maven、JDK21 / ./mvnw。不得切换分支、创建 worktree、提交、推送或覆盖已有修改。
仅拥有本任务列出的三线文件；所有新增 null 判断使用导入的 Objects，日志如需要使用 SLF4J，不新增 provider。

**Files:**
- Modify: `ddd4j-data/ddd4j-data-event-store-jpa/src/main/java/io/ddd4j/data/event/store/jpa/JpaEventStore.java`
- Test: `ddd4j-data/ddd4j-data-event-store-jpa/src/test/java/io/ddd4j/data/event/store/jpa/JpaEventStoreTest.java`

**Interfaces:**
- Consumes: 已有三个 JpaEventStore 构造器、四个 EventStore 读写方法、JpaStoredEventRepository、EventPayloadSerializer。
- Produces: `public static JpaEventStore participating(EntityManager entityManager)` 和
  `public static JpaEventStore participating(EntityManager entityManager, JpaStoredEventRepository repository, EventPayloadSerializer serializer)`。
- 本任务仅实现 RESOURCE_LOCAL；Managed 工厂由 Task 3 实施，不写空实现。

- [x] **Step 1: 在已有真实 H2/JPA 夹具增加参与测试。** 使用当前测试内的 TestAggregateRootId/OrderCreatedEvent，无需新模块。

```java
@Test
void participatingAppendMustLeaveCommitToCaller() {
    JpaEventStore participating = JpaEventStore.participating(entityManager);
    TestAggregateRootId id = new TestAggregateRootId("external-commit");
    EntityTransaction tx = entityManager.getTransaction();
    tx.begin();
    participating.append(ORDER_TYPE, id,
            Collections.singletonList(new OrderCreatedEvent("pending")), 0);
    assertTrue(tx.isActive());
    assertEquals(1, participating.read(ORDER_TYPE, id).size());
    tx.rollback();
    entityManager.clear();
    assertTrue(eventStore.read(ORDER_TYPE, id).isEmpty());
}
```

再覆盖：无事务的四个入口拒绝；空批次也拒绝；两次 append 不结束外层事务；外层 commit 后可读；批次中途 null 事件失败后 `tx.getRollbackOnly()` 为 true；COMMIT flush mode 保持；默认构造器原 15 项测试不变。

- [x] **Step 2: 运行定向测试取得 RED。** 预期首先缺少 participating 工厂；记录真实编译错误，再逐个行为断言验证实现。
- [x] **Step 3: 最小实现。** 增加私有模式状态和私有构造路径；旧构造器固定独立模式。参与执行先检查当前 `EntityTransaction.isActive()`，不 begin/commit/rollback，不 clear/close EM。

参与策略的核心控制流（放入 JpaEventStore 私有方法，不新增公共控制器）：

```java
if (!tx.isActive()) {
    throw new IllegalStateException("An active caller transaction is required");
}
try {
    return operation.get();
} catch (RuntimeException | Error failure) {
    try {
        tx.setRollbackOnly();
    } catch (RuntimeException | Error markingFailure) {
        if (markingFailure != failure) {
            failure.addSuppressed(markingFailure);
        }
    }
    throw failure;
}
```

`operation` 为内部 `Supplier<T>`；保留原业务体的 repository 委托、序列化和查询级 AUTO flush。独立模式的事务/clear 规则保持原样，不能把外层事务逻辑复制成四份后产生行为漂移。异常标记代码可在类内私有复用。基本参数校验后，参与模式先验证事务，再处理空批次。

- [x] **Step 4: 三线运行 JpaEventStoreTest，确认 GREEN。** 不以 1.0 通过替代其他线。保留旧构造器、自定义仓储和调用方事务保护测试。
- [x] **Step 5: 独立任务审查并记录结果。** 不提交现有工作区；仅在实现与审查完成后勾选本任务。

### Task 2: 同资源业务/事件/Outbox 原子性

**Files:**
- Modify/Test: `ddd4j-data/ddd4j-data-event-store-jpa/src/test/java/io/ddd4j/data/event/store/jpa/JpaEventStorePostgresIT.java`
- Create: `ddd4j-data/ddd4j-data-event-store-jpa/src/test/java/io/ddd4j/data/event/store/jpa/fixture/TransactionBusinessEntity.java`
- Create: `ddd4j-data/ddd4j-data-event-store-jpa/src/test/java/io/ddd4j/data/event/store/jpa/fixture/TransactionOutboxEntity.java`

**Interfaces:**
- Consumes: Task 1 的 participating 工厂和已有真实 PostgreSQL/EntityManagerFactory 夹具。
- Produces: 同一连接资源下提交、回滚、第二 EM 可见性与受管实体保护的运行证据。

- [x] **Step 1: 增加仅测试实体。** 两个文件分别用 `@Entity`、`@Table(name="tx_test_business")` / `tx_test_outbox`、`@Id String id`、`@Column(name="payload_value") String value`，使用 Lombok `@Getter @Setter @NoArgsConstructor`；增加 `(String id, String value)` 构造器。1.0 使用 javax，其他线使用 jakarta。只注册到测试 Hibernate Configuration，不改变生产迁移。扩展该测试的清理逻辑，同时清理两张夹具表和事件表，确保独立重复执行。
- [x] **Step 2: 加原子性用例并运行。** 事务内 persist 两个实体及 append 事件，在 READ_COMMITTED 下第二 EM 查询三类数据均不可见；外层 commit 后换新 EM 均可见。用独立 id 避免用例互相污染。

```java
EntityTransaction tx = entityManager.getTransaction();
tx.begin();
TransactionBusinessEntity business = new TransactionBusinessEntity("b-commit", "before");
entityManager.persist(business);
entityManager.persist(new TransactionOutboxEntity("o-commit", "pending"));
JpaEventStore participating = JpaEventStore.participating(entityManager);
participating.append("Order", aggregateId, events, 0);
assertTrue(entityManager.contains(business));
business.setValue("after");
tx.commit();
```

`aggregateId` 和 `events` 使用该测试现有 TestAggregateRootId / OrderCreatedEvent 构造；读回需断言 `after`，不得只断言 contains。另一用例 rollback 后三类数据为空，保留预先提交的对照行。失败后调用方捕获异常，再尝试 commit 必须未实际提交：允许 provider 返回或抛 RollbackException，但事务必须结束，第二 EM 证实三类目标数据为空且已提交对照保留。记录各 provider 的 API 结果，不将正常返回误判为提交成功。
- [x] **Step 3: 三线执行 `-Dtest=JpaEventStorePostgresIT`。** 要求 Docker 真实可用、零 skipped；已通过则不改生产代码凑 RED，记录这是新增覆盖而非新修复。
- [x] **Step 4: 审查提交前可见性、清理及失败路径。** 确认并未借测试把 JDBC/JPA 跨资源组合算作已支持。

### Task 3: Managed 入口与回滚标记失败

**Files:**
- Modify: `ddd4j-data/ddd4j-data-event-store-jpa/src/main/java/io/ddd4j/data/event/store/jpa/JpaEventStore.java`
- Create/Test: `ddd4j-data/ddd4j-data-event-store-jpa/src/test/java/io/ddd4j/data/event/store/jpa/JpaManagedParticipationTest.java`

**Interfaces:**
- Produces: `participatingManaged(EntityManager, Runnable)` 与
  `participatingManaged(EntityManager, JpaStoredEventRepository, EventPayloadSerializer, Runnable)`，返回 JpaEventStore。
- Consumes: Task 1 的参与执行分支；Managed 以 `isJoinedToTransaction()` 为入口检查，绝不调用 getTransaction。

- [x] **Step 1: 先测未加入事务和非法调用路径。** Managed 测试使用最小 EM/repository 测试替身暴露禁止的 getTransaction、clear、close 调用，不将替身作为 JTA 成功证据。
- [x] **Step 2: 校验原始失败与 suppressed 标记失败。** 捕获异常来源为 repository/serializer，不检查 mock 是否存在；断言原异常对象和 suppressed 内容。

```java
RuntimeException original = new IllegalStateException("repository failure");
RuntimeException markerFailure = new IllegalStateException("marker failure");
Runnable marker = () -> { throw markerFailure; };
```

让 repository 抛出 `original`，调用 Managed store，断言 `assertSame(original, failure)` 和 `assertSame(markerFailure, failure.getSuppressed()[0])`。另测未加入事务时 marker 不调用，空批次同样拒绝，factory 的 null 参数在访问 EM 前拒绝。
- [x] **Step 3: 实现 Managed 私有策略。** 校验状态失败不标记不存在的事务；开始执行后异常调用 marker，复用主异常/suppressed 保护。构造时不访问事务状态，允许应用启动期创建实例。
- [x] **Step 4: 三线运行 `-Dtest=JpaEventStoreTest,JpaManagedParticipationTest` 并审查。** 公共签名不能省略任一自定义 repository/serializer 入口。

### Task 4: 真实 Spring 和 Quarkus 事务组合

**Files:**
- Create/Test: 三线 `ddd4j-data/ddd4j-data-event-store-jpa/src/test/java/io/ddd4j/data/event/store/jpa/JpaSpringParticipationIT.java`
- Modify: 三线 `ddd4j-data/ddd4j-data-event-store-jpa/pom.xml`（只增加需要的 test 依赖）
- Create/Test: 2.0/3.0 `ddd4j-data/ddd4j-data-event-store-panache/src/test/java/io/ddd4j/data/event/store/panache/JpaManagedParticipationIT.java`
- Modify: 2.0/3.0 `ddd4j-data/ddd4j-data-event-store-panache/pom.xml`（test 依赖 JPA EventStore；3.0 如父链覆盖 Quarkus ORM，补与 2.0 相同的 `${quarkus.version}` 平台 BOM import，并验证解析前后和既有 Panache 测试）

**Interfaces:**
- Consumes: 全部四个参与工厂，现有 Hibernate/JPA 夹具及 Quarkus test profile。
- Produces: Spring 事务绑定 EM 及真实 JTA 内的参与模式运行证据，不新增默认自动装配 Bean。

- [x] **Step 1: Spring RED/GREEN。** 使用真实 JpaTransactionManager、TransactionTemplate 和 SharedEntityManagerCreator 的 EM 代理。callback 绑定本次 TransactionStatus.setRollbackOnly，不捕获过期或其他线程状态。

```java
template.execute(status -> {
    JpaEventStore store = JpaEventStore.participatingManaged(sharedEntityManager, status::setRollbackOnly);
    store.append("Order", aggregateId, events, 0);
    status.setRollbackOnly();
    return null;
});
```

退出模板后用新 EM 验证无事件；正常提交对照可见；异常被业务捕获时仍 rollback-only；查询级 flush 不清空业务实体。测试依赖使用各线集中管理的 Spring/Hibernate 版本。
- [x] **Step 2: Quarkus RED/GREEN。** 在已有 Panache 测试模块内用 `@QuarkusTest`、注入 EM 和 JTA TransactionManager；在 `QuarkusTransaction.requiringNew().run` 内创建 Managed store，回调调用当前 TransactionManager.setRollbackOnly（受检异常转换为 IllegalStateException 并保留 cause）。测试成功提交、外层回滚及失败后的 rollback-only；真正查询数据库，不以回调次数代替。
- [x] **Step 3: 保持 Quarkus ORM 由其 BOM 管理。** JPA 模块的 test Hibernate 不传播给 Panache。采用随机测试端口。1.0 无 Quarkus，不新建虚假 Quarkus 适配；Spring Managed 运行证据与 2.0/3.0 JTA 证据分别列出。

3.0 如出现实际 Maven 4 WorkspaceLoader 模型解析失败，在拥有的 Panache POM 复用同线 projection-panache 已有的 Quarkus `generate-code` / `generate-code-tests` 与 `maven.home` 引导配置。记录原失败及配置来源，不降级 Maven、不跳过门禁；结合原有模块测试验证，不新增生产 API。
- [x] **Step 4: 检查回滚标记失败的外层中止。** 测试框架外层接收到主异常后实际 rollback，并由新 EM 确认无部分提交；不承诺能控制任意吞异常的业务代码。
- [x] **Step 5: 审查 Spring/Quarkus 真实事务结果。** 不将任何源码注解或 mock 用例计作运行证据。

### Task 5: 三线差分、回归与交付

**Files:**
- Modify: `scripts/verify-three-line-eventstore-parity.py`
- Modify: `docs/architecture/cloud-agents-migration-evidence.md`
- Modify: 本计划状态；规格只更新实施状态，不修改已批准验收边界。
- Modify: 三线 `ddd4j-data/ddd4j-data-event-store-jpa/pom.xml`（保留既有/default Surefire 命名规则并收集 `**/*IT.java`，修复默认 test 漏跑 JPA 集成测试）。

**Interfaces:**
- Consumes: Tasks 1–4 的工厂、真实读回断言与日志。
- Produces: 当前三线参数/异常/状态证据和剩余边界，不宣称整体替代目标自动完成。

- [x] **Step 1: 扩展共同断言选择。** 纳入三线同名 RESOURCE_LOCAL 参与测试，检查报告后缀/时间以及 missing/skipped，保留现有 nine-assertion 覆盖。
- [x] **Step 2: 刷新已有 CodeGraph 索引并执行严格新鲜编译门禁。**

```bash
python3 scripts/test_structure_api_normalization.py
python3 scripts/verify-three-line-structure-api-parity.py --compile \
  --roots . ../ddd4j-v2.0.x ../ddd4j-v3.0.x --output /tmp/ddd4j-tx-api
```

- [x] **Step 3: 按线执行完整 `-Pparity-verification test`（含 samples）。** 报告命令退出码、真实测试数、跳过项，区分任何既有 Micronaut HTTP 失败，不加重试或禁用以掩盖。

必须核对默认收集清单，不能仅检查 skipped=0：JPA 至少包含 `JpaEventStoreTest`、`JpaManagedParticipationTest`、`JpaEventStorePostgresIT`、`JpaSpringParticipationIT`。首次未收集 IT 的成功记录保留为范围不完整；修正规则后先执行无 `-Dtest` 的默认 JPA 测试，再以新报告后缀运行最终完整 reactor。
- [ ] **Step 4: 最终独立审查及 git diff --check。** 只审查本次增量，保留基线未提交改动；修复重要问题后执行相应回归。

独立源码审查与 M1/M2 定点修复复审已通过；三线 diff 检查通过。此项保留未勾选，因为整体回归 Important I1 尚未关闭：1.0 Shiro 404、2.0 Shiro EOF；不得把局部源码通过当成整体放行。见 `.superpowers/sdd/2026-09-08-explicit-jpa-transaction-participation/final-review.md` 和 `final-fix-review.md`。
- [x] **Step 5: 汇总规格达成情况。** 明确未实现 XA、位置原子分配、reader、消息确认和 cloud 迁移；未经后续发布授权不提交/推送/deploy。
