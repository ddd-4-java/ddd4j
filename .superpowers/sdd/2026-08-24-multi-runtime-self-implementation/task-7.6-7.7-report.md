# Task 7.6+7.7 Report -- SpringProjectionScheduler + QuarkusProjectionScheduler

## Status: DONE

## Commits
- `a5f6bf7c` feat(data): ddd4j-data-projection-spring--Spring (@Component + SmartLifecycle + CronTrigger)
- `92a20bb2` feat(data): ddd4j-data-projection-quarkus--Quarkus CDI (@ApplicationScoped + ScheduledExecutorService)

## Gate Counts (combined install)
| Module | Tests | Failures | Errors |
|--------|-------|----------|--------|
| ddd4j-core | 261 | 0 | 0 |
| ddd4j-data-projection | 21 | 0 | 0 |
| ddd4j-data-projection-spring | 6 | 0 | 0 |
| ddd4j-data-projection-quarkus | 6 | 0 | 0 |
| **Total** | **294** | **0** | **0** |

## Deliverables

### A. ddd4j-data-projection-spring
- `pom.xml`: parent ddd4j-data; deps ddd4j-data-projection + spring-context + spring-tx; test spring-boot-starter-test 3.4.4
- `SpringProjectionScheduler.java`: @Component + SmartLifecycle; wraps TaskScheduler.schedule(CronTrigger); ViewScheduleHandle wraps ScheduledFuture
- `SpringProjectionViewManager.java`: @Component + SmartLifecycle; injects ProjectionView<?> beans + ProjectionRunner; auto-start on context boot (isAutoStartup=true, phase=MAX_VALUE-100); start/stop/triggerOnce lifecycle
- `SpringProjectionSchedulerIT.java`: @SpringBootTest 3 cases (schedule+cancel, lifecycle, triggerOnce)
- `ProjectionSpringModuleIndependenceTest.java`: ArchUnit 3 rules (allowlist, no_quarkus, no_micronaut)

### B. ddd4j-data-projection-quarkus
- `pom.xml`: parent ddd4j-data; deps ddd4j-data-projection + quarkus-arc (BOM); test quarkus-junit5 (BOM); module-level quarkus-bom import
- `QuarkusProjectionScheduler.java`: @ApplicationScoped; JDK ScheduledExecutorService + built-in CronExpression parser (6-field, no external deps); auto-reschedule on each run
- `QuarkusProjectionViewManager.java`: @ApplicationScoped; ArC Instance<ProjectionView<?>> + ProjectionRunner; @Observes Startup/Shutdown lifecycle
- `QuarkusProjectionSchedulerIT.java`: @QuarkusTest 3 cases (schedule+cancel, lifecycle, triggerOnce); TestProducer with @Produces stubs
- `ProjectionQuarkusModuleIndependenceTest.java`: ArchUnit 3 rules (allowlist, no_spring, no_micronaut)

### ddd4j-data/pom.xml
- Registered: ddd4j-data-projection-quarkus + ddd4j-data-projection-spring (alphabetical after projection-r2dbc, before mybatis)

## Concerns
- Quarkus module uses JDK ScheduledExecutorService as fallback (brief noted Quarkus 3.x programmatic scheduler may not be available). Included a minimal built-in CronExpression parser (6-field) to avoid spring-context dependency in Quarkus module.
- Quarkus IT uses @Produces test stubs for ProjectionRunner/ProjectionView (no H2/DataSource needed for scheduler-only tests).

## Fix Round 1

**Problem**: `SpringProjectionViewManager` lacked `SmartLifecycle`, so Spring users had to manually call `start()` — asymmetry with Quarkus `@Observes Startup`.

**Fix**: Added `SmartLifecycle` to `SpringProjectionViewManager` (import + implements + `isAutoStartup()` returning `true` + `getPhase()` returning `Integer.MAX_VALUE - 100`). Existing `start()`/`stop()`/`isRunning()` from `ViewManager` already satisfy the `SmartLifecycle` contract. Updated test to expect auto-started state.

**Commit**: `9e3f0967` fix(data): ddd4j-data-projection-spring——ViewManager 加 SmartLifecycle 自动启动

**Gate**: ddd4j-data-projection-spring 6/6 tests, BUILD SUCCESS.
