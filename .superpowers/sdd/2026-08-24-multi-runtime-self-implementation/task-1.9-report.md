# Task 1.9 Report — 08-architecture-test.md

## 交付

- 文件：`docs/reference/fuin-api-patterns/08-architecture-test.md`（88 行，要求 80-120）
- 6 节结构齐全：来源／fuin 的设计／优点（值得借鉴的）／缺点（应规避的）／ddd4j 自研决策／落地计划
- 标题精确：`# 08. fuin API 模式：ArchUnit 架构守护测试`；全角中文标点
- 提交：`ff7814c7 docs(reference): 08-architecture-test API 模式参考`（单提交，仅含此文件；01-07 与 README 未动）

## 找到的 ArchitectureTest 清单（14 份，全部通读）

- ddd-4-java（0.7.0）7 份：codegen/api、core、esc、jackson、jaxb、jsonb、jsonb-testmodel
- cqrs-4-java（0.6.0）7 份：core、esc、jackson、jaxb、jsonb、quarkus、springboot
- 附加共享规则库：ddd-4-java `junit/src/main/java/org/fuin/ddd4j/junit/Ddd4JConditions.java`（DOMAIN_EVENT_RULES :28-31、ENTITY_ID_RULES :37-42，仅 jsonb-testmodel 引用）
- 每份的规则清单＋行号已逐一写入文档「来源」节

## fuin 实际规则形态（3 类，均在文档引用真实代码＋行号）

1. 预定义 `NO_CLASSES_SHOULD_DEPEND_UPPER_PACKAGES`（14/14，如 ddd core :26-27）
2. 允许清单 `classes().that().resideInAPackage(..).should().onlyDependOnClassesThat().resideInAnyPackage(..)`（14/14，模块各异；ddd core 11 包 :30-45；quarkus 放行 io.quarkus.. :40-43；springboot 放行 org.springframework.. :39-43）
3. 注解一致性规则（ddd-4-java core/jackson/jaxb/jsonb 四份 @Test 内联＋jsonb-testmodel 走 Ddd4JConditions 共享常量 :45-49）

## 借鉴／强化／不借鉴计数（「ddd4j 自研决策」节）

- 已借鉴：3 条（测试骨架 @AnalyzeClasses+@ArchTest；javadoc 豁免即文档；每模块一份测试类组织方式）
- 自有：1 条（ban 式禁依赖 6 条现存规则——fuin 14 份全为白名单式、无一条 noClasses 禁令）
- 计划强化（Task 2.5）：5 条（noFuInReference／coreHasZeroExternalDependencies／noSpring／noQuarkus／noMicronaut，合成 8 运行时零依赖守护——fuin 无此维度）
- 不借鉴：2 条（复制粘贴式样板→共享规则库参数化；白名单锁死 objects4j 家族库→ddd4j core 白名单仅 jackson／lang3／TTL）

## Brief corrections（信源码不信 brief，已在文档「来源」节标注勘误）

1. **brief 的「典型模式」描述错误**：brief 称 fuin 用 `slices().matching("..(*)..").should().beFreeOfCycles()`＋layeredArchitecture＋noClasses 禁令——两仓库 14 份文件中均无 slices／layeredArchitecture；fuin 全部为白名单式，无 noClasses 禁令（后者恰是 ddd4j 自有风格）。文档按真实三类形态撰写。
2. brief 预期「至少 core＋esc＋cqrs/core」——实际 14 份（7＋7），全数盘点。
3. brief「fuin 每模块复制粘贴式样板」基本成立，但需补充：fuin 已抽 Ddd4JConditions 共享库（仅注解规则、仅 1 处采用），文档如实记载并在「不借鉴」中引为先例。

## Self-review

- 所有 file:line 引用逐条对照源文件核验；修正过 3 处行号（省略注释 :37-44→:41-44；quarkus/springboot 框架包行号；「13 份仍在拷贝」表述改为精确计数）
- 缺点节 4 条全部源码佐证（jackson 与 jaxb 同构测试行号完全一致 :53-78；无分层/循环规则为 14 份文件的可验证缺失；字节码 vs pom 交叉校验缺失源于规则写法；core 白名单 :35-45）
- 落地计划 4 条 `- [ ]`：Task 2.5（含 noSpringDependencyInCore 与现存 no_spring_in_core :37-40 语义重合的去重提醒——Task 2.5 执行者需注意）、ddd4j-data 每模块 ArchUnit（全局约束）、共享规则常量沉淀、ADR-0002（docs/adr/0002-core-zero-deps.md，Task 1.10）
- 计划 Task 2.5 代码片段用 @Test 方法而非 @ArchTest 字段且与现存规则重名语义——非本任务范围，已写入下方 Concerns

## Concerns

- Task 2.5 的 noSpringDependencyInCore 与 CoreIndependenceTest 现存 no_spring_in_core（:37-40）语义完全重合，落地时应去重（已在文档落地计划注明）
- Task 2.5 计划代码用 `@Test public void` 包装规则（且片段中 noClasses() 缺 .that()，系示意代码）；fuin／ddd4j 既有风格均为 `@ArchTest static final ArchRule` 字段，建议 Task 2.5 对齐字段风格
