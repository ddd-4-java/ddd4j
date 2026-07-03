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
 * <p>业务项目继承此类即可应用所有 COLA 包依赖规则：
 * <pre>
 * &#64;AnalyzeClasses(packages = "com.example.myapp")
 * class MyAppArchitectureTest extends ColaArchitectureTest {}
 * </pre>
 *
 * <p>注解驱动规则（{@link ColaDDDLayerRules} 的工厂方法）由子类自行声明，
 * 因为不同框架适配层（ddd4j-boot / ddd4j-quarkus / ddd4j-javalin）使用不同的注解类。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@AnalyzeClasses(
        packages = "..",
        importOptions = ImportOption.DoNotIncludeTests.class
)
public abstract class ColaArchitectureTest {

    // ============ 包依赖规则（COLA 核心约束）============

    /**
     * 领域层不得依赖适配器层（COLA 核心约束）
     */
    @ArchTest
    public static final ArchRule domain_not_depend_on_adapter =
            ColaDDDLayerRules.DOMAIN_NOT_DEPEND_ON_ADAPTER.allowEmptyShould(true);

    /**
     * 领域层不得依赖应用层/基础设施层
     */
    @ArchTest
    public static final ArchRule domain_not_depend_on_application =
            ColaDDDLayerRules.DOMAIN_NOT_DEPEND_ON_APPLICATION.allowEmptyShould(true);

    /**
     * 领域层不得依赖 Spring/MyBatis 等框架
     */
    @ArchTest
    public static final ArchRule domain_not_depend_on_framework =
            ColaDDDLayerRules.DOMAIN_NOT_DEPEND_ON_FRAMEWORK.allowEmptyShould(true);

    // ============ COLA 特有规则：adapter 实现 domain.gateway 接口============

    /**
     * 适配器层（持久化）的实现类必须依赖 domain.gateway 接口（依赖倒置）
     */
    @ArchTest
    public static final ArchRule adapter_persistence_should_implement_gateway = classes()
            .that().resideInAPackage("..adapter.persistence..")
            .and().areNotInterfaces()
            .should().dependOnClassesThat().resideInAPackage("..domain.gateway..")
            .because("COLA: 适配器层（持久化）的实现类必须依赖 domain.gateway 接口（依赖倒置）")
            .allowEmptyShould(true);

    // ============ COLA 特有规则：application 不依赖 adapter============

    /**
     * 应用层不得依赖适配器层（编排不应直接调用实现）
     */
    @ArchTest
    public static final ArchRule application_not_depend_on_adapter = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..")
            .because("COLA: application 不得依赖 adapter（编排不应直接调用实现）")
            .allowEmptyShould(true);
}
