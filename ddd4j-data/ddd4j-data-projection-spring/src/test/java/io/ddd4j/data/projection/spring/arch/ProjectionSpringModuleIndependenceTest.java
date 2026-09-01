package io.ddd4j.data.projection.spring.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ddd4j-data-projection-spring 模块独立性自检：Spring 投影调度适配器的依赖面锁定。
 *
 * <p>本模块是 ViewScheduler / ViewManager SPI 在 Spring 系运行时
 * （WebMVC/WebFlux/Helidon-Spring）的装配适配器，允许依赖 Spring Framework
 * （spring-context/spring-scheduling/spring-tx），但不得：
 * <ul>
 *   <li>引入 Quarkus（Quarkus 运行时适配归 ddd4j-data-projection-quarkus）</li>
 *   <li>引入 Micronaut（Micronaut 运行时适配归后续模块）</li>
 *   <li>引入 jakarta.persistence/JPA（持久化实现归 ddd4j-data-projection-jpa）</li>
 * </ul>
 * 并以允许清单式规则 {@link #projection_spring_deps_allowlist} 锁定总依赖面，
 * 新增依赖必须显式加白并经 ADR 修订。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@AnalyzeClasses(packages = "io.ddd4j.data.projection.spring", importOptions = {ImportOption.DoNotIncludeTests.class})
class ProjectionSpringModuleIndependenceTest {

    /**
     * Spring 投影调度适配器依赖允许清单：仅 JDK（java..）、io.ddd4j 家族模块
     * （投影契约来自 ddd4j-core/ddd4j-data-projection）与 Spring Framework
     * （spring-context/spring-tx/spring-scheduling 刻板与调度）。
     */
    @ArchTest
    static final ArchRule projection_spring_deps_allowlist =
            classes().that().resideInAPackage("io.ddd4j.data.projection.spring..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "io.ddd4j..",
                            "java..",
                            "org.springframework..",
                            "lombok.."
                    );

    /**
     * Spring 适配器不得依赖 Quarkus（Quarkus 运行时适配归 -quarkus 模块；
     * Spring 模块严禁混入 Quarkus/CDI 刻板）。
     */
    @ArchTest
    static final ArchRule no_quarkus_in_projection_spring =
            noClasses().that().resideInAPackage("io.ddd4j.data.projection.spring..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "io.quarkus..",
                            "jakarta.enterprise..",
                            "jakarta.inject.."
                    );

    /**
     * Spring 适配器不得依赖 Micronaut（Micronaut 运行时适配归后续模块）。
     */
    @ArchTest
    static final ArchRule no_micronaut_in_projection_spring =
            noClasses().that().resideInAPackage("io.ddd4j.data.projection.spring..")
                    .should().dependOnClassesThat().resideInAPackage("io.micronaut..");
}
