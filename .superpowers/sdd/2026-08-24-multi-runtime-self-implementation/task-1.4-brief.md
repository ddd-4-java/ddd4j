# Task 1.4 Brief — Write 03-domain-event.md (ddd4j 已对齐)

## What this task is

Task 1.4 of 43 tasks. This is one of the three "ddd4j-core 已对齐" tasks (alongside 1.7 cqrs-command and 1.8 cqrs-projection) — the doc must explicitly state that ddd4j-core's existing `DomainEvent` contract is **already aligned with or exceeds** fuin's, so this is primarily a "document what's already there" task with only minor deltas to align.

Task 1.3 wrote `02-entity-id-path.md`. Tasks 1.5-1.9 follow.

## File to create

- Create: `ddd4j/docs/reference/fuin-api-patterns/03-domain-event.md`

## Source to read

Primary (fuin):
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd-4-java/core/src/main/java/org/fuin/ddd4j/core/DomainEvent.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd-4-java/core/src/main/java/org/fuin/ddd4j/core/Event.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd-4-java/core/src/main/java/org/fuin/ddd4j/core/EventType.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd-4-java/core/src/main/java/org/fuin/ddd4j/core/AbstractDomainEvent.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd-4-java/core/src/main/java/org/fuin/ddd4j/core/DomainEventPublisher.java`

Reference (ddd4j-core existing — KEY for this task):
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-core/src/main/java/io/ddd4j/core/ddd/event/DomainEvent.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-core/src/main/java/io/ddd4j/core/ddd/event/Event.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-core/src/main/java/io/ddd4j/core/ddd/event/EventType.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-core/src/main/java/io/ddd4j/core/ddd/event/DomainEventPublisher.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-core/src/main/java/io/ddd4j/core/ddd/event/NoopDomainEventPublisher.java`

## Document structure (mandatory 6 sections — same template as 1.2/1.3)

Use exact titles: `## 来源`, `## fuin 的设计`, `## 优点（值得借鉴的）`, `## 缺点（应规避的）`, `## ddd4j 自研决策`, `## 落地计划`.

### Section 1: 来源

```
- 仓库：<fuin repo URL>
- 版本：<fuin version>
- 文件：<relative path>:<line range>
- 关键 API：
  - DomainEvent<ID extends EntityId> extends Event (interface)
  - Event (marker interface, Serializable)
  - EventType (value object holding Class<? extends Event>)
  - AbstractDomainEvent (base class with common fields)
  - DomainEventPublisher.publish(event)
```

### Section 2: fuin 的设计

Quote 2-3 snippets covering:
- `DomainEvent` interface definition (short)
- `EventType` value object
- `AbstractDomainEvent` common fields (note: fuin lacks correlationId/causationId)

### Section 3: 优点（值得借鉴的）

3-5 bullets:
- "EventType 用 value object 包装 Class，避免裸 Class 的反序列化歧义"
- "DomainEventPublisher SPI 接口让事件分发可插拔"

### Section 4: 缺点（应规避的）

3-5 bullets — be specific to fuin's shortcomings here:
- "**无 correlationId/causationId 字段** —— fuin 仅 eventId + entityIdPath，缺失追踪链（关联追踪/因果追踪），这是 ES 生产环境的硬需求"
- "AbstractDomainEvent 用抽象类而非接口，限制了多继承场景"
- "EventType 内部用 Class.simpleName 作为事件标识字符串，反序列化时易因类移动/重命名而失效"

### Section 5: ddd4j 自研决策 ← **核心**：明确"已对齐 + 已超出"

> **结论：本节是 Task 1.4 的核心 —— 必须明确写出 ddd4j-core 已对齐甚至超出 fuin 的字段**

具体结构：

**借鉴**：无新增（ddd4j 已覆盖 fuin 的全部 API 形态）

**改写/已对齐**：
- `DomainEvent` 接口已对齐 fuin 接口，但**超出** —— 已加入 `correlationId/causationId/eventTimestamp` 完整元数据（见 ddd4j-core `DomainEvent.java:86-101`）
- `EventType` 已有 ClassValue 实现，保留
- `DomainEventPublisher` SPI 已对齐；`NoopDomainEventPublisher` 是 ddd4j 自研，fuin 没有

**不借鉴**：
- `AbstractDomainEvent` 抽象类模式 —— ddd4j 直接用接口 + 业务方自由实现
- `EventType.simpleName` 字符串方案 —— ddd4j 用 Jackson `@JsonTypeInfo` 多态类型

### Section 6: 落地计划

Checkbox list `- [ ]`. Since this task is mostly "已对齐"，落地计划应该是**微调而非新增**：

- [ ] 阶段 2 验证 ddd4j-core DomainEvent 字段在 ES 序列化/反序列化下正确
- [ ] 阶段 2 给 EventType 加 ClassValue 缓存（如果尚未存在）
- [ ] 阶段 3 (event-store SPI) 验证 StoredEvent.payload 包含 correlationId/causationId

## File header

Start with `# 03. fuin API 模式：DomainEvent 领域事件` （注意是"对比对齐"，不是"借鉴"）

## Length guidance

60-100 lines（短，因为"已对齐"，重点在"超出部分"）

## Chinese punctuation

Use full-width: `（`、`）`、`，`、`。`、`：`、`；`. Keep English code/identifiers in half-width.

## Commit

After writing the file, single commit:

```bash
cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j
git add docs/reference/fuin-api-patterns/03-domain-event.md
git commit -m "docs(reference): 03-domain-event API 模式参考"
```

## Context

- Working directory: `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j`
- Branch: `feature/2.0.x`
- Latest commit: `b2c4d331` (Task 1.3 — 02-entity-id-path.md)
- ddd4j-build is unblocked
- Task 1.2 report and Task 1.3 report are templates for style/depth

## Out of scope

- Do NOT touch any .java file
- Do NOT touch any pom.xml
- Do NOT write tasks 1.5-1.9 (separate subagent dispatches will follow)
- Do NOT modify docs/adr/ (that's task 1.10)
- Do NOT modify 01-aggregate-root.md or 02-entity-id-path.md (separate tasks' scope)

## When You're in Over Your Head

If the fuin source files don't exist or have been substantially refactored, STOP and report BLOCKED with specifics.

## Self-review

- Did you write only `03-domain-event.md`?
- Does it have all 6 mandatory sections with exact `## ` titles?
- Did you read DomainEvent.java in BOTH fuin and ddd4j-core (key for this task)?
- Did Section 5 explicitly state "ddd4j 已对齐" and list what ddd4j EXCEEDS fuin (correlationId/causationId/eventTimestamp)?
- Did code snippets cite real line numbers?
- Single commit, only the new file?
- Title is exactly `# 03. fuin API 模式：DomainEvent 领域事件`?

## Report Format

Write full report to: `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-1.4-report.md`
- File path + line count
- Section count (6) + section titles
- Number of fuin source code snippets + line refs
- Number of 借鉴/改写/不借鉴 bullets per category
- **Specific list of "ddd4j exceeds fuin" findings** (the key value-add of this doc)
- Self-review findings

Then reply with ONLY (under 15 lines):
- **Status:** DONE | DONE_WITH_CONCERNS | BLOCKED | NEEDS_CONTEXT
- Commit (short SHA + subject)
- One-line summary
- Concerns (if any)
- Report path