package io.ddd4j.data.cqrs.helidon.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ddd4j-data-cqrs-helidon 模块独立性自检：Helidon 适配器的依赖面锁定（ADR-0004）。
 *
 * <p>本模块是 CQRS 命令侧 SPI 在 Helidon SE 运行时的装配适配器，允许依赖
 * Helidon SE 全家桶（helidon-common-service-loader）与 Jakarta API
 * （jakarta.inject），但不得：
 * <ul>
 *   <li>引入 Spring（Spring 系运行时适配归 ddd4j-data-cqrs-spring）</li>
 *   <li>引入 Quarkus（Quarkus 运行时适配归 ddd4j-data-cqrs-quarkus）</li>
 *   <li>引入 Micronaut（Micronaut 运行时适配归 ddd4j-data-cqrs-micronaut，
 *       防止 6.5 适配误引——两个小众运行时适配器严禁互相污染）</li>
 * </ul>
 * 并以允许清单式规则 {@link #cqrs_helidon_deps_allowlist} 锁定总依赖面，
 * 新增依赖必须显式加白并经 ADR 修订。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@AnalyzeClasses(packages = "io.ddd4j.data.cqrs.helidon", importOptions = {ImportOption.DoNotIncludeTests.class})
class CqrsHelidonModuleIndependenceTest {

    /**
     * Helidon 适配器依赖允许清单：仅 JDK（java..）、io.ddd4j 家族模块
     * （命令契约与路由来自 ddd4j-core／ddd4j-data-cqrs）、Jakarta API
     * （jakarta..，@Singleton/@Inject 注入刻板——Helidon SE 核心即用 jakarta.inject
     * 而非 CDI）与 Helidon 全家桶（io.helidon..，HelidonServiceLoader 收集）。
     */
    @ArchTest
    static final ArchRule cqrs_helidon_deps_allowlist =
            classes().that().resideInAPackage("io.ddd4j.data.cqrs.helidon..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "io.ddd4j..",
                            "java..",
                            "jakarta..",
                            "io.helidon..",
                            "lombok.."
                    );

    /**
     * Helidon 适配器不得依赖 Spring（Spring 系运行时适配归 -spring 模块；
     * Helidon 模块严禁混入 Spring 刻板/事务注解）。
     */
    @ArchTest
    static final ArchRule cqrs_helidon_no_spring =
            noClasses().that().resideInAPackage("io.ddd4j.data.cqrs.helidon..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    /**
     * Helidon 适配器不得依赖 Quarkus（Quarkus 运行时适配归 -quarkus 模块，
     * 防止双框架混合污染单一运行时适配器）。
     */
    @ArchTest
    static final ArchRule cqrs_helidon_no_quarkus =
            noClasses().that().resideInAPackage("io.ddd4j.data.cqrs.helidon..")
                    .should().dependOnClassesThat().resideInAPackage("io.quarkus..");

    /**
     * Helidon 适配器不得依赖 Micronaut（Micronaut 运行时适配归 -micronaut 模块；
     * Task 6.5 的 BeanContext 收集与本模块的 ServiceLoader 收集是两条独立装配线，
     * 防止互相误引）。
     */
    @ArchTest
    static final ArchRule cqrs_helidon_no_micronaut =
            noClasses().that().resideInAPackage("io.ddd4j.data.cqrs.helidon..")
                    .should().dependOnClassesThat().resideInAPackage("io.micronaut..");
}
