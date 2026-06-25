package io.ddd4j.boot.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.junit.AnalyzeClasses;

import io.ddd4j.boot.annotation.ApplicationService;
import io.ddd4j.boot.annotation.DomainEntity;
import io.ddd4j.boot.annotation.DomainRepository;
import io.ddd4j.boot.annotation.DomainService;

/**
 * DDD 分层纪律 ArchUnit 规则集。
 *
 * <p>定义业务项目应遵守的分层规则，通过注解标签驱动校验。
 * 业务项目可继承此类或直接引用这些 {@link ArchRule} 常量。
 *
 * <p>规则基于 ddd4j 的注解体系（{@code @DomainEntity} / {@code @DomainService} /
 * {@code @ApplicationService} / {@code @DomainRepository}），不绑定具体的包名，
 * 而是通过注解标记校验——这样无论业务项目用什么包结构，只要正确标注了注解就能被校验。
 *
 * <h3>规则清单</h3>
 * <ul>
 *   <li>{@link #DOMAIN_ENTITY_IN_DOMAIN} — @DomainEntity 标记的类必须在 domain 包</li>
 *   <li>{@link #DOMAIN_SERVICE_IN_DOMAIN} — @DomainService 标记的类必须在 domain 包</li>
 *   <li>{@link #APPLICATION_SERVICE_IN_APP} — @ApplicationService 标记的类必须在 app 包</li>
 *   <li>{@link #REPOSITORY_IMPL_IN_INFRASTRUCTURE} — @DomainRepository 标记的类必须在 infrastructure 包</li>
 *   <li>{@link #DOMAIN_NOT_DEPEND_ON_WEB} — domain 包不得依赖 web/controller 包</li>
 *   <li>{@link #DOMAIN_NOT_DEPEND_ON_INFRASTRUCTURE} — domain 包不得依赖 infrastructure 包</li>
 * </ul>
 *
 * @author wandl
 * @since 3.4.x
 */
public final class DDDLayerRules {

    private DDDLayerRules() {
    }

    /**
     * 规则1：标了 {@code @DomainEntity} 的类必须在 {@code ..domain..} 包。
     *
     * <p>领域实体是领域层的核心，不应散落在 controller/service/infrastructure 包。
     */
    public static final ArchRule DOMAIN_ENTITY_IN_DOMAIN = classes()
            .that().areAnnotatedWith(DomainEntity.class)
            .should().resideInAPackage("..domain..")
            .because("标了 @DomainEntity 的类是领域实体，必须在 domain 包");

    /**
     * 规则2：标了 {@code @DomainService} 的类必须在 {@code ..domain..} 包。
     *
     * <p>领域服务封装跨聚合的业务逻辑，属于领域层。
     */
    public static final ArchRule DOMAIN_SERVICE_IN_DOMAIN = classes()
            .that().areAnnotatedWith(DomainService.class)
            .should().resideInAPackage("..domain..")
            .because("标了 @DomainService 的类是领域服务，必须在 domain 包");

    /**
     * 规则3：标了 {@code @ApplicationService} 的类必须在 {@code ..app..} 或 {@code ..application..} 包。
     *
     * <p>应用服务负责用例编排（事务边界、调用领域服务），属于应用层，不是领域层也不是接口层。
     */
    public static final ArchRule APPLICATION_SERVICE_IN_APP = classes()
            .that().areAnnotatedWith(ApplicationService.class)
            .should().resideInAnyPackage("..app..", "..application..")
            .because("标了 @ApplicationService 的类是应用服务，必须在 app/application 包");

    /**
     * 规则4：标了 {@code @DomainRepository} 的类必须在 {@code ..infrastructure..} 或 {@code ..infras..} 包。
     *
     * <p>仓储接口在 domain 层定义，但实现（标了 @DomainRepository/@Repository）在 infrastructure 层。
     * 注意：domain 层的 Repository <b>接口</b>不应标 @DomainRepository（它只有 @Repository 组合），
     * 标 @DomainRepository 的是实现类。
     */
    public static final ArchRule REPOSITORY_IMPL_IN_INFRASTRUCTURE = classes()
            .that().areAnnotatedWith(DomainRepository.class)
            .should().resideInAnyPackage("..infrastructure..", "..infras..")
            .because("标了 @DomainRepository 的类是仓储实现，必须在 infrastructure/infras 包");

    /**
     * 规则5：domain 包不得依赖 web/controller/adapter 包。
     *
     * <p>领域层是架构核心，不应知道 HTTP/REST 的存在。
     */
    public static final ArchRule DOMAIN_NOT_DEPEND_ON_WEB = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..controller..", "..adapter..", "..web..")
            .because("领域层不得依赖 Web/Controller/Adapter 层");

    /**
     * 规则6：domain 包不得依赖 infrastructure 包。
     *
     * <p>依赖方向必须是 infrastructure → domain（依赖倒置），不能反向。
     */
    public static final ArchRule DOMAIN_NOT_DEPEND_ON_INFRASTRUCTURE = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..infrastructure..", "..infras..")
            .because("领域层不得依赖基础设施层（依赖方向应反转）");

}
