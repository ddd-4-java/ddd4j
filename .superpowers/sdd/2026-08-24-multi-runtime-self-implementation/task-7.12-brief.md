# Task 7.12 Brief — DropwizardProjectionScheduler（阶段 7 最后一个调度器）

## 背景
- ddd4j-core 已含 ViewScheduler/ViewManager/ProjectionRunner（不重定义）
- 任务 7.6-7.11 已交付 spring/quarkus/micronaut/helidon/javalin/vertx 调度器（全部 approved）
- dropwizard-bom 5.0.2 已导入（:2882）——与 6.9 dropwizard cqrs 适配器同款模式
- **核心思路**：与 cqrs-dropwizard 同款——`Application.run(env)` 阶段手动调 `create(env, views, runner)` 静态工厂；调度器用 JDK `ScheduledExecutorService` + `CronExpression`（同 7.6 Quarkus 模式）

## 交付

### A. ddd4j-data-projection-dropwizard
1. `pom.xml`：parent ddd4j-data；依赖 `ddd4j-data-projection` + `dropwizard-core`（BOM 无版本 5.0.2）；test `dropwizard-testing`（BOM 无版本）
2. `DropwizardProjectionScheduler.java`：实现 `ViewScheduler` + static `create(Environment env, Collection<ProjectionView<?>> views, ProjectionRunner<?> runner)` 工厂（与 cqrs-dropwizard 的 `create(Application, Collection)` 工厂对称）。**不实现** `Application<Configuration>`（无生命周期）；`schedule(viewName, cron, task)` → JDK `ScheduledExecutorService` + `CronExpression` 计算 period → `scheduleAtFixedRate(task, initialDelay, period, TimeUnit.MILLISECONDS)`；`ViewScheduleHandle` 包装 `ScheduledFuture`。生命周期由 Dropwizard 的 `ServerFactory.build(Environment)` 或 `Bootstrap.run()` 回调触发（javadoc 注明集成方手动调 start）。
3. `DropwizardProjectionViewManager.java`：实现 `ViewManager`，构造器 `create(Environment env, Collection<ProjectionView<?>> views, ProjectionRunner<?> runner)` 静态工厂。`start()` 注册所有 views 的 cron 调度；`stop()` 取消全部；`triggerOnce()` 调 `runner.runAll(views)`。
4. `DropwizardProjectionSchedulerIT.java`：`DropwizardTestSupport` 启动真实 Jersey/Jetty 容器（与 cqrs-dropwizard 同款 IT）；3 用例同其他调度器模板（register+cancel、lifecycle、triggerOnce）。startup 成本实测：7.05s/IT → 2.35s/IT/3 用例（与 cqrs-dropwizard 一致）。
5. ArchUnit 4：allowlist `io.dropwizard..` + `jakarta..`；`no_spring`、`no_quarkus`、`no_vertx`、`no_micronaut`（与 cqrs-dropwizard 同款 4 条）

## 门禁
`./mvnw -pl ddd4j-data/ddd4j-data-projection-dropwizard -am install` BUILD SUCCESS；报告精确测试计数 + startup 成本 + dropwizard-bom 实际解析版本。

## 提交
单 commit：`feat(data): ddd4j-data-projection-dropwizard——Application.run 工厂 + JDK ScheduledExecutorService 调度`

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-7.12-report.md`。Reply ≤15 lines.
