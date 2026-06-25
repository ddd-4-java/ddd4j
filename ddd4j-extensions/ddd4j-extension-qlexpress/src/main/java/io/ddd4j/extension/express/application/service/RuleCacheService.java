package io.ddd4j.extension.express.application.service;

import io.ddd4j.extension.express.domain.model.entity.RuleDefinition;

/**
 * 规则缓存服务接口
 * 
 * <p>应用层服务，负责规则缓存管理。
 * 提供规则的缓存、获取、清除等操作。
 * 
 * <p>实现类：
 * <ul>
 *   <li>JetCacheRuleCacheService - JetCache多级缓存（推荐，自动管理本地+远程缓存）</li>
 *   <li>CaffeineRedisRuleCacheService - Caffeine + Redis 二级缓存（备用）</li>
 *   <li>RedisRuleCacheService - 仅 Redis 缓存（回退方案）</li>
 * </ul>
 * 
 * @author ddd4j
 * @version 1.0
 * @since 1.0
 */
public interface RuleCacheService {

    /**
     * 获取规则
     * 
     * @param ruleCode 规则编码，不能为null
     * @return 规则定义，如果不存在返回null
     */
    RuleDefinition get(String ruleCode);

    /**
     * 缓存规则
     * 
     * @param ruleCode 规则编码，不能为null
     * @param rule 规则定义，不能为null
     */
    void put(String ruleCode, RuleDefinition rule);

    /**
     * 清除指定规则缓存
     * 
     * @param ruleCode 规则编码，不能为null
     */
    void evict(String ruleCode);

    /**
     * 清除所有规则缓存
     * 
     * <p>清除所有已缓存的规则，谨慎使用。
     */
    void evictAll();
}

