package io.ddd4j.ddd.clean.test;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.ddd4j.ddd.clean.rules.CleanDDDLayerRules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Clean Architecture 架构测试抽象基类。
 *
 * <p>业务项目继承此类即可应用所有 Clean Architecture 包依赖规则：
 * <pre>
 * &#64;AnalyzeClasses(packages = "com.example.myapp")
 * class MyAppArchitectureTest extends CleanArchitectureTest {}
 * </pre>
 *
 * <p>运行 {@code mvn test} 时，{@code @ArchTest} 静态字段自动执行，违反规则 → JUnit 失败 → 构建失败。
 *
 * <h3>包含的规则</h3>
 * <ul>
 *   <li>包依赖规则（Clean 四层）：domain 不依赖 adapter/infrastructure/web/framework</li>
 *   <li>聚合/装配规则：adapter 必须实现 domain.repository 接口</li>
 * </ul>
 *
 * <p>注解驱动规则（{@link CleanDDDLayerRules} 的工厂方法）由子类自行声明，
 * 因为不同框架适配层（ddd4j-boot / ddd4j-quarkus / ddd4j-javalin）使用不同的注解类。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@AnalyzeClasses(
        packages = "..",
        importOptions = ImportOption.DoNotIncludeTests.class
)
public abstract class CleanArchitectureTest {

    // ============ 包依赖规则（Clean 四层核心约束）============

    @ArchTest
    public static final ArchRule domain_not_depend_on_web =
            CleanDDDLayerRules.DOMAIN_NOT_DEPEND_ON_WEB.allowEmptyShould(true);

    @ArchTest
    public static final ArchRule domain_not_depend_on_infrastructure =
            CleanDDDLayerRules.DOMAIN_NOT_DEPEND_ON_INFRASTRUCTURE.allowEmptyShould(true);

    @ArchTest
    public static final ArchRule domain_not_depend_on_framework =
            CleanDDDLayerRules.DOMAIN_NOT_DEPEND_ON_FRAMEWORK.allowEmptyShould(true);

    // ============ Clean 经典规则：application 不应依赖 adapter / infrastructure============

    @ArchTest
    public static final ArchRule application_not_depend_on_adapter = noClasses()
            .that().resideInAnyPackage("..app..", "..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..adapter..", "..infrastructure..", "..web..")
            .because("应用层只依赖领域层，不能反向依赖适配器/基础设施层（依赖倒置）")
            .allowEmptyShould(true);

    // ============ Clean 经典规则：adapter 实现 domain.repository 接口============

    @ArchTest
    public static final ArchRule adapter_persistence_should_implement_repository = classes()
            .that().resideInAPackage("..adapter.persistence..")
            .and().areNotInterfaces()
            .should().dependOnClassesThat().resideInAPackage("..domain.repository..")
            .because("Clean Architecture: 适配器层（持久化）的实现类必须依赖 domain 层定义的 Repository 接口（依赖倒置）")
            .allowEmptyShould(true);
}
