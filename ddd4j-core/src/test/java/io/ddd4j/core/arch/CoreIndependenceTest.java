package io.ddd4j.core.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ddd4j-core 模块独立性自检：确保核心契约层不被框架污染。
 *
 * <p>本测试在 CI 阶段自动执行，防止以下耦合再次引入：
 * <ul>
 *   <li>Spring Framework（spring-context / spring-beans / spring-core）</li>
 *   <li>Quarkus / Micronaut（8 运行时框架全部禁入 core，ADR-0003）</li>
 *   <li>MyBatis-Plus（com.baomidou）</li>
 *   <li>Jakarta Servlet（jakarta.servlet）</li>
 *   <li>Hibernate Validator（org.hibernate.validator）</li>
 *   <li>AspectJ（org.aspectj）</li>
 *   <li>fuin 参考实现（org.fuin，ADR-0001 的执行器）</li>
 * </ul>
 *
 * <p>并以允许清单式规则 {@link #core_zero_external_dependencies} 锁定总依赖面（ADR-0002 白名单终态）。
 *
 * <p>已知豁免：
 * <ul>
 *   <li>{@code ddd4j-core} 通过 {@code ddd4j-annotation} 间接依赖（但 annotation 本身已零框架依赖）</li>
 *   <li>{@code ddd4j-core} 的 deprecated 转发桩（{@code io.ddd4j.core.entity.*} 等）引用了迁移后的类，这些桩将在 5.0.x 移除</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">Partme.AI</a>
 * @since 2.0.x
 */
@AnalyzeClasses(packages = "io.ddd4j.core", importOptions = {ImportOption.DoNotIncludeTests.class})
class CoreIndependenceTest {

    /**
     * 核心模块不得直接依赖 Spring Framework。
     *
     * <p>本规则即计划 Task 2.5 sketch 中的 {@code noSpringDependencyInCore}——两者语义重合，
     * 按 ADR-0002 去重义务合并保留本条，不另立新规则。
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
     * 核心 API 包不得依赖任何外部框架（纯 Java + Jackson + Lombok）。
     */
    @ArchTest
    static final ArchRule api_package_is_pure_java =
            noClasses().that().resideInAnyPackage("io.ddd4j.core.api..", "io.ddd4j.core.enums..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "com.baomidou..",
                            "jakarta.servlet..",
                            "org.hibernate..",
                            "org.aspectj.."
                    );

    /**
     * 核心模块不得引用 fuin 参考实现（ADR-0001 的执行器，防参考实现渗入）。
     */
    @ArchTest
    static final ArchRule no_fuin_reference =
            noClasses().that().resideInAPackage("io.ddd4j.core..")
                    .should().dependOnClassesThat().resideInAPackage("org.fuin..");

    /**
     * 核心模块零外部依赖（允许清单式，ADR-0002 白名单终态）：
     * 只允许 JDK（java..／jakarta..／javax..）、io.ddd4j 家族模块，以及三项工具库
     * Jackson（annotation／databind／core）、commons-lang3、TTL（com.alibaba.ttl）；
     * swagger-annotations-jakarta（io.swagger.v3..，编译期注解）为既有豁免。
     * lombok.. 仅为 {@code lombok.Generated} 编译期标记（Lombok 对生成成员自动附加，运行时无行为），非运行时依赖。
     * 新增依赖必须显式加白并经新 ADR 修订——slf4j 已按 ADR-0002 迁移义务①完成字节码清零，不设过渡期允许项。
     */
    @ArchTest
    static final ArchRule core_zero_external_dependencies =
            classes().that().resideInAPackage("io.ddd4j.core..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "io.ddd4j..",
                            "java..",
                            "jakarta..",
                            "javax..",
                            "com.fasterxml.jackson.annotation..",
                            "com.fasterxml.jackson.databind..",
                            "com.fasterxml.jackson.core..",
                            "org.apache.commons.lang3..",
                            "com.alibaba.ttl..",
                            "io.swagger.v3..",
                            "lombok.."
                    );

    /**
     * 核心模块不得直接依赖 Quarkus（8 运行时框架禁入 core，ADR-0003）。
     */
    @ArchTest
    static final ArchRule no_quarkus_in_core =
            noClasses().that().resideInAPackage("io.ddd4j.core..")
                    .should().dependOnClassesThat().resideInAPackage("io.quarkus..");

    /**
     * 核心模块不得直接依赖 Micronaut（8 运行时框架禁入 core，ADR-0003）。
     */
    @ArchTest
    static final ArchRule no_micronaut_in_core =
            noClasses().that().resideInAPackage("io.ddd4j.core..")
                    .should().dependOnClassesThat().resideInAPackage("io.micronaut..");
}
