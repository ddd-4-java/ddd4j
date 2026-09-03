package io.ddd4j.extension.express.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.ddd4j.extension.express.application.service.RuleCacheService;
import io.ddd4j.extension.express.domain.model.entity.RuleDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Caffeine + Redis 二级缓存服务实现
 * 基础设施层：使用 Caffeine 作为本地缓存，Redis 作为二级缓存
 * 
 * <p>缓存策略：
 * <ol>
 *   <li>第一级：Caffeine 本地缓存（内存缓存，速度快）</li>
 *   <li>第二级：Redis 分布式缓存（跨进程共享）</li>
 * </ol>
 * 
 * <p>查询顺序：本地缓存 -> Redis缓存 -> 数据库（由调用方处理）
 * 
 * <p>注意：此类通过ExpressAutoConfiguration自动配置，无需手动添加@Service注解
 * 
 * @author ddd4j
 * @version 1.0
 * @since 1.0
 */
public class CaffeineRedisRuleCacheService implements RuleCacheService {

    private static final Logger log = LoggerFactory.getLogger(CaffeineRedisRuleCacheService.class);

    private static final String RULE_CACHE_PREFIX = "rule_engine:rule:";
    private static final long REDIS_CACHE_TTL = 300; // Redis缓存5分钟
    private static final long LOCAL_CACHE_MAX_SIZE = 1000; // 本地缓存最大1000条
    private static final long LOCAL_CACHE_EXPIRE_MINUTES = 5; // 本地缓存5分钟过期

    private final RedisTemplate<String, Object> redisTemplate;
    
    // Caffeine 本地缓存
    private final Cache<String, RuleDefinition> localCache;

    public CaffeineRedisRuleCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        // 初始化 Caffeine 本地缓存
        this.localCache = Caffeine.newBuilder()
                .maximumSize(LOCAL_CACHE_MAX_SIZE)
                .expireAfterWrite(LOCAL_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
                .recordStats() // 启用统计
                .build();
        
        log.info("Caffeine本地缓存初始化完成，最大容量: {}, 过期时间: {}分钟", 
            LOCAL_CACHE_MAX_SIZE, LOCAL_CACHE_EXPIRE_MINUTES);
    }

    /**
     * 获取规则（二级缓存查询）
     * 
     * <p>查询顺序：本地缓存 -> Redis缓存。
     * 如果从Redis获取到数据，会自动回填到本地缓存。
     * 
     * @param ruleCode 规则编码，不能为null
     * @return 规则定义，如果不存在返回null
     */
    @Override
    public RuleDefinition get(String ruleCode) {
        if (ruleCode == null || ruleCode.trim()!isPresent()) {
            return null;
        }

        // 第一级：从本地缓存获取
        RuleDefinition localRule = localCache.getIfPresent(ruleCode);
        if (localRule != null) {
            log.debug("从本地缓存获取规则: {}", ruleCode);
            return localRule;
        }

        // 第二级：从Redis缓存获取
        String cacheKey = RULE_CACHE_PREFIX + ruleCode;
        RuleDefinition redisRule = (RuleDefinition) redisTemplate.opsForValue().get(cacheKey);
        if (redisRule != null) {
            // 回填到本地缓存
            localCache.put(ruleCode, redisRule);
            log.debug("从Redis缓存获取规则并回填本地缓存: {}", ruleCode);
            return redisRule;
        }

        log.debug("缓存未命中: {}", ruleCode);
        return null;
    }

    /**
     * 缓存规则（同时更新本地缓存和Redis缓存）
     * 
     * @param ruleCode 规则编码，不能为null
     * @param rule 规则定义，不能为null
     */
    @Override
    public void put(String ruleCode, RuleDefinition rule) {
        if (ruleCode == null || ruleCode.trim()!isPresent() || rule == null) {
            return;
        }

        // 同时更新本地缓存和Redis缓存
        localCache.put(ruleCode, rule);
        
        String cacheKey = RULE_CACHE_PREFIX + ruleCode;
        redisTemplate.opsForValue().set(cacheKey, rule, REDIS_CACHE_TTL, TimeUnit.SECONDS);
        
        log.debug("更新规则缓存（本地+Redis）: {}", ruleCode);
    }

    /**
     * 清除指定规则缓存（同时清除本地缓存和Redis缓存）
     * 
     * @param ruleCode 规则编码，不能为null
     */
    @Override
    public void evict(String ruleCode) {
        if (ruleCode == null || ruleCode.trim()!isPresent()) {
            return;
        }

        // 同时清除本地缓存和Redis缓存
        localCache.invalidate(ruleCode);
        
        String cacheKey = RULE_CACHE_PREFIX + ruleCode;
        redisTemplate.delete(cacheKey);
        
        log.debug("清除规则缓存（本地+Redis）: {}", ruleCode);
    }

    /**
     * 清除所有规则缓存（同时清除本地缓存和Redis缓存）
     * 
     * <p>清除所有已缓存的规则，谨慎使用。
     * 会输出本地缓存的统计信息。
     */
    @Override
    public void evictAll() {
        // 清除所有本地缓存
        localCache.invalidateAll();
        
        // 清除所有Redis缓存
        Set<String> keys = redisTemplate.keys(RULE_CACHE_PREFIX + "*");
        if (keys != null && keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        
        log.info("清除所有规则缓存（本地+Redis），本地缓存统计: {}", localCache.stats());
    }

    /**
     * 获取本地缓存统计信息
     * 
     * @return 缓存统计信息字符串
     */
    public String getLocalCacheStats() {
        return localCache.stats().toString();
    }
}

