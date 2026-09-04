# Task 6.7+6.8 报告 — ddd4j-data-cqrs-javalin + ddd4j-data-cqrs-vertx

**Status: DONE** — 两 commit、三门禁全绿。

## Commits（feature/2.0.x）
| commit | 内容 | 文件数 |
|---|---|---|
| `5e54f97f` | feat(data): ddd4j-data-cqrs-javalin——Javalin create 工厂 + ServiceLoader 收 CommandExecutor | 8（模块 7 + 聚合器 1 行） |
| `b75a2a24` | feat(data): ddd4j-data-cqrs-vertx——Vertx 5 工厂 + ServiceLoader 收 CommandExecutor | 8（模块 7 + 聚合器 1 行） |

聚合器 `ddd4j-data/pom.xml` 按字母序插行：javalin 于 helidon↔micronaut 之间（:23）、vertx 于 spring↔crypto 之间（:27）。

## 交付物
### A. javalin（Task 6.7）
- `JavalinCommandBus extends DefaultCommandBus`：私有构造 + `static create(Javalin app, Collection<CommandExecutor<?>>)`——工厂内经 `CommandRegistry` 整批注册（all-or-nothing ISE 装配期暴露）后 `super(registry.executors())` 快照组装；不 override execute（纯分发路由）；零事务依赖（javadoc 注明集成方自管）。
- IT：真实 Javalin 生命周期（`Javalin.create(cfg -> cfg.startup.showJavalinBanner = false).start(0)` 随机端口，`@AfterEach stop()`）+ 真实 JDK ServiceLoader 发现（test classpath `META-INF/services` 声明 `SampleCommandHandler`）；3 用例：happy 路由 / 撞型整批拒绝 ISE 自工厂传播 / 未注册命令 ISE。
- ArchUnit 3：allowlist（io.ddd4j../java../jakarta../io.javalin../lombok..）+ no_spring + no_quarkus。

### B. vertx（Task 6.8）
- `VertxCommandBus extends DefaultCommandBus`：同款私有构造 + `static create(Vertx vertx, Collection<CommandExecutor<?>>)`；**execute 保持同步返回 Result**（不暴露 Future——javadoc 注明异步包装由集成方经 `vertx.executeBlocking`/worker Verticle 委托）；零事务依赖。
- IT：真实 `Vertx.vertx()` 实例生命周期（`@AfterEach` 经 `close().toCompletionStage()...get(10s)` 同步关闭）+ ServiceLoader 发现；happy 用例在 **Vert.x 上下文线程**（`runOnContext` + `VertxTestContext`）同步执行 execute——证明 DefaultCommandBus 同步性不依赖 Vert.x 异步回路；另 2 用例同模板。
- ArchUnit 3：allowlist（io.vertx..）+ no_spring + no_quarkus。

## 门禁与实证
| 门禁 | 结果 |
|---|---|
| `-pl ddd4j-data-cqrs-javalin -am install` | BUILD SUCCESS；**JavalinCommandBusIT 3/3 + CqrsJavalinModuleIndependenceTest 3/3** |
| `-pl ddd4j-data-cqrs-vertx install` | BUILD SUCCESS；**VertxCommandBusIT 3/3 + CqrsVertxModuleIndependenceTest 3/3** |
| `-pl ddd4j-data-cqrs-javalin,ddd4j-data-cqrs-vertx -am install`（最终） | BUILD SUCCESS（8 模块 reactor：dependencies/annotation/kit/core/data/cqrs/javalin/vertx） |

dependency:tree 实际解析版本：
- `io.javalin:javalin:jar:7.2.2:compile`（javalin-bom 7.2.2 生效；jetty 线 12.1.8）✅ 应 7.2.x
- `io.vertx:vertx-core:jar:5.1.5:compile` + `io.vertx:vertx-junit5:jar:5.1.5:test`（netty 4.2.15.Final）✅ 应 5.1.x

## 坐标/偏差实证（简报预检分支复核）
1. **vertx-stack-depchain 非导入式 BOM（简报预案分支命中，已按预案处理）**：ddd4j-dependencies:9997 的 `io.vertx:vertx-stack-depchain` 条目是 `<scope>compile</scope>` 的版本管理项（非 `<scope>import</scope>`），**不向依赖链传播 vertx-core/vertx-junit5 的版本管理**——两坐标显式声明 `${vertx.version}`=5.1.5（pom 注释留档）。本地 .m2 原仅有 4.5.x；5.1.5 实证可解析（已入 .m2）。
2. **Javalin 7 配置改名（javap 实证 7.2.2 jar）**：简报所写 `cfg.showTestBanner` 在 7.x 不存在，改为 `cfg.startup.showJavalinBanner`。
3. **注册时机偏离简报字面（有意，快照语义强制）**：简报"在 `app.events(...)` 启动钩子里手动调 register"与 DefaultCommandBus 构造即快照（后继注册静默无效，helidon javadoc 明令禁止延迟回调注册）冲突——注册改为 create 工厂内一次性完成，`app`/`vertx` 参数为装配锚点（requireNonNull），javadoc「装配语义」段落留档说明。

## Self-review
- 两 bus 均零新框架抽象：不 override execute、不 Future 包装、无 Verticle/插件扩展点；javadoc 覆盖装配语义/ServiceLoader 发现/事务边界三段（对齐 helidon 模板）。
- IT 冲突轨与 helidon 同构：多类型占位执行器经 `create` 的 Collection 直入撞型（services 文件只含 SampleCommandHandler，保持发现轨纯净）。
- 测试类收口 raw 泛型转换于单一 `discover()` 方法（对齐 helidon「单点 unchecked cast」先例）。
- 遗留：`docs/superpowers/plans/*.md` 两未跟踪文件为控制器产物，非本任务范围未动。
