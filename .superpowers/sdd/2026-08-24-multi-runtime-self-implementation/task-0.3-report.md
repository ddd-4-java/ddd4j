# Task 0.3 Report: Delete fuin references from docs and README.md

**Status:** DONE_WITH_CONCERNS
**Commit:** `6ff92108` — `docs: 删除 fuin 依赖表述，标注为外部参考链接`
**Branch:** `feature/2.0.x` (parent: `30cb0d48`)

---

## Scope Decision

Files touched (2) — only files representing **current ddd4j architecture claims**:

1. `ddd4j/docs/ddd/1、DDD 经典分层架构目录结构.md` — explicitly named by the brief
2. `ddd4j/README.md` — brief says "如有 fuin 引用"; grep confirmed 7 fuin dependency-claim references, so it was edited

Files skipped (with reasons):

| File | Reason skipped |
|---|---|
| `docs/architecture/ddd4j_architecture.html` (8 matches) | Rendered SVG/HTML architecture diagram with visual `fuinorg` boxes and labels; rewriting requires regenerating the whole visualization. Out of scope per coordinator instruction. **CONCERN — see below.** |
| `docs/superpowers/plans/2026-06-27-ddd4j-2x-refactor-migration.md` (1) | Historical plan documenting past decisions. |
| `docs/superpowers/plans/2026-07-06-3x-domain-reorganization.md` (1) | Historical plan. |
| `docs/superpowers/plans/2026-08-24-fuin-reference-self-implementation.md` (many) | Historical/parallel plan; also currently **untracked** in git. Not staged. |
| `docs/superpowers/plans/2026-08-24-multi-runtime-self-implementation.md` (many) | The active plan being executed; also currently **untracked**. Not staged. |
| `docs/superpowers/specs/2026-06-29-*.md`, `2026-07-01-*.md`, `2026-07-02-*.md` (several) | Historical specs documenting a past architectural state (they self-describe as "历史基线" / 参考文档). |

## Edits

### 1. `docs/ddd/1、DDD 经典分层架构目录结构.md` (line ~656)

"Spring Boot DDD Starter" Maven snippet recommended the fuin artifact; replaced with ddd4j's own core artifact:

Before:
```xml
<groupId>org.fuin.ddd4j</groupId>
<artifactId>ddd-4-java-core</artifactId>
<version>0.7.0</version>
```
After:
```xml
<groupId>io.ddd4j</groupId>
<artifactId>ddd4j-core</artifactId>
<version>${ddd4j.version}</version>
```

### 2. `README.md` (7 spots)

| Line (before) | Before → After |
|---|---|
| 9–11 (intro) | "基于轻量级 ddd-4-java 和 cqrs-4-java 库实现…" → "DDD/CQRS/ES 抽象层全部由 `ddd4j-core` **自研实现**…API 形态参考了 ddd-4-java 与 cqrs-4-java（参考来源，不依赖）。" — URLs preserved with marker |
| 20 (核心定位 table) | "**底层依赖** \| fuinorg ddd-4-java + cqrs-4-java + esc-api" → "**底层依赖** \| 零第三方 DDD 框架依赖，DDD/CQRS/ES 抽象全部由 ddd4j-core 自研" |
| 45–46 (三层架构 ASCII diagram) | three `(fuinorg)` leaf boxes ddd-4-java/cqrs-4-java/esc-api → "DDD 构建块 / CQRS 抽象 / ES 抽象"，each labeled `(ddd4j-core)` |
| 53 (DDD 战术模式 bullet) | "并基于 fuinorg ddd-4-java 提供 ES 轨道" → "并由 ddd4j-core 自研提供 ES 轨道" |
| 55 (CQRS bullet) | "基于 fuinorg cqrs-4-java" → "基于 ddd4j-core 自研抽象" |
| 57 (三轨 DDD 模型 bullet) | "+ fuinorg CQRS/ES 轨道" → "+ ddd4j-core 自研 CQRS/ES 轨道" |
| 74 (学习资源 link) | 参考示例项目 link kept, added `**（参考来源，不依赖）**` after the bold label |

## Diff Stats

```
 README.md                                          | 22 +++++++++++-----------
 docs/ddd/1、DDD 经典分层架构目录结构.md            |  6 +++---
 2 files changed, 14 insertions(+), 14 deletions(-)
```

Single commit `6ff92108` containing only these two files (the two untracked plan files were deliberately NOT staged).

## Verification (brief Step 1 grep re-run)

- `grep -n "fuin" "docs/ddd/1、DDD 经典分层架构目录结构.md"` → **0 matches**
- `grep -n "fuin" README.md` → **3 matches**, all intentionally preserved reference URLs, each carrying the `（参考来源，不依赖）` marker:
  - line 10: `https://github.com/fuinorg/ddd-4-java`
  - line 11: `https://github.com/fuinorg/cqrs-4-java`（参考来源，不依赖）
  - line 74: `https://github.com/fuinorg/ddd-cqrs-4-java-example`（参考来源，不依赖）
- All **dependency-claim** references ("基于 fuinorg 实现"、"底层依赖 fuinorg") are gone from both files.

Note on the self-review checklist item "grep returns 0": it cannot be satisfied simultaneously with the brief's explicit instruction "若有 fuin 仓库 URL 作为外部参考链接，**保留**，但加（参考来源，不依赖）标记". I followed the brief (preserve URLs + marker). The plan's own zero-fuin gates (Task 9.2 / ledger constraint) only grep `*.java` and `pom.xml` for `org.fuin`, so this is consistent with the plan's verification regime.

## Self-Review

- [x] Touched only current-architecture files (README + docs/ddd/1); historical plans/specs and the active plan untouched
- [x] All 3 preserved URLs carry `（参考来源，不依赖）`
- [x] docs/ddd file: 0 fuin matches; README: only marked reference URLs remain
- [x] Single commit `6ff92108`, exactly 2 files; untracked plan files not swept in
- [x] Out-of-scope concerns surfaced (below)

## Out-of-Scope Concerns

1. **`docs/architecture/ddd4j_architecture.html` still features fuinorg prominently** (8 matches: "复用 fuinorg 实现"、"站在 fuinorg 系列之上"、`fuinorg` boxes in the SVG). This is a user-facing current-architecture artifact that now contradicts README. Needs a dedicated task to regenerate the diagram — recommend adding to a later stage.
2. **Leftover fuin property in `ddd4j-dependencies/pom.xml:85`**: `<fuin-cqrs4j.version>0.6.0</fuin-cqrs4j.version>` still exists. Task 0.1 (commit `4fd03ee3`) removed the 8 dependency blocks and `fuin-ddd4j.version`, but this one property slipped through (Task 0.1 Step 1 required deleting both properties). Harmless (nothing references it) but it will fail the plan's Task 9.2-style greps if those are extended to pom.xml property names. Suggest folding its removal into Task 0.4 or a follow-up.
3. **Historical plans/specs** (`docs/superpowers/plans/2026-06-27-*`, `2026-07-06-*`; `docs/superpowers/specs/2026-06-29-*`, `2026-07-01-*`, `2026-07-02-*`) retain fuin references by design — they document past states. Left untouched as instructed.
