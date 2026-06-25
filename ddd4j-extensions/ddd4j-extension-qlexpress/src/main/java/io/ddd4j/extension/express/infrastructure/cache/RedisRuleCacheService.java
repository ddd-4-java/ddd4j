package io.ddd4j.extension.express.infrastructure.cache;

import io.ddd4j.extension.express.application.service.RuleCacheService;
import io.ddd4j.extension.express.domain.model.entity.RuleDefinition;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis规则缓存服务实现
 * 
 * <p>基础设施层：使用Redis实现缓存。
 * 当 Caffeine 不可用时，作为回退方案使用。
 * 
 * <p>注意：此类通过ExpressAutoConfiguration自动配置，无需手动添加@Service注解
 * 
 * @author ddd4j
 * @version 1.0
 * @since 1.0
 */
public class RedisRuleCacheService implements RuleCacheService {

    private static final String RULE_CACHE_PREFIX = "rule_engine:rule:";
    private static final long RULE_CACHE_TTL = 300; // 5分钟缓存

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisRuleCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取规则
     * 
     * @param ruleCode 规则编码，不能为null
     * @return 规则定义，如果不存在返回null
     */
    @Override
    public RuleDefinition get(String ruleCode) {
        if (ruleCode == null || ruleCode.trim().isEmpty()) {
            return null;
        }
        String cacheKey = RULE_CACHE_PREFIX + ruleCode;
        return (RuleDefinition) redisTemplate.opsForValue().get(cacheKey);
    }

    /**
     * 缓存规则
     * 
     * @param ruleCode 规则编码，不能为null
     * @param rule 规则定义，不能为null
     */
    @Override
    public void put(String ruleCode, RuleDefinition rule) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || rule == null) {
            return;
        }
        String cacheKey = RULE_CACHE_PREFIX + ruleCode;
        redisTemplate.opsForValue().set(cacheKey, rule, RULE_CACHE_TTL, TimeUnit.SECONDS);
    }

    /**
     * 清除指定规则缓存
     * 
     * @param ruleCode 规则编码，不能为null
     */
    @Override
    public void evict(String ruleCode) {
        if (ruleCode == null || ruleCode.trim().isEmpty()) {
            return;
        }
        String cacheKey = RULE_CACHE_PREFIX + ruleCode;
        redisTemplate.delete(cacheKey);
    }

    /**
     * 清除所有规则缓存
     */
    @Override
    public void evictAll() {
        Set<String> keys = redisTemplate.keys(RULE_CACHE_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}

