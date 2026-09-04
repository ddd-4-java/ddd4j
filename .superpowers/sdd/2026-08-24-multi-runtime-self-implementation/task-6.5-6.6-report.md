# Task 6.5+6.6 Report — ddd4j-data-cqrs-micronaut + ddd4j-data-cqrs-helidon

**Status: DONE.** 两 commit 均落 feature/2.0.x，最终门禁 BUILD SUCCESS。

## Commits

| Commit | 内容 |
| --- | --- |
| `ac9bf4fa` | feat(data): ddd4j-data-cqrs-micronaut——BeanContext 收集（事务集成由 @Transactional 在 service bean 完成） |
| `67a2b81e` | feat(data): ddd4j-data-cqrs-helidon——ServiceLoader 风格收集（HelidonServiceLoader，不增加事务依赖） |

ddd4j-data/pom.xml 模块注册严格字典序：cqrs → cqrs-helidon → cqrs-micronaut → cqrs-quarkus → cqrs-spring（brief 的插入顺序说法与字典序矛盾，按控制器指示取严格字典序；两个 commit 各自保持字典序完整）。

## 门禁与测试计数

`./mvnw -pl ddd4j-data/ddd4j-data-cqrs-micronaut,ddd4j-data/ddd4j-data-cqrs-helidon -am install` → **BUILD SUCCESS**（99 模块 reactor，含 ddd4j-core/ddd4j-data-cqrs 全绿）。

- **ddd4j-data-cqrs-micronaut：6 tests** = MicronautCommandBusIT ×3（真实 @MicronautTest 容器：成功路由／装配期冲突 ISE／未注册命令 ISE）+ CqrsMicronautModuleIndependenceTest ×3（allowlist / no_spring / no_quarkus）
- **ddd4j-data-cqrs-helidon：7 tests** = HelidonCommandBusIT ×3（真实 ServiceLoader 发现：成功路由／装配期冲突 ISE／未注册命令 ISE）+ CqrsHelidonModuleIndependenceTest ×4（allowlist / no_spring / no_quarkus / no_micronaut——brief "外加 no_micronaut 防止 6.5 适配误引"，故 4 条而非 3 条）
- 单模块门禁各自 install 亦 SUCCESS。

## dependency:tree 实证（关键版本）

**micronaut 模块**（模块级再导入 `io.micronaut.platform:micronaut-platform:${micronaut.version}=4.10.17`，与根 pom:448 对齐）：
- io.micronaut:micronaut-core / micronaut-inject / micronaut-runtime（含 aop/context/context-propagation/discovery-core/core-reactive/http/retry 传递）= **4.10.26**（平台 BOM 的 `${micronaut.core.version}`）
- io.micronaut.test:micronaut-test-junit5 = **4.10.3**；jakarta.inject-api 2.0.1；archunit-junit5 1.4.2；slf4j 2.0.18
- JUnit 线注记：平台 import 只把 junit-jupiter 聚合 POM 管到 5.14.0；父链逐项显式管理（api/engine/params/platform-* 挂同一 `${junit-jupiter.version}`=6.1.0）压过 import——代码级 jar 全 6.1.0，micronaut-test 4.10.3 的 Jupiter 扩展实测兼容（IT 全绿）。拆线需连 junit-platform 退 1.14.0（父链单属性设计不可局部覆写），刻意不拆，pom 注释已记。

**helidon 模块**（零 BOM 再导入，走 ddd4j-dependencies:4032 的 helidon-bom `${helidon-bom.version}=3.2.18`）：
- io.helidon.common:helidon-common-service-loader = **3.2.18**（传递 helidon-common 3.2.18 + jakarta.annotation-api 3.0.0）
- io.helidon.common.testing:helidon-common-testing-junit5 = **3.2.18**（test）；jakarta.inject-api 2.0.1；无 spring/quarkus/micronaut/tx 任何条目

## 对 brief 的实证修正（重要，6.7+ 复用需知）

1. **micronaut-bom 坐标**：`io.micronaut.platform:micronaut-bom` 不存在；本地 `io.micronaut:micronaut-bom` 仅 3.10.10（外来错配源）。实际再导入的 BOM 是 `io.micronaut.platform:micronaut-platform:4.10.17`（root 同款，panache pattern）。注意：父链 ddd4j-dependencies:6471 本就 import 了 micronaut-platform（effective-pom 实证 micronaut 已解析 4.10.26），模块级再导入系纵深防御（对齐 panache 先例），非修错。
2. **Helidon "BeanContainer"/helidon-core 不存在**：下载并扫描 helidon 3.2.18 与 4.2.2 全线 jar——无任何 BeanContainer 类；`io.helidon:helidon`（4.x core）在 3.2.18 线 404；helidon-bom 3.2.18 不管理 `helidon-core`/`helidon-context-propagation`/`helidon-junit5`（皆不存在）。实证替换：发现原语用 `io.helidon.common:helidon-common-service-loader` 的 **HelidonServiceLoader**（Helidon SE 的 ServiceLoader 装配原语，与 brief/commit msg 的 "ServiceLoader 风格" 一致）；test 用 BOM 管理的 `io.helidon.common.testing:helidon-common-testing-junit5`；`@Singleton`/`@Inject` 用 jakarta.inject（Helidon SE 核心即 jakarta.inject，非 CDI）。commit B 消息据此改为 "HelidonServiceLoader"（原 brief 消息引用了不存在的 BeanContainer）。
3. **Micronaut APT 为硬前提**：Micronaut Bean 定义靠编译期注解处理器生成（无 APT 则 @Singleton 对 BeanContext 不可见）——模块 pom 为 maven-compiler-plugin 配 annotationProcessorPaths（lombok + micronaut-inject-java，插件级配置同盖 main/test 编译）。后续 7.x ViewScheduler / sample-micronaut 模块必须同样配置。
4. **事务面**：两模块均零事务依赖、不 override execute（与 spring 模块的方法级 @Transactional 形成有意图对比，javadoc 已注明集成方姿势：Micronaut 用 io.micronaut.transaction.annotation.Transactional（micronaut-data）标在 service bean；Helidon 事务栈 helidon-data-tx-jpa 与本模块解耦）。
5. **HelidonCommandBus.discover(registry)** 静态工厂：收口 `ServiceLoader.load` 的 raw 泛型转换（单点 @SuppressWarnings），集成方一行装配；构造器（HelidonServiceLoader + CommandRegistry）保留给带排除/优先级的自定义装配。

## 文件清单

- `ddd4j-data/ddd4j-data-cqrs-micronaut/pom.xml`、`src/main/java/io/ddd4j/data/cqrs/micronaut/MicronautCommandBus.java`、`src/test/.../{MicronautCommandBusIT,SampleCommand,SampleCommandHandler,TestFactory,arch/CqrsMicronautModuleIndependenceTest}.java`
- `ddd4j-data/ddd4j-data-cqrs-helidon/pom.xml`、`src/main/java/io/ddd4j/data/cqrs/helidon/HelidonCommandBus.java`、`src/test/.../{HelidonCommandBusIT,SampleCommand,SampleCommandHandler,arch/CqrsHelidonModuleIndependenceTest}.java`、`src/test/resources/META-INF/services/io.ddd4j.core.cqrs.command.CommandExecutor`
- `ddd4j-data/pom.xml`（+2 行注册）
