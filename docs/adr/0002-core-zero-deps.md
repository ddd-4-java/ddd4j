# ADR-0002: ddd4j-core 零外部依赖

## Status

Accepted（2026-08-24）

> 相关：ADR-0001（不 fork）、ADR-0003（跨 8 运行时）；守护测试落地于计划 Task 2.5。

## Context

ddd4j 面向 8 种运行时（Spring WebMVC／WebFlux、Quarkus、Micronaut、Helidon、Javalin、Vert.x、Dropwizard，见 ADR-0003），`ddd4j-core` 承载领域契约——聚合根、DomainEvent、CommandBus、EventStore SPI、readmodel 投影抽象——被全部运行时适配层共同依赖。核心层每引入一个第三方库，就变成全体业务方的强制传递依赖，并可能与其运行时的版本仲裁冲突（典型如 Jackson 版本被 Quarkus／Spring Boot BOM 锁定）。

fuin 是反面教材，三处证据：

- ddd-4-java core 的 ArchUnit 允许清单锁死 objects4j（3 包）＋utils4j＋jboss.jandex＋slf4j＋jakarta.validation，核心契约绑定作者家族库，「对照 ddd4j 纯 Java＋零外部依赖目标偏松」（../reference/fuin-api-patterns/08-architecture-test.md「缺点」节）；
- `EventType` 继承 objects4j 值对象基类、核心接口标 `jakarta.validation` 注解（../reference/fuin-api-patterns/03-domain-event.md「缺点」节）；
- 契约校验依赖 `org.fuin.objects4j.common.Contract`（../reference/fuin-api-patterns/01-aggregate-root.md）——03 篇「不借鉴」节明确将其列为「违背 ddd4j-core 零第三方依赖（ADR-0002）」。

ddd4j 现状（阶段 1 基线，2026-08-24 核验）：

- `ddd4j-core` 直接依赖：jackson-databind／jackson-annotations、commons-lang3、transmittable-thread-local、swagger-annotations-jakarta（编译期注解、运行时无行为，但 compile 作用域在 Maven 层面会传递给下游）；
- **slf4j-api 经 BOM 注入**：ddd4j-dependencies/pom.xml:10386 起的 `<dependencies>` 块把 `org.slf4j:slf4j-api` 注入全部子模块（:10398-10401，未声明 scope 即 compile）——ddd4j-core 未直接声明却实际继承；
- **SLF4J 已在 core 公开 API 面**：`ddd4j-core/src/main/java/io/ddd4j/core/constant/Constants.java:9-10` 导入 `org.slf4j.Marker／MarkerFactory`，:53／:57／:61 声明 `public static Marker accessMarker／authzMarker／bizMarker`——core 主源码中 org.slf4j 的唯一使用点；
- core 主源码无 `java.util.logging` 使用（grep 0 命中）；
- 契约校验统一 JSpecify 注解＋`Objects.requireNonNull`（01 篇改写结论），无 objects4j／jakarta.validation；
- `CoreIndependenceTest` 已有 6 条 ban 式规则（08 篇「自有」节）：
  - no_spring_in_core／no_mybatis_in_core／no_servlet_in_core／no_validator_in_core／no_aspectj_in_core；
  - api_package_is_pure_java（api 包×5 框架禁令）。

即：本 ADR 的白名单目前只在「直接依赖」粒度成立，slf4j-api 的注入与 Marker 常量是达成白名单前必须迁移的两处既成事实（见 Decision 迁移义务）。另需注意：家族模块 ddd4j-kit 的传递闭包远宽于白名单（hutool-core／crypto／http／json、pinyin4j、guava、jakarta.servlet-api 等）——收紧家族模块传递闭包超出本 ADR 范围，留待后续 ADR。

## Decision

**ddd4j-core 外部依赖白名单收敛为三项工具库**：

- jackson-databind＋jackson-annotations（事件契约序列化表示）；
- commons-lang3（字符串／对象工具）；
- transmittable-thread-local（TTL，供 `ThreadContext` 命令上下文跨线程池透传，06 篇改写 (e)）；
- swagger-annotations-jakarta 为编译期注解、运行时无行为，作为既有豁免保留（其 compile 作用域在 Maven 层面会传递给下游，如需彻底隔离须另行调整作用域，非本 ADR 义务）；
- 除此之外只允许 JDK 与 io.ddd4j 家族模块。

**迁移义务（Task 2.5 前置，白名单的达成条件）**：

1. 重构 `Constants.java` 的 `org.slf4j.Marker` 常量为纯 `String`（或整类迁往 ddd4j-kit）；
2. 为 ddd4j-core 排除／收窄 BOM 注入的 slf4j-api（exclusion，或将 BOM 中该依赖改为 provided 作用域），使 core 编译 classpath 不再含 org.slf4j；
3. 迁移完成前，`coreHasZeroExternalDependencies` 须把 `org.slf4j..` 列入**过渡期允许清单**，迁移完成后移除——规则落地不得先于迁移，也不得以迁就现状改写白名单终态。

**以 ArchUnit 双风格守护，禁止清单变胖**：

- 现存 6 条 ban 规则继续生效——ban 风格防特定框架渗透，是 ddd4j 自有写法（fuin 14 份测试无一条 noClasses 禁依赖，08 篇）；
- 计划 Task 2.5 新增 5 条（../reference/fuin-api-patterns/08-architecture-test.md「计划强化」节）：
  - `noFuInReference`——禁 `org.fuin..`，防参考实现渗入（ADR-0001 的执行器）；
  - `coreHasZeroExternalDependencies`——允许清单式，内容即上述白名单**终态**；现状与终态的差距（slf4j-api 注入）经迁移义务消除，过渡期允许清单见 Decision 第 3 条；
  - `noSpringDependencyInCore`／`noQuarkusDependencyInCore`／`noMicronautDependencyInCore`——「8 运行时框架全部禁入 core」；
- **去重义务**：08 篇落地计划已指出 `noSpringDependencyInCore` 与现存 `no_spring_in_core`（CoreIndependenceTest.java:37-40）语义重合，Task 2.5 落地时合并为一条，不留双份规则；
- 允许清单即依赖契约：新增依赖必须显式加白并经新 ADR 修订本决策，评审焦点从「找出违规」转为「确认加白理由」（08 篇「优点」节）。

## Consequences

- 正面：核心契约在任何运行时下零版本冲突，业务方升级 Jackson、更换运行时不受 ddd4j 牵连；
- 正面：`ThreadContext`（TTL）保证命令执行上下文跨线程池透传（06 篇），与零依赖目标兼容；
- 正面：ArchUnit 使依赖纪律成为可执行断言而非评审约定，「测试即文档」（08 篇）；
- 正面：ban＋允许清单双风格互补——ban 防特定框架渗透、白名单锁总依赖面（08 篇「自有」节结论）；
- 负面：core 内不能直接使用 Spring／Quarkus 便利设施，框架交互全部下沉到 `ddd4j-web`／`ddd4j-data-*` 适配层，适配代码量上升（ADR-0003 承接）；
- 负面：core 内不直接依赖日志门面，日志能力由家族模块（ddd4j-kit）与各运行时适配层承担，core 自身的诊断输出表达力受限；
- 负面：迁移 `Constants.java` 的 Marker 常量是**对外破坏性变更**（accessMarker／authzMarker／bizMarker 属公开 API），存量使用方需同步调整；
- 义务：Task 2.5 前完成 Decision 迁移义务三步（Marker 重构／BOM 注入收窄／过渡期允许清单）；落地 5 条新规则并完成 noSpringDependencyInCore 与 no_spring_in_core 的去重；ddd4j-data 各新模块按全局约束各建一份 ArchUnit 测试（08 篇落地计划）。

## Alternatives Considered

- 方案 A（**现状备选**）：允许 SLF4J API 留在 core——这正是今日事实：BOM compile 作用域注入（ddd4j-dependencies/pom.xml:10398-10401）＋Constants.java 公开 Marker 字段；fuin 亦如此，其白名单含 `org.slf4j..`（08 篇来源节）——**已否决**：slf4j-api 成为全体下游强制传递依赖，破坏「core 跨 8 运行时零依赖」承诺，公开 Marker 字段还会把日志实现选型传染给业务方；且此路线下无须迁移 Constants.java（迁移成本为零）正是其诱惑所在。若未来推翻（例如 Marker 常量被广泛依赖、迁移成本不可接受），须经新 ADR 记录。
- 方案 B：完全零第三方（连 Jackson 也移出 core，序列化注解下沉到 data 层）——**已否决**：ddd4j `DomainEvent` 已内联 Jackson 注解（03 篇「不借鉴」节：单一抽象基类＋注解内联，无 Builder 样板），抽出注解将迫使事件契约与序列化表示分离的大重构；Jackson 是 8 运行时事实标准，保留收益大于成本。
- 方案 C：依赖收敛只靠 Maven Enforcer／人工评审，不写 ArchUnit——**已否决**：08 篇指出守护字节码访问的规则才暴露实际耦合，评审约定会静默腐化；fuin 14 份测试的先例证明「每模块一份守护测试」可持续。
