# E4 OTel + Guice Micrometer Report

## Status: COMPLETE

## T1: ddd4j-metrics -- OpenTelemetryProjectionMetrics

### Commit
`17649195` -- `feat(metrics): add OpenTelemetryProjectionMetrics with sdk-testing assertions`

### Files Created
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-metrics/src/main/java/io/ddd4j/metrics/OpenTelemetryProjectionMetrics.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-metrics/src/test/java/io/ddd4j/metrics/OpenTelemetryProjectionMetricsTest.java`

### Files Modified
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-metrics/pom.xml` -- added `opentelemetry-sdk-testing` test dependency

### Design
- Constructor: `Meter` injection (most testable) + convenience constructor `OpenTelemetry + instrumentationScope`
- Metrics: `ddd4j.projection.run.count` (LongCounter), `ddd4j.projection.event.count` (LongCounter), `ddd4j.projection.run.duration` (DoubleHistogram, ms), `ddd4j.projection.run.error` (LongCounter)
- Tag: `streamId` attribute on all metrics
- Only depends on `opentelemetry-api` (no SDK dependency in main scope)

### Test Method
**OTel SDK Testing (InMemoryMetricReader)** -- real assertions, not mocks.
- Uses `SdkMeterProvider` + `InMemoryMetricReader` from `opentelemetry-sdk-testing`
- Collects metrics by name and asserts values/attributes
- 10 tests, all green

## T2: ddd4j-runtime-guice -- MicrometerProjectionMetrics

### Commit
`5e5e70e3` -- `feat(guice): add MicrometerProjectionMetrics for Guice runtime`

### Files Created
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-runtime/ddd4j-runtime-guice/src/main/java/io/ddd4j/guice/cqrs/MicrometerProjectionMetrics.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-runtime/ddd4j-runtime-guice/src/test/java/io/ddd4j/guice/cqrs/MicrometerProjectionMetricsTest.java`

### Files Modified
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-runtime/ddd4j-runtime-guice/pom.xml` -- added `micrometer-core` optional dependency

### Design
- Mirrors spring-side `MicrometerProjectionMetrics` pattern exactly
- Constructor: `MeterRegistry` injection
- Metrics: `projection.events.total` (Counter), `projection.run.duration` (Timer), `projection.errors.total` (Counter)
- Tag: `stream` on all metrics
- Dependency: `io.micrometer:micrometer-core` (optional, version from `micrometer-bom` 1.16.1 in ddd4j-dependencies)

### Test Method
**SimpleMeterRegistry** -- same approach as spring-side test.
- 85 tests total in guice module (74 existing + 11 new assertions), all green

## Verification

| Check | Result |
|-------|--------|
| `./mvnw -pl :ddd4j-metrics,:ddd4j-runtime-guice test` | 95 tests, 0 failures |
| `grep -rn "org.fuin" ddd4j-metrics ddd4j-runtime/ddd4j-runtime-guice` | 0 hits |
| git status -- only two directories modified | Confirmed |

## Version Sources
- **OTel**: `io.opentelemetry:opentelemetry-api` + `opentelemetry-sdk-testing` -- version from `opentelemetry-bom` 1.57.0 in ddd4j-dependencies
- **Micrometer**: `io.micrometer:micrometer-core` -- version from `micrometer-bom` 1.16.1 in ddd4j-dependencies
