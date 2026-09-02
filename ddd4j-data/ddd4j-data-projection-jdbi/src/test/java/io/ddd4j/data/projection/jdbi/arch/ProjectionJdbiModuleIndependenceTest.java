package io.ddd4j.data.projection.jdbi.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ddd4j-data-projection-jdbi 模块独立性自检：JDBI 实现的依赖面锁定（ADR-0005）。
 *
 * <p>本模块是 core {@code ProjectionPositionRepository} SPI 在 Javalin／Vert.x 等
 * 轻量运行时的 SQL-first 落地，允许依赖 jdbi3-core，但不得引入<b>任何</b>运行时框架
 * ——本模块刻意保持零容器（Javalin／Vert.x 本体也不依赖，集成方自行装配）：
 * <ul>
 *   <li>不得引入 Spring（其实现归 ddd4j-data-projection-jpa）</li>
 *   <li>不得引入 Quarkus（其实现归 ddd4j-data-projection-panache）</li>
 * </ul>
 * 并以允许清单式规则 {@link #jdbi_impl_deps_allowlist} 锁定总依赖面，
 * 新增依赖必须显式加白并经 ADR 修订。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@AnalyzeClasses(packages = "io.ddd4j.data.projection.jdbi", importOptions = {ImportOption.DoNotIncludeTests.class})
class ProjectionJdbiModuleIndependenceTest {

    /**
     * JDBI 实现模块依赖允许清单：仅 JDK（java..）、io.ddd4j 家族模块、JDBI 全家桶
     * （jdbi3-core.. 在 org.jdbi.. 下）与 Lombok 编译期标记（jakarta.. 预留：主代码
     * 当前未用，若引入 Jakarta API 无需改规则）。
     */
    @ArchTest
    static final ArchRule jdbi_impl_deps_allowlist =
            classes().that().resideInAPackage("io.ddd4j.data.projection.jdbi..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "io.ddd4j..",
                            "java..",
                            "jakarta..",
                            "org.jdbi..",
                            "lombok.."
                    );

    /**
     * JDBI 实现模块不得依赖 Spring（Spring 系运行时实现归 -jpa 模块；
     * Javalin/Vert.x 栈严禁混入 Spring 刻板/事务设施）。
     */
    @ArchTest
    static final ArchRule no_spring_in_jdbi_module =
            noClasses().that().resideInAPackage("io.ddd4j.data.projection.jdbi..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    /**
     * JDBI 实现模块不得依赖 Quarkus（Quarkus 运行时实现归 -panache 模块；
     * 本模块零容器，防误引任何 CDI/Quarkus 设施）。
     */
    @ArchTest
    static final ArchRule no_quarkus_in_jdbi_module =
            noClasses().that().resideInAPackage("io.ddd4j.data.projection.jdbi..")
                    .should().dependOnClassesThat().resideInAPackage("io.quarkus..");
}
