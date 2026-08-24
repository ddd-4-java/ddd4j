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

ddd4j 现状（阶段 1 基线）：

- `ddd4j-core` 实际依赖：jackson-databind／jackson-annotations、commons-lang3、transmittable-thread-local（另有仅编译期注解的 swagger-annotations-jakarta）；
- 契约校验统一 JSpecify 注解＋`Objects.requireNonNull`（01 篇改写结论），无 objects4j／jakarta.validation；
- `CoreIndependenceTest` 已有 6 条 ban 式规则（08 篇「自有」节）：
  - no_spring_in_core／no_mybatis_in_core／no_servlet_in_core／no_validator_in_core／no_aspectj_in_core；
  - api_package_is_pure_java（api 包×5 框架禁令）。

## Decision

**ddd4j-core 外部依赖白名单收敛为三项工具库**：

- jackson-databind＋jackson-annotations（事件契约序列化表示）；
- commons-lang3（字符串／对象工具）；
- transmittable-thread-local（TTL，供 `ThreadContext` 命令上下文跨线程池透传，06 篇改写 (e)）；
- swagger-annotations-jakarta 仅为编译期注解、无运行时传递，作为既有豁免保留；
- 除此之外只允许 JDK 与 io.ddd4j 家族模块。

**以 ArchUnit 双风格守护，禁止清单变胖**：

- 现存 6 条 ban 规则继续生效——ban 风格防特定框架渗透，是 ddd4j 自有写法（fuin 14 份测试无一条 noClasses 禁依赖，08 篇）；
- 计划 Task 2.5 新增 5 条（../reference/fuin-api-patterns/08-architecture-test.md「计划强化」节）：
  - `noFuInReference`——禁 `org.fuin..`，防参考实现渗入（ADR-0001 的执行器）；
  - `coreHasZeroExternalDependencies`——允许清单式，内容即上述白名单，对齐 pom 实际依赖；
  - `noSpringDependencyInCore`／`noQuarkusDependencyInCore`／`noMicronautDependencyInCore`——「8 运行时框架全部禁入 core」；
- **去重义务**：08 篇落地计划已指出 `noSpringDependencyInCore` 与现存 `no_spring_in_core`（CoreIndependenceTest.java:37-40）语义重合，Task 2.5 落地时合并为一条，不留双份规则；
- 允许清单即依赖契约：新增依赖必须显式加白并经新 ADR 修订本决策，评审焦点从「找出违规」转为「确认加白理由」（08 篇「优点」节）。

## Consequences

- 正面：核心契约在任何运行时下零版本冲突，业务方升级 Jackson、更换运行时不受 ddd4j 牵连；
- 正面：`ThreadContext`（TTL）保证命令执行上下文跨线程池透传（06 篇），与零依赖目标兼容；
- 正面：ArchUnit 使依赖纪律成为可执行断言而非评审约定，「测试即文档」（08 篇）；
- 正面：ban＋允许清单双风格互补——ban 防特定框架渗透、白名单锁总依赖面（08 篇「自有」节结论）；
- 负面：core 内不能直接使用 Spring／Quarkus 便利设施，框架交互全部下沉到 `ddd4j-web`／`ddd4j-data-*` 适配层，适配代码量上升（ADR-0003 承接）；
- 负面：日志只能走 JDK `java.util.logging`，由各运行时适配层桥接（SLF4J／Logback 生态在业务侧接入），core 内诊断输出表达力受限；
- 义务：Task 2.5 落地 5 条新规则并完成 noSpringDependencyInCore 与 no_spring_in_core 的去重；ddd4j-data 各新模块按全局约束各建一份 ArchUnit 测试（08 篇落地计划）。

## Alternatives Considered

- 方案 A：允许 SLF4J API 进 core（fuin 即如此，其白名单含 `org.slf4j..`，08 篇来源节）——**已否决**：slf4j-api 成为全体下游强制传递依赖，违背白名单收敛目标；fuin 的先例恰证明「只放一个日志门面」会随时间滑向家族库捆绑。日志桥接交由运行时适配层完成。
- 方案 B：完全零第三方（连 Jackson 也移出 core，序列化注解下沉到 data 层）——**已否决**：ddd4j `DomainEvent` 已内联 Jackson 注解（03 篇「不借鉴」节：单一抽象基类＋注解内联，无 Builder 样板），抽出注解将迫使事件契约与序列化表示分离的大重构；Jackson 是 8 运行时事实标准，保留收益大于成本。
- 方案 C：依赖收敛只靠 Maven Enforcer／人工评审，不写 ArchUnit——**已否决**：08 篇指出守护字节码访问的规则才暴露实际耦合，评审约定会静默腐化；fuin 14 份测试的先例证明「每模块一份守护测试」可持续。
