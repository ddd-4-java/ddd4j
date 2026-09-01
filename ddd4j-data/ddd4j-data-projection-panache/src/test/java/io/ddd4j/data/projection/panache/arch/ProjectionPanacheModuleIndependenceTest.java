package io.ddd4j.data.projection.panache.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ddd4j-data-projection-panache 模块独立性自检：Quarkus Panache 实现的依赖面锁定。
 *
 * <p>本模块是 ProjectionPositionRepository SPI（core）在 Quarkus 运行时的落地，
 * 允许依赖 Quarkus／Panache 与 Jakarta API（CDI／持久化／事务），但不得：
 * <ul>
 *   <li>引入 Spring（其实现归 ddd4j-data-projection-jpa——注意两边 {@code @Transactional}
 *       语义同名不同包，Quarkus 侧必须是 {@code jakarta.transaction}）</li>
 *   <li>引入 Vert.x（响应式实现归后续 r2dbc 模块）</li>
 * </ul>
 * 并以允许清单式规则 {@link #projection_panache_deps_allowlist} 锁定总依赖面，
 * 新增依赖必须显式加白并经 ADR 修订。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@AnalyzeClasses(packages = "io.ddd4j.data.projection.panache", importOptions = {ImportOption.DoNotIncludeTests.class})
class ProjectionPanacheModuleIndependenceTest {

    /**
     * Panache 实现模块依赖允许清单：仅 JDK（java..）、Jakarta API（jakarta..，
     * 含 CDI／persistence／transaction）、io.ddd4j 家族模块、Quarkus 全家桶
     * （含 Panache 基类，.hibernate.orm.panache 亦在 io.quarkus.. 下）、
     * Hibernate（若实体注解用到）与 Lombok 编译期标记。
     */
    @ArchTest
    static final ArchRule projection_panache_deps_allowlist =
            classes().that().resideInAPackage("io.ddd4j.data.projection.panache..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "io.ddd4j..",
                            "java..",
                            "jakarta..",
                            "io.quarkus..",
                            "org.hibernate..",
                            "lombok.."
                    );

    /**
     * Panache 实现模块不得依赖 Spring（Spring 系运行时实现归 -jpa 模块；
     * Quarkus 模块严禁混入 Spring 刻板/事务注解）。
     */
    @ArchTest
    static final ArchRule no_spring_in_panache_module =
            noClasses().that().resideInAPackage("io.ddd4j.data.projection.panache..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    /**
     * Panache 实现模块不得依赖 Vert.x（响应式持久化实现归 -r2dbc 模块）。
     */
    @ArchTest
    static final ArchRule no_vertx_in_panache_module =
            noClasses().that().resideInAPackage("io.ddd4j.data.projection.panache..")
                    .should().dependOnClassesThat().resideInAPackage("io.vertx..");
}
