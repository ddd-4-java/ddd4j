package io.ddd4j.ddd.cola.rules;

import com.tngtech.archunit.lang.ArchRule;
import io.ddd4j.annotation.ddd.ApplicationService;
import io.ddd4j.annotation.ddd.DomainEntity;
import io.ddd4j.annotation.ddd.DomainRepository;
import io.ddd4j.annotation.ddd.DomainService;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * COLA 风格的 DDD 分层纪律 ArchUnit 规则集（注解驱动）。
 *
 * <p>COLA（Clean Object-oriented and Layered Architecture）是阿里推荐的 DDD 落地架构。
 * 本类提供与 ddd4j-ddd-clean 相同的注解驱动规则，但适配 COLA 的命名风格
 * （domain / adapter / application / infrastructure）。
 *
 * <h3>COLA vs Clean 命名差异</h3>
 * <ul>
 *   <li>Clean: domain / application / adapter / infrastructure</li>
 *   <li>COLA: domain / adapter / application / infrastructure（注意 adapter 和 application 顺序）</li>
 * </ul>
 *
 * <h3>规则清单</h3>
 * <ul>
 *   <li>{@link #DOMAIN_ENTITY_IN_DOMAIN} — @DomainEntity 必须在 domain.model 包</li>
 *   <li>{@link #DOMAIN_SERVICE_IN_DOMAIN} — @DomainService 必须在 domain 包</li>
 *   <li>{@link #APPLICATION_SERVICE_IN_APP} — @ApplicationService 必须在 application.executor 包</li>
 *   <li>{@link #REPOSITORY_IMPL_IN_ADAPTER} — @DomainRepository 必须在 adapter.persistence 包</li>
 *   <li>{@link #DOMAIN_NOT_DEPEND_ON_ADAPTER} — domain 不得依赖 adapter</li>
 *   <li>{@link #DOMAIN_NOT_DEPEND_ON_FRAMEWORK} — domain 不得依赖框架</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public final class ColaDDDLayerRules {

    private ColaDDDLayerRules() {
    }

    /**
     * 规则1：标了 {@code @DomainEntity} 的类必须在 {@code ..domain..} 包（COLA 中通常在 domain.model）。
     */
    public static final ArchRule DOMAIN_ENTITY_IN_DOMAIN = classes()
            .that().areAnnotatedWith(DomainEntity.class)
            .should().resideInAPackage("..domain..")
            .because("标了 @DomainEntity 的类是领域实体，必须在 domain 包（COLA 推荐 domain.model）");

    /**
     * 规则2：标了 {@code @DomainService} 的类必须在 {@code ..domain..} 包。
     */
    public static final ArchRule DOMAIN_SERVICE_IN_DOMAIN = classes()
            .that().areAnnotatedWith(DomainService.class)
            .should().resideInAPackage("..domain..")
            .because("标了 @DomainService 的类是领域服务，必须在 domain 包（COLA 推荐 domain.ability）");

    /**
     * 规则3：标了 {@code @ApplicationService} 的类必须在 application.executor / application.query 包。
     *
     * <p>COLA 特有：应用层按 CQS 分为 executor（命令）和 query（查询）。
     */
    public static final ArchRule APPLICATION_SERVICE_IN_APP = classes()
            .that().areAnnotatedWith(ApplicationService.class)
            .should().resideInAnyPackage(
                    "..application.executor..", "..application.query..", "..app..", "..application..")
            .because("标了 @ApplicationService 的类必须在 application.executor / application.query（COLA CQS 分离）");

    /**
     * 规则4：标了 {@code @DomainRepository} 的类必须在 {@code ..adapter.persistence..} 包。
     *
     * <p>COLA 中 Repository 实现放在 adapter 层（Clean 中放在 infrastructure 层）。
     */
    public static final ArchRule REPOSITORY_IMPL_IN_ADAPTER = classes()
            .that().areAnnotatedWith(DomainRepository.class)
            .should().resideInAPackage("..adapter.persistence..")
            .because("COLA: 仓储实现必须在 adapter.persistence（Clean 在 infrastructure）");

    /**
     * 规则5：domain 包不得依赖 adapter 包（COLA 核心约束）。
     */
    public static final ArchRule DOMAIN_NOT_DEPEND_ON_ADAPTER = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..")
            .because("COLA 核心约束：domain 不得依赖 adapter（依赖方向应反转）");

    /**
     * 规则6：domain 包不得依赖 application / infrastructure 包。
     */
    public static final ArchRule DOMAIN_NOT_DEPEND_ON_APPLICATION = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..application..", "..infrastructure..")
            .because("COLA: domain 不得依赖 application / infrastructure");

    /**
     * 规则7：domain 包不得依赖 Spring/MyBatis 框架。
     */
    public static final ArchRule DOMAIN_NOT_DEPEND_ON_FRAMEWORK = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "com.baomidou..", "org.apache.ibatis..")
            .because("领域层不得依赖 Spring/MyBatis/iBatis 等框架");
}
