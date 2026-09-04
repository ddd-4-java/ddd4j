# Task 0.4 Fixup — DomainEventJsonTest JavaTimeModule registration

- Date: 2026-08-24
- Branch: `feature/2.0.x`
- Base commit: `b8fec37e`
- Fix commit: `939eaa6d` — `test(core): register JavaTimeModule in DomainEventJsonTest`

## Failing test location

- File: `ddd4j-core/src/test/java/io/ddd4j/core/ddd/event/DomainEventJsonTest.java`
- Method: `shouldSerializeEventMetadataAsStableScalarValues` (line 14; failure surfaced at line 18, the `objectMapper.writeValueAsString(event)` call)
- Root cause line: line 11 — bare `new ObjectMapper()`, with a stale comment claiming "Jackson 3 内建 JavaTimeModule（自动注册）"

Confirmed failure mode by running the test pre-fix:

```
com.fasterxml.jackson.databind.exc.InvalidDefinitionException: Java 8 date/time type
`java.time.ZonedDateTime` not supported by default: add Module
"com.fasterxml.jackson.datatype:jackson-datatype-jsr310" to enable handling
(through reference chain: ...DomainEventJsonTest$SampleDomainEvent["event-timestamp"])
```

Mechanism: `DomainEvent.eventTimestamp` is a `ZonedDateTime` field annotated `@JsonProperty("event-timestamp")` (`@JsonIgnore` on its getter loses to the explicit `@JsonProperty` on the field), so it is serialized and a bare Jackson 2.21.2 mapper has no java.time handlers.

## Classpath check result

`jackson-datatype-jsr310:2.21.2` **IS** on ddd4j-core's test classpath — no pom.xml edit required.

Evidence:

1. `./mvnw -pl ddd4j-core dependency:list`:
   `com.fasterxml.jackson.datatype:jackson-datatype-jsr310:jar:2.21.2:compile (optional)`
2. `dependency:tree` path: `ddd4j-core -> ddd4j-kit -> jackson-datatype-jsr310:2.21.2 (optional)`
3. Authoritative check — `dependency:build-classpath -Dmdep.includeScope=test` contains:
   `~/.m2/repository/com/fasterxml/jackson/datatype/jackson-datatype-jsr310/2.21.2/jackson-datatype-jsr310-2.21.2.jar`

Only four Jackson artifacts resolve on ddd4j-core (core, databind, annotations, jsr310), so `findAndAddModules()` discovers exactly one extra module (JavaTimeModule) and cannot perturb other tests.

## Chosen fix approach

**Option A** (preferred per fixup brief): replace the bare mapper with

```java
private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
```

Reasoning:

- `findAndAddModules()` (available since Jackson 2.10 via `JsonMapper.builder()`) registers modules through `ServiceLoader`; the jsr310 jar ships `META-INF/services/com.fasterxml.jackson.databind.Module`, so `JavaTimeModule` is picked up with no import of the jsr310 type and no pom change.
- Option B (`registerModule(new JavaTimeModule())`) would also have worked (jsr310 present on test classpath) but couples the test to the jsr310 type.
- The stale line-10 comment ("Jackson 3 auto-registers JavaTimeModule") was factually wrong under the resolved Jackson 2.21.2 and was the source of the bug's introduction; it is replaced with an accurate comment (Chinese, matching codebase convention).

## Diff stats

```
 ddd4j-core/src/test/java/io/ddd4j/core/ddd/event/DomainEventJsonTest.java | 5 +++--
 1 file changed, 3 insertions(+), 2 deletions(-)
```

(One import added, mapper initializer + comment replaced. No pom.xml change.)

## Verification

`./mvnw -pl ddd4j-core test` (full suite):

```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in io.ddd4j.core.ddd.event.DomainEventJsonTest
[INFO] Tests run: 237, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

- Total: 237 tests, 0 failures, 0 errors, 0 skipped
- Previously failing `DomainEventJsonTest.shouldSerializeEventMetadataAsStableScalarValues`: PASS

## Self-review

- Only `DomainEventJsonTest.java` modified; pom.xml untouched (allowed but unnecessary).
- All existing assertions preserved verbatim (`event-type`, `event-id`, `entity-id-path`, `aggregate-version` checks unchanged).
- Full `./mvnw -pl ddd4j-core test` suite passed — not just the previously failing test.
- Single commit `939eaa6d`; pre-existing untracked plan docs under `docs/superpowers/plans/` were not staged.
- 4-space Java indentation maintained; no scripts used to edit files.

## Notes / residual observations

- The jsr310 dependency is marked `<optional>` in its declaring pom, so downstream consumers of ddd4j artifacts will NOT get it transitively. That is consistent with the JsonKit approach (hand-rolled java.time serializers) and is a deliberate Jackson-2-era posture; no action taken here.
- `DomainEventJsonTest` differs from JsonKit's strategy (SPI auto-discovery vs explicit SimpleModule) — acceptable for a test; production serialization in ddd4j does not rely on this mapper.
