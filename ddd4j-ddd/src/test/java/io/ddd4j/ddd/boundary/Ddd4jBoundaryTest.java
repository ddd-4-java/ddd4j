package io.ddd4j.ddd.boundary;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ddd4j 通用基础层边界守护。
 *
 * <p>本测试确保 ddd4j monorepo 中的所有子模块（{@code ddd4j-core}、
 * {@code ddd4j-spring}、{@code ddd4j-quarkus}、{@code ddd4j-javalin}、
 * {@code ddd4j-data}、{@code ddd4j-auth} 等）不会被自动装配污染。
 *
 * <p>具体规则：
 * <ul>
 *   <li>禁止任何 ddd4j 子模块使用 {@code @AutoConfiguration} 注解</li>
 *   <li>禁止 ddd4j-core / ddd4j-annotation / ddd4j-kit 依赖 Spring/MyBatis/Servlet</li>
 *   <li>禁止 ddd4j-mq-core 依赖 spring-messaging</li>
 * </ul>
 *
 * <p>违反此规则意味着该代码应下移到 {@code ddd4j-boot-*}/{@code ddd4j-quarkus-*}/
 * {@code ddd4j-javalin-*} 等具体框架项目。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@AnalyzeClasses(
        packages = {
                "io.ddd4j.core",
                "io.ddd4j.kit",
                "io.ddd4j.ddd",
                "io.ddd4j.data",
                "io.ddd4j.mq",
                "io.ddd4j.web",
                "io.ddd4j.auth",
                "io.ddd4j.monitor",
                "io.ddd4j.spring",
                "io.ddd4j.quarkus",
                "io.ddd4j.extensions",
                "io.ddd4j.annotation"
        },
        importOptions = {ImportOption.DoNotIncludeTests.class}
)
class Ddd4jBoundaryTest {

    /**
     * ddd4j 全模块不得包含 {@code @AutoConfiguration} 注解。
     *
     * <p>Spring Boot 自动装配入口必须放在 {@code ddd4j-boot-*} 系列 starter 中，
     * 而非通用基础层。
     */
    @ArchTest
    static final ArchRule no_autoconfiguration_in_ddd4j =
            noClasses()
                    .should().beAnnotatedWith("org.springframework.boot.autoconfigure.AutoConfiguration");

    /**
     * ddd4j-core / ddd4j-kit / ddd4j-annotation 不得依赖 Spring Framework。
     */
    @ArchTest
    static final ArchRule no_spring_in_core_modules =
            noClasses()
                    .that().resideInAnyPackage(
                            "io.ddd4j.core..",
                            "io.ddd4j.kit..",
                            "io.ddd4j.annotation..",
                            "io.ddd4j.monitor.."
                    )
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    /**
     * ddd4j-mq-core 不得依赖 spring-messaging（应下移到 ddd4j-mq-spring）。
     */
    @ArchTest
    static final ArchRule no_spring_messaging_in_mq_core =
            noClasses()
                    .that().resideInAPackage("io.ddd4j.mq.core..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework.messaging..");

    /**
     * ddd4j-core 不得包含 META-INF/spring.factories（Spring Boot 1.x 自动装配清单）。
     */
    @ArchTest
    static final ArchRule no_spring_factories_in_core =
            noClasses()
                    .that().resideInAPackage("io.ddd4j.core..")
                    .should().dependOnClassesThat().haveFullyQualifiedName(
                            "org.springframework.boot.autoconfigure.AutoConfiguration.imports",
                            "org.springframework.boot.autoconfigure.EnableAutoConfiguration");

    /**
     * ddd4j 全模块不得依赖 hutool-all（全量版本，应按需引用子模块）。
     */
    @ArchTest
    static final ArchRule no_hutool_all_in_core =
            noClasses()
                    .that().resideInAPackage("io.ddd4j.core..")
                    .should().dependOnClassesThat().haveFullyQualifiedName("cn.hutool.Hutool");
}
