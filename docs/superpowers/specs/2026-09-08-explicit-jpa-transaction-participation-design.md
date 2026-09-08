# JPA EventStore 显式事务参与设计

状态：详细设计已批准；JPA 实现、定向验证及独立最终审查已通过，审查小修已复审关闭。三线整体验收未完成：1.0/2.0 的 Javalin Shiro sample 分别出现 404/HTTP EOF；连接生命周期是待验证假设，不是已确认根因。未提交或发布。

## 1. 已确认的方向与规格边界

- 保留现有构造器的独立事务默认行为。
- 增加显式参与外部事务的入口，不自动侦测后切换事务归属。
- 使用现有 data/runtime 模块，不新增独立 JAR，不建立通用事务管理子系统。
- 三条发布线提供相同公共 API 与行为；仅保留 JDK、javax/jakarta、框架依赖差异。
- 不迁移或修改 cloud-agents，不切换分支、不使用 worktree。

本文件负责新增事务参与能力。三线结构/API 原则沿用
[共同契约规格](2026-09-06-three-line-parity.md)；已有失败与修复证据见
[迁移适配证据](../../architecture/cloud-agents-migration-evidence.md)。

## 2. 当前实现与问题

JpaEventStore 的独立模式在方法内 begin/commit/rollback。当前实现已防止误回滚调用方活动事务，
但安全拒绝不等于支持业务数据与事件原子提交。

2.0/3.0 append 还会 clear EntityManager。参与业务事务时，这会分离调用方正在维护的实体，
不能直接复用整段独立事务包装。默认仓储的位置查询已采用查询级 AUTO flush，以兼容调用方的 COMMIT 模式；
这一修复不提供跨事务位置原子分配。

## 3. 事务所有权

| 行为 | 默认独立模式 | 显式参与模式 |
|---|---|---|
| 必须已有外部事务 | 否；已有活动事务时拒绝 | 是；未激活或未加入时拒绝 |
| begin / commit / rollback | 由 EventStore 管理自己的事务 | 不允许调用 |
| 操作失败 | 回滚自己开启的事务并传播异常 | 标记外部事务只能回滚并传播异常 |
| clear / close EntityManager | 沿用已有独立模式与生命周期契约 | 不允许执行 |
| flush | 可执行 | 可为持久化/查询一致性执行，但不等于提交 |
| 修改 EntityManager 的 flush mode | 不新增修改 | 不允许；查询级设置不改变 EM 配置 |
| 返回成功 | 本次独立事务已提交 | 仅表示操作已加入，最终提交仍由外层决定 |

参与模式的实例可在应用装配时创建；事务状态在每次操作执行时校验，而不是要求装配时已有事务。
输入的 EntityManager 和自定义 repository 必须对应同一事务资源。框架适配器负责使用同一个事务绑定的
EntityManager；不把“处于同一线程”当作资源相同的证明。

## 4. 公共入口设计

保留全部现有构造器，不改变 EventStore 接口。新增命名工厂表达选择，不使用含义不清的公共 boolean 参数：

- `participating(EntityManager)`：参与已开启的 RESOURCE_LOCAL 事务。
- `participating(EntityManager, JpaStoredEventRepository, EventPayloadSerializer)`：同上，保留自定义端口。
- `participatingManaged(EntityManager, Runnable markRollbackOnly)`：参与容器管理事务。
- `participatingManaged(EntityManager, JpaStoredEventRepository, EventPayloadSerializer, Runnable markRollbackOnly)`：
  同上，保留自定义端口。

RESOURCE_LOCAL 路径通过其 EntityTransaction 检查状态和设置 rollback-only，但不 begin/commit/rollback。
Managed 路径通过 EntityManager.isJoinedToTransaction 检查实际加入状态，绝不调用 getTransaction；
回滚标记由调用方提供的非空回调执行。回调必须绑定当前事务，不得使用空实现冒充支持。

这些是新增入口，旧调用方不自动改变语义。无外部事务时参与入口必须失败，不能回退为独立事务。
内部事务策略留在现有 JPA 模块内，不向纯 Java core 引入 Spring 或 JTA API。

参与模式的有效事务要求也适用于空事件批次：基本参数合法后，先验证参与前置条件，再允许空操作返回。
旧独立模式的空批次行为不变。无事务时不得调用回滚标记回调，因为没有可由该回调处置的已确认事务。

## 5. 操作与失败语义

1. 保留基本参数校验；任何持久化操作前确认选定事务模式满足前置条件。
2. 四个读写入口继续通过注入的 repository 和 serializer 执行，不绕过扩展点。
3. 参与模式不清空持久化上下文，调用方受管实体在方法返回后仍受管理。
4. 进入参与执行后，持久化、序列化或映射异常必须传播，并将外部事务标记为 rollback-only。
   外层捕获该异常后也不能提交本次部分写入。
5. 回滚标记自身失败时，原异常仍为主异常，标记失败作为 suppressed 异常保留；不得吞掉任一失败。
   此时只能保证失败被传播，不能声称事务已被标记。框架适配器和外层事务所有者必须中止该操作并放弃提交，
   不能在捕获主异常及标记失败后继续提交。任意业务方吞掉所有异常的情况下，组件不承诺强制阻止外部提交。
6. 事务超时、最终 commit 失败与外层 rollback 由外部事务所有者处理。
   EventStore 不因 append 返回就清空应用未提交事件、发送 MQ 或触发 ACK。
7. RESOURCE_LOCAL 与 Managed 必须分别验证；不能用本地事务测试冒充 JTA 验证。

## 6. 框架落点

| 运行环境 | 装配职责 | 验证重点 |
|---|---|---|
| JPA RESOURCE_LOCAL / Guice / Javalin | 外层开启事务，使用同一个 EM 创建或调用参与实例 | 不嵌套 begin，外层提交/回滚决定所有写入 |
| Spring / Boot | 显式装配参与实例，提供当前 Spring 事务的 rollback-only 操作 | 真实事务代理、同资源绑定、捕获异常后仍不能部分提交 |
| Quarkus / JTA | 使用加入当前 JTA 的 EM 和事务管理器回滚标记 | 不调用 getTransaction，不清空受管实体 |

框架选择仅在显式配置下启用，不把所有既有默认 Bean 切为参与模式。
JTA 适配不等于 XA 支持；本批不承诺多数据库或数据库加 broker 的分布式原子提交。

## 7. 验收矩阵

- 旧构造器的默认追加、读取、回滚、自定义 repository 与活动事务拒绝契约保持通过。
- 参与模式缺少事务时，在任何事件写入前拒绝，不自行创建事务。
- 空批次在参与模式下同样要求已有事务；无事务时回滚标记回调不得被执行。
- 同一事务中写入测试业务实体、事件和测试 Outbox 实体；提交前第二个 EM 看不到数据，外层提交后三者均可见。
- 外层回滚后三类数据均不存在，已有已提交数据不受影响。
- 批次中途失败后，外层即使捕获异常也不能提交部分数据；验证 rollback-only 而不只检查抛错。
- 回滚标记回调失败时，验证原异常身份和 suppressed 异常，同时通过实际框架外层验证事务被中止；
  不以模拟回调执行过作为已经回滚的证明。
- 调用前后 EM flush mode 不变；调用方原受管实体仍被 contains 识别，后续字段更新仍可提交。
- 两次参与调用共享同一外层事务，不隐式开启独立事务。
- 自定义仓储的版本、位置、保存及全部读取结果仍受尊重。
- Managed 模式使用真实 Spring/JTA 组合验证提交与回滚；本地夹具不计入该项。
- JDK8/17/21 执行相同可观察断言，记录真实数据库读回状态、异常与资源状态；不只比较测试名称或 PASS 数。

测试业务/Outbox 实体只属于夹具，不新增生产业务表。它证明 JPA 同资源参与机制，
不代表既有 JDBC Outbox 已自动加入 JPA 事务；实际应用装配需要单独验证。

## 8. 非目标与后续依赖

- 不在本批修改全局 position 分配算法、数据库序列或生产表结构。
- 不将跨事务提交顺序与投影安全游标问题视为已解决。
- 不实现数据库 EventChunkReader、消息持久确认、XA 或自动冲突重试。
- 不修改 cloud-agents 的业务逻辑、既有数据或生产配置。
- 不借该设计处理 Micronaut 偶发 HTTP 断流；该问题仍保留独立证据与诊断。

完整替代目标仍包含以上后续事项，本设计只闭合明确的事务参与能力，不以缩小整体目标宣称完成。

## 9. 实施与验收门禁

详细设计审阅后再生成实施计划。先写能暴露“独立提交/clear/误回滚”的失败测试，
再在三线同步最小实现；随后分别运行本地事务、真实框架事务、PostgreSQL 与受影响回归。
保持版本集中管理，不跳过 Enforcer，不覆盖当前未提交修改，不自动提交、推送或发布。
