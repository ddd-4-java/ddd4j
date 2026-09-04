# Task 1.3 Brief — Write 02-entity-id-path.md

## What this task is

Task 1.3 of 43 tasks in `docs/superpowers/plans/2026-08-24-multi-runtime-self-implementation.md`. Part of "阶段 1：高精度参考文档 + ADR".

This task writes `ddd4j/docs/reference/fuin-api-patterns/02-entity-id-path.md` — the second of 8 reference documents about fuin's API design patterns for `EntityIdPath` (the chained-entity-path identifier used in DDD event sourcing).

Task 1.1 created the README index. Task 1.2 wrote 01-aggregate-root.md. Tasks 1.4-1.9 follow. Task 1.10 writes ADRs. Task 1.11 verifies.

## File to create

- Create: `ddd4j/docs/reference/fuin-api-patterns/02-entity-id-path.md`

## Source to read

Primary (fuin):
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd-4-java/core/src/main/java/org/fuin/ddd4j/core/EntityIdPath.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd-4-java/core/src/main/java/org/fuin/ddd4j/core/EntityId.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd-4-java/core/src/main/java/org/fuin/ddd4j/core/AggregateRootId.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd-4-java/core/src/main/java/org/fuin/ddd4j/core/IntegerEntityId.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd-4-java/core/src/main/java/org/fuin/ddd4j/core/StringBasedEntityType.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd-4-java/core/src/main/java/org/fuin/ddd4j/core/ExpectedEntityIdPathValidator.java` (annotation validator)
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd-4-java/core/src/main/java/org/fuin/ddd4j/core/HasEntityTypeConstant.java`

Reference (ddd4j-core existing):
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-core/src/main/java/io/ddd4j/core/ddd/event/EntityIdPath.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-core/src/main/java/io/ddd4j/core/ddd/event/EntityId.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-core/src/main/java/io/ddd4j/core/ddd/event/AggregateRootId.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-core/src/main/java/io/ddd4j/core/ddd/event/StringEntityId.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-core/src/main/java/io/ddd4j/core/ddd/event/EntityType.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-core/src/main/java/io/ddd4j/core/ddd/event/AggregateVersion.java`

## Document structure (mandatory 6 sections)

Use this exact structure. Section titles must be:
`## 来源`, `## fuin 的设计`, `## 优点（值得借鉴的）`, `## 缺点（应规避的）`, `## ddd4j 自研决策`, `## 落地计划`.

### Section 1: 来源 (Source)

```
- 仓库：<fuin repo URL>
- 版本：<fuin version>
- 文件：<relative path>:<line range>
- 关键 API：
  - <MethodName>（<line range>）：<one-line description>
  - ...
```

Cover these APIs:
- `EntityIdPath.first()` / `last()` / `parent()` / `child(EntityId)` / `size()` / `iterator()`
- `EntityId` (marker interface, extends `AsStringCapable`)
- `AggregateRootId` (marker interface, extends `EntityId`)
- `IntegerEntityId` / `StringBasedEntityType` (sample concrete impls)
- `ExpectedEntityIdPathValidator` (annotation processor-validated annotation)
- `HasEntityTypeConstant` (codegen hint)

### Section 2: fuin 的设计

Quote 4-8 line snippets covering:
- `EntityIdPath` as an Iterable<EntityId>
- `EntityId` as a marker interface (e.g., `StringEntityId` impl)
- `ExpectedEntityIdPathValidator` (compile-time annotation)

### Section 3: 优点（值得借鉴的）

3-6 bullets. Specifically about EntityIdPath:
- "链式路径表达聚合根→子实体层级，与 Greg Young ES pattern 一致"
- "EntityId marker + AsStringCapable 让序列化统一"
- "ExpectedEntityIdPathValidator 编译期校验 event path 类型"

### Section 4: 缺点（应规避的）

3-6 bullets:
- "EntityIdFactory 通过 codegen 注解处理器生成，ddd4j 不走 codegen"
- "StringBasedEntityType 用 value 字段存类型字符串，无类型安全"
- "HasEntityTypeConstant 是 codegen 辅助接口，对无 codegen 项目无意义"

### Section 5: ddd4j 自研决策

**借鉴**：
- EntityIdPath.first()/last()/parent()/child() — ddd4j-core 已有，可补 validate()
- ExpectedEntityIdPathValidator 编译期校验 — 借鉴思路但改为运行时校验（ddd4j 不走 codegen）

**改写**：
- 实体 ID 类型从 String-based 改为 EntityId marker interface（ddd4j 已有）
- AggregateRootId 实现从 StringEntityId 改为值对象（ddd4j 现状）

**不借鉴**：
- EntityIdFactory codegen 注解处理器（ddd4j 走 ClassValue 反射）
- HasEntityTypeConstant codegen 辅助接口

### Section 6: 落地计划

Checkbox list `- [ ]` linking to plan tasks. Specifically stage 2 may need:
- 阶段 2: 补 `EntityIdPath.validate()` 方法（对齐 fuin 的 ExpectedEntityIdPathValidator）
- 阶段 3 (event-store SPI): 用 EntityIdPath 作为 aggregateId 参数类型

## File header

Start with `# 02. fuin API 模式：EntityIdPath 链式实体路径`

## Length guidance

80-150 lines.

## Chinese punctuation

Use full-width：`（`、`）`、`，`、`。`、`：`、`；`. Keep English code/identifiers in half-width.

## Commit

After writing the file, single commit:

```bash
cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j
git add docs/reference/fuin-api-patterns/02-entity-id-path.md
git commit -m "docs(reference): 02-entity-id-path API 模式参考"
```

## Context

- Working directory: `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j`
- Branch: `feature/2.0.x`
- Latest commit: `789760fb` (Task 1.2 — 01-aggregate-root.md)
- ddd4j-build is unblocked
- Task 1.2 report (the previous task's report) is at `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-1.2-report.md` — useful as a style/model reference for tone and depth

## Out of scope

- Do NOT touch any .java file
- Do NOT touch any pom.xml
- Do NOT write tasks 1.4-1.9 (separate subagent dispatches will follow)
- Do NOT modify docs/adr/ (that's task 1.10)
- Do NOT modify the existing 01-aggregate-root.md (separate task's scope)

## When You're in Over Your Head

If the fuin source files don't exist or have been substantially refactored, STOP and report BLOCKED with specifics.

## Self-review

- Did you write only `02-entity-id-path.md` (no premature files for 1.4-1.9)?
- Does it have all 6 mandatory sections with exact `## ` titles?
- Did you read EntityIdPath.java (not paraphrase from memory)?
- Did code snippets cite real line numbers?
- Are 借鉴/改写/不借鉴 specific?
- Did you preserve full-width Chinese punctuation?
- Single commit, only the new file?
- Does the document title match `# 02. fuin API 模式：EntityIdPath 链式实体路径`?

## Report Format

Write full report to: `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-1.3-report.md`
- File path + line count
- Section count (6) + section titles (verbatim)
- Number of fuin source code snippets + line refs
- Number of 借鉴/改写/不借鉴 bullets per category
- Self-review findings

Then reply with ONLY (under 15 lines):
- **Status:** DONE | DONE_WITH_CONCERNS | BLOCKED | NEEDS_CONTEXT
- Commit (short SHA + subject)
- One-line summary (e.g., "02-entity-id-path.md created, 6 sections, 110 lines")
- Concerns (if any)
- Report path