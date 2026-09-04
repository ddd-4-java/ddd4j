# Task 7.10+7.11 Brief（合并派发）— JavalinProjectionScheduler + VertxProjectionScheduler

## 背景
- ddd4j-core 已含 ViewScheduler/ViewManager/ProjectionRunner（不重定义）
- 任务 7.6+7.7+7.8+7.9 已交付 spring/quarkus/micronaut/helidon 调度器（全部 approved）
- javalin-bom 7.2.2 已导入（:6422）；vertx-stack-depchain 5.1.5 属性（:684）但**无 import scope**（6.7 task 已实证 vertx 需显式 ${vertx.version}）
- **核心思路**：两者均**无内置 cron 调度器**——用 JDK `ScheduledExecutorService` + built-in `CronExpression`（同 Quarkus 7.6 模式），手动注册（与 cqrs-javalin/cqrs-vertx 的 static `create()` 工厂同款哲学）

## 交付

### A. ddd4j-data-projection-javalin
1. `pom.xml`：parent ddd4j-data；依赖 `ddd4j-data-projection` + `javalin`（BOM 无版本）；test `javalin-test`（BOM 无版本）；**无模块级 BOM 再导入**（javalin-bom 已全局导入：6422）
2. `JavalinProjectionScheduler.java`：`JavalinProjectionScheduler implements ViewScheduler` + static `create(Javalin app, Collection<ProjectionView<?>> views, ProjectionRunner<?> runner)` 工厂（**与 cqrs-javalin 同款：手动注册，无 CDI 容器**）。`schedule(viewName, cron, task)` → JDK `ScheduledExecutorService` + `CronExpression` 计算 period → `scheduleAtFixedRate(task, initialDelay, period, TimeUnit.MILLISECONDS)`。`ViewScheduleHandle` 包装 `ScheduledFuture`。**@PreDestroy 由 Javalin 的 `app.events(server -> server.serverStopping(...))` 生命周期钩子** 触发 shutdown（javadoc 说明）。
3. `JavalinProjectionViewManager.java`：实现 `ViewManager`，构造器 `create(Javalin app, Collection<ProjectionView<?>> views, ProjectionRunner<?> runner)` 静态工厂（无 `@Inject`）。`start()` 注册所有 views 的 cron 调度；`stop()` 取消全部；`triggerOnce()` 调 `runner.runAll(views)`。**不实现** `SmartLifecycle`（Javalin 非 Spring）。
4. `JavalinProjectionSchedulerIT.java`：`Javalin.create().start(0)` 启动真实容器；3 用例同 spring 模板（register+cancel、lifecycle、triggerOnce）。
5. ArchUnit 3：allowlist `io.javalin..`；`no_spring`、`no_quarkus`。

### B. ddd4j-data-projection-vertx
1. `pom.xml`：parent ddd4j-data；依赖 `ddd4j-data-projection` + `vertx-core`（BOM 需显式 `${vertx.version}=5.1.5`，6.7 实证 vertx depchain 是 compile-scope 非 import）；test `vertx-junit5`（BOM 需显式 `${vertx.version}`）；**无模块级 BOM 再导入**
2. `VertxProjectionScheduler.java`：实现 `ViewScheduler` + static `create(Vertx vertx, Collection<ProjectionView<?>> views, ProjectionRunner<?> runner)` 工厂。`schedule` 用 `vertx.setPeriodic(period, id -> task.run())`（**Vertx 5.x API**——`setPeriodic` 返回 `long timerId`）。`ViewScheduleHandle` 包装 `timerId`，cancel 调 `vertx.cancelTimer(timerId)`。**注意**：`setPeriodic` 是 millis 粒度，需从 `CronExpression` 推算 period；**无精确 cron 语义**——javadoc 注明"以 millis period 逼近 cron 表达式，每轮前 N 次跳过"。
3. `VertxProjectionViewManager.java`：实现 `ViewManager`，构造器 `create(Vertx vertx, ...)` 静态工厂。生命周期由 Vertx `DeploymentOptions` 的 `instance()` 触发或集成方手动调。
4. `VertxProjectionSchedulerIT.java`：`Vertx.vertx()` 启动真实 Vertx 实例；3 用例同模板。
5. ArchUnit 3：allowlist `io.vertx..`；`no_spring`、`no_quarkus`。

## 门禁
`./mvnw -pl ddd4j-data/ddd4j-data-projection-javalin,ddd4j-data/ddd4j-data-projection-vertx,ddd4j-core -am install` BUILD SUCCESS；报告各模块测试计数。

## 提交
两 commit：
- `feat(data): ddd4j-data-projection-javalin——Javalin 工厂 + ScheduledExecutorService 调度`
- `feat(data): ddd4j-data-projection-vertx——Vertx 5 工厂 + setPeriodic 调度`

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-7.10-7.11-report.md`。Reply ≤15 lines.
