# Task 6.3+6.4 Brief（合并派发）— SpringCommandBus + QuarkusCommandBus

## 背景
Task 6.1+6.2 交付了框架无关 SPI（CommandHandler 注解 + CommandRegistry 整批拒绝注册）。Task 6.2 已给 `DefaultCommandBus` 复用入口。本任务 = 两个最主流运行时适配器（Spring WebMVC/WebFlux + Quarkus）。

## 交付

### A. ddd4j-data-cqrs-spring（WebMVC/WebFlux + Helidon-Spring）
1. `pom.xml`：parent ddd4j-data；依赖 `ddd4j-data-cqrs` + `spring-context` + `spring-tx`（均 BOM 无版本），test `spring-boot-starter-test`（**注意：本地 .m2 无 spring-boot**，但 spring-boot-starter-test 解析的 spring-context/test 在 BOM 中已管理——4.1+4.2 task 实证 spring-boot 全仓无 BOM 管理，因此本模块 pom test 显式 `${spring-boot-it.version:3.4.4}` 局部声明 + javadoc 注明"仅测试栈"——与 4.1 决策一致）
2. `src/main/java/io/ddd4j/data/cqrs/spring/SpringCommandBus.java`：`@Component` 继承 `DefaultCommandBus(registry.executors())`（public final 子类，无 override），构造器 `@Autowired CommandRegistry` + `@Autowired ApplicationContext` 收集所有 `CommandExecutor` Bean（`context.getBeansOfType`）+ 调 `registry.register(executor)`。类级 `@Transactional`（spring-tx）包 execute。**javadoc**：仅服务 Spring 系（WebMVC/WebFlux/Helidon-Spring）；Quarkus 用 QuarkusCommandBus；该实现未拦截注册失败，registry 整批拒绝语义在装配期立即暴露。
3. `CommandHandler` 适配：仅 javadoc 提示（@CommandHandler 注解扫描由 spring 内置 `ClassPathScanningCandidateComponentProvider` 完成，集成方在 `@SpringBootApplication` 所在的 `application` 类加 `@ComponentScan(basePackages = "io.ddd4j.data.cqrs")` 即可——**本模块不提供自定义 BeanPostProcessor**（与 ADR-0004 决策一致：仅扫描 + 自动注入）。
4. `src/test/java/io/ddd4j/data/cqrs/spring/SpringCommandBusIT`：`@SpringBootTest(classes=TestApp)`，`TestApp=@SpringBootConfiguration+@EnableAutoConfiguration+@ComponentScan(io.ddd4j.data.cqrs.sample)`，`application.yml` 启用 tx 注解。TestApp 包内 `TestHandler` 加 `@Component + @CommandHandler(SampleCommand.class)`，**真实 Spring 容器** 走通。3 用例：①execute 返回 Result.success（直接 wire 真实业务）；②整批拒绝传播（多 type handler 之一冲突——同 4.4 注册语义）；③未注册命令走 ISE（与 ddd4j-core 一致）。记录 warning: spring-testcontext 启动开销。
5. ArchUnit ≥3：`cqrs_spring_deps_allowlist`（含 spring.*）、`cqrs_spring_no_ejb`、`cqrs_spring_no_jakarta_persistence`（jpa 在 data-jpa 模块；这里禁止反向引）。

### B. ddd4j-data-cqrs-quarkus（Quarkus 3.x）
1. `pom.xml`：parent ddd4j-data；依赖 `ddd4j-data-cqrs` + `quarkus-arc`（BOM 无版本），test `quarkus-junit5`（BOM）；类似 panache 5.1 的**模块级 quarkus-bom 再导入**（预防 5.1 实证的父链混版——用 3.38.2 同款，引用原 ddd4j-dependencies:8022 的 quarkus-bom version 即可——**不**再声明本地 quarkus.bom.version 属性，借用根 pom:55 的 `${quarkus.version}`）。
2. `src/main/java/io/ddd4j/data/cqrs/quarkus/QuarkusCommandBus.java`：`@ApplicationScoped` 继承 `DefaultCommandBus(executors)`；构造器 `@Inject Instance<CommandExecutor<?>>`（CDI Instance 流式收集，**留 brief-correction 余地**：发现与 SpringCommandBus 显式 `ApplicationContext.getBeansOfType` 等价；Quarkus Arc 自动扫描 `@CommandHandler` 注解+@ApplicationScoped——javadoc 注明）；`@ActivateRequestContext`（CDI 事务上下文，包 execute）。javadoc：仅服务 Quarkus；与 panache 5.1 模块协调——若 6.5（micronaut）须重新讨论 Instance 等价。
3. `src/test/java/io/ddd4j/data/cqrs/quarkus/QuarkusCommandBusIT`：`@QuarkusTest`，3 用例同 spring 模板（real ArC 容器）。H2 不用——Quarkus 自带启动容器；无 tx 注解（CDI request scope）。

### C. ddd4j-data 注册（两模块）
- spring: 字母序 cqrs 之后，datascope 之前？错——spring 框架名+服务对象，应按 ddd4j-data 子模块**末尾**（按适配运行时分组），但当前 ddd4j-data/pom.xml 无该分组惯例——按字母序插在 cqrs 后、crypto 前即统一（实际上 spring 适配与 cqrs 同业务语义紧邻，crypto 等基础设施类靠后，spring 跨 webmvc/webflux 是 cqrs 的扩展——**字母序正确**）。
- quarkus: 同 spring，按字母序插 spring 之后。

## 门禁
`./mvnw -pl ddd4j-data/ddd4j-data-cqrs,ddd4j-data/ddd4j-data-cqrs-spring,ddd4j-data/ddd4j-data-cqrs-quarkus,ddd4j-core -am install` BUILD SUCCESS；记录 4 模块测试精确计数（cqrs 12 + spring ≥3 + quarkus ≥3 + core 261）+ Spring 启动 warning + Quarkus 容器启动耗时。

## 提交
两 commit（任务范围清晰分离，便于后续 6.5+ 复用 commit 拆点）：
- `feat(data): ddd4j-data-cqrs-spring——Spring 适配（@Component + @Transactional）`
- `feat(data): ddd4j-data-cqrs-quarkus——Quarkus Arc 适配（@ApplicationScoped + @ActivateRequestContext）`

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-6.3-6.4-report.md`。Reply ≤15 lines.
