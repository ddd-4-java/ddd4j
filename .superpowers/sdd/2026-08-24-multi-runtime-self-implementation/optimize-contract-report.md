# CQRS Runtime Contract Test Report

## Status: COMPLETE

## Commit List

| Commit | Message |
|--------|---------|
| `9ea10107` | `test(spring): add SpringCqrsRuntimeContractTest for cross-runtime CQRS contract` |
| `f84fcc7e` | `test(quarkus): add QuarkusCqrsRuntimeContractTest for cross-runtime CQRS contract` |
| `a9d82be1` | `test(guice): add GuiceCqrsRuntimeContractTest for cross-runtime CQRS contract` |

Note: The testkit infrastructure (`CqrsRuntimeContract.java`, `AbstractCqrsRuntimeContractTest.java`) was already committed in a prior session (`3744d594`).

## Files Created

### Testkit (already committed)
- `/ddd4j-runtime/ddd4j-runtime-testkit/src/main/java/io/ddd4j/runtime/testkit/CqrsRuntimeContract.java`
- `/ddd4j-runtime/ddd4j-runtime-testkit/src/main/java/io/ddd4j/runtime/testkit/AbstractCqrsRuntimeContractTest.java`

### Runtime Tests (new)
- `/ddd4j-runtime/ddd4j-runtime-spring/src/test/java/io/ddd4j/spring/cqrs/SpringCqrsRuntimeContractTest.java`
- `/ddd4j-runtime/ddd4j-runtime-quarkus/src/test/java/io/ddd4j/quarkus/cqrs/QuarkusCqrsRuntimeContractTest.java`
- `/ddd4j-runtime/ddd4j-runtime-guice/src/test/java/io/ddd4j/guice/cqrs/GuiceCqrsRuntimeContractTest.java`

## Contract Test Coverage (8 points x 3 runtimes = 24 tests)

| # | Contract Point | Assertion |
|---|---------------|-----------|
| 1 | Command routing: CmdA -> ExecutorA | `execute(CmdA)` returns success |
| 2 | Command routing: CmdB -> ExecutorB | `execute(CmdB)` returns success |
| 3 | Unregistered command | `execute(UnregisteredCmd)` throws Exception |
| 4 | Null command defense | `execute(null)` throws NPE or IAE |
| 5 | Position read-write roundtrip | save + findByStreamId returns same values |
| 6 | Position overwrite | save(3L) then save(15L), read back = 15L |
| 7 | ViewManager start lifecycle | `start()` then `isRunning() == true` |
| 8 | ViewManager stop lifecycle | `stop()` then `isRunning() == false` |

## Per-Runtime Test Results

| Runtime | Contract Tests | Status |
|---------|---------------|--------|
| Spring | 8 | ALL PASS |
| Quarkus | 8 | ALL PASS |
| Guice | 8 | ALL PASS |

## Assembly Strategy Decisions

| Runtime | CommandBus | ViewManager | ViewScheduler | PositionRepository |
|---------|-----------|-------------|---------------|-------------------|
| **Spring** | `SpringCommandBus` via AnnotationConfigApplicationContext (auto-discovers executor beans) | `SpringJpaViewManager` (needs ViewScheduler) | `SpringViewScheduler` (wraps ConcurrentTaskScheduler) | `InMemoryProjectionPositionRepository` (avoids JPA EntityManager) |
| **Quarkus** | `QuarkusCommandBus` via reflection injection of executorMap (same pattern as existing QuarkusCommandBusTest) | `QuarkusJpaViewManager` (direct instantiation, no CDI) | `NoopViewScheduler` (Quartz unavailable in pure unit test) | `InMemoryProjectionPositionRepository` (avoids JPA EntityManager) |
| **Guice** | `GuiceCommandBus` via constructor injection (List of executors) | `GuiceViewManager` (no-arg constructor) | `GuiceViewManager` (dual-role: implements both ViewManager and ViewScheduler) | `GuiceInMemoryProjectionPositionRepository` (Guice native impl) |

### Rationale: InMemoryProjectionPositionRepository for all runtimes

Spring's `SpringJpaProjectionPositionRepository` requires a `JpaRepository<SpringJpaProjectionPosition, String>` which needs JPA infrastructure (EntityManager, DataSource, H2). Quarkus's `QuarkusJpaProjectionPositionRepository` requires a CDI `Instance<EntityManager>`. Both are too heavy for contract tests that verify interface behavior, not persistence mechanism. The core `InMemoryProjectionPositionRepository` satisfies the `ProjectionPositionRepository` SPI contract perfectly and keeps tests lightweight (no DB, no JPA, no CDI container). The position read-write roundtrip and overwrite contracts are fully verified against the SPI interface boundary.

## Verification Results

- `grep -rn "org.fuin" ddd4j-runtime-testkit` = 0 hits (PASS)
- Only `src/test` files modified in the three runtime modules (PASS)
- All existing tests in spring/quarkus still pass (PASS)
- Guice has one pre-existing failing test (`GuiceJdbcProjectionPositionRepositoryTest.concurrentUpdatesOnSameStreamDoNotThrow`) from the parallel agent's JDBC repository work -- not caused by this change
