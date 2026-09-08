# cloud-agents 迁移适配证据清单

本清单记录源码观察与待验证契约，不代表完成迁移或生产验收。旧工程保持不变。

## 架构师阶段结论

新 ddd4j 已具备可复用的领域/命令/事件契约和多框架适配基础，但目前不能作为旧框架的无条件替换件。当前最大风险不在目录数量，而在组件之间的组合语义：事务归属、消息确认、租户状态来源、异常响应及自动装配。模块存在、签名一致、默认路径测试通过，都可能与这些组合语义缺失同时成立。

应保留的设计：框架中立 core SPI；可替换存储/reader/provider；Web 公共契约；显式事务/投递状态；真实数据库和 broker 测试夹具。近期修复应继续沿用这些端口，而非为每个框架复制一套业务实现。

必须闭合的替代门禁：

1. **业务事务边界**：同一业务操作的数据与事件/Outbox 原子提交；失败不得误提交、误回滚他人事务或提前 ACK。当前安全拒绝外部事务只是保护措施，不是事务组合能力。
2. **可靠投递边界**：发送异常传播已经改善，但需 broker 持久确认、不可路由失败、确认后本地标记失败的重复投递和幂等副作用验证。
3. **投影读取边界**：业务 reader 已能接入 Guice 默认 Runner，但真实存储 reader、提交可见位置与读模型/游标同事务仍未闭合。
4. **应用兼容边界**：从 cloud-agents 的实际异常码、鉴权注解、租户键及异步入口提取相同输入，分别验证 Boot/Javalin/Quarkus，不机械替换 import。
5. **装配和版本边界**：cloud 的旧坐标与自动配置包名仍有问题；不同框架检出不能拼作同一版本线的验证；私服旧成功产物也不代表当前工作区已发布。

实施顺序应先关闭以上组合路径中可复现的缺陷，再扩展适配能力，最后汇总全量/差分/真实基础设施证据并提交发布。不要通过增加空默认实现、扩大例外白名单、跳过测试或强行统一不同框架的 ORM 版本来获得表面绿色。本阶段没有迁移 cloud-agents，也没有对外部真实业务数据执行变更。

## 已核验的调用点与差异

## 架构审查覆盖与优先级

| 层次 | 已见优点 | 当前不足或未证实项 | 下一项验证 |
|---|---|---|---|
| annotation | 独立注解模块，DDD 元注解可运行时发现；1.0 无用 Spring 依赖已移除并通过回归 | 外部应用动态扫描及旧注解语义兼容仍未穷尽 | 按旧应用使用的注解逐项验证运行时解释 |
| core | 仓储/事件/命令 SPI；ThreadContext 快照作用域；SPI 注册按安装身份恢复 | 全局注册并发隔离待测；R 旧业务码差异；failed(data) 已局部修复 | 多容器启停与真实消费者 JSON 差分 |
| web | 共享请求/异常/幂等契约与多框架适配 | 旧应用异常策略和内部凭据规则不同 | 三框架同输入 HTTP 契约 |
| bom/parent | 模块版本管理与构建约定分层；三线 BOM 已补齐本轮发现的坐标并通过外部模型验证 | 外部企业 parent 的实际版本优先级、下载及运行尚未全部证明；历史凭据需轮换 | 保留企业 parent 的真实消费者构建与启动 |
| runtime/集成 | Spring 桥接、Guice 装配、Quarkus CDI/Quartz 入口 | Quarkus 两套 ViewManager 的选择；模板空方法；投影事务边界 | 组合启动和数据库回滚 |
| MQ/数据 | Inbox/Outbox 端口、稳定消息身份、租约与重试结构；Rabbit 发送失败传播已修复并通过真实容器测试 | broker confirm/持久性；事务和 broker ACK 的接线 | broker 故障与提交失败注入 |
| 框架与 cloud | 已有可复用 Runtime、starter 与示例 | 内存示例不能证明生产事务；cloud 响应兼容仍不完整 | 保留企业 parent 的三框架消费者夹具 |

此表是已读源码的阶段性结论，不表示列中各模块已全文审查。未覆盖的扩展、cloud 功能及 fuin 组织其他仓库仍保持待审计。

### 当前验收状态（以最新具体验证为准）

- ddd4j 1.0：当前包含 samples 的完整 Reactor 1694 项通过、零失败/错误/跳过（`/tmp/ddd4j-reviewed-final-tests-1.log`，04:25），覆盖参数元数据、JPA COMMIT flush 与 Guice 修复。
- ddd4j 3.0：此前完整 Reactor 2197 项通过、零失败/错误/跳过（`/tmp/ddd4j-all-clients-full-3.log`）；之后新增 JPA 与 Guice 修复，需新组合回归。
- ddd4j 2.0：此前完整 Reactor 2082 项通过、零失败/错误/跳过（`/tmp/ddd4j-all-clients-full-2.log`）；之后新增 JPA 与 Guice 修复，需新组合回归。
- 当前组合回归已全部结束：3.0 为 2204 项通过、零失败/错误/跳过（`/tmp/ddd4j-reviewed-final-tests-3.log`，07:22）；2.0 在 Micronaut Web contract 错误响应请求中出现 HTTP 响应头读取失败（`/tmp/ddd4j-reviewed-final-tests-2.log`，07:47），整体仍失败。此前 JPA COMMIT flush 限定复审已通过，但不是全项目合并批准。
- 结构/API：保留类型身份的新鲜编译门禁曾通过；后续 JPA 方法体及 Guice 公共重载修改需要重新同步并验证，不能沿用旧通过结果覆盖新改动。
- 外部 Boot 4.1：monitor 配置不再依赖 Logback，三个装配测试通过；成功/失败通知仍未自动接通。
- 外部 Javalin 6.7：移除过期重复类，类来源唯一性与真实 MySQL CRUD 通过；事务及并发 SqlSession 语义仍待验证。
- 外部 Quarkus 与 cloud：存在尚未闭合的投影接线、事务和构建兼容缺口，不能报告组合可替代旧框架。
- 发布状态：以上工作树修改未提交、未重新发布；更早的 deploy 成功不覆盖当前修改。

全文后续段落保留按时间推进的失败与修复记录；其中“下一步”“仍运行”等历史措辞须结合本摘要和最新日志判断，不作为当前进程存活的依据。

### 结构/API 门禁的证明范围复核

现有 verify-three-line-structure-api-parity.py 会纳入 Git 跟踪与未忽略的未跟踪文件，但 modules 比较的是 pom.xml 父目录集合，不是 Maven 有效模块树；目录中有 POM 不等于已参加 Reactor。它排除 samples、Quarkus、Panache、Helidon，且 normalize_descriptor 将 Jackson 包下所有对象描述符折叠为 `LJACKSON;`，这不仅归一包迁移，也会掩盖 ObjectMapper/JsonNode 等不同类型之间的差异。CodeGraph 方法签名也未在该函数校验索引内容哈希。

因此历史结构/API 门禁结果只能在这些限制内解释，不能单独证明“所有对象、方法、参数完全一致”。最终需核对有效 Reactor 树、保留类型身份的 Jackson 映射、源码与索引新鲜度，并对允许差异逐项给出理由；方法体行为继续依赖同输入差分和源码审查。此次仅审计工具覆盖，不在测试运行中重新编译或改动门禁结论。

校验器修复进展：新增 scripts/test_structure_api_normalization.py，四项中三项先失败，实际证明旧算法掩盖了 Jackson 类型差异、参数顺序及数组/返回类型身份。normalize_descriptor 已改为只将 com/fasterxml/jackson 与 tools/jackson 包前缀归一，保留后续类型路径；四项测试通过。该修复使后续门禁更严格，没有扩大允许差异。尚未用新算法重跑完整三线比对，历史绿色结果不能自动升级为新算法通过；有效 Reactor 与索引新鲜度问题也尚未解决。

后续已同步三个现有 CodeGraph 索引，在 2.0/3.0 全 Reactor 结束后重跑严格比对，结果 `/tmp/ddd4j-strict-api-current/results.json` passed=false。非例外 POM 目录均 86、生产源码路径均 808 且集合相同；1.0/2.0 javap API 原始差集 3330/3339，抽查显示 1.0 的参数名为空而 2.0 存在真实名称（BusinessType.setDesc 等），不能解读为数千个方法逻辑差异。2.0/3.0 仅两个原始 API 差异，集中于 Micronaut 泛型桥接方法的无名参数输出（空与 `<no name>` 被解析为 `<no`）；仍需核对编译设置及 generated/bridge 解析，不直接加入宽泛白名单。新的严格结果替代历史通过结论，尚未证明当前三线公共 API 完全一致。

已确认并修复参数元数据配置：1.0 根 compiler 缺 parameters=true，2.0/3.0 已有。补齐后 JDK8 clean compile 成功（`/tmp/ddd4j-line1-parameter-metadata-compile.log`），新的严格报告 `/tmp/ddd4j-strict-api-parameters/results.json` 中 1.0/2.0 JVM API 原始差集降为 7/16。当前非允许 JVM 差异仅见高 JDK 的 FlksecCryptoStrategy(ObjectMapper,HttpClient,address,port) 构造器（JDK8 不含 java.net.http.HttpClient），需要检查原先明确的 JDK 例外与新 Jackson 类型归一是否匹配；2.0/3.0 仍有无名桥接参数解析差异。CodeGraph 源签名差异另待分类，不能用编译元数据修复宣称它们自动解决。参数配置变更后的完整测试尚未重跑，此前 1.0 1687 项是修改前测试证据。

无名参数解析已由失败测试驱动修复：javap 的 `<no name>` 归一为空名，不再错误读取成 `<no`，真实参数名保持不变；六项校验器测试通过。Flksec 既有 JDK 专属构造器规则同步使用精确 `LJACKSON/databind/ObjectMapper;`，没有扩大到任意 Jackson 类型。报告 `/tmp/ddd4j-strict-api-names/results.json` 显示 2.0/3.0 JVM API 原始差异为零，所有 pairs 的 classes/JVM API/CodeGraph 非允许差异均为空。

但顶层 passed 仍为 false：脚本 `failed = compile_failed or not args.compile` 明确要求本次调用执行 --compile。这是之前摘要未充分说明的已有编译新鲜度门禁；本次已有产物比对不能代替完整门禁通过，不应删除该限制。下一步须用 --compile 完成同一流程内的三线编译与比对。索引内容哈希及有效 Reactor 树证明仍需单独补齐。

完整新鲜编译门禁现已执行通过：移除编译器脚本残留的 `-Denforcer.skip=true` 与强制 `-Dmaven.compiler.parameters=true`，使用各线 POM 原配置执行 clean compile；六项工具回归通过。`/tmp/ddd4j-strict-api-fresh/results.json` passed=true，三次编译均成功，非例外目录/源码集合一致，分类后的公共 JVM API 与共同 CodeGraph 显式方法无违规差异。1.0 私有辅助类及 JDK 专属构造器等原始差异仍列于报告中，没有删除差异清单。此门禁证明范围仍不含有效 Reactor 树和全部方法体行为。

另直接比对 CodeGraph files.content_hash 与当前 SHA-256：1.0/2.0/3.0 已索引生产 Java 文件分别 1004/1245/1246 个，未发现哈希不匹配。此处包括 sample 等索引文件，计数不同于非例外 808 源码路径；没有检查未被索引的文件集合，因此仅证明已索引内容新鲜。完整编译不执行测试，不能将该门禁当成参数配置更新后的测试回归。

有效 Reactor 树验证进展：三线全 Reactor help:effective-pom 一次展开均发生 OutOfMemoryError，此操作没有得到完整有效树，也不表示源码编译失败。改为 `-N -Pparity-verification help:effective-pom` 后三线根模型均成功，输出 `/tmp/ddd4j-effective-root-1.xml` 至 `-3.xml`。XML 解析显示三线根聚合模块集合一致，均 16 个并包含 samples；声明顺序不同（metrics、samples 位置），不能声称有序 XML 完全相同。子聚合器尚未逐个展开，因此本次仅证明根有效模块集合相同，完整父子树仍待验证。输出可能包含继承配置，不作为可公开发布的文档附件。

后续根及八个子聚合器（ddd-rules、data、mq、web、auth、runtime、extensions、samples）的有效模型均已逐个生成成功，并解析 modules/module 与 subprojects/subproject 为父子边。当前 parity-verification 配置下，1.0 共 102 条聚合边，2.0/3.0 各 120 条；2.0 与 3.0 边集合完全一致。1.0 少 18 条：10 条 Quarkus/Panache/Helidon 运行时、数据与 Web 适配边，以及 8 条 Helidon/Micronaut/Quarkus sample 边。逐项检查均属于已有允许例外，非例外父子结构差异为空。声明顺序未强制一致，不扩展此结论至未激活 profile 或另一组构建环境。

证据文件为 `/tmp/ddd4j-effective-{1,2,3}-ddd4j-{ddd-rules,data,mq,web,auth,runtime,extensions,samples}.xml` 与三个根模型。上述统计是实际有效模型解析，不是根据目录名推断。后续应将此有效边比较纳入自动门禁，当前严格 API 脚本自身的 modules 项仍是 POM 目录集合。

### EventStore 行为门禁重新验证

移除 verify-three-line-eventstore-parity.py 中残留的 Enforcer skip，三线分别运行八项固定断言，均 exit=0、observed=8、missing=0、invalid_reports=0；新报告 `/tmp/ddd4j-eventstore-current-guarded/results.json` passed=true。报告采用唯一 surefire 后缀和时间检查，未消费旧测试报告。

该脚本覆盖：JDBI/JPA append 不修改输入事件 aggregateVersion、readAll 的零 limit 拒绝、持久化 timestamp 使用固定输入事件时间，以及 R2DBC 的后两项行为。输出向量是断言 PASS/FAIL，不是完整事件序列/数据库状态的序列化差分；不能据此宣称所有 EventStore 输出、冲突、分页、事务回滚和异步取消一致。后续补强应优先用真实存储读回值与失败后的状态向量，而不是再增加只按测试名比较的“通过”计数。

已补充三线同输入的 JPA 批次失败状态回归：先提交一条事件，下一批次包含正常事件与 null，在批次中途抛错；清空 EntityManager 缓存后读回仅剩已提交事件，并验证随后从原版本追加成功、版本连续为 2。该测试使用真实 H2/JPA，不模拟 EntityManager；三线 JpaEventStoreTest 各 10 项通过。现有实现满足此场景，生产代码未修改。

新增断言已纳入共同门禁，`/tmp/ddd4j-eventstore-rollback-guarded/results.json` 三线各 observed=9、missing=0、invalid_reports=0，passed=true。范围是事件处理异常的批次回滚，不是数据库连接中断或 commit 失败；也未验证外部业务事务与 EventStore 同事务。仍保留上述完整输出差分限制，不因新增一项回滚测试扩大结论。

外部事务所有权缺陷已实测：调用方先开启 EntityTransaction，再调用 append，内部 begin 失败后的 catch 会 rollback 已有调用方事务；新增测试在 1.0 失败，事务 isActive 从 true 变 false（`/tmp/ddd4j-jpa-transaction-owner-red.log`）。1.0 的读取路径也把 begin 包在 rollback catch 内；2.0/3.0 读取 begin 在 try 外，原本不会走此误回滚路径。

三线统一在四个非空读写入口检测已有事务，在进入内部事务处理前明确拒绝，保留调用方事务；不新增自动参与外部事务，不改变独立事务正常路径。测试覆盖 append、read、区间 read、readAll 的拒绝后事务仍活动，三线 JpaEventStoreTest 各 11 项通过、零跳过（`/tmp/ddd4j-jpa-transaction-owner-{1,2,3}.log`）。此处修复的是错误控制他人事务；真正将业务数据与事件追加放入同一事务仍是未完成的架构需求，不能用提前拒绝代替最终组合能力。生产源码已变，之前完整 Reactor 与索引哈希结果不覆盖本次改动，需后续补受影响回归与同步。

继续追踪装配发现扩展点行为差异：三线 JpaEventStore 构造器均保存 JpaStoredEventRepository，但 1.0 的 append/read 路径直接用 EntityManager 和私有 currentVersion/maxPosition 查询，未调用 repository 的方法；2.0/3.0 则通过 repository.findCurrentVersion、save、nextPosition 及查询方法完成工作。默认仓储测试通过不足以证明自定义仓储构造器等价。这是源码层已确认的调用路径差异，尚未通过定制仓储运行夹具量化输出。

下一步应覆盖自定义版本查询、位置分配、保存和读取四类可观察行为，确保 1.0 真实使用注入端口，而不是仅保留参数和字段。外部框架中当前检索未找到 new JpaEventStore 的生产直接接线，不能凭此推断无人使用，也不能把缺少接线视为可以忽略公开扩展点契约的理由。业务事务参与策略仍需在该端口行为收敛后独立完成。

扩展点修复已推进：新增三线相同的 customRepositoryMustControlVersionConflict，以真实 EntityManager 和自定义仓储返回版本 7；1.0 旧实现没有抛预期冲突，失败证据 `/tmp/ddd4j-jpa-custom-repository-red.log`。1.0 已将版本查询、nextPosition、save 和三种查询委托 repository，保留原有全局位置行锁及事务所有权保护。修复后三线 JpaEventStoreTest 各 12 项通过，零跳过（`/tmp/ddd4j-jpa-custom-repository-{1,2,3}.log`）。新增行为证明自定义版本查询被尊重，其他自定义位置/保存/读取策略尚需独立探针；默认仓储的既有读写及回滚用例已通过。1.0 保留的行锁与 2.0/3.0 位置策略仍有差异，不将本次扩展点修复宣称为所有并发行为相同。未提交或发布。

自定义扩展点探针现已补齐：customRepositoryMustControlPositionAndPersistence 让仓储分配位置 42、通过保存钩子改变落库目标，再用默认 EventStore 和清空后的 EntityManager 读回验证，确认不是仅调用 mock；改变目标仅是测试夹具，不是业务路由建议。customRepositoryMustControlAllReadPaths 在数据库确有事件时，由自定义仓储返回过滤后的空结果，验证全流/版本区间/全局读取均尊重端口，并确认默认仓储仍能读到原事件。

三线 JpaEventStoreTest 各 14 项通过、零失败/错误/跳过（`/tmp/ddd4j-jpa-custom-read-{1,2,3}.log`），现有自定义版本、位置、保存与读取扩展点均有同输入可观察结果验证。生产代码本轮未变；剩余重点是并发位置分配、数据库 commit 失败和外部事务组合，不把单线程 H2 契约扩大到这些场景。

真实 PostgreSQL 回归已执行：初次运行发现 1.0 有三项 IT，而 2.0/3.0 只有往返和 TEXT 列类型两项，缺少陈旧版本测试。已在 2.0/3.0 补齐，并增强 1.0 对 expected=0、actual=1 及冲突后读回仍一条事件的断言。三线各三项全部通过、零跳过（`/tmp/ddd4j-jpa-postgres-conflict-{1,2,3}.log`），Docker 确实可用，本轮没有因 disabledWithoutDocker 跳过测试。此处仍为顺序追加后的版本冲突，不是并发冲突测试；位置并发策略和 commit 故障仍未证明。

并发位置策略源码核验：JpaStoredEventRepository.nextPosition 文档称“原子操作”，默认实现实际仅执行 MAX(position)+1 查询，不更新独立分配器；实现文档又承认无行锁、依赖唯一约束及调用方重试。其“单实例保证严格递增”描述不能覆盖同实例多线程并发。1.0 JpaEventStore 另有锁住当前最大位置行的路径，但空表没有可锁行；2.0/3.0 不具有同一锁路径。没有证据证明三线并发行为等价。

CodeGraph 与源码检索确认 EventStoreRetry 属于 JDBI 模块，JPA 自身及已查 runtime 没有对应自动重试接线。JPA 文档的“调用方应重试”是集成义务，不是已实现特性；不能以 JDBI 的重试测试证明 JPA 可靠。下一项并发验收需以独立 EntityManager/真实 PostgreSQL、同聚合竞争和不同聚合争用全局位置分别执行，检查成功/冲突类型、事件零丢失及重复、位置唯一性；位置分配原子性与提交顺序对读模型游标的影响必须一起设计，不能单纯换成序列后忽略未提交较小位置被游标越过的问题。此次未降低接口验收要求，也未改动数据库结构。

真实 PostgreSQL 的位置查询探针已执行：两个独立 EntityManager 各开启事务，在双方都未写入/提交时依次调用默认 repository.nextPosition，三线输出均为 `POSITION_CANDIDATES=1,1`（`/tmp/ddd4j-position-candidates-{1,2,3}.log`）。事务保持活动的断言通过，证明查询没有接管调用方事务；候选值相同是诊断观察，不是被测试认可的原子分配性质。该重叠事务调度未使用并行线程、未实际写入，因此不证明发生重复落库或丢失事件，但足以证明 nextPosition 调用自身不预留唯一位置。未引入新序列表或改变生产策略。

投影消费链进一步核验：ProjectionRunner.runOnce 读取持久游标，将其传给 EventChunkReader；处理事件后，只要 chunk.nextEventNumber 更大就持久化该值（即使该块无事件也可推进）。因此 reader 对“可安全推进的下一位置”负责，Runner 不检查比该位置更小的未提交事件。这个设计可以支持过滤事件，但要求底层提供安全的提交可见边界；不能简单添加连续整数检查，因为过滤与合法位置间隙也可能存在。

Guice 默认 Ddd4jGuiceModule 的 ProjectionRunner provider 直接 new NoopEventChunkReader，当前已读源码没有根据 EventStore 注入真实 reader。已查 core/data/runtime 生产实现及外部三框架的文本引用未发现通用 EventStore reader 接线；不排除应用自行提供或以其他类型名适配。故不能声称位置生成与投影消费已作为生产组合被验证。下一步应先核对业务需要的 reader 装配入口及提交可见性，再设计位置分配，保留对过滤、失败重放、延迟提交的验收，不通过默认空 reader 让启动测试表面通过。

Guice reader 装配已改进：真实 Injector 测试绑定 EventChunkReader<Object> 后，旧默认 Runner 仍返回空事件，目标断言失败（`/tmp/ddd4j-guice-reader-red.log`）。三线保留原单参数 projectionRunner 工厂方法，新增双参数 Guice provider：优先获取显式 EventChunkReader<Object> 绑定，未绑定时兼容原空 reader 行为。新增测试检查实际事件到达 view，并且持久游标服务推进为 1，而非仅断言 reader Bean 存在。

三线模块装配、Runtime contract、ViewManager 共各 33 项通过、零跳过（`/tmp/ddd4j-guice-reader-green-{1,2,3}.log`）。新增公开重载已在三线同步，但全 API 门禁及完整回归尚未重跑。该改进让业务 reader 能被默认 Runner 消费，不提供数据库 EventChunkReader，不保证提交顺序，也不改变未配置 reader 时的兼容空行为；真正生产投影仍需显式 reader、持久游标及事务验证。未提交或发布。

独立只读审查结果：当前不可合并。重要问题是 COMMIT flush 模式下，每事件重新查询 MAX(position)+1 可能看不到本事务前一 persist，造成同一批次位置重复；1.0 从局部递增改为 repository 委托后新增了该风险，2.0/3.0 原本即有该依赖。该结论仍是源码推导，须运行双事件回归复现后修复，不能直接当成运行故障证明。另建议删除 1.0 未使用的私有 currentVersion 查询。Guice 未发现确定的新 binding 回归，但子类、Modules.override 与旧工厂直接调用覆盖仍可补强。当前全量 `/tmp/ddd4j-jpa-guice-combined-{1,2,3}.log` 仍在执行，不将审查或运行中状态报告为通过。

上述全量进程已全部结束并成功，1.0/2.0/3.0 分别用时 04:49/08:29/07:58，未报告失败或跳过。但随后确实复现了审查指出的 COMMIT 问题：新双事件测试在 1.0 因 position=1 唯一约束失败（`/tmp/ddd4j-jpa-flush-commit-red.log`）。这再次表明默认测试全绿不能替代遗漏场景。

三线默认 repository.nextPosition 现已对 MAX 查询设置查询级 FlushModeType.AUTO，确保本事务前一 persist 对位置查询可见，不修改 EntityManager 的 COMMIT 配置，也不覆盖自定义 repository。新测试断言两条事件位置递增且 EM 模式仍为 COMMIT；三线 JpaEventStoreTest 各 15 项通过、零跳过（`/tmp/ddd4j-jpa-flush-commit-green-{1,2,3}.log`）。已清理 1.0 失去调用方的私有 currentVersion 查询，独立复审已请求，尚待结论。该修复只解决批次内可见性，不解决跨事务位置竞争或外部事务参与；此前全量结果不覆盖后续这项新修复。

限定复审已通过：此前 COMMIT 可见性 Important 问题关闭，未发现该修复新增问题；不是全项目合并批准。进一步运行三线 JpaEventStoreTest（15）与 JpaEventStorePostgresIT（4），各 19 项全部通过、零跳过（`/tmp/ddd4j-jpa-reviewed-regression-{1,2,3}.log`）。PostgreSQL 四项包含诊断性质的位置候选查询，仍不代表并发原子性通过。索引已再次同步；新鲜编译 API 门禁正在 `/tmp/ddd4j-reviewed-strict-api.log` 执行，结果尚未返回。

上述新鲜编译门禁已结束并通过，报告 `/tmp/ddd4j-reviewed-strict-api/results.json` passed=true，包含新增 Guice provider 重载及近期 JPA 修复后的三线编译；未跳过 Enforcer。所有非例外目录/源码集合、分类后的公共 JVM API 与共同显式 CodeGraph 方法无违规差异，原始例外清单仍保留。编译与签名门禁不证明并发位置策略一致，也不补足外部事务、真实 reader 或 cloud 装配缺口；整体替代目标仍未完成。

### 真实基础设施证据不能遗漏，也不能扩大

`ddd4j-sample-order-jdbc/OrderInfrastructureRoundTripTest` 使用真实 PostgreSQL、Redis、Kafka 容器，验证订单写入/投影/支付幂等、发布后 Outbox 状态与 Kafka 收到订单事件。该类 `@Testcontainers` 没有 disabledWithoutDocker，是实际集成证据，已包含在 1.0 完整通过的 sample 测试中。不能把整个示例体系概括为仅内存实现。

同类的租约用例通过真实 JDBC 存储领取、确认、重排和死信重放，但“broker unavailable”是直接传给 reschedule 的错误文本，没有制造 broker 断连；不能据该用例名字声称真实消息故障已覆盖。`JdbcOrderTransactionPortTest` 的提交失败用例通过模拟 Connection.commit 抛 SQLException 验证回滚及异常传播，不是数据库网络故障测试。

待补的端到端证据仍是：真实业务事务提交失败不 ACK/不丢 Outbox、broker 接收后本地标记失败的重复投递、持久确认与不可路由处理，以及 Boot/Javalin/Quarkus 三个真实入口对同业务内核的装配。这些要求与既有成功链路互补，不重复创建第二套成功样例。

工作树修复进展：1.0 annotation 已删除无使用的 spring-context，26 项测试通过；kit 的 StrKit 改用与 2.0 相同的 Commons ObjectUtils 并删除 spring-core，57 项测试通过；core 已删除无源码引用的 Spring/spring-biz 直接依赖，280 项测试通过。下游完整回归随后暴露 Dropwizard/Hibernate 测试依赖此前隐式引入的 javax.validation API，现将它显式放回五个实际使用模块的 test scope。完整回归结果须单独确认，不将上述局部通过视为全部框架验证。

最新验证：`/tmp/ddd4j-foundation-full-test4.log` 为保留 Enforcer 的 1.0 完整 Reactor `-Pparity-verification test`，结果 BUILD SUCCESS。`/tmp/ddd4j-foundation-spring-tree-final.log` 为 annotation、kit、core 及其 Reactor 上游的 Spring 坐标过滤依赖树，命令成功且无 Spring 匹配。说明本次清理已消除这些基础模块当前 Maven 依赖图中的 Spring 边；尚不证明外部业务工程的所有动态加载路径或已发布版本同步完成。

BOM 工作树已按实际聚合树补齐非 sample JAR 管理项；三个独立消费者无版本声明引用本轮新增坐标，Maven validate 均通过。该验证使用本地安装的修改后 BOM，证明模型解析，不证明私服已更新或所有 JAR 已下载。

| 契约 | 当前证据 | 迁移要求 |
|---|---|---|
| 异常 HTTP 状态 | cloud-agents 的 `AgentsExceptionHandler.handleIllegalState` 返回 400；新版 `DefaultWebExceptionTranslator` 返回 409 | 保留应用级策略，在 Boot、Javalin、Quarkus 分别验证同输入状态与响应体 |
| 上下文 Map | 旧 `base-core/.../ThreadContext` 返回内部 Map 或 null；新 `ddd4j-core/.../ThreadContext` 返回副本或空 Map | 审计直接修改 getValues 返回值和 Map 泛型的调用，禁止仅机械替换 import |
| 租户键 | 新旧 ContextConstants 的 tenant-id、user-id、shop-id、app-id 值相同 | 键一致只证明字段映射，不证明入口传播与清理正确 |
| 嵌套上下文 | 新 ThreadContext.open(resources) 保存并恢复旧快照；现有 ThreadContextTest 本轮 6/6 通过 | 内层执行成功与异常退出均恢复外层租户 |
| MQ 租户入口 | ImageGenSubmitMqConsumer 对缺失租户的有效消息不设置新值，finally 删除 tenant-id | 测试工作线程预存租户时的缺失租户消息；决定拒绝还是明确无租户处理 |
| 重试后确认 | cloud-agents 图片消费者先持久化重试/轮询计划，成功后 ACK，失败重新入队 | 三框架适配必须保持此顺序 |
| Inbox 事务 | MQInboxProcessor.process 先 recordIfAbsent，再同步 handler.run；事务由调用者提供 | 去重记录与业务写入同事务，提交失败不可 ACK；重复消息可 ACK |
| Rabbit 自动确认 | RabbitMQClient 在 consume 正常返回且未确认时 ackSingle，异常时 basicNack(requeue=true) | 处理器不能启动异步工作后立即返回；事务提交必须先于返回 |
| Outbox 发送成功 | MQOutboxSender.send 是 void 接口，接口注释没有 broker 持久确认要求 | 核验具体 sender 是否等待 publisher confirm/持久 ACK，再允许 markPublished |

## 源码定位

- 旧框架：`/Users/wandl/workspaces/workspace-bmgw/codeup/ddd4j/base-core/src/main/java/com/dddframework/core/context/ThreadContext.java`
- 应用异常策略：cloud-agents-common-infra 的 `AgentsExceptionHandler.java`
- 应用租户读取：cloud-agents-common-app 的 `BaseAppService.java`
- 消费入口：cloud-agents-aigc-infra 的 `ImageGenSubmitMqConsumer.java`
- 嵌套调度：cloud-agents-aigc-app 的 `AigcDispatchAppService.dispatchImageSubtask`
- 新框架：`ddd4j-core/src/main/java/io/ddd4j/core/context/ThreadContext.java`
- 新 MQ：`ddd4j-mq/ddd4j-mq-core/src/main/java/io/ddd4j/mq/delivery/` 与 `ddd4j-mq-rabbitmq/.../RabbitMQClient.java`

### 租户上下文与 Feign 传播复核（2026-09-08）

详细证据已整理到 `docs/architecture/cloud-tenant-context-contract-audit.md`。当前结论不是“键名相同即可迁移”：`ddd4j-cloud` 的过滤器只在正常返回后 clear，无效 token 和异常路径会遗留状态；`MallCompletableFuture` 复制 tenant/system/security/request 后不恢复；Feign 随后可能把污染租户继续转发。另确认 `AbstractServiceFeignRequestInterceptor` 从 before 生命周期错误调用 after 默认方法，绕过接口默认注册逻辑。

新版 `ThreadContext.open()` 与 `WebContextScope` 已提供快照恢复和 MDC 恢复基础，但 `WebRequestContext.tenantId` 只承载输入，不验证身份来源。`cloud-agents` 实际仍使用旧 `com.dddframework` ThreadContext，且既有 MQ/异步代码同时存在 remove 与手工 previous-value 恢复两种语义。因此后续必须先统一闭合作用域，再分别验证 HTTP 身份信任、线程复用、MQ 缺租户、Feign 转发与多容器生命周期；本轮没有修改或迁移 `cloud-agents`。

后续同调用链审查已发现并修复新版同步请求的异常清理缺口：`SynchronousWebRequestSession.complete` 与 WebMVC `RequestState.close` 在幂等完成回调抛异常时会跳过上下文恢复。新增三线相同回归先稳定复现内层租户遗留，再以 `try/finally` 保证租约关闭和外层上下文恢复；JDK 8/17/21 的请求会话各 6 项、WebMVC 拦截器各 2 项均通过且零跳过。该聚焦结果不覆盖响应式、MQ 或 Feign 的线程复用场景，也不替代完整回归。

WebFlux 入口随后发现一项仅存在于 1.0 的方法体漂移：请求头列表非空时条件写反，导致传给 OTel 的 header Map 为空；2.0/3.0 已是正确实现。三线新增相同 `traceparent` 与多值头采集测试，1.0 红测复现后把条件统一，JDK 8/17/21 各 3 项通过且零跳过。这证明签名一致不能替代同输入行为验证；当前仅证明请求头采集，不扩大为跨进程链路追踪验收。

真实 OTel SDK 测试继续发现：`WebOtelSupport` 用 Object 参数反射查找实际接收 Span 的方法，导致 activate 及其后的错误记录、结束、响应注入方法静默未绑定；修正真实反射签名后，又暴露 WebFlux 在 Publisher 组装阶段激活 Scope、终止线程关闭的跨线程所有权错误。三线已统一为订阅时建 span、`chain.filter` 边界同线程短暂激活/关闭、终止时结束 span，公开 API 不变。三线 WebFlux 聚焦测试现各 4 项通过；跨 scheduler 自动传播及跨进程导出仍是待验证项。

反射协议的第六项随后也由真实测试暴露：`WebOtelSupport` 查找并在文档中承诺 `WebOtelIntegration.isAvailable()`，扩展实现却缺少该方法。三线现已补齐相同公开静态方法。原先只验证“不抛异常”的 WebOtelSupportTest 已替换为 SDK 与内存导出器测试，实际确认有效 span/Scope、异常事件、HTTP 503 ERROR 状态与属性、结束导出和响应 traceparent；三线各 4 项通过且零跳过。测试 SDK 依赖均为 test scope、无模块内版本号。

其余框架调用者审查首先确认 Micronaut 3/4 都丢弃 `activate(span)` 返回的 Scope。线程池内真实 SDK 红测证明过滤器返回和 Publisher 结束后工作线程仍持有请求 span。三线已按各自 Filter API 把 Scope 缩到 `chain.proceed`/`continuation.proceed` 同线程边界并在 finally 关闭；JDK 8/17/21 各 1 项通过。1.0 的原始 ThreadLocal 请求上下文与 2.0/3.0 PropagatedContext 仍未统一，本次结果只关闭 OTel Scope 泄漏。

随后用真实 HTTP 请求在控制器内执行 `Mono.delay + publishOn`，并从业务 `ThreadContext.TENANT_ID` 读取租户。2.0/3.0 的 Micronaut 4 路径通过，1.0 首次返回 409 与 `Micronaut async tenant context is missing`。1.0 已使用 Micronaut 3 的 ServerRequestContext 与 ReactiveInvocationInstrumenterFactory 在每个 Reactor 回调打开/恢复 WebContextScope；修复后三线 scheduler 用例通过，三线完整 Micronaut Web contract 各 7 项全部通过且零跳过。实现没有增加独立模块或公开 API。

Javalin 随后确认同类 OTel 泄漏：before 激活后丢弃 Scope，after/exception 仅结束 span。真实服务器在 ddd4j after 之后仍观察到有效请求 span。三线已把 OTel Scope 合入 RequestState，并以嵌套 finally 保证幂等、WebContext 和 OTel 均关闭；JDK 8/17/21 的真实 Scope 测试各 1 项、完整 Javalin Web contract 各 6 项全部通过。Javalin 6/7 仅测试注册语法不同，行为断言一致。

Javalin 测试 provider 已统一声明为无版本、test scope 的 slf4j-simple，1.0 的版本只补在 ddd4j-dependencies。2.0 初次重跑报告多项集中版本缺失，但当前源码 effective POM 对全部 24 个报错坐标都有已解析版本，最终定位为本地仓库中同版本 BOM 过期；本地安装当前 BOM 后 Reactor 恢复。三线 Scope 测试均通过且 provider 提示消失，2.0 完整 Javalin contract 6 项也重新通过。该 install 仅更新本地 Maven 仓库，不代表私服发布。

Dropwizard 响应过滤器此前没有读取请求过滤器已保存的 OTel Scope，也没有结束 span 或移除 OTel 属性，正常响应后即会污染线程。三线现以 finally 统一结束 span 并关闭/移除 Scope；真实 Scope 测试各 1 项、完整 Dropwizard Web contract 各 6 项全部通过。1.0 因 Dropwizard LoggingUtil 保留 Logback test provider，但具体模块的两个显式版本已删除，版本继续由 ddd4j-dependencies 管理；无版本配置重跑通过。

## 证据边界与后续工作

### BOM 与企业 parent

cloud-agents 根 POM 已继承 com.bmgw:bmgw-cloud-parent，迁移不能同时继承 ddd4j-parent。应保留企业构建约定，审计导入 ddd4j BOM 后的版本优先级，再显式选择框架 starter。ddd4j-bom 负责版本管理，不会自动安装运行能力；ddd4j-parent 还包含实际 dependencies 与构建配置，不能认为 import BOM 等价于继承 parent。

XML 直接声明覆盖审计：枚举 checkout 中非 sample、非 target 的 pom.xml，按 packaging=jar（缺省亦按 jar）对照 ddd4j-bom 的直接 io.ddd4j 管理项，1.0 有 42 个未列入候选，2.0/3.0 各 35 个。共同候选包括 ddd4j-data-projection 及各投影适配、CQRS 适配、ddd4j-ddd-rules-clean/cola、ddd4j-metrics、ddd4j-runtime-testkit、ddd4j-web-testkit。此清单包含测试工具 JAR，且没有计算继承/导入后的 effective dependencyManagement，也没有限定实际 reactor 发布集合；不能将计数直接称为生产缺失构件数。下一步以外部最小消费者 POM 验证候选坐标的版本解析。

审查发现 ddd4j-parent 的 Docker 口令配置有泄露风险。三线工作树已移除 docker.registry.password 属性，登录 shell 直接读取 DDD4J_DOCKER_REGISTRY_PASSWORD，通过 printf 标准输入交给 docker login；缺失变量时失败。使用假的 docker shell 函数验证了引号、美元符、反引号等测试口令完整传输以及缺失变量拒绝，未调用真实 Docker。此前三线 parent validate 已通过。既有 Git 历史和已发布 POM 不会因此自动清除，需凭据所有者轮换曾明文暴露的凭据；本次记录不包含原值。该修复尚未提交或重新发布。

### 响应对象数值与载荷契约

旧 base-core 的 ResultCode.FAIL 为 500，新 ddd4j-core 为 1；两边 R.fail(String) 都使用该枚举，故相同消息输入会产生不同业务码。OK=0、SUCCESS=200 的值一致。是否保留旧码必须由迁移兼容层或明确版本策略决定，不能直接把新默认值改回去而影响现有消费者。

审查时发现 io.ddd4j.core.api.R.failed(T data) 调用不带 data 的 fail 重载，忽略输入载荷；ddd4j-cloud 的同名方法保留载荷。该缺陷现已在三线工作树修复：传递 data 给三参数 fail，保留本线原有失败码和消息。非字符串 Map 载荷回归先出现 1 项失败，修复后再覆盖 String 消息重载和 Object null 分支，JDK 8/17/21 的 RTest 分别 13/13 通过、零跳过。日志为 /tmp/ddd4j-r-payload-red.log 和 /tmp/ddd4j-r-overloads-1.log、-2.log、-3.log。修改尚未提交发布，且这不代表与 cloud 的所有消息默认值已经相同。

上述为源码对照，尚未完成各框架真实 JSON 输出与客户端业务码分支的差分测试。

### 内部接口注解语义

旧 base-web 的 SessionWebInterceptor 遇到类或方法 @Inside 时直接放行；BaseAuthWebInterceptor 遇到方法 @Inside 也直接放行。@Inside 只是运行时标记，本身没有内部凭据验证实现。不能因名称含 Inside 就认定接口已经具备服务间认证，也不能据此认定应用完全没有其他保护。

cloud-agents 的 SubscriptionAdminController、SubscriptionPlanAdminController 使用类级 @Inside。迁移前必须结合网关、路径匹配、其他过滤器与应用鉴权核验实际边界。新版 SaInternalCheckHandler 明确要求 API Key 并校验 scope，与旧 @Inside 的放行语义不同；直接替换会改变客户端凭据要求，直接删除也可能改变会话策略。

验收矩阵需包括：无凭据、有效内部凭据、普通用户凭据、错误 scope，以及类级/方法级注解覆盖关系；分别验证三框架的真实 HTTP 响应和是否进入业务方法。

应用级保护已进一步确认：SubscriptionPlanAdminController 的分页、详情、创建、更新等方法首先调用 AdminAuthService.isAllowed。该服务读取 AgentsApiProperties.adminListSecret；配置非空时要求 X-Agents-Admin-Token 完全匹配，配置为空则返回 true。控制器拒绝时返回 R.fail，所读方法未设置 HTTP 403。该结果纠正了仅根据 @Inside 推断无保护的可能误判，但不证明部署配置一定包含密钥或网关策略有效。

迁移适配必须覆盖配置缺失与配置存在两种环境，保留或明确调整 HTTP 状态与业务码，并确保三框架都能读取同一请求头。建议生产环境对缺失管理密钥启动失败或拒绝访问；这是建议的行为变更，不应混同于保持旧行为的机械迁移。

### fuin 本地源码对标

本轮直接读取以下本地实现；这些 checkout 尚无 CodeGraph 索引，因此此部分是源码阅读证据，不声称来自图查询或运行测试。

- `ddd-4-java/esc/.../EventStoreRepository.update`：读取聚合未提交事件，以 expectedVersion 追加事件，校验返回版本后才调用 markChangesAsCommitted；WrongExpectedVersionException 进入冲突处理路径。可借鉴的验收点是：追加失败不清空未提交事件，冲突处理后事件顺序和版本仍一致。
- `cqrs-4-java/esc/.../SimpleJpaEventDispatcher`：按 EventType 保存多个处理器，逐事件、逐处理器同步调用，并把同一个 EntityManager 传给处理器。该类本身没有开启事务，事务责任在调用者。
- `cqrs-4-java/springboot/.../QryProjectionService`：保存 nextPosition，缺失位置返回 0；写方法没有单独声明事务，不能只凭该类认定读模型与游标原子提交。
- `ddd-cqrs-4-java-example/quarkus/query/.../PersonListEventChunkHandler`：handle 方法以 @Transactional 包裹 dispatchCommonEvents 与 updateProjectionPosition，给出了读模型写入和位置推进的共同事务边界。

对 ddd4j 的具体要求是用失败注入证明：处理器失败或事务提交失败时，读模型与游标一起回滚；重放同一批事件不会跳过未提交部分。组件命名相似、存在 PositionRepository 接口不构成这一保证。

### ddd4j 投影事务核验

`ProjectionRunner.runOnce` 同步调用 view.handleEvents，再调用 projectionService.updateProjectionPosition；该方法没有统一事务执行器。`ProjectionDispatcher.dispatchOne` 明确声明不包装事务，先 handler.handle 再 commitPosition。`SpringProjectionScheduler` 仅将 Runnable 交给 TaskScheduler，并不为这两步建立共同事务。

因此，仅在 handler 方法上加事务注解，可能导致业务写入先提交、游标更新另行失败；事件随后重放时副作用可能重复。现有 ProjectionRunnerTest 的失败分支使用内存 RecordingView，证明异常传播与视图隔离，但不能证明数据库回滚。

需要验证的装配条件是：事务执行边界包住整个处理批次及位置写入，且使用相同数据源与事务管理器；调用必须经过框架代理或显式事务执行器。若跨数据源，则不能宣称原子提交，必须另行提供幂等/去重设计与故障恢复测试。

继续追踪运行入口发现：SpringProjectionViewManager.triggerOnce/runView 与 JavalinProjectionViewManager 均直接调用 runner.runOnce。ddd4j-javalin 的 Person CQRS 示例通过 JavalinPersonCqrsModule 创建 ProjectionRunner，位置仓储为 InMemoryProjectionPositionRepository，事件源为 InMemoryPersonEventStore；调度器以 lambda 调用 runOnce。该示例可证明 Guice 装配与内存流程，不提供数据库共同事务的证据。

对 ddd4j-boot 和 ddd4j-quarkus 当前 Java 源码的 ProjectionRunner/ProjectionDispatcher/TransactionTemplate/TransactionalOperator 检索未命中相应接线；这只是明确符号检索结果，不能推导整个工程没有 CQRS 能力。需要进一步检查它们采用的其他事件/事务抽象，避免把符号缺失误判为能力缺失。

后续读取确认：Boot 的 Ddd4jCoreAutoConfiguration 通过 @Import 导入 SpringCoreConfig、SpringDomainEventPublisher、SpringContextBridge，已有运行时复用入口。Quarkus 的 ddd4j-quarkus-ddd 依赖 ddd4j-runtime-quarkus 与 quarkus-quartz，并把 Panache 声明为可选依赖。

但 2.0 Runtime 的 QuarkusJpaProjectionService.runOnce 虽有 @Transactional，默认 pullAndApply 仅记录“override me”日志，没有实际读取事件、处理读模型或推进位置。它是扩展模板，不能作为开箱即用投影实现计入验收；还需查找具体子类、CDI 选择和真实数据库测试。其逐流 catch(Exception) 继续执行，也需验证失败后的事务状态，不能仅凭异常被记录就认定其他流安全提交。

继续核验 2.0/3.0 Runtime 与框架工程，没有检出该模板的生产子类；QuarkusJpaViewManager.triggerOnce 同样只打印提示。但 data-projection-quarkus 另有 QuarkusProjectionViewManager，实际调用 ProjectionRunner.runOnce，并有调度生命周期测试。这两类都以 @ApplicationScoped 实现 ViewManager，所读声明未见区分 qualifier；同时被 CDI 发现时存在注入歧义风险，需真实组合启动验证。框架工程当前 POM 未直接声明 ddd4j-data-projection-quarkus，不能假定实际消费者自动获得后者。

优先改进顺序：先确认消费者最终依赖树与 CDI 实际选择；再统一默认 ViewManager 的职责，避免空 triggerOnce 被当作成功；随后补共同事务执行和数据库故障重放验证。保留已有有效投影实现，避免重复创建另一套调度器。

### Rabbit 发送与 Outbox 接线核验

`RabbitMQClient.initProducer` 在本次修复前调用 `channel.basicPublish`，捕获异常后仅记录日志并正常返回；该吞异常行为已按下述真实容器回归修复。创建的 BasicProperties 仍只设置 messageId 与 headers，没有设置 deliveryMode，发送路径也没有调用 publisher confirm。因此仍不能把此 Consumer 的正常返回解释为 broker 持久确认。

`MQOutboxDispatcher.dispatch` 在 `sender.send(record)` 正常返回后立即调用 `store.markPublished`。若适配代码直接委托上述 Rabbit Consumer，发送失败被吞掉时可能错误标记发布成功。这是条件性集成风险，不是已经复现的生产数据丢失。

对 ddd4j-boot、ddd4j-javalin、ddd4j-quarkus、ddd4j-cloud 的 Java 源码检索，未检出 MQOutboxSender 或 new MQOutboxDispatcher 的直接接线。此结果不能排除反射、外部应用装配或其他消息机制，但意味着目前没有源码证据证明四个集成工程已经自动提供该 Outbox 发送通道。

改进验收应包括：发布异常向调用方传播；可靠发送等待 broker confirm；确认超时/断连保持记录可重试；持久消息与持久队列配置；不可路由消息处理；重复发送使用稳定 messageId。上述行为须在真实 Rabbit 容器和故障注入下验证，再在三种框架集成入口验证装配。

上述 MQ 问题是源码契约分析；尚未完成数据库提交失败、broker 断连、重复投递及跨租户线程复用的端到端差分测试。不能据此声称所有适配器已兼容。

后续按同一业务输入构造三个框架夹具，验证 HTTP 状态/响应、事务回滚、提交后 ACK、稳定 messageId、重复消息副作用和租户恢复。以 cloud-agents 现有处理流程作为输入契约来源，不实际迁移该应用。

### 1.0 Dropwizard Web 契约恢复执行（2026-09-08）

移除整类禁用后，首先重现 `DropwizardExtensionsSupport` 调用旧 JUnit 内部 `ReflectionUtils.makeAccessible` 导致的初始化失败。改用 `ResourceExtension` 公开的 `before()/after()`，由 JUnit `@BeforeAll/@AfterAll` 管理真实 Jersey 资源，未修改任何契约断言。

继续执行暴露 `javax.inject.Singleton` 缺失：Jakarta Inject 2 不提供 Jersey 2 所需的 javax 命名空间。Web Dropwizard 模块补充 `javax.inject:javax.inject` 运行时所需依赖，不声明版本，沿用 ddd4j-dependencies 的管理版本。

JDK8 执行 `mvn -B -ntp -Pparity-verification -pl ddd4j-web/ddd4j-web-dropwizard -am test` 成功，未跳过 Enforcer。Web Dropwizard 18 项测试全部通过，其中原先禁用的六项覆盖成功/创建响应、错误状态、Bearer 鉴权、请求标识传播、请求间上下文隔离和幂等去重，零失败、零错误、零跳过。日志：`/tmp/ddd4j-dropwizard-contract-regression.log`。失败复现日志：`/tmp/ddd4j-dropwizard-contract-enabled.log`；依赖缺口复现日志：`/tmp/ddd4j-dropwizard-contract-lifecycle.log`。

这补齐了 Dropwizard 适配的既有测试盲区，不代表 Boot/Javalin/Quarkus 的迁移组合、真实数据库事务与 MQ 故障场景已经通过，也不代表本次修改已提交或发布。

### Rabbit 发送失败信号修复（2026-09-08）

现有容器用例直接调用原生 Channel，未经过框架发布函数，无法检出适配器吞异常。新增用例通过真实 Rabbit 容器建立连接，创建 RabbitMQClient 生产者，然后关闭连接并发送；旧实现没有向调用方抛出异常，失败日志 `/tmp/ddd4j-rabbit-send-red.log` 为 3 项执行、1 项断言失败、零跳过。

三条版本线统一保留原始失败原因并抛出 IllegalStateException，不改方法签名。此处的兼容性变化是发送失败不再伪装为成功，调用方应保留待重试状态。成功返回仍不承诺 broker confirm，不能以本修复代替可靠发送协议。

JDK8/17/21 分别执行 RabbitMQContainerIntegrationTest（3 项）、RabbitMQAdapterContractTest（1 项）、MQOutboxDispatcherTest（7 项），每条线 11 项全部通过且零跳过。真实连接关闭断言与 Outbox 失败重试测试是两层独立验证，尚不是端到端 Outbox→Rabbit 接线验证。日志分别为 `/tmp/ddd4j-rabbit-send-final-1.log`、`/tmp/ddd4j-rabbit-send-final-2.log`、`/tmp/ddd4j-rabbit-send-final-3.log`。中间回归曾暴露测试重复关闭连接，已修正清理逻辑后重跑通过。

尚待验证和改进：broker confirm、持久消息、不可路由消息、确认超时后的重复投递、数据库状态更新失败，以及三个框架的实际 Outbox 装配。本次没有修改 cloud-agents，也没有提交或发布这些改动。

### 框架组合的版本与类所有权审查（2026-09-08）

本次只读核对的框架检出并非同一版本线：ddd4j-boot 为 4.1.x（d7866296），ddd4j-javalin 为 feature/6.7.x（01a8250），ddd4j-quarkus 为 feature/4.0.x（5ea9d6e）。不能把这些目录各自的构建结果拼成某一条 ddd4j 版本线的完整兼容证明。未切换任何分支。

旧路径的职责需按当前源码重新对应：Guice、Spring 的运行时装配分别在 ddd4j-runtime-guice、ddd4j-runtime-spring；DDD 层次检查在 ddd4j-ddd-rules，领域契约在 core。1.0 不包含 Quarkus 运行时，不能根据 1.0 目录缺失断言 2.0/3.0 也没有。路径承接只是定位，不是行为等价结论。

确认一个 Javalin 组合缺陷：ddd4j-javalin-data-mybatisplus 的源码仍定义 `io.ddd4j.guice.Ddd4jMybatisGuiceModule`，注释说明这是 1.0 未发布该类型时的兼容副本；但其 POM 直接依赖的 `ddd4j-runtime-guice:1.0.x.20260630-SNAPSHOT` 本地 JAR 已包含同一 class（unzip 核对条目 6758 bytes）。核心仓库也有真实同名实现。兼容副本的前提已失效，将随模块编译产生重复类，行为选择会依赖消费者 classpath 顺序。

两份实现均以 Guice Singleton 提供 `openSession(true)` 的 SqlSession，并在 Repository Mapper 注入失败时记录警告继续。这额外暴露自动提交事务边界与启动失败信号问题，但本轮未运行并发/事务失败场景，不能声称已经发生数据损坏。

Javalin 当前 H2 单元测试仅验证 SqlSession、Repository、Mapper 非空，不执行 CRUD 或类来源唯一性断言；MySQL IT 为独立用例。下一项修复应先加 classloader 资源唯一性回归，重现同 FQCN 两份资源，再移除过期兼容副本并验证既有 H2/MySQL 行为与调用签名。不应另建同名适配层或通过 classpath 排序隐藏问题。本轮没有修改 Javalin 源码。

后续修复结果：新增 `shouldLoadOneCanonicalRuntimeModule` 使用真实 classloader 枚举 class 资源，实际返回 adapter target/classes 与 runtime-guice JAR 两个定义，断言失败（`/tmp/ddd4j-javalin-duplicate-red.log`）。随后删除 Javalin 的过期兼容源码，保留原包装类及既有 runtime-guice 依赖；clean test 后三个 H2/类来源测试全部通过。删除的源码仍可从 Git 恢复，未切换分支。

进一步显式执行 `Ddd4jMybatisJavalinMySqlIT`，启动真实 MySQL 容器验证 CRUD，与三个 H2/类来源测试合计 5 项、零失败、零错误、零跳过，日志 `/tmp/ddd4j-javalin-canonical-mysql.log`。这证明当前 6.7.x 组合可以复用已提供该类的 1.0 runtime-guice，不证明所有历史 SNAPSHOT、其他版本线、事务回滚或并发 SqlSession 安全。Javalin 改动尚未提交或发布。

### ddd4j-cloud 租户传播与自动装配缺口（2026-09-08）

当前 cloud 检出为 `2020.0.x`，HEAD `da93d8b`，根 revision 为 `2020.0.x.20251205-SNAPSHOT`。因此不能将此目录视为已对齐 ddd4j 三条 20260630 版本线的最终 cloud 组件。已有 `.codegraph`，本次用其追踪 Feign/租户符号；未修改 cloud 源码或其未跟踪文件。

| 链路 | 当前源码证据 | 对迁移的影响 |
|---|---|---|
| 自动装配发现 | data 的 `META-INF/spring.factories` 仍注册 `com.qushiyun.cloud.common.data.*`；实际对应源码包为 `io.ddd4j.cloud.cmpt.data.*`，仓库 Java 搜索未发现旧包定义 | 当前注册表不能证明这些配置能从新包自动加载；需要打包及上下文启动验证，不能只改包名前缀后就宣称解决 |
| 租户状态来源 | `BaseFeignTenantInterceptor` 只读取 `TenantContextHolder.getTenantId()`；holder 使用独立 TTL，并同步 SysContentHolder；本次 cmpt Java 搜索未见 core ThreadContext 引用 | core Web 上下文与 cloud 出站 Feign 状态没有直接接通的源码证据，仍可能依赖外部桥接 |
| 请求异常退出 | `TenantContextHolderFilter` 在 `filterChain.doFilter` 后清理，没有 finally；无效 token 分支可在设置 shop/system 后直接返回 | 异常及提前返回时清理路径不完整，需同线程连续请求测试；不能以正常响应测试代表隔离安全 |
| 异步任务退出 | `MallCompletableFuture` 两条执行路径设置租户、系统、SecurityContext、RequestAttributes 后运行 Runnable，没有恢复/清理 | 不能保证复用执行线程上的上下文生命周期；需要指定可复用执行器的异常及嵌套任务测试，不能假定 TTL 自动补足 |

还有独立的 Feign 装配疑点：`AbstractServiceFeignRequestInterceptor.postProcessBeforeInitialization` 调用接口的 `postProcessAfterInitialization`，绕过接口 before 方法中的工厂注册逻辑。已阅读源码，但尚未执行具体子类的 Spring 生命周期测试。

修复顺序应先确定受支持 cloud 版本线与真实依赖组合，验证自动装配可启动；随后明确唯一租户状态及入口信任规则，补同输入的 core→cloud Feign 传播、异常清理、嵌套恢复与线程复用测试。不得直接把未经认证的 tenant-id 请求头当作授权租户，也不能通过在多个 holder 间无条件复制状态来掩盖边界。以上是当前检出的源码审查，不是 cloud-agents 已发生越权或串租户的运行时证明。

### Cloud Maven 基线实测

JDK17 执行当前 2020.0.x 的 `mvn -B -ntp validate`，在构建模型阶段失败：父坐标 `io.ddd4j.boot:ddd4j-boot-parent:2.4.x.20251215-SNAPSHOT` 无法从当前配置的阿里云仓库解析，且 parent.relativePath 为空。日志 `/tmp/ddd4j-cloud-current-validate.log`。此次未执行编译、测试或部署；该结果仅证明当前环境解析失败，不推断该版本在所有仓库中从未发布。

不切换分支，使用 git show 读取八条已有分支的根 POM，得到以下父版本矩阵（均以 20251215-SNAPSHOT 结尾）：

| Cloud 分支 | ddd4j-boot-parent 版本线 |
|---|---|
| Hoxton.x | 2.3.x |
| 2020.0.x | 2.4.x |
| 2021.0.x | 2.7.x |
| 2022.0.x | 3.2.x |
| 2023.0.x | 3.3.x |
| 2024.0.x | 3.4.x |
| 2025.0.x | 3.5.x |
| 2025.1.x | 4.0.x |

所有已检查分支仍指向旧日期父版本；其他七条分支未实际执行 Maven，不能报告它们同样构建失败。下一步需核对匹配 Boot 线的实际可解析父产物及其管理依赖，建立对应 cloud 消费者基线，随后才可将自动装配和租户异常测试作为真实组合证据。不能把现有 Boot 4.1 检出直接替换到 Cloud 2020，或通过忽略父模型错误宣称该组合可用。

后续已确认 Boot 2.4.x 分支仍存在，配置为 Spring Boot 2.4.13、ddd4j 1.0.x.20260630-SNAPSHOT、JDK8；本地 2.4.x.20260630-SNAPSHOT 父 POM 在 JDK8 下独立 validate 成功（`/tmp/ddd4j-boot24-parent-model.log`）。因此仅将当前 Cloud 根 parent 日期改为 20260630，未切换版本线、未改 Cloud 自身 revision、未提交或发布。

该修复消除了根父坐标解析失败，但完整 Reactor 仍失败（`/tmp/ddd4j-cloud-parent-aligned-validate.log`）：暴露旧 `ddd4j-boot-cmpt-*` 坐标、ddd4j-boot-core 以及多个 hiwepy starter、MyBatis/Druid/Springfox 依赖的版本管理缺失。不能把这些项简单填入模块级数字版本；应先区分已迁移坐标与 BOM 管理缺口，再逐项验证源 API 和装配行为。当前修改是构建基线修复的中间状态，不是 cloud 构建通过或迁移完成。

进一步核对继承链：cloud-dependencies 继承 cloud 根，根继承 Boot parent；Boot parent 已 import Boot BOM，并非 cloud 完全没有继承 BOM。实际 2.4.x 源码 BOM 与本地 20260630 BOM 均未声明 ddd4j-boot-core，故这一项属于上游 BOM 漏项，不应与旧坐标缺失混为一谈。

异常契约核验中的确定性修复：新 core 的 BizRuntimeException(Throwable) 与 BizCheckedException(Throwable) 原先只委托 message 构造器，导致 getCause() 为 null。新增两个 cause 身份断言均先失败（`/tmp/ddd4j-exception-cause-red.log`），三线统一改为委托 message/cause 构造器，保留参数名、公开签名及既有 message 国际化路径。此项改进不等于旧 cloud 异常已完成迁移。

JDK8/17/21 各自 core 及上游测试成功，core 分别 282/319/359 项，均零失败、零错误、零跳过，包括新增两个 cause 回归。日志 `/tmp/ddd4j-exception-cause-green-1.log`、`/tmp/ddd4j-exception-cause-green-2.log`、`/tmp/ddd4j-exception-cause-green-3.log`。测试数量差异仍不能当作三线全部行为一致的证明。修改未提交或发布。

### 组合回归与 samples 覆盖补齐

近期修改组合后的 1.0 根 Reactor（未含 samples）在 JDK8 下 `mvn -B -ntp -fae -Pparity-verification test` 成功，用时 03:45，日志 `/tmp/ddd4j-consolidated-regression-1.log`。此次未跳过 Enforcer。2.0/3.0 同轮回归仍在执行，尚无最终结果。

检查发现 1.0 根 POM 仍以“record 不支持 JDK8”为由注释 samples，但当前 samples 聚合器已选择 15 个 Java8 兼容示例并排除要求新 JDK 的框架。已恢复根 `<module>ddd4j-samples</module>` 并启动包含它们的完整 Reactor 测试，日志 `/tmp/ddd4j-line1-including-samples.log`。此举使底座与示例在同一源码 Reactor 验证，避免仅使用本地已安装旧 JAR；截至本条记录新增测试尚未完成，不能报告 samples 已通过。

本轮最终结果：三个测试进程均已结束。1.0 含 samples 的 Reactor 失败（04:07），OrderControllerTest.addLine_multipleLines_shouldAccumulate 报 SocketException: Unexpected end of file from server；未找到端口占用的对应异常，原因尚待隔离复现。2.0 Reactor 失败（08:29）：ProjectionPositionJpaIT 五项初始化错误，日志明确显示 Spring Data JPA/Commons 4.0.6 与 Spring Context 6.2.19、Boot 3.5.10 混用，引发 RuntimeBeanReference 构造器 NoSuchMethodError；另 Quarkus sample 启动遇到 8081 占用，带来一项错误及 72 项跳过。3.0 Reactor BUILD SUCCESS（08:03），但 Quarkus、Vert.x、Dropwizard 三组 Web contract 合计 18 项跳过；2.0 也有这 18 项跳过。没有以日志中的 BUILD SUCCESS 代替全部测试通过。

下一批应先收敛 2.0 Spring Data 与 Spring/Boot 依赖体系、隔离 Quarkus 测试端口，然后复现 1.0 HTTP 断流；恢复被禁用的 Web contract。此次失败说明当前源码组合尚未满足三线测试全通过门禁，不应直接发布未验证的修改。

2.0 JPA 修复进展：统一 dependencies 中 Spring Data BOM 从 2025.1.6 改为 2025.0.13（JPA/Commons 3.5.13）。原 RuntimeBeanReference 错误消失后，实测又暴露 Hibernate 7.2.6 与模块 JPA 3.1 不兼容，缺少 PersistenceUnitTransactionType（`/tmp/ddd4j-line2-spring-data-aligned.log`）。dependencies 已有 Hibernate 6.6.40 属性及 platform 导入，但多 BOM 组合最终选择了 7；新增 hibernate-core 的显式管理项引用既有 `${hibernate.version}`，未在具体模块定义数字版本。

修改后，JDK17 对 JPA EventStore、JPA 投影及上游执行测试成功：JpaEventStoreTest 9 项、ProjectionPositionJpaIT 5 项、投影架构测试 5 项全部通过，日志 `/tmp/ddd4j-line2-jpa-stack-aligned.log`。这是 Spring JPA 栈的定向验证；统一管理变更影响其他消费者，仍需验证 Quarkus/Hibernate 组合和完整 2.0 Reactor，不能将本次定向成功报告为全线修复完成。

Quarkus CQRS sample 端口隔离：2.0 先用 `-Dquarkus.http.test-port=0` 运行原失败的三组 sample 测试，73 项通过，确认该组启动失败可由端口隔离消除（`/tmp/ddd4j-line2-quarkus-stack-check.log`）。随后在 2.0/3.0 sample 的 application.properties 将 `%test.quarkus.http.test-port` 从 8081 改为 0，生产端口不变。两线并行运行、不带命令行端口覆盖，各自 73 项全部通过，零失败/错误/跳过，日志 `/tmp/ddd4j-quarkus-random-port-2.log`、`/tmp/ddd4j-quarkus-random-port-3.log`。

此结果只覆盖该 sample 的订单事件、商品查询、订单 CQRS 查询，不证明所有 Quarkus ORM 模块对统一 Hibernate 管理变更兼容。此前整类禁用的 Web contract 仍需恢复；1.0 HTTP 断流和完整 Reactor 复验仍未完成。改动未提交或发布。

2.0/3.0 Web contract 恢复：分别移除 Dropwizard 和 Vert.x 整类 Disabled 后，原有测试在当前依赖组合直接通过。旧的 JUnit ReflectionUtils/Jackson 不兼容禁用理由已经不适用于这两个检出，不应盲目套用 1.0 的修复。保留原生命周期和全部断言；每条线 Dropwizard 6 项、Vert.x 6 项均零失败/错误/跳过，日志 `/tmp/ddd4j-web-contracts-restored-2.log`、`/tmp/ddd4j-web-contracts-restored-3.log`。共恢复 24 项实际执行，生产代码未变。仍有 Quarkus Web contract 待恢复，不能报告所有 skipped 已清零。

后续 Quarkus Web contract 也已恢复：2.0/3.0 移除过期 Jackson VerifyError 禁用标记后各六项通过；将随机端口配置写入 src/test/resources/application.properties，再不带命令行端口覆盖并行重跑，两线各六项零失败/错误/跳过（`/tmp/ddd4j-quarkus-web-restored-2.log`、`/tmp/ddd4j-quarkus-web-restored-3.log`）。至此此前两条线各 18 项、合计 36 项被禁用的三组 Web contract 已在定向运行中全部执行通过。尚未完成修改后的全 Reactor 复验，不能将此结论扩大到所有模块测试；1.0 sample 的 HTTP 断流仍需处理。

1.0 Javalin CQRS HTTP 断流隔离：OrderControllerTest 单次 35 项通过，但连续第三轮在另一 GET 用例 queryList_shouldReturnOk 复现相同 SocketException（`/tmp/ddd4j-http-repeat-3.log`），故不是原加订单行用例独有。测试每个用例重启服务器，JDK8 HttpURLConnection 默认允许连接复用。仅在测试客户端增加 `Connection: close`，不改生产服务、不加入请求重试；随后订单和商品共 72 项测试连续五轮通过，合计 360 次、零失败/错误/跳过（`/tmp/ddd4j-http-close-1.log` 至 `-5.log`）。结果支持跨服务器生命周期的连接复用是触发因素，但未取得网络抓包，不能将其描述为完整 TCP 根因证明。当前测试隔离修复仍需完整 Reactor 回归确认。

### 旧监控职责的承接边界

旧仓库 base-monitor 的 HealthController 仅在 `/health` 返回固定 `ok`，不是依赖就绪检查。CodeVersionService 监听 ApplicationStartedEvent 与 ApplicationFailedEvent，读取 git.properties 并通过 Sender 通知启动成功或失败，失败时附项目异常栈；机器人日志 appender 属于另外一条告警路径。因此不能用新底座的健康注册表或 tracing 模块单独证明该模块已完整替代。

新底座 extension-monitor 的 ApplicationStartReporter 已承接读取版本信息和启动成功通知，并用 try-with-resources 关闭输入流，对缺失 git.properties 安全返回，这是比旧实现直接加载空流更稳健的实现。但 init 仅生成成功通知，没有失败事件参数；同时标注 PostConstruct，却在说明中要求应用就绪后调用。组件初始化与整个应用就绪不是同一时刻。当前读取的核心扩展和 runtime 源码未检出将 ApplicationFailedEvent 桥接到它的路径；外部框架全部装配尚待核对。

迁移验收应分别验证健康探针兼容、readiness 依赖失败、成功通知时序、启动失败通知、日志告警的路由/脱敏/限流，以及通知通道失败是否影响业务启动。新实现增加通道与纯 Java 入口是优势，但不能丢弃旧失败通知行为。此节为源码审查，未发送任何真实机器人消息，也未以日志类存在替代运行验证。

外部 Boot 4.1 当前接线已进一步核对：Ddd4jMonitorBootAutoConfiguration 仅注册 BaseMonitorProperties，源码明确将实际告警发送与 Appender 留给业务项目装配，没有实例化 ApplicationStartReporter。因此在这一入口，不能声称已发生 PostConstruct 提前发送；实际确认的是成功/失败通知均没有自动接通的代码。现有三个测试只覆盖配置 Bean 的默认注册、缺 Logback 时回退和用户覆盖，不覆盖任何通知发送或应用生命周期。

该自动配置还以 `@ConditionalOnClass(LoggerContext.class)` 限制整个配置绑定，无 Logback 时 BaseMonitorProperties 也不注册。条件将纯配置能力与特定日志实现绑定，不等于机器人发送本身必须依赖 Logback。改进应区分通用 monitor 配置、通知生命周期、Logback 专用 Appender 三层条件；不应强迫 SLF4J 其他 provider 消费者引入 Logback 才能获得配置。该外部入口尚未修改，需先定义通知接线的验收行为再实现。

最新组合验证：1.0 在修复 HTTP 测试连接隔离后，包含 samples 的完整 Reactor 测试 BUILD SUCCESS，用时 03:58，日志 `/tmp/ddd4j-final-combined-1.log` 未报告失败/错误/跳过，Enforcer 未跳过。该轮真实执行 CQRS、Satoken/Shiro 等 sample 控制器测试。2.0/3.0 对应 `/tmp/ddd4j-final-combined-2.log`、`-3.log` 的进程尚未结束，结果待收集；不以 1.0 成功代替其他线成功。

该轮后续结果已齐：1.0 模块级汇总 1687 项（sample 包测试 385 项），3.0 模块级汇总 2197 项，两线均零失败/错误/跳过；3.0 用时 06:40。2.0 用时 07:06 后失败，ddd4j-data-projection-panache 的 Quarkus 构建抛 NoSuchFieldError: BOOT_LOGGER，继而 ArchUnit 引擎无法发现测试。该发现失败不能从测试数字为零推断没有问题；优先检查统一 Hibernate 6 管理对需要另一 Hibernate 版本的 Quarkus ORM 模块的影响。全部进程已结束，没有仍运行的该轮测试可等待。

2.0 ORM 边界修正：Panache 已有 Quarkus BOM 导入，但新增全局 hibernate-core 显式管理压过该导入。已撤掉全局显式项，两个 Spring/JPA 模块仅在 test 依赖中引用 dependencies 集中定义的 `${hibernate.version}`，没有模块数字版本；Panache 的 ORM 继续交由 Quarkus BOM 管理。JPA EventStore 9 项、JPA 投影 10 项、Panache 投影 7 项及上游在同一个 Reactor 全部通过，零跳过（`/tmp/ddd4j-line2-orm-boundary.log`）。由此取代此前“全局强制 Hibernate 6”的中间方案。新的 2.0 全 Reactor 回归已启动（`/tmp/ddd4j-line2-final-orm-regression.log`），尚待最终结果；1.0/3.0 源码未变，不重复运行已通过的回归。

Boot 4.1 monitor 配置解耦已实施：测试先将缺 Logback 场景改为要求配置 Bean 存在。首次 Maven 仅 BUILD SUCCESS 但 Tests are skipped，不计验证；为 monitor 模块明确启用 surefire 后，三个测试中目标断言失败（`/tmp/ddd4j-boot-monitor-no-logback-red2.log`）。随后将条件从 LoggerContext 改为 BaseMonitorProperties，并移除模块的可选 logback-classic 依赖，三个测试全部通过、零跳过（`/tmp/ddd4j-boot-monitor-no-logback-green.log`）。证明配置注册不再依赖特定日志后端；不代表所有传递依赖无 Logback，也不代表启动通知已接通。只修改当前 Boot 4.1 检出，其他 Boot 分支尚未同步验证；未提交或发布。

2.0 ORM 边界修正后的全 Reactor 已结束（04:31，`/tmp/ddd4j-line2-final-orm-regression.log`），仍 BUILD FAILURE：Satoken sample 的 AuthorizationControllerTest.createRole_withEmptyCode_shouldFail 报 `HTTP/1.1 header parser received no bytes`。该次日志没有再出现 Panache BOOT_LOGGER 问题，但不能将一个 HTTP 错误视为可忽略。本次客户端为 JDK17 java.net.http，必须独立验证生命周期，不能直接照抄 JDK8 的 Connection 请求头方案。当前没有该轮仍运行的测试进程。

Satoken 测试生命周期隔离：该 AuthorizationControllerTest 每个用例都会停止/重启服务器，但复用 BeforeAll 的 HttpClient。2.0/3.0 在每次重启后重建客户端，避免跨服务器生命周期复用旧连接池；未改生产逻辑、鉴权断言或增加重试。两线各 29 项连续三轮通过，合计 174 次零失败/错误/跳过（`/tmp/ddd4j-satoken-client-2-1.log` 至 `-2-3.log`，以及 `-3-1.log` 至 `-3-3.log`）。2.0 完整 Satoken sample 149 项及上游回归也通过（`/tmp/ddd4j-line2-satoken-client-regression.log`）。结果支持客户端生命周期隔离方案，但不是网络层根因的抓包证明；修改后的完整 2.0 Reactor 仍待最终复验。

后续全量复验（`/tmp/ddd4j-line2-final-samples-regression.log`，04:35）在同 sample 的 AuthControllerTest.updateUser_shouldReturn200 再次出现无响应头，说明只修一个类不足。已核对并将另外四个同样重启服务器的测试类同步为重建客户端，2.0/3.0 均保持五类一致；两线各 149 项定向运行通过、零跳过（`/tmp/ddd4j-satoken-all-clients-2.log`、`-3.log`）。此前全量失败仍是有效证据，不能用这次定向运行覆盖；最新源码完整回归尚未重新执行。没有保留仍运行的该轮进程。

Cloud core 的 CheckedException/ValidateCodeException 仍导入旧 `io.ddd4j.boot.core.ApiCode`、`CustomApiCode`、`exception.BizCheckedException`/`BizRuntimeException`，WebUtils 还继承旧 `io.ddd4j.boot.core.utils.WebUtils`。Boot 2.4.x Git 树未发现这些文件，暂未验证传递依赖是否另行提供旧兼容类。CheckedException 有九个重载构造器，包含 code、i18nCode、args、message、cause，并提供默认 code=500 的工厂方法；不能仅重命名 import 后假定异常响应和国际化语义一致。下一步应以这些构造器、继承关系和 HTTP 异常翻译结果作为兼容输入，查找新底座对应能力，决定兼容桥接或调用方适配，而不是补版本号后删除编译失败的旧 API。

后续方法级核验已确认：两类Cloud异常只替换为新Core包后，在JDK8/17/21均能保留全部
构造器、参数及工厂方法并编译通过；Boot13线及已构建Boot Core JAR确实没有旧类型。
但HTTP语义不等价：新默认翻译器把400100业务码映射为HTTP500/code400100、空code映射
HTTP500/code500、IllegalStateException映射409；Cloud自身R.failed对空code使用-1。
因此import适配可行，响应策略仍需Cloud应用级契约。完整证据见
`docs/architecture/cloud-exception-contract-audit.md`。

### JPA 显式事务参与 Task 5 最终证据（2026-09-08）

三线 EventStore 差分脚本在原有 9 项 Jdbi/JPA/R2DBC 断言上新增 7 项同名
RESOURCE_LOCAL 参与行为，并把报告文件名、suite、testcase 的本轮后缀、启动时间、
missing、failure/error/skipped 都纳入拒绝条件。六个聚焦 Python 用例覆盖正常报告、
缺失、陈旧、跳过、错误后缀和无后缀；最终实际差分运行三线各 16/16，零缺失、
零无效报告，结果向量一致。最终证据为
`/tmp/ddd4j-tx-eventstore-parity-task5-final/results.json` 和
`task-5-logs/eventstore-parity-final.log`。

已有三个 CodeGraph 索引均已刷新。严格 API 工具使用 `--compile` 重新编译三线后
返回 `passed=true`，但这表示允许例外归一后通过，不是原始 API 全等：1.0↔2.0 和
1.0↔3.0 的原始差异仍为 classes `3/0`、javap `7/16`、CodeGraph side-only
`402/23`，另有 6 个共同符号冲突；2.0↔3.0 各项为零。允许项包括 1.0 的三个内部类、
Micronaut 代际接口、Java 11 HttpClient 构造器扩展和 Javalin 配置类型差异，完整清单在
`/tmp/ddd4j-tx-api/results.json` 与 `task-5-logs/api-allowed-differences-summary.txt`。
对本次入口另从 fresh class 文件执行三线 `javap -public`：每线均有两个
`participating` 和两个 `participatingManaged` 工厂；只把 1.0 的
`javax.persistence` 归一为 `jakarta.persistence` 后，四个字节码签名完全一致。
证据为 `task-5-logs/factory-signature-summary.json`。

第一次完整 Reactor 虽然三线 exit 0，报告数分别为 1708/2107/2223，随后核对发现
JPA 模块继承的 Surefire 规则只收集 `**/*Test.java`：三线都没有运行
`JpaEventStorePostgresIT` 和 `JpaSpringParticipationIT`。这批日志保留为“默认收集范围内
通过但验收范围不完整”，不能作为最终全绿证据。三线 JPA 模块 POM 现已显式并列保留
`**/*Test.java` 与新增 `**/*IT.java`；修改前 POM 固化为
`task-5-jpa-pom-baseline-{1,2,3}.xml`。无 `-Dtest` 的模块级验证随后实际生成全部报告：
1.0 为 40 项（PostgreSQL 7、Spring 4、EventStore 22、Managed 7），2.0/3.0
各 43 项（另含既有 JpaEventStoreIT 3），全部零失败、零错误、零跳过。证据为
`task-5-logs/jpa-default-report-summary.json`。

收集修复后的最终完整 Reactor 使用新的独立后缀和启动时间，首次结果如下；未重试
失败轨，也未用陈旧 XML 补数：

| 线 | 退出 | fresh tests | samples | JPA | 失败/错误 | test skipped |
|---|---:|---:|---:|---:|---|---:|
| 1.0.x / JDK8 + Maven 3 | 1 | 1638 | 385 | 40 | `GoodsResourceTest.create_highPrice_shouldBeOk`：期望 201、实际 404 | 0 |
| 2.0.x / JDK17 + Maven 3 | 1 | 2121 | 594 | 43 | `GoodsResourceTest.multipleCreates_allShouldSucceed`：HTTP/1.1 header parser 无字节/EOF | 0 |
| 3.0.x / JDK21 + `./mvnw` | 0 | 2237 | 594 | 43 | 无 | 0 |

1.0 因 fail-fast 另有 11 个后续 Reactor 模块未执行；这不是 test class skipped=0 能
消除的缺口。2.0 的失败模块位于 Reactor 尾部，没有后续模块跳过。三线 JPA 报告在
最终 full 中仍分别为 40/43/43 且全绿，所以 JPA 参与能力的定向、默认收集和本轮
Reactor 内执行证据成立；整个三线 Reactor 全绿门禁不成立。精确统计与报告路径见
`task-5-logs/final-full-report-summary.json`，原始日志为
`task-5-logs/final-full-{1.0.x,2.0.x,3.0.x}.log`。

对 Shiro sample 只做了诊断，没有修改或刷绿。1.0/2.0 的
`GoodsResourceTest` 都在每个用例前 stop/start Javalin，但 HttpClient 只在
`@BeforeAll` 创建：1.0 的 HttpURLConnection 门面没有设置 `Connection: close`
或显式 disconnect，2.0 使用同一个 `java.net.http.HttpClient` 跨服务器生命周期；
2.0/3.0 对应测试文件 hash 相同而本轮只有 2.0 失败，1.0 同一用例在前一轮成功。
这些证据支持“连接池/服务器重启生命周期的非确定性”假设，与此前 Satoken 类问题同型，
但没有抓包，不能写成已确认根因。JPA POM 变更未修改 sample 源码或依赖，只有执行时序
可能间接影响暴露概率。后续应在独立 sample 任务中先记录端口、响应体和连接生命周期并
重复类级测试证伪，再决定是否在每次 restart 后重建 client 或显式关闭连接；本批不处理。

结构化 POM 审计分别解析 103/121/121 个 POM，具体模块数字 dependency version 与
多行 description 均为零。当前仍未实现 XA、全局 position 原子分配、数据库
EventChunkReader、broker confirm/持久确认或 cloud-agents 迁移；本次没有提交、推送、
部署或发布，也不能据此宣称整体替代或生产就绪。

### 本轮最终审查与小修状态

独立整体审查认可 JPA 实现与差分脚本，没有发现重要源码缺陷；整体放行仍被上述
1.0/2.0 Shiro 完整回归失败阻塞。最终小修补齐了 suite 正确而 testcase 后缀错误的
独立负例，并将三线 PostgreSQL 测试新增判空统一为 `Objects.isNull`。
修正后 Python 7/7、三线真实 PostgreSQL 各 7/7（零失败/错误/跳过）通过，定点复审
确认 M1/M2 关闭。未重新运行 full，也未声称 Shiro 问题已修复。

审查与复审报告分别保留在本计划 SDD 目录 `final-review.md`、`final-fix-review.md`；
小修证据为 `final-fix-report.md` 和 `final-fix-logs/`。无 HTTP listener 的 Panache
测试仍会忽略 `quarkus.http.test-port`，不能把该配置当作随机端口验证；既有日志告警
继续披露。所有快照、原始失败与新鲜测试报告保留，当前未提交、推送或发布。

### 后续只读认证链审计

三线隔离探针确认正式 Shiro provider 与 sample 测试替代实现行为不一致：当前控制器
请求未提供 credential，正式实现拒绝而测试替代实现通过；已认证线程上正式 verify
对无关 token 仍返回当前主体。详见 [正式认证路径审计](shiro-real-provider-audit.md)。
该证据不解释此前 404/EOF，也不是远程可利用性证明；真实 provider 的请求负例与
线程/会话隔离验收仍缺失，不能用 sample 变绿代替正式认证能力验收。本轮未改认证代码。

随后以127.0.0.1随机端口、正式控制器进行最小HTTP集成对照：三线正确密码走正式provider
均500，测试替代provider均200，错误密码均401。详见[HTTP登录对照](shiro-http-login-audit.md)。
该差异来自独立JVM/独立关闭连接的对照，不代表已定位旧404/EOF；也没有启动完整样例应用。

### TrueLicense 对迁移目标的边界

三条ddd4j线已完成TrueLicense4.1.4迁移，但框架消费侧尚未闭合。只读维护线审计确认：Boot13线
仍重复引入1.33并未透传signatureAlgorithm；Quarkus3.3/4.0使用独立extension-license却仍带
无用1.33；Javalin6.7/7.1/7.2无旧依赖，Cloud无直接许可证集成。旧BMGW ddd4j和cloud-agents
当前也没有许可证相关源码/POM/配置匹配，所以这不是替代旧系统的行为对等阻塞，而是新能力的
依赖合规阻塞。完整证据见 `docs/migrations/truelicense4-framework-consumer-audit.md`。
