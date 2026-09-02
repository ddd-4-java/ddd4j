package io.ddd4j.data.projection.jpa.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ddd4j-data-projection-jpa 模块独立性自检：Spring Data JPA 实现的依赖面锁定。
 *
 * <p>与 {@code ddd4j-data-cqrs-spring} 不同——投影持久化基于 Spring Data JPA
 * <b>必然依赖 Spring</b>（JpaRepository/事务/刻板注解），故 no_spring 规则不适用本模块；
 * 改锁其它运行时不得混入，并以允许清单式规则 {@link #jpa_impl_deps_allowlist}
 * 锁定总依赖面，新增依赖必须显式加白并经 ADR 修订。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@AnalyzeClasses(packages = "io.ddd4j.data.projection.jpa", importOptions = {ImportOption.DoNotIncludeTests.class})
class ProjectionJpaModuleIndependenceTest {

    /**
     * JPA 实现模块依赖允许清单：仅 JDK（java..）、Jakarta API（jakarta..）、
     * io.ddd4j 家族模块、Spring 家族（Spring Data／刻板／事务等，
     * JPA 实现本质依赖）、Hibernate（JPA 供应商 API 面预留）与 Lombok 编译期标记。
     */
    @ArchTest
    static final ArchRule jpa_impl_deps_allowlist =
            classes().that().resideInAPackage("io.ddd4j.data.projection.jpa..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "io.ddd4j..",
                            "java..",
                            "jakarta..",
                            "org.springframework..",
                            "org.hibernate..",
                            "lombok.."
                    );

    /**
     * JPA 实现模块不得依赖 Quarkus（其实现归 ddd4j-data-projection-panache）。
     */
    @ArchTest
    static final ArchRule no_quarkus_in_jpa_module =
            noClasses().that().resideInAPackage("io.ddd4j.data.projection.jpa..")
                    .should().dependOnClassesThat().resideInAPackage("io.quarkus..");

    /**
     * JPA 实现模块不得依赖 Micronaut（防误引其它运行时依赖）。
     */
    @ArchTest
    static final ArchRule no_micronaut_in_jpa_module =
            noClasses().that().resideInAPackage("io.ddd4j.data.projection.jpa..")
                    .should().dependOnClassesThat().resideInAPackage("io.micronaut..");

    /**
     * JPA 实现模块不得依赖 Vert.x（防误引响应式运行时依赖）。
     */
    @ArchTest
    static final ArchRule no_vertx_in_jpa_module =
            noClasses().that().resideInAPackage("io.ddd4j.data.projection.jpa..")
                    .should().dependOnClassesThat().resideInAPackage("io.vertx..");

    /**
     * JPA 实现模块不得依赖 Dropwizard（防误引其它运行时依赖）。
     */
    @ArchTest
    static final ArchRule no_dropwizard_in_jpa_module =
            noClasses().that().resideInAPackage("io.ddd4j.data.projection.jpa..")
                    .should().dependOnClassesThat().resideInAPackage("io.dropwizard..");
}
