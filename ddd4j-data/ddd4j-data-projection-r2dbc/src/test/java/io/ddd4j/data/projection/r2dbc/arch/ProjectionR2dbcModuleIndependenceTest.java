package io.ddd4j.data.projection.r2dbc.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ddd4j-data-projection-r2dbc 模块独立性自检：R2DBC 实现的依赖面锁定（ADR-0005）。
 *
 * <p>本模块是 core {@code ProjectionPositionRepository} SPI 在响应式运行时的纯
 * {@code io.r2dbc.spi} 落地（Task 7.5 预授权偏离：计划 sketch 的 ConnectionPool
 * 构造参数收敛为 ConnectionFactory SPI 接口，池化实例 is-a ConnectionFactory 直接
 * 可传），同时服务 WebFlux 与 Vert.x（ADR-0003），允许依赖 r2dbc-spi 与 Reactor，
 * 但不得引入<b>任何</b>运行时框架：
 * <ul>
 *   <li>不得引入 Spring——纯 io.r2dbc.spi 落地可对模块立 no_spring 硬规则，
 *       响应式方法（Mono／Flux）供 WebFlux/Vert.x 直组，同步桥接满足 core SPI</li>
 *   <li>不得引入 Quarkus（其实现归 ddd4j-data-projection-panache）</li>
 * </ul>
 * 并以允许清单式规则 {@link #r2dbc_impl_deps_allowlist} 锁定总依赖面，
 * 新增依赖必须显式加白并经 ADR 修订。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@AnalyzeClasses(packages = "io.ddd4j.data.projection.r2dbc", importOptions = {ImportOption.DoNotIncludeTests.class})
class ProjectionR2dbcModuleIndependenceTest {

    /**
     * R2DBC 实现模块依赖允许清单：仅 JDK（java..）、io.ddd4j 家族模块、Reactor
     * （根包 {@code reactor..}——Maven 坐标 io.projectreactor:reactor-core 的
     * Java 包名，Mono/Flux 组装）、R2DBC SPI（io.r2dbc..）与 Lombok 编译期标记
     * （jakarta.. 预留：主代码当前未用，若引入 Jakarta API 无需改规则）。
     */
    @ArchTest
    static final ArchRule r2dbc_impl_deps_allowlist =
            classes().that().resideInAPackage("io.ddd4j.data.projection.r2dbc..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "io.ddd4j..",
                            "java..",
                            "jakarta..",
                            "reactor..",
                            "io.r2dbc..",
                            "lombok.."
                    );

    /**
     * R2DBC 实现模块不得依赖 Spring（纯 io.r2dbc.spi 同时服务 WebFlux 与 Vert.x，
     * 不依赖 Spring 事务管理器／DataAccessException 体系也能保有原子 upsert）。
     */
    @ArchTest
    static final ArchRule no_spring_in_r2dbc_module =
            noClasses().that().resideInAPackage("io.ddd4j.data.projection.r2dbc..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    /**
     * R2DBC 实现模块不得依赖 Quarkus（Quarkus 运行时实现归 -panache 模块；
     * 本模块零容器，防误引任何 CDI/Quarkus 设施）。
     */
    @ArchTest
    static final ArchRule no_quarkus_in_r2dbc_module =
            noClasses().that().resideInAPackage("io.ddd4j.data.projection.r2dbc..")
                    .should().dependOnClassesThat().resideInAPackage("io.quarkus..");
}
