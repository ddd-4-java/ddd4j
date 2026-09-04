# Task 7.10+7.11 Report — JavalinProjectionScheduler + VertxProjectionScheduler

## Status: DONE

## Commits
- `d4880aa4` feat(data): ddd4j-data-projection-javalin——Javalin 工厂 + ScheduledExecutorService 调度
- `2e01464b` feat(data): ddd4j-data-projection-vertx——Vertx 5 工厂 + ScheduledExecutorService 调度

## Gate Counts
- ddd4j-data-projection-javalin: 6 tests (3 IT + 3 ArchUnit), 0 failures
- ddd4j-data-projection-vertx: 6 tests (3 IT + 3 ArchUnit), 0 failures
- Combined gate (javalin + vertx + core -am install): BUILD SUCCESS

## Vert.x Version Evidence
- pom.xml: `<version>${vertx.version}</version>` on vertx-core and vertx-junit5
- ddd4j-dependencies: `${vertx.version}=5.1.5` (compile-scope depchain, NOT import)
- Same pattern as ddd4j-data-cqrs-vertx (6.7 lesson)

## Module Registration (ddd4j-data/pom.xml)
- projection-javalin: after projection-helidon, before projection-jpa (j-a < j-d)
- projection-vertx: after projection-spring (v > s)

## Design Decisions
1. Both modules use JDK ScheduledExecutorService + CronExpression (same pattern as Quarkus/Helidon)
2. Javalin: no `app.events()` on Javalin 7.x instance (events configured during `Javalin.create(config -> config.events(...))`). Shutdown is manual — javadoc documents lifecycle pattern.
3. Vertx: same ReschedulingHandle pattern (single-shot + auto-reschedule), not `setPeriodic`. More cron-accurate.
4. Both ViewManager factories internally create the scheduler (single entry point for integrate方).
5. `javalin-test` not in BOM — removed (cqrs-javalin has no test deps either).

## Concerns
- Javalin 7.x lifecycle: integrate方 must register shutdown hook during `Javalin.create()` config phase, not after. Javadoc documents this.
- Vertx DNS warning on macOS (netty-resolver-dns-native-macos missing) — cosmetic, not a blocker.

## Files Created
- `ddd4j-data/ddd4j-data-projection-javalin/` (pom + 2 main + 2 test)
- `ddd4j-data/ddd4j-data-projection-vertx/` (pom + 2 main + 2 test)
