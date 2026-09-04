# Task 7.6+7.7 Brief（合并派发）— SpringProjectionScheduler + QuarkusProjectionScheduler

## 背景
- ddd4j-core 已含完整 projection 调度契约（**不重定义**）：
  - `ViewScheduler`（schedule + ViewScheduleHandle.cancel/isActive）
  - `ViewManager`（start/stop/isRunning/triggerOnce）
  - `ProjectionRunner`（runOnce(view) → EventChunk，含读位置→拉块→业务投影→推进位置全链路）
- 本模块本质 = **用框架特定 cron 机制包装 `ProjectionRunner.runOnce(view)`**，让 ProjectionView 实例在指定 cron 表达式下持续增量拉取事件。

## 交付

### A. ddd4j-data-projection-spring（Spring WebMVC/WebFlux + Helidon-Spring）
1. `pom.xml`：parent ddd4j-data；依赖 `ddd4j-data-projection`（${revision}）+ `spring-context`（BOM）+ `spring-tx`（BOM）；test `spring-boot-starter-test`（模块局部 spring-boot-it.version=3.4.4，同 4.1 模式）。
2. `SpringProjectionScheduler.java`：`@Component` 实现 `ViewScheduler` + `SmartLifecycle`（Spring 生命周期适配）。构造器注入 `@Autowired ProjectionRunner<?> runner`；`schedule(viewName, cron, task)` → 用 `org.springframework.scheduling.support.CronTrigger` + `TaskScheduler.scheduleCronTask`（或 `ScheduledTaskRegistrar`）注册；`ViewScheduleHandle` 包装 `ScheduledFuture`（cancel 调 future.cancel）；`SmartLifecycle.start()` 遍历已注册 views 启动所有调度，`stop()` 取消全部；`isRunning()` 返回生命周期状态。
3. `SpringProjectionViewManager.java`：`@Component` 实现 `ViewManager`。注入所有 `ProjectionView<?>` Beans + `ProjectionRunner<?>`；`start()` → 遍历 views 按 cron 调 `springScheduler.schedule(view.getName(), view.getCron(), () -> runner.runOnce(view))`；`stop()` 取消全部 handle；`triggerOnce()` 调 `runner.runAll(views)`。
4. `SpringProjectionSchedulerIT.java`：`@SpringBootTest(classes=TestApp)`，真实 Spring `TaskScheduler` 容器。3 用例：①register + start + assert handle isActive + stop + assert inactive；②多 view 同时注册+start+stop 隔离；③triggerOnce 立即执行一个 view 的 runOnce。
5. ArchUnit ≥3：allowlist spring.context/spring.scheduling/spring-tx/org.springframework.stereotype..；`no_quarkus`、`no_micronaut`。

### B. ddd4j-data-projection-quarkus（Quarkus CDI）
1. `pom.xml`：parent ddd4j-data；依赖 `ddd4j-data-projection` + `quarkus-scheduler`（BOM）+ `quarkus-arc`（BOM）；test `quarkus-junit5`（BOM）+ `quarkus-jdbc-h2`（BOM）；**模块级 quarkus-bom 再导入**（5.1 模式）。
2. `QuarkusProjectionScheduler.java`：`@ApplicationScoped` 实现 `ViewScheduler`。注入 `@Inject Instance<ProjectionView<?>>`；`schedule(viewName, cron, task)` → 用 Quarkus `io.quarkus.scheduler.Scheduler`（或 `@Scheduled(cron)` 动态注册——Quarkus 3.x 支持编程式调度：`scheduler.schedule("viewName").cron(cron).task(task::run)`）；`ViewScheduleHandle` 包装 `Scheduled` 的 cancel。若 Quarkus 不支持编程式 cron 注册（某些版本只支持注解式），用 `ScheduledExecutorService` 兜底（与 javalin 模式同款）。
3. `QuarkusProjectionViewManager.java`：`@ApplicationScoped` 实现 `ViewManager`。注入所有 `ProjectionView<?>` Beans（ArC `Instance<ProjectionView<?>>`）；`start()` → 遍历 views 调 `scheduler.schedule(...)`；`stop()` 取消全部；`triggerOnce()` 调 `runner.runAll(views)`。
4. `QuarkusProjectionSchedulerIT.java`：`@QuarkusTest`，真实 ArC 容器。3 用例同 spring 模板。
5. ArchUnit ≥3：allowlist io.quarkus..；`no_spring`、`no_micronaut`。

## 门禁
`./mvnw -pl ddd4j-data/ddd4j-data-projection-spring,ddd4j-data/ddd4j-data-projection-quarkus,ddd4j-data/ddd4j-data-projection,ddd4j-core -am install` BUILD SUCCESS；报告 4 模块测试计数 + Spring 启动耗时 + Quarkus 启动耗时。

## 提交
两 commit：
- `feat(data): ddd4j-data-projection-spring——Spring 任务调度（@Component + SmartLifecycle + CronTrigger）`
- `feat(data): ddd4j-data-projection-quarkus——Quarkus CDI 调度（@ApplicationScoped + Scheduler 或 ScheduledExecutorService 兜底）`

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-7.6-7.7-report.md`。Reply ≤15 lines.
