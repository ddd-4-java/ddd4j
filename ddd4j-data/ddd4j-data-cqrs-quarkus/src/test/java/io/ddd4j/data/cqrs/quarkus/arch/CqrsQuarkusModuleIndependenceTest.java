package io.ddd4j.data.cqrs.quarkus.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ddd4j-data-cqrs-quarkus 模块独立性自检：Quarkus 适配器的依赖面锁定（ADR-0004）。
 *
 * <p>本模块是 CQRS 命令侧 SPI 在 Quarkus 运行时的装配适配器，允许依赖 Quarkus
 * ／ArC 与 Jakarta API（CDI），但不得：
 * <ul>
 *   <li>引入 Spring（Spring 系运行时适配归 ddd4j-data-cqrs-spring——注意两边
 *       {@code @Transactional}／{@code @ActivateRequestContext} 语义不同包，
 *       Quarkus 侧严禁混入 Spring 刻板）</li>
 *   <li>引入 jakarta.persistence／JPA（持久化实现归 ddd4j-data-event-store-panache
 *       ／ddd4j-data-jpa 模块；本模块只做命令分发装配，禁止反向引）</li>
 * </ul>
 * 并以允许清单式规则 {@link #cqrs_quarkus_deps_allowlist} 锁定总依赖面，
 * 新增依赖必须显式加白并经 ADR 修订。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@AnalyzeClasses(packages = "io.ddd4j.data.cqrs.quarkus", importOptions = {ImportOption.DoNotIncludeTests.class})
class CqrsQuarkusModuleIndependenceTest {

    /**
     * Quarkus 适配器依赖允许清单：仅 JDK（java..）、io.ddd4j 家族模块
     * （命令契约与路由来自 ddd4j-core／ddd4j-data-cqrs）、Jakarta API
     * （jakarta..，CDI 上下文／注入）与 Quarkus 全家桶（io.quarkus..，ArC）。
     */
    @ArchTest
    static final ArchRule cqrs_quarkus_deps_allowlist =
            classes().that().resideInAPackage("io.ddd4j.data.cqrs.quarkus..")
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
    static final ArchRule cqrs_quarkus_no_spring =
            noClasses().that().resideInAPackage("io.ddd4j.data.cqrs.quarkus..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    /**
     * Quarkus 适配器不得依赖 Jakarta Persistence（JPA／Panache 持久化实现归
     * ddd4j-data-jpa／-event-store-panache 模块；本模块只做命令分发装配，
     * 禁止反向引）。
     */
    @ArchTest
    static final ArchRule cqrs_quarkus_no_jakarta_persistence =
            noClasses().that().resideInAPackage("io.ddd4j.data.cqrs.quarkus..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "jakarta.persistence..",
                            "javax.persistence.."
                    );
}
