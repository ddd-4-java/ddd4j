package io.ddd4j.auth.spring.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ddd4j-auth 框架独立性 ArchUnit 守护规则（6.3 验收标准）。
 *
 * <p>运行 {@code mvn test} 时，{@code @ArchTest} 自动执行，违反规则 → JUnit 失败 → 构建失败。
 * 防止框架耦合（Spring/Sa-Token/Shiro/Servlet）泄漏到不该依赖的模块。
 *
 * <h3>规则清单</h3>
 * <ul>
 *   <li>{@link #AUTH_CORE_NO_FRAMEWORK} - auth-core 不得依赖任何鉴权框架</li>
 *   <li>{@link #AUTH_SATOKEN_NO_SPRING} - auth-satoken 不得依赖 Spring</li>
 *   <li>{@link #AUTH_SHIRO_NO_SPRING} - auth-shiro 不得依赖 Spring</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.4.x
 */
@AnalyzeClasses(
        packages = {"io.ddd4j.auth", "io.ddd4j.core"},
        importOptions = ImportOption.DoNotIncludeTests.class
)
public class AuthIndependenceTest {

    /**
     * auth-core 不得依赖任何框架（Spring / Sa-Token / Shiro / Servlet）。
     */
    @ArchTest
    static final ArchRule AUTH_CORE_NO_FRAMEWORK =
            noClasses().that().resideInAPackage("io.ddd4j.auth.core..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "cn.dev33..",
                            "org.apache.shiro..",
                            "jakarta.servlet..");

    /**
     * auth-satoken 不得依赖 Spring（保持纯 Java，可被 Quarkus/Javalin 复用）。
     */
    @ArchTest
    static final ArchRule AUTH_SATOKEN_NO_SPRING =
            noClasses().that().resideInAPackage("io.ddd4j.auth.satoken..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    /**
     * auth-shiro 不得依赖 Spring（保持纯 Java，可被 Quarkus/Javalin 复用）。
     */
    @ArchTest
    static final ArchRule AUTH_SHIRO_NO_SPRING =
            noClasses().that().resideInAPackage("io.ddd4j.auth.shiro..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

}
