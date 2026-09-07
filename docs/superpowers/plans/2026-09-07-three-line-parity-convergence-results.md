# 三版本结构、公开 API 与 EventStore 行为收敛结果

## 快照与范围

| 版本 | 基线 HEAD | JDK |
|---|---|---|
| 1.0.x | `861e5db4a675e04a6ed49219decc19aeca101412` | Corretto 8u504 |
| 2.0.x | `e89e1e83` | Corretto 17.0.20.1 |
| 3.0.x | `aced4bdd` | Microsoft 21.0.12.1 |

执行前已 fetch 两个远端；2.0.x fast-forward 3 个提交，3.0.x fast-forward 1 个提交，复用了远端已恢复的 `ddd4j-auth-spring`，未重复实现。1.0.x detached HEAD 已等于远端 1.0.x 最新提交，未切换分支。全过程未创建 worktree、未提交、未推送、未发布。

## 结构与对象收敛

- 三线非例外 Maven 模块目录均为 86，双向差异 0/0。
- 三线非例外生产 Java 相对路径均为 808，双向差异 0/0。
- 初始 10 个独有对象中，`ddd4j-auth-spring` 的 `AuthSpringConfiguration`、`SubjectRegistrar`、`SaTokenExceptionHandler`、`ShiroExceptionHandler` 已由远端 fast-forward 提交恢复；本轮没有重复实现。
- 1.0.x 新增 `R2dbcAsyncEventStore` 和 `Ddd4jMybatisGuiceModule`。
- 1.0.x/2.0.x 新增 `DomainEventCarrier`、`MqDomainEventPublisher`、`SpringCommandBus`、`MicrometerProjectionMetrics`。
- 1.0.x 的 `R2dbcEventStore` 收敛为同步 `EventStore` 适配器，异步实现迁到 `R2dbcAsyncEventStore`。由于旧异步 `read(String, AggregateRootId)` 与同步方法仅返回类型不同，Java 不能在同一类型中保留二者；本项按共同规格选择 2.0/3.0 双对象拓扑，属于明确的 1.0 旧二进制 API 迁移。

目标对象测试：1.0.x 23 项、2.0.x 26 项，均 0 failure/error/skip；3.0.x 原有对应测试保持通过。

## 公开 API 收敛

新增 `scripts/verify-three-line-structure-api-parity.py`。门禁先按 JDK 8/17/21 执行三次 `clean compile`，再比较 Git 工作树生产路径、编译 class 拓扑、CodeGraph 两边共同显式符号的源码签名/参数名，以及 `javap -public -s -v` 的公开 descriptor/`MethodParameters`；不再读取陈旧 class，也不再让未分类差异绕过失败条件。

- 2.0.x ↔ 3.0.x：模块、生产路径、公开 JVM API 均 0/0。
- 1.0.x ↔ 2.0.x：非例外公开 API 差异 0/0。
- `AggregateController` 的八个 helper 已统一移入同文件包级 `AggregateControllerSupport`，消除了 JDK8 interface 无 private method 造成的公开 API 泄漏。
- 保留的允许差异仅为：JDK8 不存在 `java.net.http.HttpClient`，以及 Micronaut/Javalin 依赖代际强制产生的适配器签名。每条均在 JSON 中逐项列出；非允许 class、JVM API 与 CodeGraph 共同显式符号冲突均为 0。
- 2.0/3.0 record 补齐 1.0 已发布 bean getter；1.0 手写类补齐 record-style accessor 与 record 值语义。
- 1.0 `SnowflakeOptions` 补齐缓存 key 的 equals/hashCode；`IdKitTest` 11/11 通过。

最终门禁：`/tmp/ddd4j-structure-api-parity-final12/results.json`，`passed=true`。三线均为 86 个模块、808 个生产 Java 路径；1↔2、2↔3、1↔3 的模块差异、路径差异、非允许 class 差异、非允许公开 JVM API/参数差异、CodeGraph 共同显式符号冲突均为 0。CodeGraph 的重载以签名集合比较，不再按方法名互相覆盖；JDK8 缺失的 7 个 `HttpClient` 构造器按 FQCN+descriptor 精确放行。CodeGraph 对 record 隐式方法不建节点，因此其原始单边候选只保留为诊断信息，record 生成 API 由本次 fresh `javap` 结果证明。

## EventStore 同输入行为

共同契约：

1. append 不修改调用者传入事件的 aggregateVersion。
2. 持久化事件自身 timestamp，不使用写入时当前时间。
3. SQL TIMESTAMP 按 UTC 编码/还原，避免系统时区造成 instant 偏移。
4. `readAll(..., limit<=0)` 同步轨立即抛 `IllegalArgumentException`，异步轨返回 error signal。
5. JDBI/JPA/R2DBC 保留现有版本、position、冲突与事务回滚契约。

RED 证据包括：1.0 JPA 输入事件被改为版本 1、固定时间被写成当前时间；2.0 JPA/JDBI 不拒绝 limit=0；3.0 JDBI 固定 UTC 时间偏移 8 小时。

GREEN：修正后的脚本为每条线生成唯一 `surefire.reportNameSuffix`，只读取三个精确测试类的本次报告，并要求 XML 的 mtime 晚于本次 Maven 启动时间；任一并发报告、旧报告、缺失报告、suite failure/error/skip 或 Maven 非零退出都会失败。三线各运行同一 8 个具体行为断言，均 `exit=0`、`observed=8`、`missing=0`、`invalid_reports=0`，PASS/FAIL 结果向量一致。证据：`/tmp/ddd4j-eventstore-parity-final9/results.json`。这些断言直接验证输入事件副作用、limit 异常、固定 timestamp/UTC；并非把历史 Surefire 名称当作逐字段序列化快照。

目标模块完整测试：

| 版本 | JPA | JDBI | R2DBC | 结果 |
|---|---:|---:|---:|---|
| 1.0.x | 9 | 10 | 12 | 31/31 通过 |
| 2.0.x | 9 | 5 | 12 | 26/26 通过 |
| 3.0.x | 9 | 5 | 12 | 26/26 通过 |

## Samples

Surefire XML 汇总：

| 版本 | 报告文件 | 测试数 | Failure | Error | Skipped |
|---|---:|---:|---:|---:|---:|
| 1.0.x | 20 | 385 | 0 | 0 | 0 |
| 2.0.x | 31 | 594 | 0 | 0 | 0 |
| 3.0.x | 31 | 594 | 0 | 0 | 0 |

1.0/2.0 的 Javalin 与 Javalin-CQRS 显式注册 Jackson2 JavaTimeModule；3.0 Jackson3 使用内建 Java time。3.0 四个 Quarkus sample 为避免固定 8081 端口冲突串行执行，普通 37、CQRS 73、Sa-Token 46、Shiro 45 项全部通过。

最终 samples 日志为 `/tmp/ddd4j-line1-samples-all-versions-centralized-final2.log`、`/tmp/ddd4j-line2-samples-centralized-provider-final3.log`、`/tmp/ddd4j-line3-samples-centralized-provider-final.log`。三线分别为 385、594、594 项，全部 0 failure/error/skip，且 SLF4J provider 缺失、多绑定和旧 binding 告警扫描为 0。2.0/3.0 standalone samples 均先以当前 reactor `-DskipTests install` 更新本地依赖与 Quarkus 索引，再执行完整测试。

值对象专项补证：三线 `MQOutboxRecordTest` 均实际运行 3 项且全绿，日志为 `/tmp/ddd4j-line1-mq-outbox-final.log`、`/tmp/ddd4j-line2-mq-outbox-final.log`、`/tmp/ddd4j-line3-mq-outbox-final.log`；`AuthEventValueContractTest` 三线均为 2/2 通过。最终跨 JDK 值语义门禁 `/tmp/ddd4j-value-parity-final5` 在 JDK 8/17/21 上均为 `compile=0`、`run=0`。

## 根 reactor 收敛

| 版本 | JDK | Reactor | 结果 | 日志 |
|---|---|---:|---|---|
| 1.0.x | 8 | 87/87 | `BUILD SUCCESS`，1:58 | `/tmp/ddd4j-line1-full-reactor-all-versions-centralized-final3.log` |
| 2.0.x | 17 | 121/121 | `BUILD SUCCESS`，4:33 | `/tmp/ddd4j-line2-full-reactor-final.log` |
| 3.0.x | 21 | 121/121 | `BUILD SUCCESS`，4:04 | `/tmp/ddd4j-line3-full-reactor-final2.log` |

1.0.x 额外收敛了 JDK8 测试依赖组合：Projection JPA 的 Boot2/Spring5/Hibernate5/`javax.persistence`/SnakeYAML1/Hikari4，Projection R2DBC 的 r2dbc-h2 1.0/H2 2.2，Web Core 的 SLF4J2 MDC provider 与 OTel test classpath，以及 Javalin/Micronaut Java8 HTTP 测试客户端和 Vert.x/Dropwizard 测试夹具。生产组件仍只面向 `slf4j-api`；`parity-verification` 使用 test-scope Log4j2 provider，并显式选择 provider。Logback 只保留在 Dropwizard 2、Boot 2/Hibernate 5 及 Dropwizard `LoggingUtil` 确实需要的测试模块，全部为 test scope。三条线所有第三方依赖和兼容/插件版本数值均集中在各自 `ddd4j-dependencies/pom.xml`；具体模块依赖数字版本为 0，具体模块数字版本属性为 0，仅通过集中属性引用覆盖兼容通道。1.0 Testcontainers 2 坐标与 core 均统一为 2.0.5，并直接管理 commons-lang3 3.20.0，避免 first-wins 混版。

3.0.x 为三个包含 `@QuarkusTest` 的库模块增加构建期 `generate-code-tests`，避免 Maven 3 WorkspaceLoader 解析 Maven 4.1 POM；Panache 模块同时将 Hibernate 7.4.5、Agroal 3.2.1 与 JAXB4 对齐到 Quarkus 3.38.2 平台。

全局 `verify-java-style.sh` 仍会命中仓库既有 PF4J、旧 logger、sample `System.out` 和旧 null 写法；它不属于本次三线结构/API/EventStore/全 reactor 的行为门禁。最终三线 `git diff --check` 另行执行并记录。

最终审查结论为 `Ready to merge: Yes`，无 Critical/Important。三线 `git diff --check` 均通过；结构/API final12、EventStore final9、value final5 与测试日志共同构成本轮最终证据。1.0 `Ddd4jVertxWebContractTest` 已移除失效的 `@Disabled` 并真实执行 6/6；SLF4J/Log4j provider、旧 binding 与 `StaticLoggerBinder` 错误扫描为 0。当前根 Reactor 的 6 个 skipped 均为既有 `Ddd4jDropwizardWebContractTest @Disabled`；Redis/Kafka/NATS/RabbitMQ 外部服务测试在本机 Docker 可用时已实际运行。三线全部 POM 的 `<description>...</description>` 已统一为单行。
