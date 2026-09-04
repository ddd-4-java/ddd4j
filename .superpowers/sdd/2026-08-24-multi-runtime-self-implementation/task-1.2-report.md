# Task 1.2 Report: 01-aggregate-root.md

## Result

- **Status: DONE**
- Commit: `789760fb` `docs(reference): 01-aggregate-root API 模式参考`（单提交，仅新增 1 个文件，142 insertions）
- File: `ddd4j/docs/reference/fuin-api-patterns/01-aggregate-root.md`，142 行（guideline 80-150 ✓）

## Sections (6/6, verbatim titles)

1. `## 来源`
2. `## fuin 的设计`
3. `## 优点（值得借鉴的）`
4. `## 缺点（应规避的）`
5. `## ddd4j 自研决策`
6. `## 落地计划`

标题行为 `# 01. fuin API 模式：聚合根反射事件应用`（与 README 索引一致）。

## Sources actually read (not from memory)

- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd-4-java/core/src/main/java/org/fuin/ddd4j/core/AbstractAggregateRoot.java`（全文 238 行）
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd-4-java/core/src/main/java/org/fuin/ddd4j/core/AggregateRoot.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd-4-java/core/src/main/java/org/fuin/ddd4j/core/AbstractEntity.java`
- `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd-4-java/core/src/main/java/org/fuin/ddd4j/core/DomainEvent.java`
- 补充：`MethodExecutor.java`（反射工具类，支撑 缺点 2 的缓存/bug 论断）
- ddd4j 参考：`ddd4j-core/.../ddd/model/AggregateRoot.java`、`ddd4j-core/.../ddd/event/DomainEvent.java`
- 版本确认：fuin ddd-4-java 0.7.0（root pom）

## Code snippets quoted

4 个逐字 Java 代码块（带行号引用）+ 1 个纯 prose 解析：

| # | 方法 | 引用行 |
|---|------|--------|
| 1 | `AbstractAggregateRoot.apply` | AbstractAggregateRoot.java:187-193 |
| 2 | `loadFromHistory(List)` | AbstractAggregateRoot.java:115-129 |
| 3 | `callAnnotatedEventHandlerMethod` | AbstractAggregateRoot.java:224-236 |
| 4 | 子实体路由（prose，无代码块） | :141-170（细分 :146-149、:151-154、:160-161、:169） |
| 5 | `AbstractEntity.apply` | AbstractEntity.java:56-58 |

所有行号已逐条对照源文件核实（含 ：20-21、:36-238、:40、:52-73、:68-72、:86-104、:97-99、:102-104、:107-129、:173、:177、:203-211；AggregateRoot.java:47/:59；MethodExecutor.java:82-99/:111-129/:192；ddd4j DomainEvent.java:54-59/:97-98/:217-229）。

## 借鉴/改写/不借鉴 bullets

- 借鉴：4 条（注解反射分发→Task 2.1/2.2；双通道分离→Task 2.2/2.3 硬断言；子实体经根登记→沿用 registerEvent 单列表；EntityIdPath 路由→复用现有字段）
- 改写：4 条（@ApplyEvent→@EventHandler+ignoreOnReplay()；全扫描→ClassValue 缓存；异常消息带 EntityIdPath；版本统一 AggregateVersion 值对象）
- 不借鉴：4 条（objects4j/jakarta.validation；`<ID extends AggregateRootId>` 泛型约束；final 化 equals/hashCode；接口暴露 markChangesAsCommitted）

## 落地计划 checkbox 项

6 项，分别链接 Task 2.1、2.2、2.3、2.4（ADR-0006）、2.5、2.6。

## Key findings（对后续任务有输入价值）

1. **fuin 真实缺陷**：`getIgnoredEvents()` 声明为 `protected final`（AbstractAggregateRoot.java:177），但其 Javadoc（:173）声称 "Subclasses can overwrite" —— 子类实际无法覆写，「忽略废弃历史事件」机制失效。ddd4j 的方法级 `@EventHandler(ignoreOnReplay=true)`（Task 2.1）是直接修复方案，已写入 改写。
2. **性能**：`MethodExecutor` 无任何 Method 缓存，每次 apply/回放全类层次 `getDeclaredMethods()` 线性扫描；且 `same()` 在 MethodExecutor.java:192 用 `expected[0] != actual[i]`（应为 `expected[i]`），多参匹配有 bug。支撑 ddd4j 改用 `ClassValue` 缓存（ddd4j DomainEvent 已有 `ClassValue<EventType>` 先例）。
3. **契约对齐**：ddd4j 自研决策基于现有 `AggregateRoot<ID extends Serializable>`（含 registerEvent/domainEvents/pullDomainEvents）走「继承 + 扩展」，不动现有签名。

## Self-review

- [x] 只写了 `01-aggregate-root.md`（无 1.3-1.9 的提前文件）
- [x] 6 个强制 `## ` 标题齐全且逐字匹配
- [x] 逐行阅读了 4 个 fuin 源文件（非凭记忆转述）；补充读了 MethodExecutor
- [x] 代码片段均逐字引用并标注真实行号（已逐一核对）
- [x] 借鉴/改写/不借鉴 均具体到 ddd4j 实际契约与阶段 2 任务号
- [x] 中文正文全角标点（（）、，。：；），代码/标识符半角，代码块带 `java` hint
- [x] 142 行 ∈ [80, 150]（初稿 167 行，已压缩：路由改 prose、合并引言、精简 API 清单）
- [x] 单提交 `789760fb`，diff 仅含新文件；两个未跟踪的 plan 文档未带入
- [x] 未触碰任何 .java、pom.xml、docs/adr/

## Concerns

无阻塞项。一个提示：发现并记录的 fuin `getIgnoredEvents()` final 缺陷与 MethodExecutor 缓存缺失已作为 改写 依据写入文档，Task 2.1/2.2 的子代理可直接引用本文件。
