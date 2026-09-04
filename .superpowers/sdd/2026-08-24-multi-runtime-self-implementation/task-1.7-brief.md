# Task 1.7 Brief — Write 06-cqrs-command.md（ddd4j-core 已对齐）

## Task

Write `ddd4j/docs/reference/fuin-api-patterns/06-cqrs-command.md` — the sixth of 8 fuin API reference documents. Topic: **CQRS command side** (Command / CommandExecutor / MultiCommandExecutor / Result). This is one of the three "ddd4j-core 已对齐" docs (with 1.4 and 1.8) — the core message is that ddd4j-core ALREADY has an aligned command abstraction, so the doc documents parity + deltas rather than proposing new design.

## Sources to read

**Primary (fuin cqrs-4-java 0.6.0):**
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/cqrs-4-java/core/src/main/java/org/fuin/cqrs4j/core/Command.java`
- `.../core/src/main/java/org/fuin/cqrs4j/core/CommandExecutor.java` (generic `<CONTEXT, RESULT, CMD>`, `getCommandTypes()` returns `Set<EventType>`, `execute(ctx, cmd)` throws 5 aggregate exceptions + CommandExecutionFailedException)
- `.../core/src/main/java/org/fuin/cqrs4j/core/AbstractMultiCommandExecutor.java` and `MultiCommandExecutor.java`
- `.../core/src/main/java/org/fuin/cqrs4j/core/Result.java` ( fuin version) and `ResultType.java`
- `.../core/src/main/java/org/fuin/cqrs4j/core/AggregateCommand.java` (getAggregateRootId)
- List the directory first and read whatever else is command-related (e.g. CommandExecutionFailedException, UrlParamEntityIdPathNotEqualsCmdException)

**ddd4j-core (parity side — KEY for this doc):**
- `ddd4j-core/src/main/java/io/ddd4j/core/cqrs/command/Command.java` (marker interface with design-principle javadoc)
- `.../command/CommandExecutor.java` (`<C extends Command>`, `supportedCommands()` returns `Set<Class<? extends Command>>`, `execute(C)` returns `Result`)
- `.../command/CommandBus.java` + `DefaultCommandBus.java` (Map<Class,CommandExecutor> routing, duplicate-executor detection)
- `.../command/Result.java` (`ok()/fail()`, `code/message/data`, `data()` returns `Optional<T>`, `isSuccess()`)

**Sibling docs for style**: 03-domain-event.md (the other 已对齐 doc — closest template), 05-event-store.md (marker convention). Do NOT modify 01-05.

## Mandatory structure (6 sections, exact `## ` titles)

`## 来源` / `## fuin 的设计` / `## 优点（值得借鉴的）` / `## 缺点（应规避的）` / `## ddd4j 自研决策` / `## 落地计划`

Content requirements:
- Section 5 must carry the headline conclusion「已对齐 + 超出」and enumerate concretely (verify each against real code before writing):
  - 对齐: Command marker；CommandExecutor 概念；Result 概念
  - ddd4j 改写/超出: (a) 泛型收窄 `<C extends Command>` vs fuin 三参 `<CONTEXT, RESULT, CMD>`；(b) `supportedCommands()` 返回 `Set<Class<? extends Command>>`（类型安全） vs fuin `Set<EventType>`（字符串类型对象）；(c) ddd4j `CommandBus`/`DefaultCommandBus` 注册中心 + 重复执行器检测 vs fuin 无总线、由 Spring/Quarkus 适配层直接发现；(d) `Result.data()` 返回 `Optional`、无受检异常 vs fuin `execute` 抛 5 个聚合受检异常 + CommandExecutionFailedException；(e) fuin 的 ctx 参数（上下文注入）ddd4j 通过 ThreadContext/Contexts 解决
  - 不借鉴: fuin 的 MultiCommandExecutor 抽象类层级（ddd4j 用 CommandBus 单一入口）；fuin 的 EventType 字符串路由
- 缺点 must cite real line refs (e.g. fuin CommandExecutor.java 的 throws 列表行号、三参泛型的实际行号)
- 落地计划: 微调型——指向阶段 6（ddd4j-data-cqrs SPI + 7 运行时适配器按 DefaultCommandBus 继承）与 ADR-0004
- 60-100 lines (已对齐 docs are shorter); full-width Chinese punctuation; title exactly `# 06. fuin API 模式：CQRS 命令侧`

## Commit

```bash
cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j
git add docs/reference/fuin-api-patterns/06-cqrs-command.md
git commit -m "docs(reference): 06-cqrs-command API 模式参考（ddd4j-core 已对齐）"
```

## Precedent (Tasks 1.3-1.6)

If this brief mischaracterizes either codebase, trust the real source over the brief and surface corrections in your report (DONE_WITH_CONCERNS). Every 借鉴/改写/不借鉴 bullet must be verifiable by file:line on both sides.

## Report

Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-1.7-report.md` (line count, sections, snippet count + line refs, per-category bullet counts, the concrete 对齐/超出 checklist, brief corrections, self-review). Reply ≤15 lines: Status / commit / one-line summary / concerns / report path.
