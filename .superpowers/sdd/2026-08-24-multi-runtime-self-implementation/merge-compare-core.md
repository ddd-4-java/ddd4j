# ddd4j-core 2.0.x -> 3.0.x Merge Comparison Report

**Date**: 2026-08-27
**Branch**: feature/3.0.x (baseline)
**Source**: feature/2.0.x (logic reference)
**Scope**: ddd4j-core module only

---

## Summary

| Category | Count | Description |
|----------|-------|-------------|
| A (noise) | 36 | License headers, import migrations, intentional 3.0.x improvements |
| B (logic diff) | 1 | Constants.java Marker regression (implemented) |
| C (cross-module API) | 0 | No cross-module API changes requiring coordinator action |

**Total files examined**: 37 (main + test)
**Tests**: 317 run, 0 failures, 0 errors

---

## Per-File Classification

### Main Source Files (22 files)

#### NEW_IN_3.0.x (15 files) -- A class, no action

These files were added during 3.0.x development and do not exist in 2.0.x. They represent 3.0.x architectural evolution (EventStore convergence to core, projection metrics SPI, etc.).

| File | Lines | Purpose |
|------|-------|---------|
| `cqrs/eventstore/AsyncStoredEvent.java` | 162 | Reactive event store carrier (ADR-0005) |
| `cqrs/eventstore/AsyncEventStore.java` | 113 | Reactive EventStore SPI (Project Reactor) |
| `cqrs/eventstore/InMemoryEventStore.java` | 87 | In-memory EventStore default implementation |
| `cqrs/eventstore/EventStore.java` | 61 | Synchronous EventStore SPI interface |
| `cqrs/eventstore/StoredEvent.java` | 31 | Stored event record |
| `cqrs/eventstore/AggregateVersionConflictException.java` | 96 | Optimistic lock exception |
| `cqrs/eventstore/EventDeserializer.java` | 65 | Event payload deserializer with class name validation |
| `cqrs/eventstore/jackson/EventPayloadSerializer.java` | 104 | Jackson 3 event serializer (no polymorphic @class) |
| `cqrs/readmodel/ProjectionMetrics.java` | 93 | Projection metrics SPI |
| `cqrs/readmodel/NoopProjectionMetrics.java` | 36 | No-op metrics singleton |
| `cqrs/readmodel/ProjectionRunInfo.java` | 33 | Projection run snapshot record |
| `cqrs/readmodel/ProjectionStatus.java` | 72 | Projection status value object |
| `ddd/event/EntityIdRegistry.java` | 116 | EntityId type registry for deserialization |
| `constant/ProjectionConstants.java` | 86 | Projection shared constants |
| `constant/EventStoreConstants.java` | 80 | EventStore shared constants |

#### EXISTS_IN_BOTH (7 files) -- classified individually

| File | Class | Rationale |
|------|-------|-----------|
| `constant/Constants.java` | **B** | **REGRESSION**: 2.0.x ADR-0002 changed Marker fields to final String constants (core must not depend on SLF4J). 3.0.x reverted to mutable SLF4J Marker objects. **Fixed**: restored final String constants. |
| `ddd/model/AggregateRoot.java` | A | 3.0.x consolidated event handler cache (single ClassValue with replay flag vs 2.0.x dual cache). Exception handling improved (InvocationTargetException/IllegalAccessException specific catches). loadFromHistory() no longer calls clearDomainEvents() because replay=true prevents event registration. All improvements are intentional 3.0.x evolution. |
| `ddd/event/EntityIdPath.java` | A | 3.0.x added escape/unescape for values containing `/` or `\`, and EntityIdRegistry integration for custom EntityId deserialization. Significant feature enhancement, not regression. |
| `ddd/repository/RepositoryRegistry.java` | A | Added `clear()` method for testing cleanup. Pure addition, no behavioral change. |
| `context/ThreadContext.java` | A | Added `@Slf4j` and trace-level logging for get/bind/remove operations. Observability improvement. |
| `ddd/model/metadata/DomainModelInfo.java` | A | Replaced `FieldUtils.getAllFieldsList()` (commons-lang3) with zero-dependency `getAllFields()`. Dependency reduction. |
| `ddd/model/metadata/DomainModelHelper.java` | A | Added `@Slf4j` and debug logging for DomainModelInfo initialization. Observability improvement. |

### Test Source Files (11 files)

#### NEW_IN_3.0.x (8 files) -- A class, no action

| File | Lines | Tests |
|------|-------|-------|
| `cqrs/readmodel/ProjectionMetricsTest.java` | 170 | ProjectionMetrics SPI tests |
| `ddd/model/AggregateRootEventSourcingTest.java` | 162 | Event sourcing lifecycle tests |
| `ddd/event/EntityIdPathTest.java` | 146 | EntityIdPath escape/unescape/registry tests |
| `cqrs/readmodel/ProjectionStatusTest.java` | 125 | ProjectionStatus record tests |
| `cqrs/eventstore/EventStoreContractTest.java` | 119 | EventStore contract tests |
| `cqrs/eventstore/jackson/EventPayloadSerializerTest.java` | 98 | EventPayloadSerializer tests |
| `cqrs/eventstore/AsyncStoredEventTest.java` | 76 | AsyncStoredEvent tests |
| `cqrs/eventstore/InMemoryEventStoreTest.java` | 7 | InMemoryEventStore tests |

#### EXISTS_IN_BOTH (3 files) -- A class

| File | Rationale |
|------|-----------|
| `ddd/repository/RepositoryRegistryTest.java` | Added test for new `clear()` method. Matches 3.0.x addition. |
| `cqrs/readmodel/ProjectionRunnerTest.java` | Updated test: runAll exception isolation (3.0.x catches per-view, continues). Matches 3.0.x behavior change. |
| `ddd/event/DomainEventJsonTest.java` | Jackson 2->3 import migration (`com.fasterxml` -> `tools.jackson`). Version noise. |

#### DELETED_IN_3.0.x (1 file) -- A class

| File | Rationale |
|------|-----------|
| `ddd/event/DomainEventRoundTripTest.java` | Round-trip test removed. Coverage absorbed by DomainEventJsonTest + EntityIdPathTest in 3.0.x. |

---

## Implemented B-class Items

### 1. Constants.java -- Marker fields regression fix

**Commit**: `3c233641` (`merge(core): revert Constants.java Marker fields to final String constants per 2.0.x ADR-0002`)

**Problem**: 2.0.x ADR-0002 explicitly decided that ddd4j-core must not depend on SLF4J. The Marker fields were changed from SLF4J `Marker` objects to `final String` constants. The 3.0.x branch reverted this to mutable `Marker` objects with SLF4J imports, contradicting the architectural decision.

**Fix**: Restored 2.0.x semantics:
- `ACCESS_MARKER`, `AUTHZ_MARKER`, `BIZ_MARKER` are now `public static final String` constants
- Removed `import org.slf4j.Marker` and `import org.slf4j.MarkerFactory`
- No references to the old field names exist anywhere in the codebase (verified via grep)

**Risk**: Zero. No code references these fields. The change is purely architectural alignment.

---

## C-class Items (Cross-module API Changes)

None identified. All 3.0.x additions are module-internal. The new EventStore/AsyncEventStore SPIs are consumed by runtime modules (ddd4j-runtime-*), not exposed as cross-module API changes in ddd4j-core itself.

---

## Key Architectural Observations

1. **EventStore convergence**: 3.0.x moved EventStore SPI and implementations into ddd4j-core (from ddd4j-data). This is the single largest structural change, adding 15 new files.

2. **AggregateRoot event sourcing**: 3.0.x unified the event handler cache (single ClassValue with replay flag) and improved exception handling. The 2.0.x dual-cache design (AGGREGATE_HANDLER_CACHE + AGGREGATE_REPLAY_CACHE) was replaced by a single EVENT_HANDLER_CACHE with a `replay` parameter.

3. **EntityIdPath hardening**: 3.0.x added escape/unescape for path values containing `/` or `\`, and EntityIdRegistry for custom EntityId type deserialization. This is a significant security/correctness improvement.

4. **Constants.java**: The only regression found. The 2.0.x ADR-0002 principle (core stays SLF4J-free) was accidentally reverted. Fixed.
