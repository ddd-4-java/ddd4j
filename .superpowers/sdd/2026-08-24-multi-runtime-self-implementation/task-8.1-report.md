# Task 8.1 Report -- ddd4j-sample-order-application Spring CQRS 改造

## Status: DONE

## Commit
`feat(sample): ddd4j-sample-order-application 改造为 Spring CQRS 完整集成示例`

## Gate
`./mvnw -pl ddd4j-samples/ddd4j-sample-order-application -am install` -> BUILD SUCCESS

## Test Count
- OrderCqrsIT: 3 tests (createOrder_thenProjection, getOrder_fromReadModel, createOrder_idempotent)
- ResilientIdempotencyPortTest: 3 tests (pre-existing)
- Total: 6 tests, 0 failures, 0 errors

## Deliverables

### A. pom.xml
- Added: ddd4j-data-event-store-jpa, ddd4j-data-cqrs-spring, ddd4j-data-projection-spring, ddd4j-data-projection-jpa
- Added: spring-boot-starter-web, spring-boot-starter-data-jpa (runtime)
- Added: spring-boot-starter-test, hibernate-core, h2 (test)
- Pinned: spring-data-jpa 3.5.13, spring-data-commons 3.5.13, jakarta.persistence-api 3.1.0 (Framework 6.2 alignment)

### B. Domain Changes (ddd4j-sample-order-domain)
- OrderDomainEvent: added protected no-arg constructor (Jackson deserialization + replay)
- OrderCreatedEvent: added orderNo/buyerId/buyerName fields + no-arg constructor
- Order: added package-private no-arg constructor + `Order.empty()` factory + @EventHandler methods for OrderCreatedEvent/OrderPaidEvent/OrderCancelledEvent/OrderShippedEvent
- Order.draft(): updated to pass order details to OrderCreatedEvent

### C. Write Side
- CreateOrderCommand: record implements Command (orderNo, buyerId, buyerName)
- CreateOrderCommandHandler: @Component + @CommandHandler, uses Order.draft() + EventSourcingOrderRepository
- EventSourcingOrderRepository: @Component, implements OrderRepository, uses EventStore.append/read + loadFromHistory, in-memory orderNo index for idempotency

### D. Read Side
- OrderSummaryViewEntity: JPA entity (order_summary_view table)
- OrderSummaryViewRepository: Spring Data JPA repository
- OrderSummaryView: ProjectionView<DomainEvent<?>>, subscribes OrderCreatedEvent + OrderPaidEvent

### E. Spring Integration
- OrderSampleApplication: @SpringBootConfiguration + @EnableAutoConfiguration + @ComponentScan + @EntityScan + @EnableJpaRepositories; provides EventPayloadSerializer, CommandRegistry, TaskScheduler, ProjectionService, EventChunkReader, ProjectionRunner beans
- OrderController: POST /orders (create), GET /orders/{id} (query read model), idempotency via orderNo check (409 Conflict)
- application.yml (test): H2 in-memory, MODE=PostgreSQL, create-drop DDL

### F. Integration Test
- OrderCqrsIT: @SpringBootTest + @AutoConfigureMockMvc, 3 tests covering full CQRS flow

## Concerns
- EventSourcingOrderRepository uses in-memory orderNo index (not persisted); production should use a persistent index
- Projection is manually triggered via ViewManager.triggerOnce() in tests; production uses CRON scheduling
- Order aggregate gained @EventHandler methods and no-arg constructor for event sourcing support

---

## Fix Round 1

### Status: DONE

### Commit
`07b59cbb` — `fix(sample): 补齐事件类无参构造器 + README CQRS 集成示例`

### Gate
`./mvnw -pl ddd4j-samples/ddd4j-sample-order-application -am install` -> BUILD SUCCESS, 6 tests (3 OrderCqrsIT + 3 ResilientIdempotencyPortTest), 0 failures

### Changes
1. **OrderPaidEvent / OrderCancelledEvent / OrderShippedEvent** — added `protected` no-arg constructor calling `super()` (same pattern as `OrderDomainEvent` and `OrderCreatedEvent`), enabling Jackson deserialization for EventStore persistence and event replay.
2. **ddd4j-sample-order-application/README.md** — created with "CQRS 集成示例" (write-side: Command -> Aggregate -> EventStore; read-side: EventStore -> Projection -> 查询模型) and "如何运行" (`./mvnw -pl ddd4j-samples/ddd4j-sample-order-application -am install`) chapters.

### Concerns Resolved
- Important #1: All 5 domain event classes now have no-arg constructors for Jackson.
- Important #2: README created with required CQRS integration and run instructions.
