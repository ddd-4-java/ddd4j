package io.ddd4j.core.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ddd4j-core 模块独立性自检：确保核心契约层不被框架污染。
 *
 * <p>本测试在 CI 阶段自动执行，防止以下耦合再次引入：
 * <ul>
 *   <li>Spring Framework（spring-context / spring-beans / spring-core）</li>
 *   <li>MyBatis-Plus（com.baomidou）</li>
 *   <li>Jakarta Servlet（jakarta.servlet）</li>
 *   <li>Hibernate Validator（org.hibernate.validator）</li>
 *   <li>AspectJ（org.aspectj）</li>
 * </ul>
 *
 * <p>已知豁免：
 * <ul>
 *   <li>{@code ddd4j-core} 通过 {@code ddd4j-annotation} 间接依赖（但 annotation 本身已零框架依赖）</li>
 *   <li>{@code ddd4j-core} 的 deprecated 转发桩（{@code io.ddd4j.core.entity.*} 等）引用了迁移后的类，这些桩将在 5.0.x 移除</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@AnalyzeClasses(packages = "io.ddd4j.core", importOptions = {ImportOption.DoNotIncludeTests.class})
class CoreIndependenceTest {

    /**
     * 核心模块不得直接依赖 Spring Framework。
     */
    @ArchTest
    static final ArchRule no_spring_in_core =
            noClasses().that().resideInAPackage("io.ddd4j.core..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    /**
     * 核心模块不得直接依赖 MyBatis-Plus。
     */
    @ArchTest
    static final ArchRule no_mybatis_in_core =
            noClasses().that().resideInAPackage("io.ddd4j.core..")
                    .should().dependOnClassesThat().resideInAPackage("com.baomidou..");

    /**
     * 核心模块不得直接依赖 Jakarta Servlet。
     */
    @ArchTest
    static final ArchRule no_servlet_in_core =
            noClasses().that().resideInAPackage("io.ddd4j.core..")
                    .should().dependOnClassesThat().resideInAPackage("jakarta.servlet..");

    /**
     * 核心模块不得直接依赖 Hibernate Validator。
     */
    @ArchTest
    static final ArchRule no_validator_in_core =
            noClasses().that().resideInAPackage("io.ddd4j.core..")
                    .should().dependOnClassesThat().resideInAPackage("org.hibernate.validator..");

    /**
     * 核心模块不得直接依赖 AspectJ。
     */
    @ArchTest
    static final ArchRule no_aspectj_in_core =
            noClasses().that().resideInAPackage("io.ddd4j.core..")
                    .should().dependOnClassesThat().resideInAPackage("org.aspectj..");

    /**
     * 核心 contract 包（domain.contract）不得依赖任何外部框架（纯 Java + Jackson + Lombok）。
     */
    @ArchTest
    static final ArchRule contract_package_is_pure_java =
            noClasses().that().resideInAPackage("io.ddd4j.core.domain.contract..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "com.baomidou..",
                            "jakarta.servlet..",
                            "org.hibernate..",
                            "org.aspectj.."
                    );
}
