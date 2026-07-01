package io.ddd4j.ddd.clean.rules;

import com.tngtech.archunit.lang.ArchRule;

import java.lang.annotation.Annotation;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Clean Architecture 风格的 DDD 分层纪律 ArchUnit 规则集（注解驱动）。
 *
 * <p>从 ddd4j-core 的 DDDLayerRules 迁出，按 Clean Architecture 命名（domain/application/adapter/infrastructure）。
 * 业务项目可继承 {@link io.ddd4j.ddd.clean.test.CleanArchitectureTest} 自动应用所有规则，
 * 或直接引用这些 {@link ArchRule} 常量自定义检查。
 *
 * <p>规则基于 ddd4j 的注解体系（{@code @DomainEntity} / {@code @DomainService} /
 * {@code @ApplicationService} / {@code @DomainRepository}），不绑定具体的包名，
 * 而是通过注解标记校验——这样无论业务项目用什么包结构，只要正确标注了注解就能被校验。
 *
 * <p><b>框架无关性说明</b>：本模块不依赖任何具体注解实现类。注解类作为 {@link Class} 参数
 * 由调用方传入，通常为业务项目使用的某个框架适配注解包中的具体注解：
 * <ul>
 *   <li>Spring Boot 业务：{@code io.ddd4j.spring.annotation.DomainService}</li>
 *   <li>Quarkus 业务：{@code io.ddd4j.quarkus.annotation.ddd.DomainService}</li>
 *   <li>Javalin 业务：{@code io.ddd4j.javalin.annotation.DomainService}</li>
 * </ul>
 *
 * <h3>规则清单</h3>
 * <ul>
 *   <li>{@link #DOMAIN_ENTITY_IN_DOMAIN} — @DomainEntity 标记的类必须在 domain 包</li>
 *   <li>{@link #DOMAIN_SERVICE_IN_DOMAIN} — @DomainService 标记的类必须在 domain 包</li>
 *   <li>{@link #APPLICATION_SERVICE_IN_APP} — @ApplicationService 标记的类必须在 app 包</li>
 *   <li>{@link #REPOSITORY_IMPL_IN_INFRASTRUCTURE} — @DomainRepository 标记的类必须在 infrastructure 包</li>
 *   <li>{@link #DOMAIN_NOT_DEPEND_ON_WEB} — domain 包不得依赖 web/controller/adapter 包</li>
 *   <li>{@link #DOMAIN_NOT_DEPEND_ON_INFRASTRUCTURE} — domain 包不得依赖 infrastructure 包</li>
 *   <li>{@link #DOMAIN_NOT_DEPEND_ON_FRAMEWORK} — domain 包不得依赖 Spring/MyBatis 等框架</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public final class CleanDDDLayerRules {

    /**
     * 规则5：domain 包不得依赖 web/controller/adapter 包。
     */
    public static final ArchRule DOMAIN_NOT_DEPEND_ON_WEB = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..controller..", "..adapter..", "..web..")
            .because("领域层不得依赖 Web/Controller/Adapter 层");
    /**
     * 规则6：domain 包不得依赖 infrastructure 包。
     */
    public static final ArchRule DOMAIN_NOT_DEPEND_ON_INFRASTRUCTURE = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..infrastructure..", "..infras..")
            .because("领域层不得依赖基础设施层（依赖方向应反转）");
    /**
     * 规则7：domain 包不得依赖 Spring 框架。
     */
    public static final ArchRule DOMAIN_NOT_DEPEND_ON_FRAMEWORK = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "com.baomidou..", "org.apache.ibatis..")
            .because("领域层不得依赖 Spring/MyBatis/iBatis 等框架");

    private CleanDDDLayerRules() {
    }

    /**
     * 规则1：标了 {@code @DomainEntity} 的类必须在 {@code ..domain..} 包。
     */
    public static ArchRule domainEntityInDomain(Class<? extends Annotation> domainEntityAnnotation) {
        return classes()
                .that().areAnnotatedWith(domainEntityAnnotation)
                .should().resideInAPackage("..domain..")
                .because("标了 @DomainEntity 的类是领域实体，必须在 domain 包");
    }

    /**
     * 规则2：标了 {@code @DomainService} 的类必须在 {@code ..domain..} 包。
     */
    public static ArchRule domainServiceInDomain(Class<? extends Annotation> domainServiceAnnotation) {
        return classes()
                .that().areAnnotatedWith(domainServiceAnnotation)
                .should().resideInAPackage("..domain..")
                .because("标了 @DomainService 的类是领域服务，必须在 domain 包");
    }

    /**
     * 规则3：标了 {@code @ApplicationService} 的类必须在 {@code ..app..} 或 {@code ..application..} 包。
     */
    public static ArchRule applicationServiceInApp(Class<? extends Annotation> applicationServiceAnnotation) {
        return classes()
                .that().areAnnotatedWith(applicationServiceAnnotation)
                .should().resideInAnyPackage("..app..", "..application..")
                .because("标了 @ApplicationService 的类是应用服务，必须在 app/application 包");
    }

    /**
     * 规则4：标了 {@code @DomainRepository} 的类必须在 {@code ..infrastructure..} 或 {@code ..infras..} 包。
     */
    public static ArchRule repositoryImplInInfrastructure(Class<? extends Annotation> domainRepositoryAnnotation) {
        return classes()
                .that().areAnnotatedWith(domainRepositoryAnnotation)
                .should().resideInAnyPackage("..infrastructure..", "..infras..")
                .because("标了 @DomainRepository 的类是仓储实现，必须在 infrastructure/infras 包");
    }

    /**
     * 规则8：标了 {@code @DomainGateway} 的接口必须在 {@code ..domain..} 包。
     */
    public static ArchRule domainGatewayInDomain(Class<? extends Annotation> domainGatewayAnnotation) {
        return classes()
                .that().areAnnotatedWith(domainGatewayAnnotation)
                .should().resideInAPackage("..domain..")
                .because("标了 @DomainGateway 的接口是领域网关，必须在 domain 包");
    }

    /**
     * 规则9：标了 {@code @CommandExecutor} 的类必须在 {@code ..app..} 或 {@code ..application..} 包。
     */
    public static ArchRule commandExecutorInApp(Class<? extends Annotation> commandExecutorAnnotation) {
        return classes()
                .that().areAnnotatedWith(commandExecutorAnnotation)
                .should().resideInAnyPackage("..app..", "..application..")
                .because("标了 @CommandExecutor 的类必须在 app/application 包");
    }

    /**
     * 规则10：标了 {@code @QueryService} 的类必须在 {@code ..app..} 或 {@code ..application..} 包。
     */
    public static ArchRule queryServiceInApp(Class<? extends Annotation> queryServiceAnnotation) {
        return classes()
                .that().areAnnotatedWith(queryServiceAnnotation)
                .should().resideInAnyPackage("..app..", "..application..")
                .because("标了 @QueryService 的类必须在 app/application 包");
    }
}
