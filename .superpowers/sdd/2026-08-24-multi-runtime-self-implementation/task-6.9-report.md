# Task 6.9 Report — ddd4j-data-cqrs-dropwizard

## Status: DONE

- Commit: `f0bcd34b` `feat(data): ddd4j-data-cqrs-dropwizard——Application.run 工厂 + ServiceLoader 收 CommandExecutor`（8 files, +571）
- Gate: `./mvnw -pl ddd4j-data/ddd4j-data-cqrs-dropwizard -am install` → **BUILD SUCCESS**（Total time 9.4s）
- Tests: **8** = DropwizardCommandBusIT 3 + CqrsDropwizardModuleIndependenceTest 5（ArchUnit，brief 的 3 条基线 allowlist/no_spring/no_quarkus ＋ 外加 no_micronaut/no_vertx 防互窜）。Tests run: 8, Failures: 0, Errors: 0.

## 交付物

| 文件 | 说明 |
| --- | --- |
| `ddd4j-data/ddd4j-data-cqrs-dropwizard/pom.xml` | parent ddd4j-data；`ddd4j-data-cqrs` + `dropwizard-core`（BOM 5.0.2 无版本）+ test `dropwizard-testing`（同）；surefire 追加 `**/*IT.java`；模块级 dependencyManagement 见下 |
| `src/main/java/io/ddd4j/data/cqrs/dropwizard/DropwizardCommandBus.java` | `extends DefaultCommandBus`；`static create(Application<?>, Collection<CommandExecutor<?>>)`（私有构造、`super(registry.executors())`、CommandRegistry 整批注册）；零框架抽象：不 override execute、不实现 Application 生命周期、无 health check/metrics/事务依赖 |
| `src/test/.../DropwizardCommandBusIT.java` | 3 用例（成功路由＋run 装配轨、冲突整批拒绝 ISE、未注册命令 ISE）；`DropwizardTestSupport` 类级 @BeforeAll 单次启动真实 Jetty/Jersey（随机端口 port 0）；嵌套 `TestApplication` 仅覆盖 `run()`，其内即生产接法 `create(this, discover())`；用例 1 断言 `getLocalPort() > 0` 佐证真实监听 |
| `src/test/.../arch/CqrsDropwizardModuleIndependenceTest.java` | 5 规则：allowlist（io.ddd4j.. / java.. / jakarta.. / io.dropwizard.. / lombok..）＋ no_spring + no_quarkus + no_micronaut + no_vertx |
| `src/test/.../SampleCommand.java` / `SampleCommandHandler.java` + `META-INF/services/...CommandExecutor` | 与 javalin 同款（handler public 无参构造，ServiceLoader 契约） |
| `ddd4j-data/pom.xml` | 模块注册于 `ddd4j-data-cqrs` 与 `ddd4j-data-cqrs-helidon` 之间——字母序 "dropwizard"(d) < "helidon"(h)，且在裸 cqrs 之后 |

## 启动开销（实证）

- IT 类总耗时 **0.841s**（3 用例 ＋ @BeforeAll 一次真实 Dropwizard 5 启动：Bootstrap→ServerCommand→Jetty/Jersey 随机端口）；
  分摊 ≈ **0.28s/IT**（最慢单用例 0.04s，boot 占类耗时约 0.8s）。
- **远低于 5s/IT 阈值 → 未启用 DropwizardMetricsFactory 关闭优化**（brief 允许但不必要；jetty 12.1.8 + jersey 3.1.11 本地启动很快）。

## 关键发现：hadoop BOM 劫持（模块级修复，零全局副作用）

`dropwizard-bom` 5.0.2 **只管理 `io.dropwizard.*` 构件**（其父 dropwizard-project 无 dependencyManagement）——jersey/metrics 版本不随 BOM 导入传播。实际解析时：

1. `ddd4j-dependencies:3936-3943` 以 BOM 形式导入 `org.apache.hadoop:hadoop-mapreduce-client:3.5.0`（继承 hadoop-project 的 dependencyManagement，`jersey2.version=2.46` 管理 jersey-common/server/client/container/hk2 等 2.x 子集，且以 4.0.1 管理 `io.dropwizard.metrics:metrics-core`）；
2. 该导入（:3943）先于 jersey-bom 导入（:5178，3.1.11）→ first-wins 下 2.46/4.0.1 劫持；
3. jersey 2.46 是 javax.ws.rs 旧命名空间 → 与 Dropwizard 5 的 jakarta 线混链，`Environment` 初始化即 `NoClassDefFoundError: javax/ws/rs/core/Configurable`；metrics-core 4.0.1 缺 `MetricRegistry.registerGauge` → `NoSuchMethodError`。

**修复**：模块 pom 的 `<dependencyManagement>` 本地再导入 `org.glassfish.jersey:jersey-bom:${jersey.version}`（3.1.11，与 ddd4j-dependencies:5178 与 dropwizard-dependencies 5.0.2 同线）＋ `io.dropwizard.metrics:metrics-bom:${dropwizard-metrics.version}`（4.2.39，与 ddd4j 全局属性同线）。当前 pom 的管理优先于父级导入 → 整族回正，其他模块零影响。

## dependency:tree 实证

```
+- io.dropwizard:dropwizard-core:jar:5.0.2:compile
|  +- io.dropwizard.metrics:metrics-core:jar:4.2.39:compile
|  +- org.eclipse.jetty:jetty-server:jar:12.1.8:compile
|  +- org.glassfish.jersey.core:jersey-common:jar:3.1.11:compile
+- io.dropwizard:dropwizard-testing:jar:5.0.2:test
|  +- org.glassfish.jersey.core:jersey-server:jar:3.1.11:compile
```

## 遗留关注（非阻塞）

1. **全局 quirk**：`hadoop-mapreduce-client` BOM 导入劫持 jersey 2.x 子集/metrics-core（first-wins）。本模块已局部对齐；未来任何直接消费 jersey/metrics 的模块同样会踩——全局修法是把 jersey-bom/metrics-bom 导入移到 :3943 之前（超出本任务范围，留给 Task 6.10/后续）。
2. 测试 classpath 同时存在 slf4j-simple（ddd4j 全局 test）与 logback-classic（dropwizard 传递）→ SLF4J 多 provider 警告（选一，不影响结果），与其他带真实容器的适配器 IT 同类噪音。
3. jetty 12.1.8（ddd4j 管理）vs dropwizard 5.0.2 自带 12.1.9——patch 级漂移，同 jakarta 线，IT 实证无碍。
4. effective-model "1462 problems" 警告为全仓既有噪音（helidon 1458 / dependencies 1458 / annotation 1457 同报），非本模块引入。

## Self-review

- 与 javalin/vertx 模板对称性：私有构造 + 静态工厂 + CommandRegistry 整批注册 + ServiceLoader 发现轨，javadoc 结构（装配语义/发现/事务三节）一致；7 运行时互指注记已更新（各适配器 javadoc 的「仅服务 X 运行时」清单不含 dropwizard——**未回改 6 个既有模块**，如需补齐属文档级 follow-up）。
- brief 对照：`create(Application, Collection)` ✓；不实现 `Application<Configuration>` ✓（TestApplication 仅测试用）；无 health check/metrics/事务 ✓；3 IT ✓（≥1 真实启动）；ArchUnit allowlist io.dropwizard../jakarta.. + no_spring/no_quarkus + 外加 no_vertx/no_micronaut ✓；字母序注册 ✓（d < h，位于 cqrs 与 cqrs-helidon 之间）；单 commit ✓。
