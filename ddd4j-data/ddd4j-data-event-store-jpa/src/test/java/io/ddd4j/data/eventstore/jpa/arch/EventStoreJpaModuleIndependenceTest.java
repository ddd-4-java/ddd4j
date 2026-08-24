package io.ddd4j.data.eventstore.jpa.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ddd4j-data-event-store-jpa 模块独立性自检：Spring Data JPA 实现的依赖面锁定（ADR-0005）。
 *
 * <p>本模块是 EventStore SPI 在 Spring 系运行时（WebMVC/WebFlux 等）的 JPA 落地，
 * 允许依赖 Spring Data／Spring 刻板与事务注解，但不得：
 * <ul>
 *   <li>引入 Quarkus（其实现归 ddd4j-data-event-store-panache）</li>
 *   <li>引入 Micronaut（其实现归后续 micronaut 适配模块）</li>
 * </ul>
 * 并以允许清单式规则 {@link #jpa_impl_deps_allowlist} 锁定总依赖面，
 * 新增依赖必须显式加白并经 ADR 修订。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@AnalyzeClasses(packages = "io.ddd4j.data.eventstore.jpa", importOptions = {ImportOption.DoNotIncludeTests.class})
class EventStoreJpaModuleIndependenceTest {

    /**
     * JPA 实现模块依赖允许清单：仅 JDK（java..）、Jakarta API（jakarta..）、
     * io.ddd4j 家族模块、Jackson 三件套、Spring Data／刻板／事务注解与 Lombok 编译期标记。
     */
    @ArchTest
    static final ArchRule jpa_impl_deps_allowlist =
            classes().that().resideInAPackage("io.ddd4j.data.eventstore.jpa..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "io.ddd4j..",
                            "java..",
                            "jakarta..",
                            "com.fasterxml.jackson.annotation..",
                            "com.fasterxml.jackson.core..",
                            "com.fasterxml.jackson.databind..",
                            "org.springframework.data..",
                            "org.springframework.stereotype..",
                            "org.springframework.transaction.annotation..",
                            "lombok.."
                    );

    /**
     * JPA 实现模块不得依赖 Quarkus（Quarkus/Hibernate ORM 运行时归 panache 实现模块）。
     */
    @ArchTest
    static final ArchRule no_quarkus_in_jpa_module =
            noClasses().that().resideInAPackage("io.ddd4j.data.eventstore.jpa..")
                    .should().dependOnClassesThat().resideInAPackage("io.quarkus..");

    /**
     * JPA 实现模块不得依赖 Micronaut（防误引其它运行时依赖）。
     */
    @ArchTest
    static final ArchRule no_micronaut_in_jpa_module =
            noClasses().that().resideInAPackage("io.ddd4j.data.eventstore.jpa..")
                    .should().dependOnClassesThat().resideInAPackage("io.micronaut..");
}
