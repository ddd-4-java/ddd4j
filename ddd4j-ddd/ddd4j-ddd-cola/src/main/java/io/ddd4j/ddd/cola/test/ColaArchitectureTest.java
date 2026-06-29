package io.ddd4j.ddd.cola.test;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.ddd4j.ddd.cola.rules.ColaDDDLayerRules;

import java.lang.annotation.Annotation;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * COLA 架构测试抽象基类。
 *
 * <p>业务项目继承此类并覆写注解类覆写方法即可应用所有 COLA + DDD 规则：
 * <pre>
 * &#64;AnalyzeClasses(packages = "com.example.myapp")
 * class MyAppArchitectureTest extends ColaArchitectureTest {
 *     protected Class&lt;? extends Annotation&gt; domainEntityAnnotation() {
 *         return io.ddd4j.boot.annotation.ddd.DomainEntity.class;
 *     }
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
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@AnalyzeClasses(
        packages = "..",
        importOptions = ImportOption.DoNotIncludeTests.class
)
public abstract class ColaArchitectureTest {

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

    // ============ 注解驱动规则（来自 ColaDDDLayerRules）============

    @ArchTest
    public static final ArchRule domain_entity_in_domain = ColaDDDLayerRules.domainEntityInDomain(null);

    @ArchTest
    public static final ArchRule domain_service_in_domain = ColaDDDLayerRules.domainServiceInDomain(null);

    @ArchTest
    public static final ArchRule application_service_in_app = ColaDDDLayerRules.applicationServiceInApp(null);

    @ArchTest
    public static final ArchRule repository_impl_in_adapter = ColaDDDLayerRules.repositoryImplInAdapter(null);

    @ArchTest
    public static final ArchRule domain_gateway_in_domain = ColaDDDLayerRules.domainGatewayInDomain(null);

    @ArchTest
    public static final ArchRule command_executor_in_app = ColaDDDLayerRules.commandExecutorInApp(null);

    @ArchTest
    public static final ArchRule query_service_in_app = ColaDDDLayerRules.queryServiceInApp(null);

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