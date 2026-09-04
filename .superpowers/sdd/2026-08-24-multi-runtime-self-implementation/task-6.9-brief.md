# Task 6.9 Brief — ddd4j-data-cqrs-dropwizard

## 背景与 BOM 实证
- `dropwizard-bom:5.0.2` 属性（:169）+ BOM 5.0.2 已导入（:2886-2888）。dropwizard-core 5.0.2 本地有。
- 已知事实（来自 Spring 6.3 fix round）：CQRS 适配器**零框架增强**——`extends DefaultCommandBus(super(executors))` + ServiceLoader 收 executor。dropwizard 不内置 CDI，**用 dropwizard-core 的 lifecycle 钩子**（Application.run 时手动调 `create(env, executors)`），与 javalin/vertx 同款 manual-registration 模式。

## 交付
1. `pom.xml`：parent ddd4j-data；依赖 `ddd4j-data-cqrs` + `dropwizard-core`（BOM 无版本 5.0.2）；test `dropwizard-testing`（BOM 无版本）。注册于 ddd4j-data/pom.xml 字母序（vertx 之后？验证两字串——"dropwizard" < "vertx"**不**成立，dropwizard 在 d 段、vertx 在 v 段——dropwizard 应在 cqrs-dropwizard 与 cqrs-external 之间或 cqrs-event-store-jdbi 之后，需实测：实测显示 dropwizard 在 v 段前——但 cqrs 适配组内字母序按「cqrs-*」基准，所以先看其他 cqrs-* 的相对位置）。
2. `src/main/java/io/ddd4j/data/cqrs/dropwizard/DropwizardCommandBus.java`：
   - `public static DropwizardCommandBus create(Application app, Collection<CommandExecutor<?>> executors)`——与 javalin/vertx 模式对称
   - 集成方在 Application.run 调此工厂，把 `Bootstrap` / `Environment` 注入的 service 实例经 ServiceLoader（用 `ServiceLoader.load(CommandExecutor.class)`）传入 executors 集合
   - **不**实现 `Application<Configuration>`（无运行时生命周期），**不**做 health check / metrics
   - 继承 DefaultCommandBus，无 execute override，无事务依赖
3. `DropwizardCommandBusIT`：`new DropwizardApplication<Configuration>()`（仅 Application 的 run 阶段被覆盖——`dropwizard-testing` 启动实际 Jersey/Jetty 容器开销大，**实证评估**：若 > 5s/IT 则关闭 DropwizardMetricsFactory 减小开销——简报要求 3 用例 ≥1 真实启动）
4. ArchUnit 3：allowlist io.dropwizard../jakarta..；no_spring、no_quarkus（外加 no_vertx/no_micronaut 防止 6.7+ 互窜）。

## 门禁
`./mvnw -pl ddd4j-data/ddd4j-data-cqrs-dropwizard -am install` BUILD SUCCESS；报告精确测试计数与启动开销（dropwizard-testing 可能 >5s/IT，brief 允许优化）。

## 提交
单 commit：`feat(data): ddd4j-data-cqrs-dropwizard——Application.run 工厂 + ServiceLoader 收 CommandExecutor`

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-6.9-report.md`。Reply ≤15 lines.
