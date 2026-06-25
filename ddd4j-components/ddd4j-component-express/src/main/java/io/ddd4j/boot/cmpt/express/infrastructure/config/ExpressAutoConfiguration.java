package io.ddd4j.boot.cmpt.express.infrastructure.config;

import com.alibaba.qlexpress4.Express4Runner;
import io.ddd4j.boot.cmpt.express.application.service.RuleCacheService;
import io.ddd4j.boot.cmpt.express.application.service.RuleEngineApplicationService;
import io.ddd4j.boot.cmpt.express.application.service.RuleManagementService;
import io.ddd4j.boot.cmpt.express.domain.repository.RuleDefinitionRepository;
import io.ddd4j.boot.cmpt.express.domain.service.RuleEngineDomainService;
import io.ddd4j.boot.cmpt.express.infrastructure.cache.JetCacheRuleCacheService;
import io.ddd4j.boot.cmpt.express.infrastructure.service.RuleEngineDomainServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Express组件自动配置
 * 
 * <p>基础设施层：自动配置规则引擎相关的所有服务Bean。
 * 确保所有服务类被Spring管理，提供开箱即用的功能。
 * 
 * <p>自动配置的服务：
 * <ul>
 *   <li>RuleEngineDomainService - 规则引擎领域服务</li>
 *   <li>RuleCacheService - 规则缓存服务（优先使用JetCache多级缓存）</li>
 *   <li>RuleEngineApplicationService - 规则引擎应用服务</li>
 *   <li>RuleManagementService - 规则管理服务</li>
 * </ul>
 * 
 * @author ddd4j
 * @version 1.0
 * @since 1.0
 */
@Configuration
@ConditionalOnClass(name = "com.alibaba.qlexpress4.Express4Runner")
public class ExpressAutoConfiguration {

    /**
     * 规则引擎领域服务实现
     * 
     * @param expressRunner QLExpress运行器
     * @return 规则引擎领域服务实例
     */
    @Bean
    @ConditionalOnMissingBean
    public RuleEngineDomainService ruleEngineDomainService(Express4Runner expressRunner) {
        return new RuleEngineDomainServiceImpl(expressRunner);
    }

    /**
     * 规则缓存服务实现（JetCache多级缓存）
     * 
     * <p>使用JetCache实现多级缓存，自动管理本地缓存（Caffeine）和远程缓存（Redis）。
     * JetCache会自动处理缓存穿透、缓存回填、缓存同步等逻辑。
     * 
     * <p>查询顺序：本地缓存 -> Redis缓存 -> 数据库（由调用方处理）
     * 
     * <p>配置要求：
     * <ul>
     *   <li>需要在配置文件中配置JetCache（jetcache配置项）</li>
     *   <li>需要添加jetcache-spring-boot-starter依赖</li>
     *   <li>需要配置本地缓存（Caffeine）和远程缓存（Redis）</li>
     * </ul>
     * 
     * <p>注意：JetCacheRuleCacheService 使用 @Component 注解，会被Spring自动扫描并创建。
     * 这里只需要确保在JetCache可用时，该Bean会被优先使用。
     * 如果JetCache不可用，会自动回退到其他实现（如果有）。
     * 
     * @param jetCacheRuleCacheService JetCache缓存服务实例（由Spring自动注入）
     * @return JetCache多级缓存服务实例
     */
    @Bean
    @ConditionalOnMissingBean(RuleCacheService.class)
    @ConditionalOnClass(name = "com.alicp.jetcache.Cache")
    public RuleCacheService ruleCacheService(JetCacheRuleCacheService jetCacheRuleCacheService) {
        return jetCacheRuleCacheService;
    }


    /**
     * 规则引擎应用服务
     * 
     * @param ruleEngineDomainService 规则引擎领域服务
     * @param ruleRepository 规则定义仓储
     * @param ruleCacheService 规则缓存服务
     * @return 规则引擎应用服务实例
     */
    @Bean
    @ConditionalOnMissingBean
    public RuleEngineApplicationService ruleEngineApplicationService(
            RuleEngineDomainService ruleEngineDomainService,
            RuleDefinitionRepository ruleRepository,
            RuleCacheService ruleCacheService) {
        return new RuleEngineApplicationService(
                ruleEngineDomainService,
                ruleRepository,
                ruleCacheService);
    }

    /**
     * 规则管理服务
     * 
     * @param ruleRepository 规则定义仓储
     * @param ruleCacheService 规则缓存服务
     * @param ruleEngineDomainService 规则引擎领域服务
     * @return 规则管理服务实例
     */
    @Bean
    @ConditionalOnMissingBean
    public RuleManagementService ruleManagementService(
            RuleDefinitionRepository ruleRepository,
            RuleCacheService ruleCacheService,
            RuleEngineDomainService ruleEngineDomainService) {
        return new RuleManagementService(
                ruleRepository,
                ruleCacheService,
                ruleEngineDomainService);
    }

}

