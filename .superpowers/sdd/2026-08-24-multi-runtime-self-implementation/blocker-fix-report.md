# Pre-Task Blocker Fix Report

**Date:** 2026-08-24 · **Branch:** feature/2.0.x · **Commit:** `43ad36d2`
**Status:** DONE (with deviations from the brief's prescribed fix — see "Deviations"; all acceptance criteria met)

## What was fixed

### Blocker 1 — Maven wrapper pinned to broken 4.0.0-rc-5
`.mvn/wrapper/maven-wrapper.properties` line 3: `4.0.0-rc-5` → `4.0.0-rc-6` (distributionUrl only; `wrapperVersion`/`distributionType` untouched). Aligns with the 3.0.x branch (commit 293f2071).

### Blocker 2 — JsonKit referenced Jackson-3-only APIs against Jackson 2
`ddd4j-kit/src/main/java/io/ddd4j/kit/lang/JsonKit.java` was written against the Jackson 3 API surface but compiles against Jackson 2 (resolved `jackson-databind` 2.21.2). The initial `./mvnw -pl ddd4j-kit compile` error inventory (after the wrapper fix) showed **more Jackson-3 symbols than the brief listed**:

| Line(s) | Jackson-3 API (broken) | Jackson-2 replacement used |
|---|---|---|
| 67–74, 105–116 | `com.fasterxml.jackson.databind.ext.javatime.ser/deser.*` (10 registrations) | `SimpleModule` + 6 new private helpers `localDate/LocalDateTime/LocalTimeSerializer/Deserializer` built on `JsonSerializer`/`JsonDeserializer` + the existing `DateTimeFormatter` patterns |
| 80, 94 | `changeDefaultPropertyInclusion(lambda)` | `defaultPropertyInclusion(JsonInclude.Value.construct(...))` (identical semantics) |
| 91 | bare `DefaultTyping.NON_FINAL` | `ObjectMapper.DefaultTyping.NON_FINAL` |
| 93 | `changeDefaultVisibility(lambda)` | `visibility(PropertyAccessor.ALL, Visibility.ANY)` |
| 117/128 | `ValueSerializer` / `ValueDeserializer` | `JsonSerializer<Date>` / `JsonDeserializer<Date>` (bodies preserved verbatim; `throws IOException` added per Jackson-2 signatures) |
| 118 | `SerializationContext` | `SerializerProvider` |
| 172 | `JsonNode.propertyNames().iterator()` | `JsonNode.fieldNames()` |

Preserved untouched: `BaseSimpleDateFormat` (legacy Date path), `defaultDateFormat`, `defaultTimeZone("Asia/Shanghai")`, `FAIL_ON_UNKNOWN_PROPERTIES`/`FAIL_ON_EMPTY_BEANS`/`ACCEPT_EMPTY_STRING_AS_NULL_OBJECT` config, `redisObjectMapper` semantics, all public method signatures.

## Deviations from the brief (deliberate, documented)

1. **Did not use `JavaTimeModule` from jsr310.** The brief assumed `jackson-datatype-jsr310` is "already a transitive of jackson-databind 2.x" — it is **not** on ddd4j-kit's classpath (verified via `dependency:list`; the kit pom comment "J3 中 jsr310 已内置于 databind" only holds for Jackson 3). Using it would have required editing `ddd4j-kit/pom.xml`, a third file outside the brief's allowed file list. The chosen fix (SimpleModule + JsonSerializer/JsonDeserializer) stays within the 2 allowed files and additionally **preserves the intended wire format exactly** (`yyyy-MM-dd`, `yyyy-MM-dd HH:mm:ss`, custom patterns), whereas JavaTimeModule would emit ISO-8601 'T' separators or timestamp arrays (`WRITE_DATES_AS_TIMESTAMPLES` defaults true in Jackson 2).
2. **Fixed the extra Jackson-3 symbols above** (lines 80, 91, 93–94, 117–118, 128, 172). These were compile-blocking in the same file and same methods; without them the acceptance criteria could not pass.
3. **Commit message body point 2 adapted** to describe the actual fix (the prescribed text claimed a JavaTimeModule switch, which would have been false).

## Diff stats

```
43ad36d2 build: unblock ddd4j-core compile — bump Maven wrapper to rc-6 + fix JsonKit Jackson-3 javatime imports
 .mvn/wrapper/maven-wrapper.properties              |  2 +-
 ddd4j-kit/src/main/java/io/ddd4j/kit/lang/JsonKit.java | 98 +++++++++++++-------
 2 files changed, 69 insertions(+), 31 deletions(-)
```

## Acceptance command output

1. `./mvnw --version 2>&1 | grep -E "Apache Maven.*4\.0\.0-rc"`
   → `Apache Maven 4.0.0-rc-6 (6a8189b24518daa120539fa41ce12f2b48ec09a8)` — **PASS**
2. `./mvnw -pl ddd4j-core compile` → `BUILD SUCCESS` — **PASS** (re-verified after commit on the current branch head)
3. `./mvnw -pl ddd4j-kit compile` → `BUILD SUCCESS` (34 source files) — **PASS** (re-verified after commit)
4. `./mvnw -pl ddd4j-kit test -Dtest=JsonKitTest`
   → `No tests matching pattern "JsonKitTest" were executed!` — **PASS per brief** ("no tests if JsonKitTest doesn't exist — both acceptable as long as compile succeeded"; JsonKitTest does not exist in the repo).

### Extra regression evidence (beyond the brief)
- Full `./mvnw -pl ddd4j-kit test`: **57 tests, 0 failures, 0 errors** (IdKitTest 11, CollKitTest 18, StrKitTest 10, BeanKitTest 18).
- jshell runtime smoke on the built classes:
  - `defaultObjectMapper()`: `{"d":"2026-08-24","dt":"2026-08-24 12:34:56","u":"2026-08-24 10:00:00.000"}` — exact intended patterns; null fields omitted (NON_NULL); full deserialize round-trip OK; `toMap` (fieldNames fix) OK.
  - `buildObjectMapper("yyyy/MM/dd","yyyy/MM/dd HH:mm:ss","HH:mm:ss")`: `{"d":"2026/08/24","t":"09:05:03","u":"1970/01/01"}` — custom patterns, LocalTime, and legacy Date path all round-trip OK.

## Self-review checklist

- Only `4.0.0-rc-5` → `4.0.0-rc-6` in maven-wrapper.properties? **Yes** (single line).
- `BaseSimpleDateFormat` preserved? **Yes** (untouched; verified at runtime).
- Rest of `defaultObjectMapper()` config preserved? **Yes, semantically** — one necessary rename: `changeDefaultPropertyInclusion` does not exist in Jackson 2; replaced by its exact Jackson-2 equivalent `defaultPropertyInclusion` (verified NON_NULL behavior at runtime).
- Rest of `buildObjectMapper()` config preserved? **Yes** — `ACCEPT_EMPTY_STRING_AS_NULL_OBJECT`, `FAIL_ON_EMPTY_BEANS`, `FAIL_ON_UNKNOWN_PROPERTIES=false`, Date ser/deser bodies verbatim.
- Single commit, only the two changed files? **Yes** (`43ad36d2`, 2 files).
- All 4 acceptance commands passed? **Yes** (see above).

## Issues / observations (pre-existing, out of scope)

1. **Concurrent agent activity on this branch.** During this fix, another process committed `e491dbd7` and `a4c2d270` (both `ddd4j-dependencies/pom.xml` only) and left further **uncommitted** edits in `ddd4j-dependencies/pom.xml` (fuin-cqrs4j 0.6.0, springdoc 2.8.14, sshd 3.0.0-M2, resilience4j-spring-boot2/3/cloud2 entries). These are **not** part of my commit and were left as-is in the working tree.
2. **Resolved Jackson is 2.21.2, not 2.22.2.** Root pom / ddd4j-dependencies set `jackson.version=2.22.2`, but `dependency:list` shows `jackson-databind:2.21.2` on ddd4j-kit's classpath (some earlier-winning dependencyManagement pins it). The Jackson-2 API surface used is identical for 2.21/2.22, but the version skew itself may be worth a look in a later plan task.
3. **`LocalTime` is not registered in `defaultObjectMapper()`** (same as the original code — only `buildObjectMapper` registers it). Under Jackson 2.21's `REQUIRE_HANDLERS_FOR_JAVA8_TIMES` (default on), serializing a `LocalTime` through `DEFAULT_OBJECT_MAPPER`/`toJson` throws `InvalidDefinitionException`. If any DTO needs LocalTime via the default mapper, register it there or disable that `MapperFeature` in a later task.
4. **`REDIS_OBJECT_MAPPER`'s `BasicPolymorphicTypeValidator.builder().build()`** (empty validator) denies resolution of arbitrary/custom types under Jackson 2 — pre-existing config choice, preserved verbatim; only affects `toType`/Redis typing paths with non-allowlisted types.
