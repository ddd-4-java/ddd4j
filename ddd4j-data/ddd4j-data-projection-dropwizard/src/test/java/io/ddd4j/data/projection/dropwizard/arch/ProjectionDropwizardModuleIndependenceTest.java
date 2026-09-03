package io.ddd4j.data.projection.dropwizard.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ddd4j-data-projection-dropwizard 模块独立性自检：Dropwizard 投影调度适配器的依赖面锁定。
 *
 * <p>本模块是 ViewScheduler / ViewManager SPI 在 Dropwizard 5 运行时的装配适配器，
 * 允许依赖 Dropwizard 全家桶（io.dropwizard..，静态工厂的装配锚点）与 Jakarta API
 * （jakarta..，Dropwizard 5 为 Jakarta EE 10 系刻板保留的声明面），但不得：
 * <ul>
 *   <li>引入 Spring（Spring 系运行时适配归 ddd4j-data-projection-spring）</li>
 *   <li>引入 Quarkus（Quarkus 运行时适配归 ddd4j-data-projection-quarkus）</li>
 *   <li>引入 Micronaut（Micronaut 运行时适配归 ddd4j-data-projection-micronaut）</li>
 *   <li>引入 Vert.x（Vert.x 运行时适配归 ddd4j-data-projection-vertx）</li>
 * </ul>
 * 并以允许清单式规则 {@link #projection_dropwizard_deps_allowlist} 锁定总依赖面，
 * 新增依赖必须显式加白并经 ADR 修订。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@AnalyzeClasses(packages = "io.ddd4j.data.projection.dropwizard", importOptions = {ImportOption.DoNotIncludeTests.class})
class ProjectionDropwizardModuleIndependenceTest {

    /**
     * Dropwizard 投影调度适配器依赖允许清单：仅 JDK（java..）、io.ddd4j 家族模块
     * （投影契约来自 ddd4j-core/ddd4j-data-projection）、Jakarta API
     * （jakarta..，Dropwizard 5 的 Jakarta EE 10 声明面）与 Dropwizard 全家桶
     * （io.dropwizard..，create 工厂的装配锚点）。
     */
    @ArchTest
    static final ArchRule projection_dropwizard_deps_allowlist =
            classes().that().resideInAPackage("io.ddd4j.data.projection.dropwizard..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "io.ddd4j..",
                            "java..",
                            "jakarta..",
                            "io.dropwizard..",
                            "lombok.."
                    );

    /**
     * Dropwizard 适配器不得依赖 Spring（Spring 系运行时适配归 -spring 模块；
     * Dropwizard 模块严禁混入 Spring 刻板/事务注解）。
     */
    @ArchTest
    static final ArchRule projection_dropwizard_no_spring =
            noClasses().that().resideInAPackage("io.ddd4j.data.projection.dropwizard..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    /**
     * Dropwizard 适配器不得依赖 Quarkus（Quarkus 运行时适配归 -quarkus 模块，
     * 防止双框架混合污染单一运行时适配器）。
     */
    @ArchTest
    static final ArchRule projection_dropwizard_no_quarkus =
            noClasses().that().resideInAPackage("io.ddd4j.data.projection.dropwizard..")
                    .should().dependOnClassesThat().resideInAPackage("io.quarkus..");

    /**
     * Dropwizard 适配器不得依赖 Micronaut（Micronaut 运行时适配归 -micronaut
     * 模块——同为「无传统容器扫描」家族，防止 ServiceLoader 装配线互窜）。
     */
    @ArchTest
    static final ArchRule projection_dropwizard_no_micronaut =
            noClasses().that().resideInAPackage("io.ddd4j.data.projection.dropwizard..")
                    .should().dependOnClassesThat().resideInAPackage("io.micronaut..");

    /**
     * Dropwizard 适配器不得依赖 Vert.x（Vert.x 运行时适配归 -vertx 模块——
     * ServiceLoader 风格近亲，防止 manual-registration 家族内互窜）。
     */
    @ArchTest
    static final ArchRule projection_dropwizard_no_vertx =
            noClasses().that().resideInAPackage("io.ddd4j.data.projection.dropwizard..")
                    .should().dependOnClassesThat().resideInAPackage("io.vertx..");
}
