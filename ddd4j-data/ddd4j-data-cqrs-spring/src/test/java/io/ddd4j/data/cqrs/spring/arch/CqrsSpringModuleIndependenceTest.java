package io.ddd4j.data.cqrs.spring.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ddd4j-data-cqrs-spring 模块独立性自检：Spring 适配器的依赖面锁定（ADR-0004）。
 *
 * <p>本模块是 CQRS 命令侧 SPI 在 Spring 系运行时（WebMVC/WebFlux/Helidon-Spring）
 * 的装配适配器，允许依赖 Spring Framework（spring-context/spring-tx），但不得：
 * <ul>
 *   <li>引入 EJB（Spring 系不用 Jakarta EJB；EJB 装配若将来需要应另立模块）</li>
 *   <li>引入 jakarta.persistence／JPA（持久化实现归 ddd4j-data-jpa 模块；
 *       本模块只做命令分发装配，禁止反向引）</li>
 * </ul>
 * 并以允许清单式规则 {@link #cqrs_spring_deps_allowlist} 锁定总依赖面，
 * 新增依赖必须显式加白并经 ADR 修订。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@AnalyzeClasses(packages = "io.ddd4j.data.cqrs.spring", importOptions = {ImportOption.DoNotIncludeTests.class})
class CqrsSpringModuleIndependenceTest {

    /**
     * Spring 适配器依赖允许清单：仅 JDK（java..）、io.ddd4j 家族模块
     * （命令契约与路由来自 ddd4j-core／ddd4j-data-cqrs）与 Spring Framework
     * （spring-context／spring-tx 刻板与收集）。
     */
    @ArchTest
    static final ArchRule cqrs_spring_deps_allowlist =
            classes().that().resideInAPackage("io.ddd4j.data.cqrs.spring..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "io.ddd4j..",
                            "java..",
                            "org.springframework..",
                            "lombok.."
                    );

    /**
     * Spring 适配器不得依赖 EJB（Spring 系运行时无 Jakarta EJB 装配场景）。
     */
    @ArchTest
    static final ArchRule cqrs_spring_no_ejb =
            noClasses().that().resideInAPackage("io.ddd4j.data.cqrs.spring..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "jakarta.ejb..",
                            "javax.ejb.."
                    );

    /**
     * Spring 适配器不得依赖 Jakarta Persistence（JPA 持久化实现归
     * ddd4j-data-jpa 模块；本模块只做命令分发装配，禁止反向引）。
     */
    @ArchTest
    static final ArchRule cqrs_spring_no_jakarta_persistence =
            noClasses().that().resideInAPackage("io.ddd4j.data.cqrs.spring..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "jakarta.persistence..",
                            "javax.persistence.."
                    );
}
