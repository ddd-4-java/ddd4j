# Task 8.2 Report — 4 CQRS Samples (micronaut-cqrs / helidon-cqrs / vertx-cqrs / dropwizard-cqrs)

## Status: COMPLETED

## Commits
1. `d04d37b4` — feat(sample): ddd4j-sample-micronaut-cqrs——Micronaut CQRS 集成示例
2. `04c65aa8` — feat(sample): ddd4j-sample-helidon-cqrs——Helidon CQRS 集成示例
3. `12617b44` — feat(sample): ddd4j-sample-vertx-cqrs——Vert.x CQRS 集成示例
4. `8da892e8` — feat(sample): ddd4j-sample-dropwizard-cqrs——Dropwizard CQRS 集成示例

## Gate: BUILD SUCCESS
Combined test of all 4 samples: `mvnw test -pl ddd4j-sample-micronaut-cqrs,ddd4j-sample-helidon-cqrs,ddd4j-sample-vertx-cqrs,ddd4j-sample-dropwizard-cqrs -am` → BUILD SUCCESS

## Per-Sample Test Counts
| Sample | Tests | Failures | Errors |
|--------|-------|----------|--------|
| ddd4j-sample-micronaut-cqrs | 3 | 0 | 0 |
| ddd4j-sample-helidon-cqrs | 3 | 0 | 0 |
| ddd4j-sample-vertx-cqrs | 3 | 0 | 0 |
| ddd4j-sample-dropwizard-cqrs | 3 | 0 | 0 |
| **Total** | **12** | **0** | **0** |

## Architecture
Each sample demonstrates the full CQRS flow:
- **Write-side**: Command → CommandHandler → Aggregate → InMemoryEventStore
- **Read-side**: EventStore → ProjectionView (OrderSummaryView) → QueryModel

### Runtime-Specific Adaptations
- **Micronaut**: `@Singleton` DI, Micronaut HTTP `@Controller`, `@MicronautTest`
- **Helidon**: CDI `@ApplicationScoped`, JAX-RS `@Path`, `@HelidonTest`
- **Vert.x**: Static wiring, Vert.x Router, `VertxExtension`
- **Dropwizard**: Static wiring, Dropwizard Application/JAX-RS Resource, direct unit tests

### Shared Components (same logic, different annotations)
- `CreateOrderCommand` / `CreateOrderCommandHandler`
- `EventSourcingOrderRepository`
- `OrderSummaryView` / `OrderSummaryViewEntity`
- `InMemoryEventStore` / `CommandBus` / `ViewManager`

## Concerns
- The `ddd4j-data-event-store`, `ddd4j-data-cqrs-*`, `ddd4j-data-projection-*` modules have no source code on the `feature/3.0.x` branch (only stale target dirs from 2.0.x). The samples use self-contained in-memory implementations instead of the framework CQRS modules.
- The `ddd4j-data/pom.xml` was temporarily modified to add the missing modules but was reverted.
- Helidon 3.2.x uses `jakarta.*` imports (not `javax.*`).
- Dropwizard 5.0.2 testing module has a `javax.ws.rs` dependency conflict.

---

## Fix Round 1

### Commit
`2a906ca9` — fix(sample): helidon-cqrs CDI 接入 + dropwizard-cqrs HTTP 测试 + micronaut-cqrs README 修正 + samples pom 字母序

### Gate: BUILD SUCCESS
Combined test: `mvnw -pl ddd4j-samples/ddd4j-sample-helidon-cqrs,ddd4j-samples/ddd4j-sample-dropwizard-cqrs,ddd4j-samples/ddd4j-sample-micronaut-cqrs -am install` → BUILD SUCCESS (9/9 tests, 0 failures)

### Changes

**Important #1 — micronaut-cqrs README**: Rewrote to describe actual `InMemoryEventStore`/`CommandBus`/`ViewManager` components; added note about framework module migration to 3.0.x.

**Important #2 — helidon-cqrs CDI**: Replaced static fields in `HelidonCqrsApplication` with CDI-managed `HelidonCqrsBeans` (`@ApplicationScoped` + `@Produces` factory methods). `OrderResource` now uses `@Inject` constructor. Test uses `@HelidonTest` with CDI injection (no `@AddBean` — `beans.xml` `bean-discovery-mode="annotated"` handles discovery). Lifecycle via `@Observes @Initialized`/`@BeforeDestroyed` with `Instance<ViewManager>` to avoid CDI self-reference cycle.

**Important #3 — dropwizard-cqrs HTTP tests**: Added `dropwizard-testing` + `jersey-bom` deps to pom.xml. Rewrote test to use `ResourceExtension` + `DropwizardExtensionsSupport` for HTTP-level testing (POST /orders, GET /orders/{id}, 409 idempotency). Test shares `DropwizardCqrsApplication` static components.

**Alphabetical ordering**: Reordered CQRS group in `ddd4j-samples/pom.xml` (dropwizard → helidon → javalin → micronaut → quarkus → vertx).

### Files Changed
- `ddd4j-samples/pom.xml` — CQRS group alphabetical order
- `ddd4j-samples/ddd4j-sample-micronaut-cqrs/README.md` — accurate description
- `ddd4j-samples/ddd4j-sample-helidon-cqrs/` — new `HelidonCqrsBeans.java`, updated `HelidonCqrsApplication.java`, `OrderResource.java`, `HelidonOrderCqrsIT.java`, `beans.xml`
- `ddd4j-samples/ddd4j-sample-dropwizard-cqrs/` — updated `pom.xml`, `DropwizardOrderCqrsIT.java`
