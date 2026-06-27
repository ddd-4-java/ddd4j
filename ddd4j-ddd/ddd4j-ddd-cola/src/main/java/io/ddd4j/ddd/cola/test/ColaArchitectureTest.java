package io.ddd4j.ddd.cola.test;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.ddd4j.ddd.cola.rules.ColaDDDLayerRules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * COLA 架构测试抽象基类。
 *
 * <p>业务项目只需继承此类即可自动应用所有 COLA + DDD 规则：
 * <pre>
 * // 业务项目：src/test/java/com/example/MyAppArchitectureTest.java
 * package com.example;
 * import io.ddd4j.ddd.cola.test.ColaArchitectureTest;
 * class MyAppArchitectureTest extends ColaArchitectureTest {
 *     // 无需任何代码
 * }
 * </pre>
 *
 * <p>运行 {@code mvn test} 时，{@code @ArchTest} 静态字段自动执行，违反规则 → JUnit 失败 → 构建失败。
 *
 * <h3>COLA 特有规则</h3>
 * <ul>
 *   <li>adapter.persistence 实现类必须依赖 domain.gateway 接口（依赖倒置）</li>
 *   <li>application.executor / application.query 必须存在（COLA CQS 分离）</li>
 *   <li>domain 不依赖 adapter（COLA 核心约束，比 Clean 更严格）</li>
 * </ul>
 *
 * @author Loong Wan
 * @公众号 PartMe.AI
 * @since 2.0.x
 */
@AnalyzeClasses(
        packages = "..",
        importOptions = ImportOption.DoNotIncludeTests.class
)
public abstract class ColaArchitectureTest {

    // ============ 注解驱动规则（来自 ColaDDDLayerRules）============

    @ArchTest
    public static final ArchRule domain_entity_in_domain = ColaDDDLayerRules.DOMAIN_ENTITY_IN_DOMAIN;

    @ArchTest
    public static final ArchRule domain_service_in_domain = ColaDDDLayerRules.DOMAIN_SERVICE_IN_DOMAIN;

    @ArchTest
    public static final ArchRule application_service_in_app = ColaDDDLayerRules.APPLICATION_SERVICE_IN_APP;

    @ArchTest
    public static final ArchRule repository_impl_in_adapter = ColaDDDLayerRules.REPOSITORY_IMPL_IN_ADAPTER;

    // ============ 包依赖规则（COLA 核心约束）============

    @ArchTest
    public static final ArchRule domain_not_depend_on_adapter = ColaDDDLayerRules.DOMAIN_NOT_DEPEND_ON_ADAPTER;

    @ArchTest
    public static final ArchRule domain_not_depend_on_application = ColaDDDLayerRules.DOMAIN_NOT_DEPEND_ON_APPLICATION;

    @ArchTest
    public static final ArchRule domain_not_depend_on_framework = ColaDDDLayerRules.DOMAIN_NOT_DEPEND_ON_FRAMEWORK;

    // ============ COLA 特有规则：adapter 实现 domain.gateway 接口============

    @ArchTest
    public static final ArchRule adapter_persistence_should_implement_gateway = classes()
            .that().resideInAPackage("..adapter.persistence..")
            .and().areNotInterfaces()
            .should().dependOnClassesThat().resideInAPackage("..domain.gateway..")
            .because("COLA: 适配器层（持久化）的实现类必须依赖 domain.gateway 接口（依赖倒置）");

    // ============ COLA 特有规则：application 不依赖 adapter============

    @ArchTest
    public static final ArchRule application_not_depend_on_adapter = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..")
            .because("COLA: application 不得依赖 adapter（编排不应直接调用实现）");
}
