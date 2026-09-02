package io.ddd4j.data.projection.quarkus.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ddd4j-data-projection-quarkus 模块独立性自检：Quarkus CDI 投影调度适配器的依赖面锁定。
 *
 * <p>本模块是 ViewScheduler / ViewManager SPI 在 Quarkus 运行时的装配适配器，
 * 允许依赖 Quarkus／ArC 与 Jakarta API（CDI），但不得：
 * <ul>
 *   <li>引入 Spring（Spring 系运行时适配归 ddd4j-data-projection-spring——注意两边
 *       调度机制不同，Quarkus 侧严禁混入 Spring 刻板）</li>
 *   <li>引入 Micronaut（Micronaut 运行时适配归后续模块）</li>
 * </ul>
 * 并以允许清单式规则 {@link #projection_quarkus_deps_allowlist} 锁定总依赖面，
 * 新增依赖必须显式加白并经 ADR 修订。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@AnalyzeClasses(packages = "io.ddd4j.data.projection.quarkus", importOptions = {ImportOption.DoNotIncludeTests.class})
class ProjectionQuarkusModuleIndependenceTest {

    /**
     * Quarkus 投影调度适配器依赖允许清单：仅 JDK（java..）、io.ddd4j 家族模块
     * （投影契约来自 ddd4j-core/ddd4j-data-projection）、Jakarta API
     * （jakarta..，CDI 上下文／注入）与 Quarkus 全家桶（io.quarkus..，ArC）。
     */
    @ArchTest
    static final ArchRule projection_quarkus_deps_allowlist =
            classes().that().resideInAPackage("io.ddd4j.data.projection.quarkus..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "io.ddd4j..",
                            "java..",
                            "jakarta..",
                            "io.quarkus..",
                            "lombok.."
                    );

    /**
     * Quarkus 适配器不得依赖 Spring（Spring 系运行时适配归 -spring 模块；
     * Quarkus 模块严禁混入 Spring 刻板/事务注解）。
     */
    @ArchTest
    static final ArchRule no_spring_in_projection_quarkus =
            noClasses().that().resideInAPackage("io.ddd4j.data.projection.quarkus..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    /**
     * Quarkus 适配器不得依赖 Micronaut（Micronaut 运行时适配归后续模块）。
     */
    @ArchTest
    static final ArchRule no_micronaut_in_projection_quarkus =
            noClasses().that().resideInAPackage("io.ddd4j.data.projection.quarkus..")
                    .should().dependOnClassesThat().resideInAPackage("io.micronaut..");
}
