package io.ddd4j.data.cqrs.vertx.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ddd4j-data-cqrs-vertx 模块独立性自检：Vert.x 适配器的依赖面锁定（ADR-0004）。
 *
 * <p>本模块是 CQRS 命令侧 SPI 在 Vert.x 5 运行时的装配适配器，允许依赖
 * Vert.x 全家桶（io.vertx..，静态工厂的装配锚点）与 Jakarta API
 * （jakarta..，为集成方 jakarta 系刻板保留的声明面），但不得：
 * <ul>
 *   <li>引入 Spring（Spring 系运行时适配归 ddd4j-data-cqrs-spring）</li>
 *   <li>引入 Quarkus（Quarkus 运行时适配归 ddd4j-data-cqrs-quarkus）</li>
 * </ul>
 * 并以允许清单式规则 {@link #cqrs_vertx_deps_allowlist} 锁定总依赖面，
 * 新增依赖必须显式加白并经 ADR 修订。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@AnalyzeClasses(packages = "io.ddd4j.data.cqrs.vertx", importOptions = {ImportOption.DoNotIncludeTests.class})
class CqrsVertxModuleIndependenceTest {

    /**
     * Vert.x 适配器依赖允许清单：仅 JDK（java..）、io.ddd4j 家族模块
     * （命令契约与路由来自 ddd4j-core／ddd4j-data-cqrs）、Jakarta API
     * （jakarta..，声明性刻板保留面）与 Vert.x 全家桶（io.vertx..，
     * create 工厂的装配锚点）。
     */
    @ArchTest
    static final ArchRule cqrs_vertx_deps_allowlist =
            classes().that().resideInAPackage("io.ddd4j.data.cqrs.vertx..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "io.ddd4j..",
                            "java..",
                            "jakarta..",
                            "io.vertx..",
                            "lombok.."
                    );

    /**
     * Vert.x 适配器不得依赖 Spring（Spring 系运行时适配归 -spring 模块；
     * Vert.x 模块严禁混入 Spring 刻板/事务注解）。
     */
    @ArchTest
    static final ArchRule cqrs_vertx_no_spring =
            noClasses().that().resideInAPackage("io.ddd4j.data.cqrs.vertx..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    /**
     * Vert.x 适配器不得依赖 Quarkus（Quarkus 运行时适配归 -quarkus 模块，
     * 防止双框架混合污染单一运行时适配器）。
     */
    @ArchTest
    static final ArchRule cqrs_vertx_no_quarkus =
            noClasses().that().resideInAPackage("io.ddd4j.data.cqrs.vertx..")
                    .should().dependOnClassesThat().resideInAPackage("io.quarkus..");
}
