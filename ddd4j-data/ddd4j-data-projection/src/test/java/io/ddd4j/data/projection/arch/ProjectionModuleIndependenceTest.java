package io.ddd4j.data.projection.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ddd4j-data-projection 模块独立性自检：确保框架无关的投影侧 SPI
 * （{@code ProjectionHandler} 入口 + {@code ProjectionHandlerRegistry} 注册中心
 * + {@code ProjectionDispatcher} 分发门面）不被任何运行时框架污染
 * ——投影契约全部复用 ddd4j-core（阶段 7 Task 7.1 决策：不重定义 core 契约）。
 *
 * <p>本测试在 CI 阶段自动执行，防止以下耦合再次引入：
 * <ul>
 *   <li>Spring Framework（调度器归阶段 7 Task 7.7+ 的 ddd4j-data-projection-spring）</li>
 *   <li>Quarkus（同上，ddd4j-data-projection-quarkus 等下游适配模块）</li>
 *   <li>Micronaut（ddd4j-data-projection-micronaut）</li>
 *   <li>Vert.x（ddd4j-data-projection-vertx——与阶段 6 各 SPI 模块一致的防误引约定）</li>
 * </ul>
 *
 * <p>并以允许清单式规则 {@link #projection_impl_deps_allowlist} 锁定总依赖面：
 * 仅 JDK、io.ddd4j 家族模块、Reactor（{@code ProjectionDispatcher.chunkByEvent}
 * 的 Flux 流式轨道，ADR-0005 单轨决策——与 ddd4j-data-event-store 同源约定）
 * 与 Lombok 编译期标记。新增依赖必须显式加白并经 ADR 修订。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@AnalyzeClasses(packages = "io.ddd4j.data.projection", importOptions = {ImportOption.DoNotIncludeTests.class})
class ProjectionModuleIndependenceTest {

    /**
     * SPI 模块不得直接依赖 Spring Framework。
     */
    @ArchTest
    static final ArchRule no_spring_in_projection_module =
            noClasses().that().resideInAPackage("io.ddd4j.data.projection..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    /**
     * SPI 模块不得直接依赖 Quarkus。
     */
    @ArchTest
    static final ArchRule no_quarkus_in_projection_module =
            noClasses().that().resideInAPackage("io.ddd4j.data.projection..")
                    .should().dependOnClassesThat().resideInAPackage("io.quarkus..");

    /**
     * SPI 模块不得直接依赖 Micronaut。
     */
    @ArchTest
    static final ArchRule no_micronaut_in_projection_module =
            noClasses().that().resideInAPackage("io.ddd4j.data.projection..")
                    .should().dependOnClassesThat().resideInAPackage("io.micronaut..");

    /**
     * SPI 模块不得直接依赖 Vert.x（防止误引 vertx 容器依赖；
     * 调度器适配归 ddd4j-data-projection-vertx，与阶段 6 一致约定）。
     */
    @ArchTest
    static final ArchRule no_vertx_in_projection_module =
            noClasses().that().resideInAPackage("io.ddd4j.data.projection..")
                    .should().dependOnClassesThat().resideInAPackage("io.vertx..");

    /**
     * SPI 模块依赖允许清单：仅 JDK（java..）、io.ddd4j 家族模块（投影契约
     * 来自 ddd4j-core）、Reactor（根包 {@code reactor..}——Maven 坐标
     * io.projectreactor:reactor-core 的 Java 包名；{@code chunkByEvent} 的
     * Flux 响应式轨道，ADR-0005 单轨决策：异步扩展仅此一份 Reactor 签名，
     * 接受 Reactor 进入 SPI 层）与 Lombok 编译期标记。
     * 新增依赖必须显式加白并经 ADR 修订。
     */
    @ArchTest
    static final ArchRule projection_impl_deps_allowlist =
            classes().that().resideInAPackage("io.ddd4j.data.projection..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "io.ddd4j..",
                            "java..",
                            "reactor..",
                            "lombok.."
                    );
}
