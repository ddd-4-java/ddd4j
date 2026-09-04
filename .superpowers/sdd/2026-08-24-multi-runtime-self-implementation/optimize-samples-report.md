# P0-2b/E3: CQRS Sample Migration Report

## Status: COMPLETE

## Migration Approach: B (Delete local cqrs, use core SPI directly)

All 4 samples (dropwizard/helidon/micronaut/vertx) had identical local `cqrs/` packages containing:
- `CommandBus.java` - simple `Map<Class<?>, Function<?,?>>` command routing
- `ViewManager.java` - manual `lastPosition` tracking with `InMemoryEventStore`
- `ProjectionView.java` - `handleEvents(List<Object>)` interface

These were replaced with core SPI from `ddd4j-core/src/main/java/io/ddd4j/core/cqrs/`:

### Per-Sample Changes

| File | Change |
|------|--------|
| `cqrs/CommandBus.java` | **Deleted** - replaced by core `DefaultCommandBus` |
| `cqrs/ViewManager.java` | **Deleted** - replaced by new `InMemoryViewManager` |
| `cqrs/ProjectionView.java` | **Deleted** - `OrderSummaryView` now implements core `ProjectionView<Object>` |
| `command/CreateOrderCommand` | Added `implements io.ddd4j.core.cqrs.command.Command` |
| `command/CreateOrderCommandHandler` | Changed to implement `CommandExecutor<CreateOrderCommand>` with `supportedCommands()` + `execute() -> Result<String>` |
| `readmodel/OrderSummaryView` | Changed to `implements ProjectionView<Object>`, added `getCron()`, `getChunkSize()`, `getEventTypes()`, changed `handleEvents(List)` to `handleEvents(Collection)` |
| `readmodel/InMemoryEventChunkReader` | **New** - adapts `InMemoryEventStore` to core `EventChunkReader<Object>` |
| `readmodel/InMemoryViewManager` | **New** - implements core `ViewManager` using `ProjectionRunner` + `InMemoryProjectionPositionRepository` |
| App wiring | Uses `DefaultCommandBus(List.of(handler))` and `InMemoryViewManager` |
| Controller/Resource | Handles `Result<String>` from `commandBus.execute()` |

### Commits

```
f89d3bbc refactor(sample): dropwizard-cqrs -- migrate local cqrs to core SPI
2307207f refactor(sample): helidon-cqrs -- migrate local cqrs to core SPI
20b32cd4 refactor(sample): micronaut-cqrs -- migrate local cqrs to core SPI
ff26fc26 refactor(sample): vertx-cqrs -- migrate local cqrs to core SPI
```

### Test Results

| Sample | Tests | Pass | Fail | Error |
|--------|-------|------|------|-------|
| dropwizard-cqrs | 3 | 3 | 0 | 0 |
| helidon-cqrs | 3 | 3 | 0 | 0 |
| micronaut-cqrs | 3 | 3 | 0 | 0 |
| vertx-cqrs | 3 | 3 | 0 | 0 |
| javalin-cqrs | (existing) | pass | 0 | 0 |
| **Total** | **12+** | **12+** | **0** | **0** |

### Verification

- `grep -rn "org.fuin" ddd4j-samples --include="*.java"` = **0 hits**
- No leftover `cqrs/CommandBus.java`, `cqrs/ViewManager.java`, `cqrs/ProjectionView.java` in any of the 4 samples
- `git status` confirms only `ddd4j-samples/` directory was modified (plus other agents' changes to core/guice/testkit/mq)

### javalin-cqrs Confirmation

`ddd4j-sample-javalin-cqrs` has **no** local `cqrs/` package. It already uses core SPI (`TypedEventDispatcher`, `TypedEventHandler`, `Query`, `LambdaCondition`). No changes needed.

### Design Notes

- `InMemoryViewManager` and `InMemoryEventChunkReader` are sample-level helpers (not in core) because they bridge `InMemoryEventStore` (a sample convenience) to core's generic projection infrastructure. Production code would use database-backed implementations.
- The `ProjectionRunner` + `InMemoryProjectionPositionRepository` replaces the manual `lastPosition` field, giving proper position tracking with the core SPI contract.
- Micronaut's `InMemoryViewManager` and `InMemoryEventChunkReader` do NOT carry `@Singleton` because they're created by `@Factory` methods (Micronaut would see duplicate beans otherwise).
