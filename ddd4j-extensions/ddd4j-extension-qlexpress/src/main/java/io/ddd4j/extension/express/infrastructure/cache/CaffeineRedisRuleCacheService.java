package io.ddd4j.extension.express.infrastructure.cache;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.extension.express.application.service.RuleCacheService;
import io.ddd4j.extension.express.domain.model.entity.RuleDefinition;
import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * 规则缓存服务实现（基于 {@link CacheKit} 统一缓存门面）。
 *
 * <p>缓存通过 {@link CacheKit} 统一管理，不再直接依赖 Caffeine。
 * 默认使用 Caffeine 本地缓存（5 分钟过期，最大 1000 条）。
 *
 * <p>如需跨进程的分布式二级缓存，可通过 {@code CacheKit.register(biz, redissonCache)}
 * 注册远程缓存实例，业务代码无需修改。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 1.0
 */
@Slf4j
public class CaffeineRedisRuleCacheService implements RuleCacheService {

    /**
     * 缓存业务标识
     */
    private static final String CACHE_BIZ = "rule_engine";
    /**
     * 本地缓存最大条目数
     */
    private static final long LOCAL_CACHE_MAX_SIZE = 1000;
    /**
     * 本地缓存过期时间（分钟）
     */
    private static final long LOCAL_CACHE_EXPIRE_MINUTES = 5;

    static {
        // 初始化规则缓存（Caffeine 本地缓存，5 分钟过期，最大 1000 条）
        CacheKit.build(CACHE_BIZ, config -> config
                .maximumSize(LOCAL_CACHE_MAX_SIZE)
                .expireAfterWriteSeconds(LOCAL_CACHE_EXPIRE_MINUTES * 60)
                .recordStats(true)
        );
        log.info("规则缓存初始化完成（CacheKit/Caffeine），最大容量: {}, 过期时间: {}分钟",
                LOCAL_CACHE_MAX_SIZE, LOCAL_CACHE_EXPIRE_MINUTES);
    }

    @Override
    public RuleDefinition get(String ruleCode) {
        if (Objects.isNull(ruleCode) || !StrKit.hasText(ruleCode)) {
            return null;
        }
        RuleDefinition rule = CacheKit.get(CACHE_BIZ, ruleCode);
        if (Objects.nonNull(rule)) {
            log.debug("从缓存获取规则: {}", ruleCode);
        } else {
            log.debug("缓存未命中: {}", ruleCode);
        }
        return rule;
    }

    @Override
    public void put(String ruleCode, RuleDefinition rule) {
        if (Objects.isNull(ruleCode) || !StrKit.hasText(ruleCode) || Objects.isNull(rule)) {
            return;
        }
        CacheKit.put(CACHE_BIZ, ruleCode, rule);
        log.debug("更新规则缓存: {}", ruleCode);
    }

    @Override
    public void evict(String ruleCode) {
        if (Objects.isNull(ruleCode) || !StrKit.hasText(ruleCode)) {
            return;
        }
        CacheKit.invalidate(CACHE_BIZ, ruleCode);
        log.debug("清除规则缓存: {}", ruleCode);
    }

    @Override
    public void evictAll() {
        CacheKit.invalidateAll(CACHE_BIZ);
        log.info("清除所有规则缓存");
    }

    /**
     * 获取缓存统计信息。
     *
     * @return 缓存统计信息字符串
     */
    public String getCacheStats() {
        return Objects.toString(CacheKit.getStats(CACHE_BIZ));
    }

}
