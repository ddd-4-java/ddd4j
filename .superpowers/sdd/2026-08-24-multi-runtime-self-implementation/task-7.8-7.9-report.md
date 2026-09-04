# Task 7.8+7.9 Report — MicronautProjectionScheduler + HelidonProjectionScheduler

## Status
COMPLETE — both modules BUILD SUCCESS, all tests green.

## Commits
1. `feat(data): ddd4j-data-projection-micronaut——BeanContext 调度 + @PostConstruct 自动启动`
2. `feat(data): ddd4j-data-projection-helidon——HelidonServiceLoader 发现 + ScheduledExecutorService`

## Gate Counts
- `./mvnw -pl ddd4j-data/ddd4j-data-projection-micronaut,ddd4j-data/ddd4j-data-projection-helidon,ddd4j-core -am install` — BUILD SUCCESS (11.5s)
- ddd4j-data-projection-micronaut: 6 tests (3 IT + 3 ArchUnit), 0 failures
- ddd4j-data-projection-helidon: 7 tests (3 IT + 4 ArchUnit), 0 failures
- ddd4j-core: existing tests pass (reactor dependency)

## Module Summary

### A. ddd4j-data-projection-micronaut
- **Scheduler**: `@Singleton` + JDK `ScheduledExecutorService` + built-in `CronExpression` parser (same pattern as Quarkus 7.6)
- **ViewManager**: `@Singleton` + `@PostConstruct` auto-start (mirrors Quarkus `@Observes Startup`)
- **POM**: module-level `micronaut-platform` BOM re-import (7.6 pattern), `micronaut-inject-java` annotation processor
- **IT**: `@MicronautTest` real BeanContext, 3 test cases (schedule/cancel, lifecycle, triggerOnce)
- **ArchUnit**: 3 rules — allowlist (`io.ddd4j..`, `java..`, `jakarta..`, `io.micronaut..`), `no_spring`, `no_quarkus`

### B. ddd4j-data-projection-helidon
- **Scheduler**: `@Singleton` (jakarta.inject) + JDK `ScheduledExecutorService` + built-in `CronExpression` parser
- **ViewManager**: `@Singleton` + constructor injection `Collection<ProjectionView<?>>` — **no `@PostConstruct`** (Helidon SE mode, javadoc documents manual `start()`)
- **POM**: `helidon-common-service-loader` + `jakarta.inject-api` (BOM-managed), no module-level helidon-bom re-import (confirmed at ddd4j-dependencies:4029)
- **IT**: manual assembly (no container), 3 test cases matching Quarkus/Micronaut pattern
- **ArchUnit**: 4 rules — allowlist (`io.ddd4j..`, `java..`, `jakarta..`, `io.helidon..`), `no_spring`, `no_quarkus`, `no_micronaut`

### Parent POM Registration
Both modules registered in `ddd4j-data/pom.xml` in alphabetical order:
`ddd4j-data-projection-helidon` (after projection), `ddd4j-data-projection-micronaut` (after projection-jdbi).

## Design Notes
- Both modules reuse the same minimal `CronExpression` 6-field parser from Quarkus 7.6 — zero new framework cron dependency.
- `HelidonProjectionScheduler.shutdown()` is a public method (no `@PreDestroy` in Helidon SE) — javadoc documents that integrators must call it manually or wire it to a lifecycle hook.
- `MicronautProjectionViewManager` uses double-wildcard cast `Collection<ProjectionView<?>> (Collection<?>) context.getBeansOfType(ProjectionView.class)` to bridge Micronaut's raw-type `getBeansOfType` return.
- Test isolation: `MicronautProjectionSchedulerIT` accounts for shared `@MicronautTest` context (second test's `stop()` may precede third test).

## Concerns
- None significant. Both modules follow the established 7.6 Quarkus pattern exactly, with framework-specific annotations swapped.
