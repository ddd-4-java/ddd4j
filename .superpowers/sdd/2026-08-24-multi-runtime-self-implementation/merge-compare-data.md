# ddd4j-data 2.0.x <-> 3.0.x Merge Comparison Report

**Date**: 2026-08-27
**Branch**: feature/3.0.x (baseline)
**Scope**: ddd4j-data/ directory only
**Excluded**: ddd4j-data-event-store/ (parallel session), ddd4j-data/pom.xml (parallel session)

---

## 1. Summary

The ddd4j-data module has undergone a **deliberate architectural simplification** between 2.0.x and 3.0.x. The core EventStore SPI moved from ddd4j-data (2.0.x) to ddd4j-core (3.0.x), with a simpler synchronous contract and a richer async contract. All event-store implementations (JPA, R2DBC, ESDB) were rewritten from scratch in 3.0.x against the new core SPI.

**A/B/C Statistics**:
- **A-class (pure noise)**: 7 modules, ~50 files -- license headers and import path changes only
- **B-class (logic to port)**: **0 items** -- all differences are intentional architectural evolution
- **C-class (cross-module API)**: 15 modules -- cqrs-*, projection-*, event-store SPI, jdbi, panache

**No implementation commits** were made -- there are no B-class items to act on.

---

## 2. Architecture Evolution: 2.0.x vs 3.0.x Core SPI

### 2.0.x EventStore SPI (in ddd4j-data-event-store)

```
interface EventStore {
    void append(String aggregateType, AggregateRootId aggregateId,
                List<? extends DomainEvent<?>> events, long expectedVersion);
    List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId);
    List<StoredEvent> read(String aggregateType, AggregateRootId aggregateId,
                           long fromVersion, long toVersion);
    List<StoredEvent> readAll(long fromPosition, int limit);
}

record StoredEvent(EventId eventId, String aggregateType, AggregateRootId aggregateId,
                   long version, long position, ZonedDateTime timestamp,
                   DomainEvent<?> payload, EventId correlationId, EventId causationId)
```

### 3.0.x EventStore SPI (in ddd4j-core)

```
interface EventStore {  // sync
    void append(String aggregateId, List<Object> events, long expectedVersion);
    List<StoredEvent> read(String aggregateId);
    List<StoredEvent> readAll(long fromPosition, int limit);
}

record StoredEvent(String aggregateId, long version, Object event,
                   long position, Instant timestamp)

interface AsyncEventStore {  // reactive
    Mono<Void> append(String aggregateType, AggregateRootId aggregateId,
                      Flux<? extends DomainEvent<?>> events, long expectedVersion);
    Flux<AsyncStoredEvent> read(String aggregateType, AggregateRootId aggregateId);
    Flux<AsyncStoredEvent> read(String aggregateType, AggregateRootId aggregateId,
                                long fromVersion, long toVersion);
    Flux<AsyncStoredEvent> readAll(long fromPosition, int limit);
}
```

**Key differences**:
- Sync SPI: removed aggregateType, DomainEvent typing, eventId/correlationId/causationId, version range query
- Async SPI: preserved all 2.0.x richness (aggregateType, DomainEvent, version range)
- StoredEvent: simplified to 5-field record (sync), AsyncStoredEvent retains full metadata
- Timestamp: Instant (3.0.x) vs ZonedDateTime (2.0.x)

---

## 3. Per-File A/B/C Classification

### 3.1 Shared Modules (Same Package, Both Branches)

| Module | Files | Classification | Notes |
|--------|-------|---------------|-------|
| ddd4j-data-crypto | 14 | **A (noise)** | License + import only |
| ddd4j-data-datascope | 3+1 test | **A (noise)** | License + import only |
| ddd4j-data-external | 23 | **A (noise)** | License + import only |
| ddd4j-data-jpa | 10+2 tests | **A (noise)** | License + import only |
| ddd4j-data-logs | 3 | **A (noise)** | License + import only |
| ddd4j-data-mybatis | 10+1 test | **A (noise)** | License + import only |
| ddd4j-data-mybatisplus | 11+6 tests | **A (noise)** | License + import only |

**Total A-class**: ~50 files across 7 modules. Zero substantive logic differences.

### 3.2 Event-Store Modules (Different Packages, New Implementations)

| Module (3.0.x) | Files | Classification | Notes |
|-----------------|-------|---------------|-------|
| ddd4j-data-event-store-jpa | 6 main + 1 test | **C (new impl)** | Rewritten against 3.0.x core SPI |
| ddd4j-data-event-store-r2dbc | 2 main + 2 tests | **C (new impl)** | Sync + Async dual-track, new impl |
| ddd4j-data-event-store-esdb | 1 main + 2 tests | **C (new impl)** | New module, no 2.0.x equivalent |

These are **not merge candidates** -- they are entirely new implementations aligned with the 3.0.x core SPI. The 2.0.x equivalents had different SPI contracts (aggregateType, DomainEvent, Spring Data) and cannot be "merged" without breaking the 3.0.x architecture.

---

## 4. Event-Store Implementation Detail Comparison

### 4.1 JPA

| Aspect | 2.0.x | 3.0.x |
|--------|-------|-------|
| Package | `io.ddd4j.data.eventstore.jpa` | `io.ddd4j.data.event.store.jpa` |
| SPI | ddd4j-data EventStore | ddd4j-core EventStore |
| Spring | `@Component`, `@Transactional`, Spring Data JPA | Pure JPA, programmatic `EntityTransaction` |
| PK | `position` (IDENTITY auto-increment) | `(aggregate_id, version)` composite |
| Table | `ddd4j_stored_event` | `DDD4J_EVENT_STORE` |
| Fields | eventId, aggregateType, aggregateId, version, eventType, payload, correlationId, causationId, tenantId, createdAt | aggregateId, version, position, eventType, eventId, payload, timestamp |
| Repository | `SpringDataStoredEventRepository` (Spring Data) | `JpaStoredEventRepository` + `JpaStoredEventRepositoryImpl` (pure JPA) |
| Version check | `MAX(version)` with `PESSIMISTIC_WRITE` lock | `COUNT(*)` (semantically equivalent for append-only) |
| Serialization | `EventPayloadSerializer` (Jackson) | `JsonKit` + `EventDeserializer` |

### 4.2 R2DBC

| Aspect | 2.0.x | 3.0.x (sync) | 3.0.x (async) |
|--------|-------|--------------|---------------|
| Package | `...eventstore.r2dbc` | `...event.store.r2dbc` | `...event.store.r2dbc` |
| SPI | AsyncEventStore (data) | EventStore (core) | AsyncEventStore (core) |
| Spring | Zero Spring | Zero Spring | Zero Spring |
| Bridge | N/A (native reactive) | `block()` bridge | Native reactive |
| aggregateType | Yes | No | Yes |
| DomainEvent typing | Yes | No (Object) | Yes |
| eventId/corr/caus | Yes | No | Yes |
| Version range read | Yes | No | Yes |
| Table init | DDL external | `CREATE TABLE IF NOT EXISTS` | `CREATE TABLE IF NOT EXISTS` |

### 4.3 ESDB (3.0.x only)

New module with no 2.0.x equivalent. Implements sync `EventStore` against EventStoreDB gRPC client. Maps SPI expectedVersion to ESDB ExpectedRevision. Filters system streams in readAll.

---

## 5. 2.0.x-Only Files Classification

### 5.1 Replaced by 3.0.x (event-store-jpa, event-store-r2dbc)

These modules exist in both versions but with completely different implementations:

| 2.0.x Module | 3.0.x Equivalent | Status |
|--------------|-------------------|--------|
| ddd4j-data-event-store-jpa | ddd4j-data-event-store-jpa | **Replaced** -- new impl against core SPI |
| ddd4j-data-event-store-r2dbc | ddd4j-data-event-store-r2dbc | **Replaced** -- new impl, sync+async dual-track |

### 5.2 Truly Missing from 3.0.x

| Module | Files | Status | Recommendation |
|--------|-------|--------|----------------|
| ddd4j-data-event-store (SPI) | 7 files | **Superseded** by ddd4j-core | Core SPI replaces data-layer SPI; no action needed |
| ddd4j-data-event-store-jdbi | 3 files | **Truly missing** | 3.0.x has no JDBI implementation; coordinate with maintainer |
| ddd4j-data-event-store-panache | 5 files | **Truly missing** | 3.0.x has no Panache/Quarkus implementation; coordinate |

### 5.3 Migrated to ddd4j-runtime-* (per protocol)

| Module | Files | Status |
|--------|-------|--------|
| ddd4j-data-cqrs | 5 | Migrated to ddd4j-runtime-* |
| ddd4j-data-cqrs-dropwizard | 5 | Migrated to ddd4j-runtime-* |
| ddd4j-data-cqrs-helidon | 5 | Migrated to ddd4j-runtime-* |
| ddd4j-data-cqrs-javalin | 5 | Migrated to ddd4j-runtime-* |
| ddd4j-data-cqrs-micronaut | 6 | Migrated to ddd4j-runtime-* |
| ddd4j-data-cqrs-quarkus | 5 | Migrated to ddd4j-runtime-* |
| ddd4j-data-cqrs-spring | 9 | Migrated to ddd4j-runtime-* |
| ddd4j-data-cqrs-vertx | 5 | Migrated to ddd4j-runtime-* |
| ddd4j-data-projection | 6 | Migrated to ddd4j-runtime-* |
| ddd4j-data-projection-jpa | 6 | Migrated to ddd4j-runtime-* |
| ddd4j-data-projection-panache | 1 | Migrated to ddd4j-runtime-* |

**Note**: These directories appear in the 3.0.x working tree (created by a parallel session) but are NOT committed to the feature/3.0.x branch. They are tracked by the parallel agent and were excluded from this analysis per the protocol.

---

## 6. Implementation Commits

**None.** There are no B-class items to implement. All differences are either:
- A-class noise (license/import changes in shared modules)
- C-class architectural evolution (event-store modules rewritten against new core SPI)

---

## 7. Test Results

```
./mvnw test -pl :ddd4j-data-event-store-jpa,:ddd4j-data-event-store-r2dbc,:ddd4j-data-event-store-esdb -am

BUILD SUCCESS
Total time: 25.959 s

ddd4j-data-event-store-jpa .............. SUCCESS [0.623 s]
ddd4j-data-event-store-r2dbc ............ SUCCESS [2.230 s]
ddd4j-data-event-store-esdb ............. SUCCESS [3.112 s]

Tests run: 17, Failures: 0, Errors: 0, Skipped: 6
(Skipped: ESDB integration tests -- Docker not available in this environment)
```

All unit tests and JPA/R2DBC integration tests pass. ESDB IT tests skipped (require Docker/EventStoreDB container).

---

## 8. C-Class Cross-Module API List

These items affect the core SPI contract and require coordination with the core module maintainer:

| Item | Location | Impact |
|------|----------|--------|
| EventStore SPI simplified | ddd4j-core | Sync path lost aggregateType, DomainEvent typing, version range query |
| StoredEvent record simplified | ddd4j-core | 5 fields vs 2.0.x 9-field class |
| AsyncEventStore added | ddd4j-core | New reactive SPI preserves 2.0.x richness |
| AsyncStoredEvent added | ddd4j-core | Rich record with eventId, correlationId, causationId |
| EventPayloadSerializer moved | ddd4j-core | From data-event-store to core |
| AggregateVersionConflictException moved | ddd4j-core | From data-event-store to core |
| EventStoreConstants added | ddd4j-core | Shared table/column name constants |
| EventDeserializer added | ddd4j-core | Class.forName with fallback to Map |

---

## 9. Deviations and Notes

1. **Parallel session interference**: The working tree contains `ddd4j-data-event-store/` (untracked) and `ddd4j-data/pom.xml` (modified) from a parallel agent session. These were excluded per protocol. Additionally, cqrs-* and projection-* directories exist in the working tree but are NOT committed to feature/3.0.x -- they appear to be created by the parallel session restoring 2.0.x modules.

2. **Version checking strategy**: 3.0.x uses `COUNT(*)` for version checking while 2.0.x used `MAX(version)`. For append-only event stores these are semantically identical. The2.0.x approach with `PESSIMISTIC_WRITE` lock is arguably safer for high-concurrency scenarios, but the 3.0.x approach with composite PK `(aggregate_id, version)` provides the same guarantee via PK uniqueness constraint.

3. **aggregate_type column**: The 3.0.x schema includes `aggregate_type` as a nullable column (defined in EventStoreConstants and CREATE TABLE SQL) for dual-track compatibility. The sync implementations (JPA, R2DBC sync) do NOT write it; only R2dbcAsyncEventStore writes it. This is intentional per the 3.0.x design.

4. **No JDBI or Panache implementations in 3.0.x**: These are genuinely missing. If needed, they should be implemented as new modules against the 3.0.x core SPI, not ported from 2.0.x.
