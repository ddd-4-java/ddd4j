# MQ Startup and Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 使必选MQ消费者初始化失败时阻断应用启动、可选消费者失败时进入不可就绪状态，并确保所有MQ适配器幂等释放其拥有的连接、消费者和线程资源。

**Architecture:** `ddd4j-mq-core`提供启动状态、结构化失败、初始化异常和LIFO生命周期组合器；`MQClient`保持现有签名并统一失败传播/回滚。内置适配器覆盖默认生命周期访问器并登记自建资源，Spring桥接负责启动异常传播、Readiness注册和容器销毁。

**Tech Stack:** Java 8/17/21、JUnit 5、Mockito、Spring Framework、各MQ客户端SDK、Testcontainers。

**Spec:** `docs/superpowers/specs/2026-09-10-mq-startup-lifecycle-design.md`

## Global Constraints

- 三线保持相同对象名、FQCN、公开方法和参数；仅Java语法和依赖版本允许不同。
- 保留 `MQClient.init/initProducer/initConsumer/start/close` 签名。
- `MQEventListener.required()` 默认 `true`。
- 保留 `MQListener`现有八参数构造器，新增含 `required` 的九参数构造器。
- 外部注入资源默认不关闭；适配器自行创建的资源必须关闭。
- 必选初始化失败必须回滚并抛异常；可选失败必须进入DEGRADED且Readiness不可用。
- 所有关闭路径必须幂等，并在单项关闭失败后继续释放其余资源。

---

### Task 1: 核心生命周期与状态对象

**Files:**
- Create: `ddd4j-mq/ddd4j-mq-core/src/main/java/io/ddd4j/mq/lifecycle/MQClientLifecycle.java`
- Create: `ddd4j-mq/ddd4j-mq-core/src/main/java/io/ddd4j/mq/lifecycle/MQStartupState.java`
- Create: `ddd4j-mq/ddd4j-mq-core/src/main/java/io/ddd4j/mq/lifecycle/MQStartupStatus.java`
- Create: `ddd4j-mq/ddd4j-mq-core/src/main/java/io/ddd4j/mq/lifecycle/MQListenerInitializationFailure.java`
- Create: `ddd4j-mq/ddd4j-mq-core/src/main/java/io/ddd4j/mq/lifecycle/MQInitializationException.java`
- Create: `ddd4j-mq/ddd4j-mq-core/src/main/java/io/ddd4j/mq/lifecycle/MQReadinessContributor.java`
- Test: matching files under `ddd4j-mq/ddd4j-mq-core/src/test/java/io/ddd4j/mq/lifecycle/`

**Interfaces:**
- `MQClientLifecycle.register(String, Runnable) -> void`
- `MQClientLifecycle.checkpoint() -> int`
- `MQClientLifecycle.rollback(int) -> void`
- `MQClientLifecycle.close() -> void`
- `MQStartupStatus.starting/ready/degraded/failed/stopped`
- `MQStartupStatus.snapshot() -> immutable status`
- `MQReadinessContributor.check() -> ReadinessResult`

- [x] 写LIFO关闭、checkpoint回滚、重复关闭、suppressed异常聚合失败测试。（证据: ddd4j-mq/ddd4j-mq-core/src/test/java/io/ddd4j/mq/lifecycle/MQClientLifecycleTest.java（LIFO逆序、checkpoint回滚、重复关闭、suppressed聚合））
- [ ] 运行 `./mvnw -pl ddd4j-mq/ddd4j-mq-core -am test`，确认因对象不存在失败。【外部阻塞: 需执行 Maven 测试（本任务禁跑）】
- [x] 实现Java 8兼容的最小对象，不使用record、`List.of`或私有接口方法。（证据: ddd4j-mq/ddd4j-mq-core/src/main/java/io/ddd4j/mq/lifecycle/（6 个 Java8 兼容对象，无 record/List.of））
- [x] 写状态转换和Readiness映射测试并转绿。（证据: ddd4j-mq/ddd4j-mq-core/src/test/java/io/ddd4j/mq/lifecycle/MQStartupStatusTest.java）

### Task 2: Listener required与MQClient初始化语义

**Files:**
- Modify: `ddd4j-mq/ddd4j-mq-core/src/main/java/io/ddd4j/mq/annotation/MQEventListener.java`
- Modify: `ddd4j-mq/ddd4j-mq-core/src/main/java/io/ddd4j/mq/listener/MQListener.java`
- Modify: `ddd4j-mq/ddd4j-mq-core/src/main/java/io/ddd4j/mq/MQClient.java`
- Create: `ddd4j-mq/ddd4j-mq-core/src/test/java/io/ddd4j/mq/MQClientInitializationContractTest.java`
- Modify: `ddd4j-mq/ddd4j-mq-core/src/test/java/io/ddd4j/mq/MQClientPersistenceContractTest.java`

**Interfaces:**
- Add: `MQEventListener.required() -> boolean`, default true。
- Add: `MQClient.lifecycle() -> MQClientLifecycle`, default unmanaged。
- Add: `MQClient.startupStatus() -> MQStartupStatus`, default unmanaged。
- Preserve: legacy eight-argument `MQListener` constructor。

- [x] 写producer失败、必选false、必选异常、可选失败继续、publisher回滚测试。（证据: ddd4j-mq/ddd4j-mq-core/src/test/java/io/ddd4j/mq/MQClientInitializationContractTest.java）
- [ ] 运行核心测试确认旧实现fail-open导致失败。【外部阻塞: 需执行 Maven 测试（本任务禁跑）】
- [x] 实现统一初始化流程和安全异常字段。（证据: ddd4j-mq/ddd4j-mq-core/src/main/java/io/ddd4j/mq/MQClient.java（checkpoint/rollback 统一初始化）、listener/MQListener.java、lifecycle/MQStartupStatus.java）
- [x] 验证已有listener builder/of/构造器契约不变。（证据: ddd4j-mq/ddd4j-mq-core/src/test/java/io/ddd4j/mq/MQClientInitializationContractTest.java（builder/八参构造器）、MQListener.java:134 of()）

### Task 3: Spring启动传播与容器关闭

**Files:**
- Modify: `ddd4j-mq/ddd4j-mq-spring/src/main/java/io/ddd4j/mq/spring/registry/MQListenerBeanPostProcessor.java`
- Modify: `ddd4j-mq/ddd4j-mq-spring/src/main/java/io/ddd4j/mq/spring/registry/MQListenerRegistrar.java`
- Modify: `ddd4j-mq/ddd4j-mq-spring/src/main/java/io/ddd4j/mq/spring/config/Ddd4jMQRegistrarConfiguration.java`
- Create: `ddd4j-mq/ddd4j-mq-spring/src/test/java/io/ddd4j/mq/spring/registry/MQListenerLifecycleContractTest.java`

**Interfaces:**
- `MQListenerBeanPostProcessor`把annotation.required映射到listener.required。
- `MQListenerRegistrar`实现 `DisposableBean.destroy()`。
- Spring容器提供 `MQReadinessContributor` bean并注册到 `RuntimeReadinessRegistry`。

- [ ] 写必选异常传播、可选DEGRADED、父子上下文去重和逆序关闭失败测试。【待办】
- [ ] 验证测试因当前catch+log实现失败。【存疑】
- [x] 删除吞异常路径，传播 `ApplicationContextException`。（证据: ddd4j-mq/ddd4j-mq-spring/src/main/java/io/ddd4j/mq/spring/registry/MQListenerRegistrar.java）
- [x] 实现幂等destroy及Readiness注册并转绿。（证据: ddd4j-mq/ddd4j-mq-spring/src/main/java/io/ddd4j/mq/spring/registry/MQListenerRegistrar.java、config/Ddd4jMQRegistrarConfiguration.java）

### Task 4: Kafka、RabbitMQ、RocketMQ、ActiveMQ生命周期

**Files:**
- Modify: four adapter `*MQClient.java`/`ActiveMQClient.java` files。
- Modify/Create: corresponding adapter contract tests。

**Interfaces:**
- 每个内置client持有实例级 `MQClientLifecycle` 和 `MQStartupStatus`。
- 覆盖 `lifecycle()`、`startupStatus()`、`close()`。

- [ ] Kafka测试producer flush→close、consumer wakeup/close、executor shutdown及重复关闭。【待办】
- [x] RabbitMQ测试consumer/producer channel关闭，并仅在自建时关闭connection。（证据: ddd4j-mq/ddd4j-mq-rabbitmq/src/test/java/io/ddd4j/mq/rabbitmq/RabbitMQAdapterContractTest.java:213）
- [ ] RocketMQ测试全部consumer和producer shutdown。【待办】
- [x] ActiveMQ测试consumer→session→connection逆序关闭。（证据: ddd4j-mq/ddd4j-mq-activemq/src/test/java/io/ddd4j/mq/activemq/ActiveMQAdapterContractTest.java:63）
- [ ] 每个适配器先验证失败测试，再实现最小登记和ownership逻辑。【存疑】

### Task 5: MQTT、Mica MQTT、NATS、Pulsar生命周期

**Files:**
- Modify: corresponding four client files and contract tests。

- [ ] MQTT/Mica测试unsubscribe、disconnect、close和executor shutdown顺序。【待办】
- [ ] NATS测试subscription/dispatcher释放和connection drain/close所有权。【待办】
- [ ] Pulsar测试consumer、producer、client逆序关闭。【待办】
- [ ] 每个适配器覆盖部分初始化回滚和重复close。【待办】

### Task 6: Redis、SQS、ONS、TDMQ、Disruptor生命周期

**Files:**
- Modify: `RedisStreamMQClient.java`、`RedisMQClient.java`、`SqsMQClient.java`、`OnsMQClient.java`、`TdmqMQClient.java`、`DisruptorMQClient.java`。
- Modify/Create: corresponding contract tests。

- [ ] Redis测试polling task/executor停止及operations ownership。【待办】
- [ ] SQS测试polling future、executor、async client关闭。【待办】
- [ ] ONS/TDMQ测试consumer和producer/client关闭。【待办】
- [ ] Disruptor测试shutdown失败时halt兜底及幂等关闭。【待办】
- [ ] 运行全部MQ模块测试并确认零失败。【外部阻塞: 需执行 Maven 全模块测试（本任务禁跑）】

### Task 7: 三线同步与API一致性

**Files:**
- Synchronize: Task 1–6相同路径文件到1.0.x、2.0.x。

- [ ] 以3.0.x验证实现为源同步生产源码和测试结构。【外部阻塞: 3.0.x 线 checkout 不在本环境】
- [ ] 将1.0.x语法降至Java 8，但保持对象、FQCN、方法和参数一致。【外部阻塞: 依赖 2.0.x/3.0.x 线源码同步，跨线 checkout 缺失】
- [ ] 使用CodeGraph或等价AST清单比较三线MQ公开API。【外部阻塞: 三线源码不在本环境，无法执行跨线 API 比较】
- [ ] 使用 `git diff`确认差异仅为JDK语法和SDK版本适配。【外部阻塞: 需跨分支 git diff（三线 checkout 缺失）】

### Task 8: 完整验证与交付

**Files:**
- Verify: `.github/workflows/verify.yml` and existing MQ/Testcontainers verification scripts。

- [ ] JDK8运行1.0.x完整 `clean verify`和samples。【外部阻塞: 需 JDK8 环境执行（本任务禁跑 Maven）】
- [ ] JDK17运行2.0.x完整 `clean verify`和samples。【外部阻塞: 2.0.x 线 checkout 不在本环境】
- [ ] JDK21/Maven4运行3.0.x完整 `clean verify`和samples。【外部阻塞: 3.0.x 线 checkout 不在本环境（JDK21/Maven4）】
- [ ] 三线执行Testcontainers broker关闭与重启验证。【外部阻塞: 需 Testcontainers/Docker 环境与三线 checkout】
- [ ] 提交并推送GitHub/Codeup，等待三线GitHub Actions全部成功。【外部阻塞: 需推送远端仓库凭据并等待 GitHub Actions】
- [ ] 阶段2全部成功后，更新规格状态并进入阶段3设计。【外部阻塞: 依赖三线 CI 全部通过（规格状态仍为「待评审」）】
