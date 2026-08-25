package io.ddd4j.data.projection.javalin.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ddd4j-data-projection-javalin 模块独立性自检：Javalin 投影调度适配器的依赖面锁定。
 *
 * <p>本模块是 ViewScheduler / ViewManager SPI 在 Javalin 运行时的装配适配器，
 * 允许依赖 Javalin 全家桶（io.javalin..，静态工厂的装配锚点）与 JDK 标准库，但不得：
 * <ul>
 *   <li>引入 Spring（Spring 系运行时适配归 ddd4j-data-projection-spring）</li>
 *   <li>引入 Quarkus（Quarkus 运行时适配归 ddd4j-data-projection-quarkus）</li>
 * </ul>
 * 并以允许清单式规则 {@link #projection_javalin_deps_allowlist} 锁定总依赖面，
 * 新增依赖必须显式加白并经 ADR 修订。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@AnalyzeClasses(packages = "io.ddd4j.data.projection.javalin", importOptions = {ImportOption.DoNotIncludeTests.class})
class ProjectionJavalinModuleIndependenceTest {

    /**
     * Javalin 投影调度适配器依赖允许清单：仅 JDK（java..）、io.ddd4j 家族模块
     * （投影契约来自 ddd4j-core/ddd4j-data-projection）、Jakarta API
     * （jakarta..，为集成方 jakarta 系刻板保留的声明面）与 Javalin 全家桶
     * （io.javalin..，create 工厂的装配锚点）。
     */
    @ArchTest
    static final ArchRule projection_javalin_deps_allowlist =
            classes().that().resideInAPackage("io.ddd4j.data.projection.javalin..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "io.ddd4j..",
                            "java..",
                            "jakarta..",
                            "io.javalin..",
                            "lombok.."
                    );

    /**
     * Javalin 适配器不得依赖 Spring（Spring 系运行时适配归 -spring 模块；
     * Javalin 模块严禁混入 Spring 刻板/事务注解）。
     */
    @ArchTest
    static final ArchRule no_spring_in_projection_javalin =
            noClasses().that().resideInAPackage("io.ddd4j.data.projection.javalin..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    /**
     * Javalin 适配器不得依赖 Quarkus（Quarkus 运行时适配归 -quarkus 模块，
     * 防止双框架混合污染单一运行时适配器）。
     */
    @ArchTest
    static final ArchRule no_quarkus_in_projection_javalin =
            noClasses().that().resideInAPackage("io.ddd4j.data.projection.javalin..")
                    .should().dependOnClassesThat().resideInAPackage("io.quarkus..");
}
