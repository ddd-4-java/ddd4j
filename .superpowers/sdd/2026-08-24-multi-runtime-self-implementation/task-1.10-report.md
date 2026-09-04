# Task 1.10 Report — ADR 模板 + ADR-0001~0005

Status: DONE
Commit: `87baa7be docs(adr): ADR 模板 + ADR-0001~0005（参考系列 01-08 证据落地）`（feature/2.0.x，仅含 docs/adr/ 6 文件，352 行插入）

## 文件清单 + 行数

| 文件 | 行数 | 说明 |
|---|---|---|
| docs/adr/0000-template.md | 25 | 5 节模板＋顶部使用说明（编号递增、NNNN-kebab-case.md、Accepted 只能经新 ADR 推翻） |
| docs/adr/0001-no-fork-strategy.md | 75 | 不 fork fuin，reference-only |
| docs/adr/0002-core-zero-deps.md | 61 | core 零外部依赖（白名单三项＋ArchUnit 守护） |
| docs/adr/0003-multi-runtime-strategy.md | 64 | 跨 8 运行时适配策略（矩阵表＋56-84 天） |
| docs/adr/0004-command-bus-design.md | 62 | CommandBus 设计 |
| docs/adr/0005-event-store-spi.md | 65 | EventStore SPI 设计 |

5 篇 ADR 均在 60-120 行要求区间。

## 各 ADR 5 节确认

全部 6 个文件均为 `## Status / ## Context / ## Decision / ## Consequences / ## Alternatives Considered` 严格按序（grep 核验）；标题均为 H1 `# ADR-000N: <标题>`；正文全角标点（grep 检查 CJK 字符间无半角 `，。；：（`）。

## 引用核对

5 篇 ADR 中出现的相对路径引用全部指向存在文件（逐一验证）：

- ../reference/fuin-api-patterns/01-aggregate-root.md（ADR-0001/0002）
- ../reference/fuin-api-patterns/03-domain-event.md（ADR-0001/0002/0005）
- ../reference/fuin-api-patterns/04-event-sourcing-repository.md（ADR-0005，墓碑结论）
- ../reference/fuin-api-patterns/05-event-store.md（ADR-0001/0003/0005）
- ../reference/fuin-api-patterns/06-cqrs-command.md（ADR-0001/0002/0004）
- ../reference/fuin-api-patterns/07-cqrs-projection.md（ADR-0001/0003）
- ../reference/fuin-api-patterns/08-architecture-test.md（ADR-0001/0002）

每条引用均落在 brief 指定的证据点上：0001 补强引用 8 篇系列为 reference-only 落地证据（file:line 核验）；0002 引用 08 的「白名单只含 jackson/lang3/TTL」＋「ban＋白名单互补」＋noSpringDependencyInCore 与 no_spring_in_core 去重 deferred；0003 引用 07 的「投影抽象框架无关＋ViewScheduler SPI」；0004 引用 06 的「总线内置＋Class 路由＋无受检异常」三超 出结论；0005 引用 05 的「无 deleteStream（墓碑替代）、无 open/close、无双轨异步」三项决策（05 篇落地计划明确点名 Task 1.10 的三处回链）。

## Brief corrections（编号冲突修复）

- 计划同时列出 `0001-template.md` 与 `0001-no-fork-strategy.md`（编号撞车）——按 brief 指示模板命名为 **0000-template.md**，ADR 保持 0001-0005，0006 留给计划 Task 2.4。
- 计划 ADR-0003 骨架写「56-72 天」，与计划总表「合计 56-84 天」不一致——按 brief 采用 **56-84 天**。
- 现状核实：ddd4j-core pom 除白名单三项外还有 swagger-annotations-jakarta（编译期注解、无运行时传递），ADR-0002 中如实记为既有豁免，与 08 篇允许清单结论不冲突。

## Self-review

- 逐条对照 brief 的每 ADR 内容要求（Context/Decision/Consequences/Alternatives 四块）全部覆盖；ADR-0001/0003 在计划骨架上补强（缺陷证据引用、矩阵扩充、备选方案 C）。
- 验证脚本输出：6 文件、5 节有序、无 TBD/TODO/占位/FIXME（5 篇 ADR 计 0 命中；模板占位符按 brief 允许保留）、引用文件全存在。
- 越界检查：未改动 docs/reference/、未写 ADR-0006、未触碰 .java/pom；提交只含 docs/adr/。
- 残留关注：无阻塞项。ADR-0002 中「日志走 JUL、适配层桥接」是为闭环备选方案 A 而写的决策含义推断（brief 未显式给出），如与后续实现冲突可经新 ADR 修订。

## Fix Round 1（ADR-0002 SLF4J 基线事实修正）

Commit: `docs(adr): ADR-0002 修正 SLF4J 基线事实——记录现状与迁移义务（Task 2.5 前置）`

### 证据复核（本人逐条验证，非转抄评审）

- `ddd4j-dependencies/pom.xml:10386` 起为 BOM 的 `<dependencies>` 块；`:10398-10401` 的 `org.slf4j:slf4j-api` 未声明 `<scope>`（＝compile），注入全部子模块——ddd4j-core 未直接声明却继承（grep 全文件确认这是唯一 slf4j-api 注入点；slf4j-simple 为 test 作用域）；
- `ddd4j-core/src/main/java/io/ddd4j/core/constant/Constants.java:9-10` 导入 `org.slf4j.Marker／MarkerFactory`；`:53`／`:57`／`:61` 声明 `public static Marker accessMarker／authzMarker／bizMarker`——core 主源码 org.slf4j 唯一使用点（grep -rln org.slf4j 仅此一文件）；
- core 主源码 `java.util.logging` 0 命中——原 ADR「日志走 JDK JUL」确系虚构；
- 次要项复核：ddd4j-kit 传递闭包含 hutool-core／crypto／http／json、pinyin4j、guava、jakarta.servlet-api（ddd4j-kit/pom.xml:22-69），caveat 如实。

### 变更内容（ADR-0002，四项主要＋两项次要）

- Context「现状」改为直接依赖粒度的如实清单：＋slf4j-api BOM 注入（file:line）、＋Constants.java 公开 Marker 字段（file:line）、＋JUL 0 命中、＋swagger compile 作用域 Maven 层面会传递（修正原「无运行时传递」表述）；
- Context 末新增 caveat：白名单仅在直接依赖粒度成立；ddd4j-kit 传递闭包（hutool/guava/pinyin4j/servlet-api）收紧留待后续 ADR；
- Decision 新增「迁移义务（Task 2.5 前置）」三步：①Constants.java Marker 常量改纯 String（或迁 ddd4j-kit）；②core 排除／收窄 BOM slf4j-api（exclusion 或 BOM 改 provided）；③迁移前 `coreHasZeroExternalDependencies` 把 `org.slf4j..` 列过渡期允许清单、迁移后移除；`coreHasZeroExternalDependencies` 描述由「对齐 pom 实际依赖」（不实）改为「白名单终态＋差距经迁移消除」；
- Consequences：删除「日志只能走 JDK JUL」推断；改为「core 内不直接依赖日志门面，日志由家族模块（kit）与适配层承担」；新增负面项「迁移 Marker 常量是对外破坏性变更（公开 API）」；
- Alternatives 方案 A 改写为「现状备选」：如实标注 BOM 注入＋Marker 字段即今日事实，给出否决理由（破坏跨 8 运行时零依赖承诺、传染日志选型），并注明推翻须新 ADR；
- ADR-0001 Consequences 补回「不受 fuin 的 Spring 5＋Java 8 锁定」正面后果（独立一条）。

### 复验

- 5 节顺序完好（Status/Context/Decision/Consequences/Alternatives Considered）；无 TBD/TODO/占位（0 命中）；
- 行数：0001=76、0002=73（其余未动），均在 60-120 区间；
- `../reference/fuin-api-patterns/` 7 个引用文件全部存在；全角标点检查（CJK 间无半角 ，；：）通过；
- 范围检查：仅改 docs/adr/0001、0002 两个 .md，未触碰 pom／.java／docs/reference。
