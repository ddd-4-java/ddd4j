package io.ddd4j.data.cqrs.micronaut.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ddd4j-data-cqrs-micronaut 模块独立性自检：Micronaut 适配器的依赖面锁定（ADR-0004）。
 *
 * <p>本模块是 CQRS 命令侧 SPI 在 Micronaut 运行时的装配适配器，允许依赖 Micronaut
 * 全家桶（micronaut-core/inject/runtime）与 Jakarta API（jakarta.inject），但不得：
 * <ul>
 *   <li>引入 Spring（Spring 系运行时适配归 ddd4j-data-cqrs-spring——注意两边
 *       {@code @Transactional} 语义不同包，Micronaut 侧严禁混入 Spring 刻板）</li>
 *   <li>引入 Quarkus（Quarkus 运行时适配归 ddd4j-data-cqrs-quarkus；本模块
 *       刻意不引 micronaut-data/micronaut-data-tx，事务不在适配器层）</li>
 * </ul>
 * 并以允许清单式规则 {@link #cqrs_micronaut_deps_allowlist} 锁定总依赖面，
 * 新增依赖必须显式加白并经 ADR 修订。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@AnalyzeClasses(packages = "io.ddd4j.data.cqrs.micronaut", importOptions = {ImportOption.DoNotIncludeTests.class})
class CqrsMicronautModuleIndependenceTest {

    /**
     * Micronaut 适配器依赖允许清单：仅 JDK（java..）、io.ddd4j 家族模块
     * （命令契约与路由来自 ddd4j-core／ddd4j-data-cqrs）、Jakarta API
     * （jakarta..，@Singleton/@Inject 注入刻板）与 Micronaut 全家桶
     * （io.micronaut..，BeanContext 收集）。
     */
    @ArchTest
    static final ArchRule cqrs_micronaut_deps_allowlist =
            classes().that().resideInAPackage("io.ddd4j.data.cqrs.micronaut..")
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
    static final ArchRule cqrs_micronaut_no_spring =
            noClasses().that().resideInAPackage("io.ddd4j.data.cqrs.micronaut..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    /**
     * Micronaut 适配器不得依赖 Quarkus（Quarkus 运行时适配归 -quarkus 模块，
     * 防止双框架混合污染单一运行时适配器）。
     */
    @ArchTest
    static final ArchRule cqrs_micronaut_no_quarkus =
            noClasses().that().resideInAPackage("io.ddd4j.data.cqrs.micronaut..")
                    .should().dependOnClassesThat().resideInAPackage("io.quarkus..");
}
