# Task 1.2 Brief (enriched — plan's brief is sparse)

## What this task is

Task 1.2 of 43 tasks in `docs/superpowers/plans/2026-08-24-multi-runtime-self-implementation.md`. Part of "阶段 1：高精度参考文档 + ADR".

This task writes `ddd4j/docs/reference/fuin-api-patterns/01-aggregate-root.md` — the first of 8 reference documents that summarize the API design patterns of fuin's `ddd-4-java` / `cqrs-4-java` projects, to inform ddd4j's self-implemented ES/CQRS abstraction layer.

Subsequent tasks (1.3-1.9) follow the same template. Each is dispatched separately.

## File to create

- Create: `ddd4j/docs/reference/fuin-api-patterns/01-aggregate-root.md`

## Source to read

The brief is intentionally research-heavy: this is a research/reference document, not implementation code. Read these fuin source files first:

- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd-4-java/core/src/main/java/org/fuin/ddd4j/core/AbstractAggregateRoot.java` (primary source — 238 lines, all methods)
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd-4-java/core/src/main/java/org/fuin/ddd4j/core/AggregateRoot.java` (interface)
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd-4-java/core/src/main/java/org/fuin/ddd4j/core/AbstractEntity.java` (companion abstract)
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd-4-java/core/src/main/java/org/fuin/ddd4j/core/DomainEvent.java` (interface)

Reference for ddd4j's existing self-developed contract (to align the "借鉴/改写/不借鉴" decision):

- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-core/src/main/java/io/ddd4j/core/ddd/model/AggregateRoot.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-core/src/main/java/io/ddd4j/core/ddd/event/DomainEvent.java`

## Document structure (mandatory 6 sections)

Use this exact structure. Section titles must be `## 来源`, `## fuin 的设计`, `## 优点（值得借鉴的）`, `## 缺点（应规避的）`, `## ddd4j 自研决策`, `## 落地计划`.

### Section 1: 来源 (Source)

```
- 仓库：<fuin repo URL>
- 版本：<fuin version>
- 文件：<relative path>:<line range>
- 关键 API：
  - <MethodName>（<line range>）：<one-line description>
  - <MethodName>（<line range>）：<one-line description>
  ...
```

### Section 2: fuin 的设计 (fuin's design)

Quote actual code from AbstractAggregateRoot.java (paste 5-15 line snippets). Use Java fenced blocks with `java` syntax highlighting. Cover the most important 3-5 methods with brief explanations of what each does.

### Section 3: 优点（值得借鉴的）

Bulleted list of 3-6 things that are good design. Be specific, not generic. E.g.:
- "反射驱动事件应用，避免手写 if-else 分发"
- "批量 loadFromHistory 比单事件 apply 快"

### Section 4: 缺点（应规避的）

Bulleted list of 3-6 design issues. E.g.:
- "JSR-305 @Nullable/@NotNull（javax.annotation）—— ddd4j 用 JSpecify"
- "getUncommittedChanges() 在抽象类里强制实现——违反封装"

### Section 5: ddd4j 自研决策

Three sub-bullets: 借鉴, 改写, 不借鉴. Be concrete.

### Section 6: 落地计划

Checkbox list `- [ ]` linking to plan tasks (stage 2 etc.) where each decision is implemented.

## Existing ddd4j-contract alignment

Since ddd4j-core already has AggregateRoot/EventHandler patterns (planned for stage 2), the doc should reference them and recommend **inherit + extend** rather than rewrite from scratch. Example tone:

> **借鉴**：apply() 反射机制 + ClassValue 缓存 — 落地在阶段 2 Task 2.2，扩展 ddd4j-core 现有 AggregateRoot

## Length guidance

Aim for 80-150 lines. Shorter = too sparse, longer = unnecessary detail.

## File header

Start with `# 01. fuin API 模式：聚合根反射事件应用` (the title from the README index).

## Chinese punctuation

Use full-width：`（`、`）`、`，`、`。`、`：`、`；`. Keep English code/identifiers in half-width.

## Commit

After writing the file, single commit:

```bash
cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j
git add docs/reference/fuin-api-patterns/01-aggregate-root.md
git commit -m "docs(reference): 01-aggregate-root API 模式参考"
```

## Context

- Working directory: `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j`
- Branch: `feature/2.0.x`
- Latest commit: `72a9f746` (Task 1.1 — README index skeleton)
- ddd4j-build is unblocked (`./mvnw verify -pl ddd4j-core,ddd4j-dependencies` passes)

## Out of scope

- Do NOT touch any .java file
- Do NOT touch any pom.xml
- Do NOT write tasks 1.3-1.9 (separate subagent dispatches will follow)
- Do NOT modify docs/adr/ (that's task 1.10)

## When You're in Over Your Head

If the fuin source files don't exist (fuin repos removed) or if ddd4j-core's existing AggregateRoot.java no longer matches expectations, STOP and report BLOCKED with specifics.

## Self-review

- Did you write only `01-aggregate-root.md` (no premature files for 1.3-1.9)?
- Does it have all 6 mandatory sections with exact `## ` titles?
- Did you read AbstractAggregateRoot.java (not paraphrase from memory)?
- Did the code snippets cite real line numbers?
- Are the "借鉴/改写/不借鉴" decisions specific (not generic)?
- Did you preserve full-width Chinese punctuation?
- Single commit, only the new file?

## Report Format

Write full report to: `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-1.2-report.md`
- File created (path + line count)
- Number of sections (must be 6)
- Number of code snippets quoted from fuin source (with line refs)
- Number of 借鉴/改写/不借鉴 bullets
- Self-review

Then reply with ONLY (under 15 lines):
- **Status:** DONE | DONE_WITH_CONCERNS | BLOCKED | NEEDS_CONTEXT
- Commit (short SHA + subject)
- One-line summary
- Concerns (if any)
- Report path