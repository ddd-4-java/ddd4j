# Task 1.5 Brief — Write 04-event-sourcing-repository.md

## What this task is

Task 1.5 of 43 tasks. Part of "阶段 1：高精度参考文档 + ADR".

This task writes `ddd4j/docs/reference/fuin-api-patterns/04-event-sourcing-repository.md` — the fourth reference document about fuin's EventStore/EventSourcingRepository API.

Task 1.4 (commit `9b8cfcf9`) wrote 03-domain-event.md. Tasks 1.6-1.9 follow. Task 1.10 writes ADRs.

**Important precedent from Tasks 1.3 / 1.4**: previous briefs contained factual errors about fuin's API surface. The implementer correctly refused to propagate them and surfaced the corrections. **You should do the same** — read the actual fuin source files first, do not trust any pre-existing characterization in any prior doc, and surface any discrepancy with source-accurate corrections in your report.

## File to create

- Create: `ddd4j/docs/reference/fuin-api-patterns/04-event-sourcing-repository.md`

## Source to read

Primary (fuin `ddd-4-java/esc`):
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd-4-java/esc/src/main/java/org/fuin/ddd4j/esc/EventStoreRepository.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd-4-java/esc/src/main/java/org/fuin/ddd4j/esc/AggregateStreamId.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd-4-java/esc/src/main/java/org/fuin/ddd4j/esc/package-info.java`
- (Also note: fuin's `EventStore` interface comes from external `org.fuin.esc:esc-api:0.9.0` — outside ddd-4-java proper; Task 1.6 covers that)

Reference (ddd4j-core existing):
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-core/src/main/java/io/ddd4j/core/ddd/repository/EventSourcingRepository.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-core/src/main/java/io/ddd4j/core/ddd/repository/Repository.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-core/src/main/java/io/ddd4j/core/ddd/event/AggregateVersion.java`

## Document structure (mandatory 6 sections — same template as 1.2/1.3)

Use exact titles: `## 来源`, `## fuin 的设计`, `## 优点（值得借鉴的）`, `## 缺点（应规避的）`, `## ddd4j 自研决策`, `## 落地计划`.

### Section 1: 来源 (Source)

```
- 仓库：https://github.com/fuinorg/ddd-4-java
- 版本：0.7.0
- 文件：esc/src/main/java/org/fuin/ddd4j/esc/<ClassName>.java:<line range>
- 关键 API：
  - <MethodName>（<line range>）：<one-line description>
  - ...
```

**Before writing this section**, list the actual files in `ddd-4-java/esc/src/main/java/org/fuin/ddd4j/esc/` and read them all. The brief's claim that the package contains "EventStoreRepository, EventStore, AggregateStreamId" is from the plan's characterizations — verify against actual ls output (EventStore may NOT be in this package — it might be in external esc-api).

### Section 2: fuin 的设计

Quote 4-8 line snippets from real fuin source covering:
- The main class's key methods (load/add/update or whatever the real method names are — verify)
- AggregateStreamId definition
- Any helper types (Version conflict, etc.)

### Section 3: 优点（值得借鉴的）

3-5 bullets. Specifically about ES Repository pattern:
- "聚合根状态完全由事件流重建（loadFromHistory），无 ORM 持久化状态"
- "乐观锁版本冲突显式抛异常（AggregateVersionConflictException）"

### Section 4: 缺点（应规避的）

3-5 bullets:
- "fuin EventStoreRepository 与 ddd4j EventSourcingRepository 方法签名不完全对齐（fuin 用 add/update，ddd4j 用 read/add/update），需要明确迁移路径"
- "fuin 用 esc-api 子项目命名，ddd4j 集成到 ddd4j-data-event-store"

### Section 5: ddd4j 自研决策

**借鉴**：
- AggregateVersion 版本模型（ddd4j 已对齐）
- 乐观锁语义

**改写/对齐**：
- 接口名：fuin `EventStoreRepository` → ddd4j `EventSourcingRepository`（更准确表达"事件溯源"语义）
- 方法签名对齐 ddd4j-core 现有 `EventSourcingRepository<M, ID>` 接口

**不借鉴**：
- fuin 的 esc-api 子项目命名（ddd4j 集成到 `ddd4j-data-event-store`）
- fuin 用 `add/update` 而非 `add/read/update` 的命名

### Section 6: 落地计划

Checkbox list `- [ ]` linking to plan tasks:
- [ ] 阶段 3 (Task 3.2) EventStore SPI 设计——参考本 doc 的 `EventSourcingRepository` 设计
- [ ] 阶段 3 (Task 3.3) JpaEventStore 实现
- [ ] 阶段 4 (Task 4.x) 验证 ddd4j-core 现有 `EventSourcingRepository` 接口与 fuin 的对齐情况

## File header

Start with `# 04. fuin API 模式：EventSourcingRepository 事件溯源仓储`

## Length guidance

80-150 lines.

## Chinese punctuation

Use full-width: `（`、`）`、`，`、`。`、`：`、`；`. Keep English code/identifiers in half-width.

## Commit

After writing the file, single commit:

```bash
cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j
git add docs/reference/fuin-api-patterns/04-event-sourcing-repository.md
git commit -m "docs(reference): 04-event-sourcing-repository API 模式参考"
```

## Context

- Working directory: `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j`
- Branch: `feature/2.0.x`
- Latest commit: `9b8cfcf9` (Task 1.4 — 03-domain-event.md)
- ddd4j-build is unblocked
- Task 1.2, 1.3, 1.4 reports are templates for style/depth

## Out of scope

- Do NOT touch any .java file
- Do NOT touch any pom.xml
- Do NOT write tasks 1.6-1.9 (separate subagent dispatches will follow)
- Do NOT modify docs/adr/ (that's task 1.10)
- Do NOT modify 01/02/03 reference docs

## When You're in Over Your Head

If the fuin source files don't exist or have been substantially refactored, STOP and report BLOCKED with specifics. If the brief's characterization of fuin's API surface is wrong (you find the file doesn't exist or the methods are different), **surface that as a Concern in DONE_WITH_CONCERNS** rather than propagating the error.

## Self-review

- Did you write only `04-event-sourcing-repository.md`?
- Does it have all 6 mandatory sections with exact `## ` titles?
- Did you read the actual `esc/` files (not paraphrase from brief)?
- Did code snippets cite real line numbers?
- Are 借鉴/改写/不借鉴 specific to ddd4j's actual contract?
- Single commit, only the new file?
- Title is exactly `# 04. fuin API 模式：EventSourcingRepository 事件溯源仓储`?

## Report Format

Write full report to: `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-1.5-report.md`
- File path + line count
- Section count (6) + section titles
- Number of fuin source code snippets + line refs
- Number of 借鉴/改写/不借鉴 bullets per category
- **Any brief-corrections surfaced** (follow the Task 1.3/1.4 pattern)
- Self-review findings

Then reply with ONLY (under 15 lines):
- **Status:** DONE | DONE_WITH_CONCERNS | BLOCKED | NEEDS_CONTEXT
- Commit (short SHA + subject)
- One-line summary
- Concerns (if any)
- Report path