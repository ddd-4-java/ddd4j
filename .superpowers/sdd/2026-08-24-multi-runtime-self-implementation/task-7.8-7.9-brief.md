# Task 7.8+7.9 Brief（合并派发）— MicronautProjectionScheduler + HelidonProjectionScheduler

## 背景
- ddd4j-core 已含 ViewScheduler/ViewManager/ProjectionRunner（不重定义）
- 任务 7.6+7.7 已交付 Spring + Quarkus 调度器（含 SmartLifecycle 修复）
- BOM 实证：micronaut.version=4.10.17（:448）+ micronaut-platform 再导入（7.6 模块模式）；helidon-bom 3.2.18（:300）已导入（:4029）

## 交付

### A. ddd4j-data-projection-micronaut
1. `pom.xml`：parent ddd4j-data；依赖 `ddd4j-data-projection` + `io.micronaut:micronaut-context`（BOM）；test `io.micronaut.test:micronaut-test-junit5`（BOM）；**模块级 micronaut-platform 再导入**（7.6 模式防御父链混版，${micronaut.version}=4.10.17）
2. `MicronautProjectionScheduler.java`：`@Singleton` + `@Inject BeanContext` + `ViewScheduler`；`schedule(viewName, cron, task)` → Micronaut 4.x 支持 `ScheduledExecutorService`（从 `BeanContext.getBean(ScheduledExecutorService.class)` 或手动 `Executors.newSingleThreadScheduledExecutor`），用 `scheduleWithFixedDelay(task, initialDelay, period, unit)` + cron 解析为 period（简化版：固定 period，cron 留作 metadata——**准确实现需自定义 cron 解析**，但 Micronaut 没有内置 cron 支持——参考 Quarkus 7.6 模块的 JDK `ScheduledExecutorService` 模式）；`ViewScheduleHandle` 包装 `ScheduledFuture`。
3. `MicronautProjectionViewManager.java`：`@Singleton` + `@Inject Collection<ProjectionView<?>>` + `@Inject ProjectionRunner<?>`；`@PostConstruct` 自动 start（与 Quarkus @Observes Startup 对等）；stop 取消全部 handle。
4. `MicronautProjectionSchedulerIT.java`：`@MicronautTest`（junit5），真实 BeanContext；3 用例同 7.6 模板（register+cancel、lifecycle、triggerOnce）。
5. ArchUnit ≥3：allowlist `io.micronaut..`；`no_spring`、`no_quarkus`。

### B. ddd4j-data-projection-helidon
1. `pom.xml`：parent ddd4j-data；依赖 `ddd4j-data-projection` + `io.helidon:helidon-common-service-loader`（BOM）+ `jakarta.inject:jakarta.inject-api`（BOM）；test `io.helidon:helidon-common-testing-junit5`（BOM）；**无模块级 helidon-bom 再导入**（7.6 已证实 helidon-bom 3.2.18 已导入：4029）
2. `HelidonProjectionScheduler.java`：`@Singleton`（jakarta.inject）+ `@Inject BeanContainer`（ServiceLoader 风格——与 cqrs-helidon 的 HelidonCommandBus 同款 `HelidonServiceLoader` 模式）；`schedule` 用 JDK `ScheduledExecutorService`（JDBC cron→period，同 Quarkus 7.6 模式）；`ViewScheduleHandle` 包装 `ScheduledFuture`。
3. `HelidonProjectionViewManager.java`：`@Singleton` + `@Inject` 注入 views（ServiceLoader 发现 `META-INF/services/io.ddd4j.core.cqrs.readmodel.ProjectionView`）；无 `@PostConstruct`（Helidon SE 模式）——`start()` 由集成方手动调或 Helidon lifecycle hook 触发（javadoc 注明）。
4. `HelidonProjectionSchedulerIT.java`：`@HelidonTest`（junit5）或 `@HelidonSE`；3 用例同模板。
5. ArchUnit ≥3：allowlist `io.helidon..` + `jakarta.inject..`；`no_spring`、`no_quarkus`、`no_micronaut`。

## 门禁
`./mvnw -pl ddd4j-data/ddd4j-data-projection-micronaut,ddd4j-data/ddd4j-data-projection-helidon,ddd4j-core -am install` BUILD SUCCESS；报告各模块测试计数。

## 提交
两 commit：
- `feat(data): ddd4j-data-projection-micronaut——BeanContext 调度 + @PostConstruct 自动启动`
- `feat(data): ddd4j-data-projection-helidon——HelidonServiceLoader 发现 + ScheduledExecutorService`

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-7.8-7.9-report.md`。Reply ≤15 lines.
