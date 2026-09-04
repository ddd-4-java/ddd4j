# Task 7.12 Report — DropwizardProjectionScheduler

## Status
DONE — Stage 7 closes here (7/7 runtime adapters delivered).

## Deliverables
- `ddd4j-data-projection-dropwizard/pom.xml` — parent ddd4j-data; deps: ddd4j-data-projection + dropwizard-core (BOM 5.0.2) + dropwizard-testing (test)
- `DropwizardProjectionScheduler.java` — ViewScheduler SPI; static `create(Environment, Collection, ProjectionRunner)` factory; JDK ScheduledExecutorService + CronExpression (same pattern as Quarkus 7.6)
- `DropwizardProjectionViewManager.java` — ViewManager SPI; static `create(Environment, Collection, ProjectionRunner)` factory; start/stop/triggerOnce lifecycle
- `DropwizardProjectionSchedulerIT.java` — 3 IT: register+cancel, lifecycle, triggerOnce (real DropwizardTestSupport Jetty/Jersey container)
- `ProjectionDropwizardModuleIndependenceTest.java` — ArchUnit 4 rules: allowlist (io.dropwizard.. + jakarta..) + no_spring + no_quarkus + no_vertx + no_micronaut

## Gate
- Command: `./mvnw -pl ddd4j-data/ddd4j-data-projection-dropwizard -am install`
- Result: BUILD SUCCESS
- Tests: 8 total (3 IT + 5 ArchUnit), 0 failures
- Startup cost: ~2.65s/module (DropwizardTestSupport real Jetty/Jersey container, shared across 3 IT)
- dropwizard-bom version: 5.0.2

## Commit
`b404f5ac feat(data): ddd4j-data-projection-dropwizard——Application.run 工厂 + JDK ScheduledExecutorService 调度`

## Concerns
- None: zero new framework abstractions, same pattern as cqrs-dropwizard 6.9 and projection-quarkus 7.6.

## Files Created
1. `ddd4j-data/ddd4j-data-projection-dropwizard/pom.xml`
2. `ddd4j-data/ddd4j-data-projection-dropwizard/src/main/java/io/ddd4j/data/projection/dropwizard/DropwizardProjectionScheduler.java`
3. `ddd4j-data/ddd4j-data-projection-dropwizard/src/main/java/io/ddd4j/data/projection/dropwizard/DropwizardProjectionViewManager.java`
4. `ddd4j-data/ddd4j-data-projection-dropwizard/src/test/java/io/ddd4j/data/projection/dropwizard/DropwizardProjectionSchedulerIT.java`
5. `ddd4j-data/ddd4j-data-projection-dropwizard/src/test/java/io/ddd4j/data/projection/dropwizard/arch/ProjectionDropwizardModuleIndependenceTest.java`

## File Modified
- `ddd4j-data/pom.xml` — added `<module>ddd4j-data-projection-dropwizard</module>` in alphabetical slot
