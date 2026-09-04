# Task 6.7+6.8 Brief（合并派发）— JavalinCommandBus + VertxCommandBus

## 背景与 BOM 实证（控制器预检）
- **javalin-bom 7.2.2** 已导入（:6426-6428）；`javalin.version=7.2.2` 属性（:360）；本地 .m2 有 7.2.2 与 7.2.3（≥7 即可）。
- **vertx-stack-depchain 5.1.5** 已导入（:9997-9999）；`vertx.version=5.1.5` 属性（:684）；本地 4.5.30 + 4.5.32 旧版——**Vertx 5.x 与 4.x API 差异**（Vertx 5 = `io.vertx.core.Future` 与 `io.vertx.core.Vertx` 接口化），模板须使用 5.x 模式。
- 两模块均**轻量级**——javalin 4→5 已是 `io.javalin.Javalin` 静态 create 入口，vertx 4→5 已是 `io.vertx.core.Vertx.vertx()` 工厂方法。

## 交付

### A. ddd4j-data-cqrs-javalin
1. `pom.xml`：parent ddd4j-data；依赖 `ddd4j-data-cqrs` + `javalin`（BOM 无版本）。
2. `src/main/java/io/ddd4j/data/cqrs/javalin/JavalinCommandBus.java`：**纯手注册**（无 CDI 容器——Javalin 不内置）。`public static JavalinCommandBus create(Javalin app, Collection<CommandExecutor<?>> executors)` 静态工厂（与 panache 5.4/helidon 6.5 的"集成方供 Bean"模式一致）→ 在 `app.events(...)` 启动钩子里手动调 `register` 一次。**事务**由集成方用 `app.get(...)` 取 tx handler 自管——javadoc 明确不参与事务。继承 DefaultCommandBus。
3. `JavalinCommandBusIT`：`Javalin.create(config -> cfg.showTestBanner = false).start(0)`（**test 模式**）→ **真实 Javalin 启动 + ServiceLoader 收 CommandExecutor 候选**（用 jdk SPI 而非 javalin 容器——`META-INF/services/io.ddd4j.core.cqrs.command.CommandExecutor` 声明）。3 用例：execute happy/冲突/未注册 ISE。**注**：javalin 启动会绑 0 端口——`start(0)` 表示随机端口。
4. ArchUnit 3：allowlist io.javalin../jakarta..；no_spring、no_quarkus。

### B. ddd4j-data-cqrs-vertx
1. `pom.xml`：parent ddd4j-data；依赖 `ddd4j-data-cqrs` + `vertx-core`（BOM 无版本）；test `vertx-junit5`（BOM 无版本，但**本地 .m2 仅有 4.5.x 旧版**——实证优先；如 BOM 5.x 缺失需显式 `${vertx.version}`）。
2. `VertxCommandBus.java`：`public static VertxCommandBus create(Vertx vertx, Collection<CommandExecutor<?>> executors)`（Vertx 5.x `Vertx.vertx()` 工厂方法获取 Vertx 实例）。**execute 同步返回 Result**（Vertx 异步 API 不暴露——bus 内部同步处理 Result，Vertx 集成方把 `execute` 委托给 worker thread 即可，javadoc 注明）。继承 DefaultCommandBus。
3. `VertxCommandBusIT`：`Vertx.vertx()` → **真实 Vertx 实例生命周期**→ close in @AfterEach；3 用例同模板（Vertx 上下文线程执行 execute 验证 DefaultCommandBus 同步性不依赖 Vertx 异步回路）。
4. ArchUnit 3：allowlist io.vertx../jakarta..；no_spring、no_quarkus。

## 门禁
`./mvnw -pl ddd4j-data/ddd4j-data-cqrs-javalin,ddd4j-data/ddd4j-data-cqrs-vertx -am install` BUILD SUCCESS；报告两模块精确测试计数 + dependency:tree javalin-bom 实际解析版本（应 7.2.x）+ vertx-stack-depchain 解析版本（应 5.1.x）——若 5.1.x 解析为 0 检查 BOM 实际是否含该坐标（如缺则显式加 vertx.version 属性到模块 dependencyManagement）。

## 提交
两 commit（任务范围清晰分离，便于 6.9 复用 commit 拆点）：
- `feat(data): ddd4j-data-cqrs-javalin——Javalin create 工厂 + ServiceLoader 收 CommandExecutor`
- `feat(data): ddd4j-data-cqrs-vertx——Vertx 5 工厂 + ServiceLoader 收 CommandExecutor`

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-6.7-6.8-report.md`。Reply ≤15 lines.
