# Task 1.3 Report

## Deliverable

- File: `ddd4j/docs/reference/fuin-api-patterns/02-entity-id-path.md`
- Line count: 146
- Commit: `b2c4d331` `docs(reference): 02-entity-id-path API 模式参考` (single commit, only the new file)

## Note on brief

`task-1.3-brief.md` was **empty (0 bytes)** on dispatch. Task definition was recovered from the coordinator's dispatch message (7 fuin files + 6 ddd4j-core files + title + 6-section structure), the plan doc (`docs/superpowers/plans/2026-08-24-multi-runtime-self-implementation.md`, Task 1.3 + README index row 02), and `task-1.2-brief.md` as the structure model. Not blocked: all specified source files existed.

## Sections (6, exact `## ` titles)

1. `## 来源`
2. `## fuin 的设计`
3. `## 优点（值得借鉴的）`
4. `## 缺点（应规避的）`
5. `## ddd4j 自研决策`
6. `## 落地计划`

## fuin source snippets

4 fenced `java` blocks, all verified verbatim (dedented) against source via diff script:

1. EntityIdPath list constructor — `EntityIdPath.java:83-95` (constructor invariants)
2. `first()`/`last()` navigation — `EntityIdPath.java:113-116` + `:124-127`
3. `asBaseType()` stable text — `EntityIdPath.java:170-180`
4. `valueOf()` parse — `EntityIdPath.java:196-208`

Plus prose-covered mechanisms with line refs: `rest()` :134-143, `parent()` :150-159, `size()` :166-168, `iterator()` :103-105, `isValid/requireArgValid` :194-254, ExpectedEntityIdPathValidator :36-47, HasEntityTypeConstant :22-37/:29, EntityId.java:52/:104-116/:108-115/:109-112, IntegerEntityId.java:100-102, AbstractAggregateRoot.java:146-149 (cross-ref to 01 doc, verified).

## Decision bullets

- 借鉴: 4 bullets (navigation algebra kept; typed-segment text `asString()`/`@JsonValue`; constructor invariants `List.copyOf`; defensive `iterator()`)
- 改写: 4 bullets (add `isValid(String)`/`valueOf(String)` per ddd4j `Type:id` colon format — the README row "ddd4j-core EntityIdPath 补 validate"; `@Nullable` on null-returning `rest()/parent()`; `first()` cast guarded by `instanceof AggregateRootId` in Task 2.2; expected-type-sequence as test-time assertion)
- 不借鉴: 3 bullets (`EntityIdFactory` registry; objects4j/jakarta.validation stack — ADR-0002; `HasEntityTypeConstant`/`IntegerEntityId`-style base classes vs ddd4j's 3-method `EntityId` + records)

## Sources read (13 files)

fuin (7): EntityIdPath, EntityId, AggregateRootId, IntegerEntityId, StringBasedEntityType, ExpectedEntityIdPathValidator, HasEntityTypeConstant — all present at `../ddd-4-java/core/src/main/java/org/fuin/ddd4j/core/`.
ddd4j-core (6): `io/ddd4j/core/ddd/event/` EntityIdPath, EntityId, AggregateRootId, EntityType, StringEntityId, StringEntityType (+ DomainEvent.java line-spot-check for `getEntityIdPath`/`getEntityId`).

## Self-review findings

- Title exact: `# 02. fuin API 模式：EntityIdPath 链式实体路径` ✓
- All 6 mandatory `## ` sections ✓ (grep-verified)
- 146 lines (80-150 range) ✓ — initial drafts were 171/164, trimmed by converting the 5th snippet to prose and compacting the 来源 file list
- Snippets cite real line numbers, machine-verified against fuin source (one citation fixed during review: valueOf `:196-209` → `:196-208` because closing brace omitted; null-return cites `:135/:151` → `:136/:152`)
- 借鉴/改写/不借鉴 specific to ddd4j's actual contract (colon `Type:id` typed-string vs fuin's space `Type id`; `List.copyOf` immutability; existing `first/last/rest/parent` already aligned) ✓
- Full-width Chinese punctuation in prose verified programmatically (no half-width `,;` adjacent to CJK, no half-width parens around CJK); code in half-width ✓
- Single commit `b2c4d331`, only the new file (`git show --stat` = 1 file, 146 insertions) ✓
- Key factual finding documented: fuin's `isValid`/`requireArgValid` javadocs are copy-pasted from an EmailAddress value object ("well-formed email address", EntityIdPath.java:212-216/:239-244); `EntityId.valueOf` silently returns `null` on malformed segments (EntityId.java:109-112)
