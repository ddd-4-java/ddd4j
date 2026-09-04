# Task 1.9 Brief — Write 08-architecture-test.md

## Task

Write `ddd4j/docs/reference/fuin-api-patterns/08-architecture-test.md` — the eighth and LAST of the fuin API reference documents. Topic: **ArchUnit 架构守护测试模式**（模块边界、循环依赖、分层规则）。This doc closes out the reference series: ddd4j already HAS CoreIndependenceTest (partially borrowed from fuin's per-module ArchitectureTest pattern), and plan Task 2.5 will strengthen it with 8-runtime zero-dependency rules — this doc supplies the pattern inventory for that.

## Sources to read

**Primary (fuin — find and read ALL ArchitectureTest files):**
```bash
find /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd-4-java -name "ArchitectureTest.java" -o -name "*Architecture*Test*.java" | sort
find /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/cqrs-4-java -name "ArchitectureTest.java" -o -name "*Architecture*Test*.java" | sort
```
Read every hit (expect at least ddd-4-java/core and esc, plus cqrs-4-java/core). Typical fuin pattern: ArchUnit `slices().matching("..(*)..").should().beFreeOfCycles()` + layeredArchitecture rules + `noClasses().that().resideInAPackage(...)` dependency bans.

**ddd4j side:**
- `ddd4j-core/src/test/java/io/ddd4j/core/arch/CoreIndependenceTest.java` — read fully; note which rules exist today (6 tests, verified passing in Stage 0)
- Plan Task 2.5 section in `docs/superpowers/plans/2026-08-24-multi-runtime-self-implementation.md` — the 5 NEW rules planned (noFuInReference / coreHasZeroExternalDependencies allowlist / noSpringDependencyInCore / noQuarkusDependencyInCore / noMicronautDependencyInCore)

**Sibling docs**: 04（marker 约定）、06/07（引用密度）。不修改 01-07，也不修改 README 索引。

## Mandatory structure (6 sections, exact `## ` titles)

`## 来源` / `## fuin 的设计` / `## 优点（值得借鉴的）` / `## 缺点（应规避的）` / `## ddd4j 自研决策` / `## 落地计划`

Content requirements:
- 来源 lists every ArchitectureTest found (repo + module + file), with the rule inventory per file (rule type + line refs)
- fuin 的设计: quote 2-3 real rule snippets (slices cycle check, layered architecture, dependency ban) with file:line; abridgments marked `// ... omitted：...（:行号）`
- 优点: e.g. 每模块自带边界守护；ArchUnit 声明式规则可读性强；测试即文档（架构约束可执行）
- 缺点: must be source-grounded — e.g. 规则松紧不均/模块间重复样板/只测包结构不测运行时行为（引用真实行号佐证；若某条不成立就删，勿编造）
- ddd4j 自研决策: 已借鉴（CoreIndependenceTest 存在的规则 vs fuin 模式逐条对照）+ 计划强化（Task 2.5 的 5 条新规则，8 运行时零依赖守护——fuin 无此维度）+ 不借鉴（fuin 每模块复制粘贴式样板 → ddd4j 用共享规则库/参数化）
- 落地计划: `- [ ]` 链接 Task 2.5（5 条新规则）、各 data 模块 ArchUnit（计划全局约束「每个新模块必须有独立 ArchUnit 测试」）、ADR-0002
- 80-120 lines；全角中文标点；title 精确为 `# 08. fuin API 模式：ArchUnit 架构守护测试`

## Commit

```bash
cd /Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j
git add docs/reference/fuin-api-patterns/08-architecture-test.md
git commit -m "docs(reference): 08-architecture-test API 模式参考"
```

## Precedent (Tasks 1.3-1.8)

信源码不信 brief；corrections 写报告。所有规则条目 file:line 可查。

## Report

Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-1.9-report.md`（行数、6 节、找到的 ArchitectureTest 清单、snippet+行引用、借鉴/强化/不借鉴 计数、brief corrections、self-review）。Reply ≤15 lines: Status / commit / one-line summary / concerns / report path.
