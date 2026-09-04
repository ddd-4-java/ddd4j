# Task 1.7 Report — 06-cqrs-command.md（ddd4j-core 已对齐）

## Deliverable

- File: `docs/reference/fuin-api-patterns/06-cqrs-command.md`
- Commit: `f30c5354`（single commit, message `docs(reference): 06-cqrs-command API 模式参考（ddd4j-core 已对齐）`, amended once during self-review to fold in verified corrections）
- Lines: 84（要求 60-100 ✓）
- Sections: 6，标题精确匹配要求（来源 / fuin 的设计 / 优点（值得借鉴的） / 缺点（应规避的） / ddd4j 自研决策 / 落地计划）
- Title: `# 06. fuin API 模式：CQRS 命令侧` ✓；全角标点 ✓

## Sources read

fuin cqrs-4-java 0.6.0（tag `0.6.0`，remote fuinorg/cqrs-4-java）core 全部命令侧文件：Command / CommandExecutor / CommandExecutionFailedException / AbstractMultiCommandExecutor / MultiCommandExecutor / Result / ResultType / AggregateCommand / UrlParamEntityIdPathNotEqualsCmdException / ToResultCapable。
ddd4j-core 4 文件：cqrs/command 下 Command / CommandExecutor / CommandBus / DefaultCommandBus / Result；另读 context/ThreadContext.java、context/Contexts.java（支撑 (e) 点）。
plan 文档阶段 6（行 2718-2940：7 适配器清单行 2749、SpringCommandBus 继承行 2932、ADR-0004=0004-command-bus-design 行 282）。

## Snippet count + line refs

代码片段 1 段（fuin CommandExecutor 三参签名＋throws 列表，CommandExecutor.java:37-62 逐字核对）。全文 file:line 引用 40+ 处，关键锚点：
- fuin CommandExecutor.java:33-37（三参泛型）、:44-45（Set&lt;EventType&gt;）、:61-62（6 受检异常）
- fuin AbstractMultiCommandExecutor.java:44/:47（raw 抑制＋Map）、:78-82（重复检测）、:93-101（EventType 路由 :97）
- fuin Command.java:25（extends Event）、Result.java:62-63（@Nullable getData）
- ddd4j Command.java:46（纯 marker）、CommandExecutor.java:48/:55/:63、CommandBus.java:6-9、DefaultCommandBus.java:14/:23/:33-36、Result.java:60-69/:75-77
- ThreadContext.java:23-37、Contexts.java:46-52

## Bullet counts

- 优点（值得借鉴的）：4 条（聚合五异常类型化／构造期重复检测／AggregateCommand＋URL 校验／Result 四元结构）
- 缺点（应规避的）：6 条（全部带 file:line）
- 自研决策：借鉴 0、已对齐 3、改写/超出 5（(a)-(e)）、不借鉴 3
- 落地计划：4 条（阶段 6×2、阶段 3×1、Task 1.10×1）

## 对齐/超出 checklist（逐条核实）

- 对齐：Command 概念（ddd4j Command.java:46 ↔ fuin Command.java:25）；CommandExecutor 概念（:48-63 ↔ fuin :37-62）；Result 概念（Result.java:21-77 ↔ fuin Result.java:31-63）。
- 改写/超出：(a) `<C extends Command>` vs 三参；Command 纯 marker vs extends Event；(b) `Set<Class<? extends Command>>` vs `Set<EventType>`；(c) CommandBus/DefaultCommandBus（Map 路由＋putIfAbsent 重复检测）vs fuin 无总线；(d) 无受检异常＋`Result.fail`＋`data()` Optional vs 5 聚合受检异常＋CommandExecutionFailedException＋@Nullable getData；(e) ThreadContext/Contexts 替代显式 ctx 参数。
- 不借鉴：MultiCommandExecutor 层级；EventType 字符串路由；Command extends Event 耦合。

## Brief corrections（以真实源码为准）

1. **brief (c) 称「fuin 无总线、由 Spring/Quarkus 适配层直接发现」——不准确**。核实：cqrs-4-java 0.6.0 的 quarkus/springboot 模块仅投影侧支撑（ViewManager/QryProjectionService），无任何执行器发现机制；官方示例 ddd-cqrs-4-java-example 0.5.0 的 PersonResource/PersonController 直调聚合仓储、抛聚合异常交 ExceptionMapper，**全仓库 grep 0 处 `implements CommandExecutor`**。即 fuin 的 CommandExecutor「有接口、无生态」。已在 doc 来源/缺点/(c) 三处修正——此发现反而强化 ddd4j「总线内置」的超出结论。
2. **brief「对齐: Command marker」表述需精确化**：fuin Command 不是纯 marker（`extends Event`，Command.java:25，强制携带事件元数据）；ddd4j 才是纯 marker。doc 已把该差异归入改写 (a) 并在已对齐项标注。
3. 微小：阶段 6 标题写「8 运行时适配」但 cqrs 适配器实为 7 个（spring/quarkus/micronaut/helidon/javalin/vertx/dropwizard，plan 行 2749）——doc 按 7 表述，与 brief 一致。

## Self-review

- 所有 file:line 在写前逐一重读核对（含 brief 预验证事实的再验证）；无凭记忆引用。
- 核实链条延伸出的最大发现（fuin 执行器未接线）在提交后自查时发现，已 Edit 三处修正并 `--amend` 并入同一提交，保持 single-commit 要求。
- ddd4j-core 既有 javadoc 小瑕疵（不属本任务修复范围，仅记录）：CommandExecutor.java:20 提到的 `io.ddd4j.core.cqrs.command.CommandRegistry` 在 ddd4j-core 中不存在——按 plan Task 6.2 该类将建于 `io.ddd4j.data.cqrs.CommandRegistry`，届时建议顺手修正该 javadoc 引用。
- 01-05 未改动；工作树仅剩本任务无关的既有未跟踪 plan 文件。

Status: DONE_WITH_CONCERNS（唯一 concern 即 brief correction #1——结论方向不变，证据链更强）
