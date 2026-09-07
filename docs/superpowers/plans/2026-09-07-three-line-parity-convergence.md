# 三版本结构、公开 API 与 EventStore 行为收敛 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 本项目禁止 Git worktree，本计划在三个既有独立 checkout 中执行。

**Goal:** 将 1.0.x、2.0.x、3.0.x 的非例外模块树、生产对象、公开方法与 EventStore 可观察行为收敛到共同契约。

**Architecture:** 以公开 API 兼容并集作为二进制契约，以 2.0.x/3.0.x 已存在的同步/异步 EventStore 双对象拓扑作为结构基线。1.0.x 使用 JDK8 等价语法，2.0.x 使用 JDK17/Jackson2，3.0.x 使用 JDK21/Jackson3；不以删除旧 API 达成表面一致。

**Tech Stack:** JDK 8/17/21、Maven、JUnit 5、R2DBC、JDBI、JPA、Spring、Guice、Micrometer、CodeGraph、javap。

**Spec:** `../specs/2026-09-06-three-line-parity.md`

## Global Constraints

- 只允许 JDK 语法、Maven 依赖、Jackson、sample/Quarkus 差异；Helidon 仅按已确认的 JDK8 不支持边界在 1.0.x 删除。
- 非例外目录、包、对象、方法、参数名/类型/顺序、返回类型和可见性必须一致。
- 公开 API 取三线兼容并集，不通过删除已发布方法收敛。
- 同输入的输出、异常、事件、数据库写入、输入对象副作用和事务边界必须一致。
- 所有生产修改先取得能够暴露差异的失败测试或失败门禁，再实施最小修复。
- 不创建 worktree、不切换分支、不覆盖用户修改；本计划不提交、不推送、不发布。

---

### Task 1: 固化当前三线结构与 API 失败门禁

**Files:**
- Create: `scripts/verify-three-line-structure-api-parity.py`
- Modify: `docs/superpowers/plans/2026-09-07-three-line-parity-convergence.md`

**Interfaces:**
- Consumes: 三个 checkout 路径、各自 `.codegraph/codegraph.db`、Maven 编译产物。
- Produces: 非例外模块/源码/FQCN 差异、公开构造器和方法参数名/descriptor 差异、JSON 结果与非零失败退出码。

- [x] **Step 1: 写结构/API 门禁**

  门禁排除 `ddd4j-samples`、Quarkus、Panache，以及 1.0.x 已批准删除的 Helidon；比较 Maven 模块目录、生产 Java 相对路径、CodeGraph 类型/方法声明，并使用 `javap -public -s -v` 补足 record/Lombok 生成 API 与 `MethodParameters`。

- [x] **Step 2: 运行门禁并确认 RED**

  Run: `python3 scripts/verify-three-line-structure-api-parity.py --roots . ../ddd4j-v2.0.x ../ddd4j-v3.0.x --output /tmp/ddd4j-parity-convergence-red`

  Expected: 非零退出；当前至少报告 1.0 缺 `R2dbcAsyncEventStore`、`Ddd4jMybatisGuiceModule`，1/2 缺四个 3.0 生产对象，并报告 1.0/2.0 公开 API 差异。

### Task 2: 收敛六个非例外生产对象

**Files:**
- Create in 1.0.x: `ddd4j-data/ddd4j-data-event-store-r2dbc/src/main/java/io/ddd4j/data/event/store/r2dbc/R2dbcAsyncEventStore.java`
- Modify in 1.0.x: `ddd4j-data/ddd4j-data-event-store-r2dbc/src/main/java/io/ddd4j/data/event/store/r2dbc/R2dbcEventStore.java`
- Create in 1.0.x: `ddd4j-runtime/ddd4j-runtime-guice/src/main/java/io/ddd4j/guice/Ddd4jMybatisGuiceModule.java`
- Modify in 1.0.x: `ddd4j-runtime/ddd4j-runtime-guice/pom.xml`
- Create in 1.0.x and 2.0.x: `ddd4j-mq/ddd4j-mq-core/src/main/java/io/ddd4j/mq/event/DomainEventCarrier.java`
- Create in 1.0.x and 2.0.x: `ddd4j-mq/ddd4j-mq-core/src/main/java/io/ddd4j/mq/event/MqDomainEventPublisher.java`
- Create in 1.0.x and 2.0.x: `ddd4j-runtime/ddd4j-runtime-spring/src/main/java/io/ddd4j/spring/command/SpringCommandBus.java`
- Create in 1.0.x and 2.0.x: `ddd4j-runtime/ddd4j-runtime-spring/src/main/java/io/ddd4j/spring/cqrs/MicrometerProjectionMetrics.java`
- Test: corresponding `R2dbcAsyncEventStoreTest`, `MqDomainEventPublisherTest`, `SpringCommandBusTest`, `MicrometerProjectionMetricsTest`, plus Guice module construction test.

**Interfaces:**
- Consumes: `AsyncEventStore`, `EventStore`, `DomainEventPublisher`, `CommandBus`, `ProjectionMetrics`, R2DBC/Spring/Guice existing module dependencies.
- Produces: 三线相同六个 FQCN、构造器、方法名、参数与返回类型；1.0 仅使用 Java 8 语法等价实现。

- [x] **Step 1: 把现有契约测试同步到缺失分支并确认 RED**

  Run each affected module with its line JDK. Expected: compilation failure naming the absent production type; failures caused by missing objects rather than fixture errors.

- [x] **Step 2: 建立同步/异步 R2DBC 双对象拓扑**

  将 1.0 现有异步实现收敛为 `R2dbcAsyncEventStore implements AsyncEventStore`；`R2dbcEventStore implements EventStore` 作为同步适配器委托异步实现。保留两对象的现有构造器与四方法契约。

- [x] **Step 3: 恢复 Guice MyBatis 模块**

  在 1.0 恢复 `Ddd4jMybatisGuiceModule`，添加 `ddd4j-data-mybatisplus` 依赖；验证 mapper、repository、`SqlSessionFactory` 和 `SqlSession` 绑定。

- [x] **Step 4: 恢复 MQ、Spring CommandBus 与 Micrometer 对象**

  从 3.0 共同实现向 1.0/2.0 回填；1.0 将 pattern matching `instanceof` 改为显式强转，去掉 `java.io.Serial` 注解但保留 `serialVersionUID`。Jackson 注解包按分支保留。

- [x] **Step 5: 运行目标测试并确认 GREEN**

  Run affected module tests under JDK8/17/21; expected zero failures/errors/skips for specified tests.

### Task 3: 收敛 1.0.x 与 2.0.x 公开方法兼容并集

**Files:**
- Modify: Task 1 门禁产生的 `public-api-differences.json` 所列非例外生产对象。
- Test: fresh `javap -public -s -v`/CodeGraph 门禁及各兼容方法受影响的既有模块测试。

**Interfaces:**
- Consumes: CodeGraph source signatures plus `javap` constructors/method descriptors/parameter names。
- Produces: 除允许的依赖命名空间与 JDK 生成差异外，三线公开 API 集合完全相同。

- [x] **Step 1: 生成并按模块冻结完整差异台账**

  每条记录包含模块、FQCN、方法名、参数名/类型/顺序、返回类型、可见性、所在分支和允许原因；无允许原因的记录一律为 RED。

- [x] **Step 2: 为 record/class 兼容并集补公开访问器**

  对 2.0/3.0 record 补 1.0 已发布 bean getter；对 1.0 手写类保留 record-style accessor。构造器、equals/hashCode/toString 由现有值契约继续保护。

- [x] **Step 3: 收敛非 record 方法与构造器**

  对同一 FQCN 使用兼容重载保留旧构造器；JDK8 不存在的 `java.net.http.HttpClient` 通过分支内部适配器实现，不删除共同业务参数。框架版本强制 namespace 差异仅在门禁中显式归一。

- [x] **Step 4: 逐模块 RED/GREEN 验证**

  每批 API 补齐前运行门禁确认具体缺失，补齐后运行该模块测试和门禁；禁止一次性修改后才补测试。

### Task 4: EventStore 同输入差分测试与行为收敛

**Files:**
- Modify: three lines' JDBI/JPA/R2DBC EventStore tests with the same eight observable assertions.
- Create: `scripts/verify-three-line-eventstore-parity.py`
- Modify in all lines as required: JDBI/JPA/R2DBC EventStore implementations and their existing tests.

**Interfaces:**
- Consumes: `EventStore.append/read/readAll`、`AsyncEventStore.append/read/readAll`、固定事件 fixture、H2/JDBI/JPA/R2DBC-H2。
- Produces: 相同输入下相同返回事件、异常类型、事件时间、版本、position、payload、输入事件副作用及事务结果。

- [x] **Step 1: 写同步 EventStore 差分契约并确认 RED**

  使用相同 aggregateType/id、相同事件时间和 metadata，覆盖成功 append/read、陈旧 expectedVersion、`limit <= 0`、版本区间、输入事件 aggregateVersion、未知字段与失败回滚；预期当前 JDBI/JPA 分支输出不一致。

- [x] **Step 2: 写异步 EventStore 差分契约并确认 RED**

  覆盖 Mono/Flux 成功、冲突 error signal、分页、typed ID、因果 ID、空事件流和失败回滚；三线使用各自真实 R2DBC-H2 实现。

- [x] **Step 3: 统一共同行为**

  以不修改调用者输入事件、持久化事件自身 timestamp、`limit <= 0` 立即 `IllegalArgumentException`/error signal、版本从 `expectedVersion + 1`、冲突整体回滚为共同契约；Jackson 仅做等价序列化适配。

- [x] **Step 4: 运行差分探针和模块回归并确认 GREEN**

  三条线必须执行同一组具体字段/副作用断言，且本次新生成报告中的结果向量相同；JDBI/JPA/R2DBC 目标测试均为零失败/错误/跳过。

### Task 5: 全量收敛门禁

**Files:**
- Modify: this plan checkbox state and parity result document.
- Create: `docs/superpowers/plans/2026-09-07-three-line-parity-convergence-results.md`

**Interfaces:**
- Consumes: Tasks 1-4 outputs.
- Produces: 当前 SHA、结构/API/行为结果、测试数、未完成项和允许差异台账。

- [x] **Step 1: 刷新三个 CodeGraph 索引并验证内容哈希**
- [x] **Step 2: 运行结构/API 与 EventStore 差分门禁**
- [x] **Step 3: 运行三条线受影响模块测试与全量 reactor 测试**
- [x] **Step 4: 运行三条线 `ddd4j-samples` 全测试**
- [x] **Step 5: 执行 `git diff --check`、工作区与 SHA 审计，记录未提交文件**
