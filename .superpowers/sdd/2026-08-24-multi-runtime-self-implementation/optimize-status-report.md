# E4 Status Report: getProjectionStatus() + Runtime Metrics Adapters

## Completion Status: DONE

## Commits (feature/3.0.x)

| Commit | Scope | Description |
|--------|-------|-------------|
| `fbada828` | `feat(core)` | ProjectionStatus record + ViewManager.getProjectionStatus() default method |
| `e4d2aa74` | `feat(spring)` | SpringJpaViewManager.getProjectionStatus() override + test |
| `11e99af5` | `feat(quarkus)` | QuarkusJpaViewManager.getProjectionStatus() override + test |
| `2b2a4bba` | `feat(guice)` | GuiceViewManager.getProjectionStatus() override + test |
| `c5214f77` | `feat(spring)` | MicrometerProjectionMetrics adapter + test |
| `a868f277` | `feat(quarkus)` | MicrometerProjectionMetrics adapter (Quarkus CDI) + test |

## Test Results

| Module | Tests | Failures |
|--------|-------|----------|
| ddd4j-core | 275 | 0 |
| ddd4j-runtime-spring | 32 | 0 |
| ddd4j-runtime-quarkus | 32 | 0 |
| ddd4j-runtime-guice | 78 | 0 |
| **Total** | **417** | **0** |

## T1: ProjectionStatus + ViewManager.getProjectionStatus()

- **ProjectionStatus**: immutable Java record with `streamId`, `nextEventNumber`, `running`, `lastRunAt` (nullable), `lastEventCount`, `lastError` (nullable). Compact constructor validates non-null streamId, non-negative numbers. Includes `baseline()` factory for untracked views.
- **ViewManager**: new `default getProjectionStatus(String streamId)` method returns `ProjectionStatus.baseline(streamId, isRunning())`. Non-breaking: existing implementations compile without changes.
- **Test**: ProjectionStatusTest (11 tests) covering record construction, validation, baseline factory, immutability/equals, ViewManager default behavior.

## T2: Runtime ViewManager Overrides

All three runtimes override `getProjectionStatus()`:
- **nextEventNumber**: read from the runtime's ProjectionPositionRepository (Spring JPA, Quarkus JPA, Guice InMemory/JDBC)
- **running**: `isRunning()` (unchanged)
- **lastRunAt / lastEventCount / lastError**: null / 0 / null (javadoc notes these are tracked by ProjectionMetrics callbacks; full persistence deferred)

Constructor changes are backward-compatible (new overloaded constructors, old constructors still work with null repository).

## T3: Micrometer ProjectionMetrics Adapters

**Micrometer management confirmed**: `ddd4j-dependencies/pom.xml` imports `micrometer-bom` version `1.16.1`. Quarkus-bom also manages micrometer.

| Runtime | Adapter | Dependency | Notes |
|---------|---------|-----------|-------|
| Spring | `MicrometerProjectionMetrics` | `micrometer-core` (optional) | Version from ddd4j-dependencies BOM |
| Quarkus | `MicrometerProjectionMetrics` | `micrometer-core` (optional) | Version from quarkus-bom |
| Guice | N/A | No BOM | Skipped; users can implement ProjectionMetrics directly |

Both adapters record:
- `projection.events.total` (Counter, tag: stream)
- `projection.run.duration` (Timer, tag: stream)
- `projection.errors.total` (Counter, tag: stream)

## Verification

- `grep -rn "org.fuin"` across 4 target directories: **ZERO HITS**
- `git status`: only ddd4j-core, ddd4j-runtime-spring, ddd4j-runtime-quarkus, ddd4j-runtime-guice modified (pre-existing ddd4j-data changes are unrelated)
- All commits on `feature/3.0.x`, **not pushed**

## Files Changed

### New files
- `ddd4j-core/src/main/java/io/ddd4j/core/cqrs/readmodel/ProjectionStatus.java`
- `ddd4j-core/src/test/java/io/ddd4j/core/cqrs/readmodel/ProjectionStatusTest.java`
- `ddd4j-runtime/ddd4j-runtime-spring/src/main/java/io/ddd4j/spring/cqrs/MicrometerProjectionMetrics.java`
- `ddd4j-runtime/ddd4j-runtime-spring/src/test/java/io/ddd4j/spring/cqrs/MicrometerProjectionMetricsTest.java`
- `ddd4j-runtime/ddd4j-runtime-spring/src/test/java/io/ddd4j/spring/cqrs/SpringJpaViewManagerTest.java`
- `ddd4j-runtime/ddd4j-runtime-quarkus/src/main/java/io/ddd4j/quarkus/cqrs/MicrometerProjectionMetrics.java`
- `ddd4j-runtime/ddd4j-runtime-quarkus/src/test/java/io/ddd4j/quarkus/cqrs/MicrometerProjectionMetricsTest.java`
- `ddd4j-runtime/ddd4j-runtime-quarkus/src/test/java/io/ddd4j/quarkus/cqrs/QuarkusJpaViewManagerTest.java`

### Modified files
- `ddd4j-core/src/main/java/io/ddd4j/core/cqrs/readmodel/ViewManager.java` (added default method)
- `ddd4j-runtime/ddd4j-runtime-spring/src/main/java/io/ddd4j/spring/cqrs/SpringJpaViewManager.java`
- `ddd4j-runtime/ddd4j-runtime-spring/pom.xml` (optional micrometer-core)
- `ddd4j-runtime/ddd4j-runtime-quarkus/src/main/java/io/ddd4j/quarkus/cqrs/QuarkusJpaViewManager.java`
- `ddd4j-runtime/ddd4j-runtime-quarkus/pom.xml` (optional micrometer-core)
- `ddd4j-runtime/ddd4j-runtime-guice/src/main/java/io/ddd4j/guice/cqrs/GuiceViewManager.java`
- `ddd4j-runtime/ddd4j-runtime-guice/src/test/java/io/ddd4j/guice/cqrs/GuiceViewManagerTest.java`

## Deviations

- None. All planned tasks completed as specified.
