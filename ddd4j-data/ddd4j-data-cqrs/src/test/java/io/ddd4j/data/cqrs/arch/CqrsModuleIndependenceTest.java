package io.ddd4j.data.cqrs.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ddd4j-data-cqrs 模块独立性自检：确保框架无关的 CQRS 命令侧 SPI
 * （{@code @CommandHandler} 发现注解 + {@code CommandRegistry} 注册中心）
 * 不被任何运行时框架污染（ADR-0004：命令契约与路由复用 ddd4j-core）。
 *
 * <p>本测试在 CI 阶段自动执行，防止以下耦合再次引入：
 * <ul>
 *   <li>Spring Framework（适配器归阶段 6 Task 6.3+ 的 ddd4j-data-cqrs-spring）</li>
 *   <li>Quarkus（同上，ddd4j-data-cqrs-quarkus 等下游适配模块）</li>
 * </ul>
 *
 * <p>并以允许清单式规则 {@link #cqrs_deps_allowlist} 锁定总依赖面：
 * 仅 JDK 与 io.ddd4j 家族模块（当前未用 jakarta／lombok，故不入清单）。
 * 新增依赖必须显式加白并经 ADR 修订。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@AnalyzeClasses(packages = "io.ddd4j.data.cqrs", importOptions = {ImportOption.DoNotIncludeTests.class})
class CqrsModuleIndependenceTest {

    /**
     * SPI 模块不得直接依赖 Spring Framework。
     */
    @ArchTest
    static final ArchRule no_spring_in_cqrs_module =
            noClasses().that().resideInAPackage("io.ddd4j.data.cqrs..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    /**
     * SPI 模块不得直接依赖 Quarkus。
     */
    @ArchTest
    static final ArchRule no_quarkus_in_cqrs_module =
            noClasses().that().resideInAPackage("io.ddd4j.data.cqrs..")
                    .should().dependOnClassesThat().resideInAPackage("io.quarkus..");

    /**
     * SPI 模块依赖允许清单：仅 JDK（java..）与 io.ddd4j 家族模块
     * （Command／CommandExecutor 契约来自 ddd4j-core）。
     * 新增依赖必须显式加白并经 ADR 修订。
     */
    @ArchTest
    static final ArchRule cqrs_deps_allowlist =
            classes().that().resideInAPackage("io.ddd4j.data.cqrs..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "io.ddd4j..",
                            "java..",
                            "lombok.."
                    );
}
