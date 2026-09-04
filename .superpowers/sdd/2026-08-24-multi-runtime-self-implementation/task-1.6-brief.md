# Task 1.6 Brief — Write 05-event-store.md

## Task

Write `ddd4j/docs/reference/fuin-api-patterns/05-event-store.md` — the fifth of 8 fuin API reference documents. Topic: the **EventStore SPI** itself (as opposed to Task 1.5's EventStoreRepository which sits on top of it).

**Critical context from Task 1.5**: fuin's `EventStore` interface does NOT live in ddd-4-java — it comes from the **external dependency `org.fuin.esc:esc-api:0.9.0`**. The `ddd-4-java/esc/` module has only 3 files (AggregateStreamId, EventStoreRepository, package-info).

## Sources to read

**Primary — esc-api 0.9.0 sources.** Extract if not already extracted:
```bash
cd /tmp && rm -rf esc-api-src && mkdir esc-api-src && cd esc-api-src
unzip ~/.m2/repository/org/fuin/esc/esc-api/0.9.0/esc-api-0.9.0-sources.jar
find . -name "*.java" | sort
```
(If the sources jar is absent, download it: `./mvnw dependency:get -Dartifact=org.fuin.esc:esc-api:0.9.0:jar:sources` from any dir, or `dependency:sources` — worst case decompile is NOT acceptable; report BLOCKED.)

Read at minimum: `EventStore` / `WritableEventStore` / `ReadableEventStore` (or whatever the real interfaces are — the Task 1.5 review already verified `WritableEventStore.java:181` contains the `deleteStream(streamId, expectedVersion, hardDelete)` javadoc), plus `StreamId`, `StreamEventsSlice` (or equivalents), `EventStoreConfiguration` if present, and the service/loader discovery mechanism (`META-INF/services`) if present.

**ddd4j side** (for Section 5 alignment):
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-core/src/main/java/io/ddd4j/core/cqrs/readmodel/EventChunkReader.java` (ddd4j's read-side SPI)
- Plan's stage-3 design: `docs/superpowers/plans/2026-08-24-multi-runtime-self-implementation.md` Task 3.2 defines ddd4j's self-developed `EventStore` SPI (append/read/readAll + AggregateVersionConflictException + AsyncEventStore). Read that task section for the target design.

**Sibling docs for style/depth** (do not modify):
- `docs/reference/fuin-api-patterns/04-event-sourcing-repository.md` — most recent, includes the soft-delete finding and `// ... omitted` marker convention
- `03-domain-event.md` — "已对齐 + 超出" pattern

## Mandatory structure (6 sections, exact `## ` titles)

`## 来源` / `## fuin 的设计` / `## 优点（值得借鉴的）` / `## 缺点（应规避的）` / `## ddd4j 自研决策` / `## 落地计划`

Requirements:
- 来源 must state clearly this is **external artifact `org.fuin.esc:esc-api:0.9.0`** (repo: https://github.com/fuinorg/esc-api if verifiable from sources, otherwise state "外部构件"), NOT ddd-4-java source
- Snippets quote real code with file:line refs from the extracted sources; mark every abridgment with `// ... omitted：...（:行号）`
- 缺点 must include (verified in Task 1.5): `deleteStream` soft-delete semantics emit no tombstone domain event (invisible to projections, version continuation on recreation)
- ddd4j 自研决策 must compare esc-api's SPI against the plan's Task 3.2 design (`append/read/readAll`, global `position`, `AsyncEventStore` reactive variant) — note what ddd4j deliberately does differently (e.g., no TypeMultiEvent meta-events if esc-api has them, Jackson payload instead of esc-api's serialization SPI, etc. — verify from actual source)
- 落地计划 links to plan Task 3.2 / 3.3 / stage 5 (jpa/panache/jdbi/r2dbc implementations)
- 80-150 lines, full-width Chinese punctuation, title exactly `# 05. fuin API 模式：EventStore 事件存储 SPI`

## Commit

```bash
cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j
git add docs/reference/fuin-api-patterns/05-event-store.md
git commit -m "docs(reference): 05-event-store API 模式参考"
```

## Precedent (Tasks 1.3/1.4/1.5)

If this brief mischaracterizes the esc-api surface, trust the extracted source over the brief and surface the correction in your report as DONE_WITH_CONCERNS.

## Report

Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-1.6-report.md` (line count, sections, snippets+line refs, 借鉴/改写/不借鉴 counts, brief corrections, self-review). Reply ≤15 lines: Status / commit / one-line summary / concerns / report path.
