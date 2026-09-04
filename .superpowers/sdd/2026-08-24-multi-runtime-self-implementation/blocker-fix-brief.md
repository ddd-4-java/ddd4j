# Pre-Task Blocker Fix Brief (NOT in plan, unblocks Tasks 0.2+)

## Why this brief exists

This brief fixes **two pre-existing build blockers** that were identified by the Task 0.1 implementer + reviewer. Until both are fixed, `./mvnw -pl ddd4j-core compile` (and therefore every verify step in every subsequent plan task) cannot succeed. These blockers are **not introduced by Task 0.1** — they exist at the clean `abf539a4` HEAD.

## Blocker 1: Maven wrapper version is broken

**File:** `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/.mvn/wrapper/maven-wrapper.properties`

**Current content (3 lines):**
```
wrapperVersion=3.3.4
distributionType=only-script
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/4.0.0-rc-5/apache-maven-4.0.0-rc-5-bin.zip
```

**Fix:** Change `4.0.0-rc-5` → `4.0.0-rc-6` in the distributionUrl line. The `3.0.x` branch already migrated to rc-6 (commit `293f2071`), so this aligns `2.0.x` with the same proven Maven version.

**New content (after fix):**
```
wrapperVersion=3.3.4
distributionType=only-script
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/4.0.0-rc-6/apache-maven-4.0.0-rc-6-bin.zip
```

## Blocker 2: JsonKit uses Jackson-3 packages that don't exist in Jackson 2

**File:** `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-kit/src/main/java/io/ddd4j/kit/lang/JsonKit.java`

**Problem:** Lines 67-74 (in `defaultObjectMapper()`) and lines 105-116 (in `buildObjectMapper()`) reference `com.fasterxml.jackson.databind.ext.javatime.ser.*` and `com.fasterxml.jackson.databind.ext.javatime.deser.*`. These packages **do not exist in Jackson 2.x** — they are Jackson 3.x paths. In Jackson 2.x, java.time serialization is provided by the separate `jackson-datatype-jsr310` module under `com.fasterxml.jackson.datatype.jsr310.*`.

The file comment at line 65 already says "Jackson 3: JavaTimeInitializer is auto-registered" — but ddd4j-core depends on Jackson 2.22.2 (Jackson 2, not 3). The package was migrated to Jackson-3 paths but the dependency was not migrated, so it does not compile under Jackson 2.

**Fix:** Replace all 6 fully-qualified Jackson-3 javatime imports with the standard Jackson-2 `JavaTimeModule` from `com.fasterxml.jackson.datatype.jsr310` (this module is auto-included via `jackson-datatype-jsr310` which is already a transitive of `jackson-databind` 2.x). The simplest, idiomatic fix is to register `JavaTimeModule` once via `addModule(new JavaTimeModule())` and rely on its default ISO-8601 (de)serialization — the existing custom date-pattern override via `defaultDateFormat(new BaseSimpleDateFormat())` on the JsonMapper is the long-standing way to set Jackson-2 pattern behavior, but per JavaTimeModule, the per-call pattern via `@JsonFormat(pattern=...)` on each field is the correct override point. To keep the existing **class-level behavior** (DATE_PATTERN = `yyyy-MM-dd`, TIME_PATTERN = `yyyy-MM-dd HH:mm:ss`) working without per-field annotations, use `JavaTimeModule` with `SerializerProvider` configured via custom serializers — but this is over-engineered for the blocker fix. The pragmatic fix is:

1. Replace the 4 lines (67-74) in `defaultObjectMapper()` with `customDateModule.addSerializer(LocalDate.class, /* Jackson-2 serializer using DateTimeFormatter */)` ... or simpler, **drop the customDateModule entirely from `defaultObjectMapper()` and rely on Jackson's built-in java.time support via `addModule(new JavaTimeModule())`** — Jackson-2 with JavaTimeModule already serializes `LocalDate` and `LocalDateTime` as ISO-8601 strings out of the box, which is the correct modern behavior.

2. Replace the 8 lines (105-116) in `buildObjectMapper()` with `customDateModule.addSerializer(LocalDate.class, /* Jackson-2 serializer */)` ... or simpler, **drop the customDateModule entirely and add JavaTimeModule instead**.

3. The existing `BaseSimpleDateFormat` (`SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")`) for legacy `Date` fields remains — it is unrelated to the broken javatime imports.

**Exact fix instructions (use Edit tool — do NOT rewrite the file):**

In `defaultObjectMapper()` (line 64-84), replace lines 65-74:
- DELETE the comment line 65 ("Jackson 3: JavaTimeInitializer is auto-registered; custom serializers use SimpleModule") — this comment lies about the Jackson version.
- DELETE lines 66 (`SimpleModule customDateModule = new SimpleModule();`) and 67-74 (the 4 javatime ser/deser registrations).
- CHANGE line 78 (`.addModule(customDateModule)`) to `.addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())`.
- ADD `import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;` near the other Jackson imports (after line 14).

In `buildObjectMapper()` (line 103-145), replace lines 104-138:
- DELETE the `SimpleModule customDateModule = new SimpleModule();` block (lines 104-138).
- CHANGE line 140 (`.addModule(customDateModule)`) to `.addModule(new JavaTimeModule())`.
- Note: the `Date` serializer/deserializer inside the deleted block (lines 117-138) **must be preserved** — it provides `yyyy-MM-dd` pattern serialization for legacy `java.util.Date` fields. Wrap it as a separate `SimpleModule` added alongside JavaTimeModule:
```java
SimpleModule legacyDateModule = new SimpleModule();
legacyDateModule.addSerializer(Date.class, new ValueSerializer<Date>() { ...existing body... });
legacyDateModule.addDeserializer(Date.class, new ValueDeserializer<Date>() { ...existing body... });
return JsonMapper.builder()
        .addModule(legacyDateModule)
        .addModule(new JavaTimeModule())
        ...
```

## Acceptance criteria (BOTH must pass before this brief is DONE)

Run from `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j`:

```bash
# 1. Wrapper uses rc-6
./mvnw --version 2>&1 | grep -E "Apache Maven.*4\.0\.0-rc"
# Expected: "Apache Maven 4.0.0-rc-6 (...)"

# 2. ddd4j-core compiles cleanly with the standard wrapper
./mvnw -pl ddd4j-core compile
# Expected: BUILD SUCCESS (no "cannot find symbol", no "package ... does not exist")

# 3. ddd4j-kit compiles too (since it is ddd4j-core's only transitive)
./mvnw -pl ddd4j-kit compile
# Expected: BUILD SUCCESS

# 4. JsonKit still produces a valid ObjectMapper (regression check)
./mvnw -pl ddd4j-kit test -Dtest=JsonKitTest 2>&1 | tail -5
# Expected: Tests pass (or "no tests" if JsonKitTest doesn't exist — both acceptable as long as compile succeeded)
```

## Files to modify

- Modify: `ddd4j/.mvn/wrapper/maven-wrapper.properties` (line 3, distributionUrl)
- Modify: `ddd4j/ddd4j-kit/src/main/java/io/ddd4j/kit/lang/JsonKit.java` (lines 65-74 and lines 104-138)

## Out of scope

- Do NOT touch anything else.
- Do NOT commit unrelated changes.
- Do NOT refactor `JsonKit` beyond the two specific javatime fixes.
- Do NOT bump Jackson version (already 2.22.2 in ddd4j-dependencies).
- Do NOT add Jackson-3 dependencies.

## Commit

Single commit with message:
```
build: unblock ddd4j-core compile — bump Maven wrapper to rc-6 + fix JsonKit Jackson-3 javatime imports

The two blockers identified by Task 0.1 implementer + reviewer:
1. .mvn/wrapper/maven-wrapper.properties pinned 4.0.0-rc-5 which fails all dependency
   resolution with "Invalid Collect Request: null". Align with 3.0.x branch (commit 293f2071).
2. JsonKit.java referenced Jackson-3 packages
   (com.fasterxml.jackson.databind.ext.javatime.ser/deser) that don't exist in Jackson 2.x.
   Switch to Jackson-2 JavaTimeModule from jackson-datatype-jsr310 (auto-included).
```

## Report

Write full report to: `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/blocker-fix-report.md`

Reply with ONLY:
- **Status:** DONE | DONE_WITH_CONCERNS | BLOCKED | NEEDS_CONTEXT
- Commit (short SHA + subject)
- One-line acceptance summary (e.g., "./mvnw -pl ddd4j-core compile → BUILD SUCCESS, wrapper 4.0.0-rc-6")
- Concerns
- Report path