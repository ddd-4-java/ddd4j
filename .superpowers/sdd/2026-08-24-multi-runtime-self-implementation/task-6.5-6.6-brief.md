# Task 6.5+6.6 Brief（合并派发）— MicronautCommandBus + HelidonCommandBus

## 背景与 BOM 实证（控制器预检）
- **micronaut**: 本地有 `micronaut-core 4.10.26` 属性（BOM 4.7.1 行 447）但 **micronaut-bom 未在 ddd4j-dependencies 导入**—— micronaut-platform 4.10.17 本地有、micronaut-bom 3.10.10 版本错配。**模块级 micronaut-bom 4.10.17 再导入**对齐（与 5.1 panache 同款 pattern）。
- **helidon**: `helidon-bom 3.2.18` 属性（:300）+ 平台 BOM 已导入（:4032-4034），本模块**零声明 BOM 导入**——沿用 BOM 管理。
- 两者均无 Spring/Quarkus 依赖；ArchUnit 规则需 no_spring + no_quarkus（与之前模块一致）。

## 交付

### A. ddd4j-data-cqrs-micronaut
1. `pom.xml`：parent ddd4j-data；依赖 `ddd4j-data-cqrs` + `micronaut-core`（BOM 无版本）+ `micronaut-runtime`（BOM 无版本，事件驱动**scheduler 与 runtime 必须有**）+ `micronaut-inject`（可能隐含）；test `micronaut-test-junit5`。
   **模块级 dependencyManagement import 块**加 `io.micronaut.platform:micronaut-bom`（scope=import, BOM-managed 4.10.17 与 `micronaut.version` 一致——若 root pom 的 `${micronaut.version}=4.10.17` 与 platform 4.10.17 不一致，将 platform 版本锁 4.10.17 解决；**若 BOM 缺管理版本则显式 `<version>4.10.17</version>`**——实证优先）。
2. `src/main/java/io/ddd4j/data/cqrs/micronaut/MicronautCommandBus.java`：`@Singleton`（Micronaut 作用域），构造器 `@Inject ApplicationContext` + `BeanContext`（后者更精确——Micronaut 4 推荐用 `BeanContext` 而非 ApplicationContext 走 `@Prototype`/`@Singleton` 严格类型查找）。`getBeansOfType(CommandExecutor)` → `registry.register`。**事务**：Micronaut 的 `@Transactional`（jakarta.transaction.Transactional 同 spring 同名——检查 Micronaut 文档，**实际 Micronaut 用 `io.micronaut.transaction.annotation.Transactional`，jakarta 不生效**；javadoc 必须注明 Micronaut 的事务机制走 `micronaut-data-tx` 与 `@Transactional` 是 `micronaut-data` 注解——为避免增加 micronaut-data 依赖，**本实现显式不做事务**——javadoc 注明集成方用 `@Transactional` 在 service bean 上而非在 bus 上）。
3. `src/test/java/io/ddd4j/data/cqrs/micronaut/MicronautCommandBusIT.java`：`@MicronautTest`（junit5），`Application.start()` 启动真实 BeanContext；3 用例同 spring 模板。
4. ArchUnit 3 条：`cqrs_micronaut_deps_allowlist`（含 io.micronaut..）、`cqrs_micronaut_no_spring`、`cqrs_micronaut_no_quarkus`（保证 Micronaut 适配器不污染为多框架混合）。

### B. ddd4j-data-cqrs-helidon
1. `pom.xml`：parent ddd4j-data；依赖 `ddd4j-data-cqrs` + `io.helidon:helidon-core` + `io.helidon:helidon-context-propagation`（BOM 无版本；**不开 MicroProfile CDI**——`helidon-core` 自身含 ServiceLoader-based `BeanContainer` 用于服务发现）；test `io.helidon:helidon-junit5`。
2. `src/main/java/io/ddd4j/data/cqrs/helidon/HelidonCommandBus.java`：`@Singleton`（jakarta.inject，**Helidon 核心用 jakarta.inject**——非 Quarkus Arc CDI），构造器 `@Inject` + `BeanContainer` 查所有 `CommandExecutor`（ServiceLoader 风格）→ `registry.register`。**无框架事务依赖**——javadoc 注明 `@Transactional` 集成在 service 层（Helidon 的事务是 helidon-data-tx-jpa，与本模块解耦）。**不增加事务依赖**。
3. `src/test/java/io/ddd4j/data/cqrs/helidon/HelidonCommandBusIT.java`：`@HelidonTest(junit5)` 启 `BeanContainer`；3 用例同模板。
4. ArchUnit 3 条：allowlist 含 `io.helidon..` + jakarta..；`no_spring`、`no_quarkus`（外加 `no_micronaut` ——防止 6.5 适配误引）。

## 门禁
`./mvnw -pl ddd4j-data/ddd4j-data-cqrs-micronaut,ddd4j-data/ddd4j-data-cqrs-helidon -am install` BUILD SUCCESS；报告两模块精确测试计数 + dependency:tree micronaut-bom 解析版本 + helidon-bom 解析版本。

## 提交
两 commit（任务范围清晰分离，便于 6.7+ 复用 commit 拆点）：
- `feat(data): ddd4j-data-cqrs-micronaut——BeanContext 收集（事务集成由 @Transactional 在 service bean 完成）`
- `feat(data): ddd4j-data-cqrs-helidon——ServiceLoader 风格 BeanContainer 收集（不增加事务依赖）`

## Report
Write to `.superpowers/sdd/2026-08-24-multi-runtime-self-implementation/task-6.5-6.6-report.md`。Reply ≤15 lines.
