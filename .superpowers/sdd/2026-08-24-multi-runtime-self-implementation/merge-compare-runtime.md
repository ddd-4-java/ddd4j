# ddd4j-runtime 2.0.x <-> 3.0.x Merge Comparison Report

**Date**: 2026-08-27
**Branch**: feature/3.0.x (baseline)
**Scope**: ddd4j-runtime/ (all 9 submodules)

## Executive Summary

**Result: No changes required.** The 3.0.x branch already contains all substantive logic from 2.0.x. Every file difference falls into A-class (noise) or is 3.0.x superseding 2.0.x with improvements. Zero B-class items, zero C-class items.

---

## A/B/C Classification

### A-Class: Pure Noise (no action) -- 109 files total

**A1: License header additions only (~80 files)**
All modified files in ddd4j-runtime-* gained the standard Apache-2.0 license header in 3.0.x. Zero logic change.

Affected modules: spring (14 annotation/util files), quarkus (16 annotation/context/cqrs files), guice (13 annotation/module files), dropwizard (4 files), helidon (5 files), micronaut (4 files), vertx (3 files), support (1 file), testkit (3 files).

**A2: New files in 3.0.x only (~29 files)**
These files exist in 3.0.x but not in 2.0.x. They implement the CQRS runtime adapters that were moved from ddd4j-data-cqrs-* (2.0.x location) into ddd4j-runtime (3.0.x location). All are 3.0.x-native additions:

| Module | New Files | Purpose |
|--------|-----------|---------|
| ddd4j-runtime-testkit | AbstractCqrsRuntimeContractTest, CqrsRuntimeContract | CQRS contract test base (E2, 3.0.x native) |
| ddd4j-runtime-spring | SpringCommandBus, MicrometerProjectionMetrics, + 4 tests | CQRS command/metrics adapters |
| ddd4j-runtime-quarkus | MicrometerProjectionMetrics, + 4 tests | CQRS metrics adapter |
| ddd4j-runtime-guice | GuiceCommandBus, GuiceConstants, Ddd4jCommandGuiceModule, Ddd4jJdbcProjectionGuiceModule, GuiceJdbcProjectionPositionRepository, MicrometerProjectionMetrics, + 5 tests | Full CQRS stack for Guice |
| ddd4j-runtime-vertx | VertxCqrsRuntimeContractTest | CQRS contract test |
| ddd4j-runtime-helidon | HelidonCqrsRuntimeContractTest | CQRS contract test |
| ddd4j-runtime-micronaut | MicronautCqrsRuntimeContractTest | CQRS contract test |
| ddd4j-runtime-dropwizard | DropwizardCqrsRuntimeContractTest | CQRS contract test |

**A3: Import/package noise (~all Modified files)**
Import statement changes (jakarta vs javax, package reorganization) -- version migration noise, keep 3.0.x.

---

### B-Class: Logic Differences (take 2.0.x) -- 0 items

After exhaustive per-file comparison, **every substantive logic change from 2.0.x is already present in 3.0.x**. Key verification points:

| File | 2.0.x Logic | 3.0.x Status |
|------|-------------|--------------|
| GuiceViewManager | Thread pool configurability, ProjectionPositionRepository, ProjectionMetrics, getProjectionStatus() | Already present with 4 constructors + full status API |
| SpringDomainEventPublisher | publish(Object) override with DomainEvent check | Already present (lines 68-80) |
| CdiDomainEventPublisher | publish(Object) override with CDI fire fallback | Already present (lines 65-76) |
| SpringJpaViewManager | ProjectionPositionRepository + ProjectionMetrics injection, getProjectionStatus() | Already present with 3 constructors + full status API |
| QuarkusJpaViewManager | CDI optional ProjectionPositionRepository + ProjectionMetrics, getProjectionStatus() | Already present with Instance<> injection |
| QuarkusCommandBus | Log message improvement ("command types" vs "executors") | Already present (line 64) |
| SpringJpaProjectionPosition | Immutable withNextEventNumber, ProjectionConstants.TABLE_NAME | Already present |
| QuarkusJpaProjectionPosition | Private fields, getters, immutable withNextEventNumber, ProjectionConstants | Already present |
| Ddd4jGuiceModule | Thread pool size @Named binding, GuiceConstants | Already present |
| SpringJpaProjectionPositionRepository | resetToZero() clarifying comment | Already present |

---

### C-Class: Cross-API Changes -- 0 items

No cross-module API signature changes detected.

---

## 3.0.x Improvements Over 2.0.x (kept as-is)

The 3.0.x branch made intentional improvements that supersede 2.0.x patterns:

1. **Immutable entities**: SpringJpaProjectionPosition/QuarkusJpaProjectionPosition.withNextEventNumber() returns new instance instead of mutating in-place (correct ProjectionPosition contract)
2. **Constant extraction**: GuiceConstants, ProjectionConstants centralize magic strings
3. **Private fields + getters**: QuarkusJpaProjectionPosition changed public fields to private (encapsulation)
4. **Table name unification**: SPRING_QRY_PROJECTION_POS / QUARKUS_QRY_PROJECTION_POS -> DDD4J_PROJECTION_POSITION
5. **CqrsRuntimeContract**: 3.0.x-native E2 contract test (per task instruction, keep 3.0.x)

---

## Test Results

| Module | Tests | Failures | Errors | Status |
|--------|-------|----------|--------|--------|
| ddd4j-runtime-testkit | 8 | 0 | 0 | PASS |
| ddd4j-runtime-spring | 32 | 0 | 0 | PASS |
| ddd4j-runtime-quarkus | 32 | 0 | 0 | PASS |
| ddd4j-runtime-micronaut | 10 | 0 | 0 | PASS |
| ddd4j-runtime-vertx | 9 | 0 | 0 | PASS |
| ddd4j-runtime-helidon | 9 | 0 | 0 | PASS |
| ddd4j-runtime-dropwizard | 18 | 0 | 0 | PASS |
| **Total** | **118** | **0** | **0** | **ALL PASS** |

**ddd4j-runtime-guice**: Could not compile due to missing transitive SNAPSHOT dependencies (ddd4j-data-crypto, ddd4j-data-logs, ddd4j-data-mybatisplus) which have compilation errors from parallel agent work on ddd4j-core (Constants.accessMarker missing). This is outside ddd4j-runtime scope.

---

## Implementation Commits

**None.** Zero files modified. No commits needed.

---

## Decision List (C-class)

**Empty.** No cross-module API changes detected.
