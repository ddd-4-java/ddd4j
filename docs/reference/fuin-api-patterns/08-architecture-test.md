# 08. fuin API 模式：ArchUnit 架构守护测试

> 对应 README 索引第 08 项；本篇收束 8 篇参考系列——fuin 两仓库 14 个模块各带一份 ArchUnit ArchitectureTest（ArchUnit 1.4.0，ddd-4-java pom.xml:37），ddd4j-core 已借其骨架建 CoreIndependenceTest（6 条规则），计划 Task 2.5 补「8 运行时零依赖」维度。

## 来源

- 仓库：https://github.com/fuinorg/ddd-4-java（0.7.0）、https://github.com/fuinorg/cqrs-4-java（0.6.0）（本地快照：`workspace-ddd4j-boot/ddd-4-java`／`cqrs-4-java`）
- 14 份 ArchitectureTest 全量盘点（均为 `@AnalyzeClasses(packagesOf = ...)`＋`@ArchTest` 静态字段骨架）：
  - ddd-4-java（7 份）——core：no_accesses_to_upper_package（:26-27）、core_access_only_to_defined_packages 允许清单 11 包（:30-45）、testDomainEventsAnnotations（:47-57）、testEntityIdAnnotations（:59-72）；jackson／jaxb／jsonb：同四条（jackson :33-34/:37-51/:53-63/:65-78；jaxb :33-34/:37-51/:53-63/:65-78；jsonb :33-34/:36-50/:52-62/:64-77）；esc：仅前两条（:19-20/:23-36）；codegen/api：仅前两条、允许清单最严（:19-20/:23-28）；jsonb-testmodel：前两条＋共享规则 verify_domain_events／verify_entity_ids（:23-24/:27-43/:45-49）
  - cqrs-4-java（7 份）——core（:19-20/:23-37）、esc（:22-23/:26-41）、jackson（:22-23/:26-45）、jaxb（:22-23/:26-43）、jsonb（:22-23/:26-43）、quarkus（:19-20/:23-44，放行 io.quarkus..）、springboot（:19-20/:23-43，放行 org.springframework..）——均只有「上层包禁依赖＋允许清单」两条
- 共享规则库：ddd-4-java `junit/src/main/java/org/fuin/ddd4j/junit/Ddd4JConditions.java`——DOMAIN_EVENT_RULES（:28-31）／ENTITY_ID_RULES（:37-42），供下游工程复用（目前仅 jsonb-testmodel 采用）
- ddd4j 侧：`ddd4j-core/src/test/java/io/ddd4j/core/arch/CoreIndependenceTest.java`（6 条禁依赖规则，Stage 0 验证通过）；计划 Task 2.5（plan :895-963）拟新增 5 条
- 勘误（本篇核实）：任务 brief 所称「slices 循环检测＋layeredArchitecture 分层规则」在两仓库 14 份文件中均不存在——fuin 实际只有「预定义上层包禁依赖＋允许清单＋注解一致性」三类规则；分层／循环维度由 ddd4j 自家 ddd4j-ddd-rules-clean／cola 模块补足

## fuin 的设计

每模块一份测试类，三种规则形态：

**1）预定义规则：禁依赖上层包（14/14 份）**

```java
@AnalyzeClasses(packagesOf = ArchitectureTest.class, importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {
    @ArchTest
    static final ArchRule no_accesses_to_upper_package = NO_CLASSES_SHOULD_DEPEND_UPPER_PACKAGES;
```

（ddd-4-java core ArchitectureTest.java:21-27）——一行复用 ArchUnit 内置规则，杜绝子包反向 import 父包。

**2）允许清单：依赖面白名单（14/14 份，模块各异）**

```java
@ArchTest
static final ArchRule core_access_only_to_defined_packages = classes()
        .that().resideInAPackage(THIS_PACKAGE)
        .should().onlyDependOnClassesThat()
        .resideInAnyPackage(THIS_PACKAGE, "java..",
                "jakarta.validation..", "jakarta.annotation..",
                "org.fuin.objects4j.common..",
                // ... 省略：objects4j.core／ui、utils4j、jboss.jandex（:41-44）
                "org.slf4j..");
```

（ddd-4-java core ArchitectureTest.java:29-45）——运行时适配模块同理但放行自家框架：cqrs-4-java quarkus 允许 io.quarkus.arc／runtime／scheduler 等（quarkus ArchitectureTest.java:28-44），springboot 允许 org.springframework.context／scheduling 等（springboot ArchitectureTest.java:28-43）。

**3）注解一致性：契约以测试强制（ddd-4-java 4 份＋共享库）**

```java
public static final ArchRule ENTITY_ID_RULES = classes()
        .that().areAssignableTo(EntityId.class)
        .and().doNotHaveModifier(JavaModifier.ABSTRACT)
        .should().beAnnotatedWith(HasPublicStaticValueOfMethod.class)
        .andShould().beAnnotatedWith(HasPublicStaticIsValidMethod.class)
        .andShould().beAnnotatedWith(HasEntityTypeConstant.class);
```

（ddd-4-java junit Ddd4JConditions.java:37-42）——core／jackson／jaxb／jsonb 四份测试类内联同义规则（如 core ArchitectureTest.java:59-72），jsonb-testmodel 已改引共享常量（ArchitectureTest.java:45-49）。

## 优点（值得借鉴的）

- 每模块自带边界守护：14 个模块 100% 覆盖，新模块从第一天起依赖面即受约束，不靠事后评审。
- 允许清单即依赖契约：核心模块 11 包白名单（core :30-45）把「能依赖什么」写成可执行断言，新增依赖必须显式加白——评审焦点从「找出违规」变成「确认加白理由」。
- 测试即文档：DomainEvent／EntityId 的注解契约（@HasSerializedDataTypeConstant／@HasPublicStaticValueOfMethod 等）由 ArchUnit 强制（Ddd4JConditions.java:28-42），javadoc 约定不会静默腐化。
- 规则声明式、可读性强：预定义规则一行（core :27）、清单规则一段流式 API（core :30-45），非架构背景的维护者也能读懂意图。
- 框架圈死在适配模块：quarkus／springboot 白名单各自放行自家框架包（quarkus :30-31/:40-43 的 jakarta.enterprise／inject 与 io.quarkus.*；springboot :39-43 的 org.springframework.*），两仓 core 白名单均不含任何运行时框架。

## 缺点（应规避的）

- 样板逐字复制：jackson 与 jaxb 两份测试类完全同构（:53-78 两处行号都一致），jsonb（:52-77）与 core（:47-72）亦为同段拷贝；fuin 自己抽了 Ddd4JConditions 共享库，但仅 jsonb-testmodel 一处迁移（:45-49），其余四份仍留内联旧写法——抽取不彻底。
- 只守包依赖，不守分层与循环：14 份文件均无 layeredArchitecture／slices 循环检测，模块内子包倒置、循环依赖不受约束。
- 守护字节码访问而非依赖清单：规则只检查实际访问的类（onlyDependOnClassesThat，core :30-45），pom 声明而未访问的依赖不会暴露——白名单与 Maven 依赖树之间无交叉校验，pom 可悄悄长胖。
- core 并非零依赖：ddd-4-java core 白名单锁死 objects4j（3 包）＋utils4j＋jboss.jandex＋slf4j＋jakarta.validation（:35-45），核心契约绑定 fuin 家族库——对照 ddd4j「纯 Java＋零外部依赖」目标偏松。

## ddd4j 自研决策

> **结论：骨架已借鉴、风格互补、强度将超越——CoreIndependenceTest 沿用 fuin 测试骨架与「测试即文档」理念，禁依赖（ban）风格为 ddd4j 自有；Task 2.5 再补允许清单与 8 运行时维度；fuin 的复制粘贴式样板以共享规则库替代。**

- **已借鉴（3 条）**：`@AnalyzeClasses(packages = "io.ddd4j.core")`＋DoNotIncludeTests＋`@ArchTest` 静态字段骨架（CoreIndependenceTest.java:31-32 ↔ fuin core :21-27）；javadoc 列耦合黑名单与已知豁免（CoreIndependenceTest.java:11-26）——「测试即文档」同款；每模块一份测试类的组织方式（对应 ddd4j-data 各模块 ArchUnit 全局约束）。
- **自有（1 条）**：fuin 全部 14 份均为「允许清单」式，无一条 noClasses 禁依赖；ddd4j 现存 6 条全是 ban 式——no_spring（:37-40）／no_mybatis（:45-48）／no_servlet（:53-56）／no_validator（:61-64）／no_aspectj（:69-72）＋api_package_is_pure_java（:77-86，双包×5 框架禁令）。两种风格互补：ban 防特定框架渗透，白名单锁总依赖面。
- **计划强化（Task 2.5，5 条新规则）**：noFuInReference（禁 org.fuin..，防参考实现渗入）＋coreHasZeroExternalDependencies（允许清单：jackson＋commons-lang3＋transmittable-thread-local，对齐 pom 实际依赖）＋noSpring／noQuarkus／noMicronaut 三条框架禁令——「8 运行时框架全部禁入 core」。fuin 无此维度：其 quarkus／springboot 模块本身就是框架耦合层，从未想过反向约束。
- **不借鉴（2 条）**：每模块复制粘贴骨架——ddd4j-data 落地时把共性规则沉淀为共享规则库／参数化（fuin Ddd4JConditions.java:28-42 已验证此路可行，但 fuin 抽取不彻底，ddd4j 全面化）；「白名单锁死家族库」——ddd4j core 白名单只含 jackson／lang3／TTL 三项（Task 2.5），不含任何必须的 io.ddd4j 外家族依赖。

## 落地计划

- [ ] Task 2.5：CoreIndependenceTest 追加 5 条规则（noFuInReference／coreHasZeroExternalDependencies／noSpringDependencyInCore／noQuarkusDependencyInCore／noMicronautDependencyInCore）；注意 noSpringDependencyInCore 与现存 no_spring_in_core（:37-40）语义重合，落地时去重。
- [ ] ddd4j-data 各模块：按全局约束「每个新模块必须有独立 ArchUnit 测试，禁止反向依赖核心」，各建一份测试类（借 fuin 每模块一份的组织方式＋允许清单写法）。
- [ ] 共性规则沉淀：参照 Ddd4JConditions 思路建 ddd4j 共享 ArchUnit 规则常量，避免 fuin 式逐字复制（注解规则仅 jsonb-testmodel 一处改用共享库，core／jackson／jaxb／jsonb 四处仍内联；允许清单骨架 14 份全为手抄）。
- [ ] ADR-0002（docs/adr/0002-core-zero-deps.md，Task 1.10）：引用本文档「core 允许清单只含 jackson／lang3／TTL」与「ban＋白名单互补」结论。
