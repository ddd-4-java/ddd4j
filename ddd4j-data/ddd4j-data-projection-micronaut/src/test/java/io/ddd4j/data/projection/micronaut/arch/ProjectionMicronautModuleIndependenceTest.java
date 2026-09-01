package io.ddd4j.data.projection.micronaut.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ddd4j-data-projection-micronaut 模块独立性自检：Micronaut CDI 投影调度适配器的依赖面锁定。
 *
 * <p>本模块是 ViewScheduler / ViewManager SPI 在 Micronaut 运行时的装配适配器，
 * 允许依赖 Micronaut 全家桶（micronaut-inject/runtime）与 Jakarta API
 * （jakarta.inject / jakarta.annotation），但不得：
 * <ul>
 *   <li>引入 Spring（Spring 系运行时适配归 ddd4j-data-projection-spring——注意两边
 *       调度机制不同，Micronaut 侧严禁混入 Spring 刻板）</li>
 *   <li>引入 Quarkus（Quarkus 运行时适配归 ddd4j-data-projection-quarkus）</li>
 * </ul>
 * 并以允许清单式规则 {@link #projection_micronaut_deps_allowlist} 锁定总依赖面，
 * 新增依赖必须显式加白并经 ADR 修订。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@AnalyzeClasses(packages = "io.ddd4j.data.projection.micronaut", importOptions = {ImportOption.DoNotIncludeTests.class})
class ProjectionMicronautModuleIndependenceTest {

    /**
     * Micronaut 投影调度适配器依赖允许清单：仅 JDK（java..）、io.ddd4j 家族模块
     * （投影契约来自 ddd4j-core/ddd4j-data-projection）、Jakarta API
     * （jakarta..，CDI 上下文／注入）与 Micronaut 全家桶（io.micronaut..，BeanContext）。
     */
    @ArchTest
    static final ArchRule projection_micronaut_deps_allowlist =
            classes().that().resideInAPackage("io.ddd4j.data.projection.micronaut..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "io.ddd4j..",
                            "java..",
                            "jakarta..",
                            "io.micronaut..",
                            "lombok.."
                    );

    /**
     * Micronaut 适配器不得依赖 Spring（Spring 系运行时适配归 -spring 模块；
     * Micronaut 模块严禁混入 Spring 刻板/事务注解）。
     */
    @ArchTest
    static final ArchRule no_spring_in_projection_micronaut =
            noClasses().that().resideInAPackage("io.ddd4j.data.projection.micronaut..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    /**
     * Micronaut 适配器不得依赖 Quarkus（Quarkus 运行时适配归 -quarkus 模块）。
     */
    @ArchTest
    static final ArchRule no_quarkus_in_projection_micronaut =
            noClasses().that().resideInAPackage("io.ddd4j.data.projection.micronaut..")
                    .should().dependOnClassesThat().resideInAPackage("io.quarkus..");
}
