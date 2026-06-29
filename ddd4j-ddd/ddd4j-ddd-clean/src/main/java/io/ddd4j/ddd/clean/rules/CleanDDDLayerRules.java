package io.ddd4j.ddd.clean.rules;

import com.tngtech.archunit.lang.ArchRule;
import io.ddd4j.annotation.ddd.*;

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
    /**
     * 规则7：domain 包不得依赖 Spring 框架。
     *
     * <p>领域层应保持框架无关（Pure Java / POJO），业务逻辑不耦合到任何 Web/IoC 框架。
     */
    public static final ArchRule DOMAIN_NOT_DEPEND_ON_FRAMEWORK = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "com.baomidou..", "org.apache.ibatis..")
            .because("领域层不得依赖 Spring/MyBatis/iBatis 等框架");
    /**
     * 规则8：标了 {@code @DomainGateway} 的接口必须在 {@code ..domain..} 包。
     *
     * <p>领域网关接口（仓储接口 / ACL 接口）定义在领域层，
     * 与 {@code @DomainRepository}（实现在 infrastructure 层）形成依赖倒置。
     */
    public static final ArchRule DOMAIN_GATEWAY_IN_DOMAIN = classes()
            .that().areAnnotatedWith(DomainGateway.class)
            .should().resideInAPackage("..domain..")
            .because("标了 @DomainGateway 的接口是领域网关，必须在 domain 包");
    /**
     * 规则9：标了 {@code @CommandExecutor} 的类必须在 {@code ..app..} 或 {@code ..application..} 包。
     */
    public static final ArchRule COMMAND_EXECUTOR_IN_APP = classes()
            .that().areAnnotatedWith(CommandExecutor.class)
            .should().resideInAnyPackage("..app..", "..application..")
            .because("标了 @CommandExecutor 的类必须在 app/application 包");
    /**
     * 规则10：标了 {@code @QueryService} 的类必须在 {@code ..app..} 或 {@code ..application..} 包。
     */
    public static final ArchRule QUERY_SERVICE_IN_APP = classes()
            .that().areAnnotatedWith(QueryService.class)
            .should().resideInAnyPackage("..app..", "..application..")
            .because("标了 @QueryService 的类必须在 app/application 包");

    private CleanDDDLayerRules() {
    }
}
