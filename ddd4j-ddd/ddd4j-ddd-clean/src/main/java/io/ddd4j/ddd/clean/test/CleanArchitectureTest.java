package io.ddd4j.ddd.clean.test;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.ddd4j.ddd.clean.rules.CleanDDDLayerRules;

import java.lang.annotation.Annotation;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Clean Architecture 架构测试抽象基类。
 *
 * <p>业务项目继承此类，并覆写 {@code domainEntityAnnotation()} 等方法传入项目使用的 DDD 注解类即可：
 * <pre>
 * &#64;AnalyzeClasses(packages = "com.example.myapp")
 * class MyAppArchitectureTest extends CleanArchitectureTest {
 *     // Spring Boot 项目
 *     protected Class&lt;? extends Annotation&gt; domainEntityAnnotation() {
 *         return io.ddd4j.boot.annotation.ddd.DomainEntity.class;
 *     }
 * }
 * </pre>
 *
 * <p>运行 {@code mvn test} 时，{@code @ArchTest} 静态字段自动执行，违反规则 → JUnit 失败 → 构建失败。
 *
 * <h3>包含的规则</h3>
 * <ul>
 *   <li>注解驱动规则（{@link CleanDDDLayerRules}）：{@code @DomainEntity} / {@code @DomainService} / {@code @ApplicationService} / {@code @DomainRepository} 必须位于正确包</li>
 *   <li>包依赖规则（Clean 四层）：domain 不依赖 adapter/infrastructure/web/framework</li>
 *   <li>聚合/装配规则：adapter 必须实现 domain.repository 接口</li>
 * </ul>
 *
 * <h3>自定义 packages</h3>
 * <p>默认扫描所有类。如需限定业务项目根包，业务项目可覆盖：
 * <pre>
 * &#64;AnalyzeClasses(packages = "com.example.myapp")
 * class MyAppArchitectureTest extends CleanArchitectureTest {}
 * </pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@AnalyzeClasses(
        packages = "..",
        importOptions = ImportOption.DoNotIncludeTests.class
)
public abstract class CleanArchitectureTest {

    // ============ 子类覆写：传入业务项目使用的 DDD 注解 ============

    protected Class<? extends Annotation> domainEntityAnnotation() {
        return null;
    }

    protected Class<? extends Annotation> domainServiceAnnotation() {
        return null;
    }

    protected Class<? extends Annotation> applicationServiceAnnotation() {
        return null;
    }

    protected Class<? extends Annotation> domainRepositoryAnnotation() {
        return null;
    }

    protected Class<? extends Annotation> domainGatewayAnnotation() {
        return null;
    }

    protected Class<? extends Annotation> commandExecutorAnnotation() {
        return null;
    }

    protected Class<? extends Annotation> queryServiceAnnotation() {
        return null;
    }

    // ============ 注解驱动规则（来自 CleanDDDLayerRules）============

    @ArchTest
    public static final ArchRule domain_entity_in_domain = CleanDDDLayerRules.domainEntityInDomain(null);

    @ArchTest
    public static final ArchRule domain_service_in_domain = CleanDDDLayerRules.domainServiceInDomain(null);

    @ArchTest
    public static final ArchRule application_service_in_app = CleanDDDLayerRules.applicationServiceInApp(null);

    @ArchTest
    public static final ArchRule repository_impl_in_infrastructure = CleanDDDLayerRules.repositoryImplInInfrastructure(null);

    @ArchTest
    public static final ArchRule domain_gateway_in_domain = CleanDDDLayerRules.domainGatewayInDomain(null);

    @ArchTest
    public static final ArchRule command_executor_in_app = CleanDDDLayerRules.commandExecutorInApp(null);

    @ArchTest
    public static final ArchRule query_service_in_app = CleanDDDLayerRules.queryServiceInApp(null);

    // ============ 包依赖规则（Clean 四层核心约束）============

    @ArchTest
    public static final ArchRule domain_not_depend_on_web = CleanDDDLayerRules.DOMAIN_NOT_DEPEND_ON_WEB;

    @ArchTest
    public static final ArchRule domain_not_depend_on_infrastructure = CleanDDDLayerRules.DOMAIN_NOT_DEPEND_ON_INFRASTRUCTURE;

    @ArchTest
    public static final ArchRule domain_not_depend_on_framework = CleanDDDLayerRules.DOMAIN_NOT_DEPEND_ON_FRAMEWORK;

    // ============ Clean 经典规则：application 不应依赖 adapter / infrastructure============

    @ArchTest
    public static final ArchRule application_not_depend_on_adapter = noClasses()
            .that().resideInAnyPackage("..app..", "..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..adapter..", "..infrastructure..", "..web..")
            .because("应用层只依赖领域层，不能反向依赖适配器/基础设施层（依赖倒置）");

    // ============ Clean 经典规则：adapter 实现 domain.repository 接口============

    @ArchTest
    public static final ArchRule adapter_persistence_should_implement_repository = classes()
            .that().resideInAPackage("..adapter.persistence..")
            .and().areNotInterfaces()
            .should().dependOnClassesThat().resideInAPackage("..domain.repository..")
            .because("Clean Architecture: 适配器层（持久化）的实现类必须依赖 domain 层定义的 Repository 接口（依赖倒置）");
}