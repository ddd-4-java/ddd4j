package io.ddd4j.extension.express.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.ddd4j.extension.express.application.service.RuleCacheService;
import io.ddd4j.extension.express.domain.model.entity.RuleDefinition;
import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Caffeine + 内存 二级缓存服务实现
 * 基础设施层：使用 Caffeine 作为本地缓存，进程内 Map 作为二级缓存
 *
 * <p>缓存策略：
 * <ol>
 *   <li>第一级：Caffeine 本地缓存（内存缓存，速度快）</li>
 *   <li>第二级：进程内 Map 缓存（同一进程共享）</li>
 * </ol>
 *
 * <p>查询顺序：本地缓存 -> 进程内缓存 -> 数据库（由调用方处理）
 *
 * <p>该实现为纯 Java 版本，去除了对 Redis 的依赖。
 * 如需跨进程的分布式二级缓存，可在上层基于 {@link RuleCacheService} 接口提供 Redis/Jedis 实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @version 1.0
 * @since 1.0
 */
@Slf4j
public class CaffeineRedisRuleCacheService implements RuleCacheService {

    private static final long LOCAL_CACHE_MAX_SIZE = 1000; // 本地缓存最大1000条
    private static final long LOCAL_CACHE_EXPIRE_MINUTES = 5; // 本地缓存5分钟过期

    // 二级缓存：进程内 Map（替代 Redis）
    private final ConcurrentHashMap<String, RuleDefinition> remoteCache;

    // Caffeine 本地缓存
    private final Cache<String, RuleDefinition> localCache;

    public CaffeineRedisRuleCacheService() {
        this.remoteCache = new ConcurrentHashMap<>();
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
     * <p>查询顺序：本地缓存 -> 进程内缓存。
     * 如果从进程内缓存获取到数据，会自动回填到本地缓存。
     *
     * @param ruleCode 规则编码，不能为null
     * @return 规则定义，如果不存在返回null
     */
    @Override
    public RuleDefinition get(String ruleCode) {
        if (Objects.isNull(ruleCode) || !StrKit.hasText(ruleCode)) {
            return null;
        }

        // 第一级：从本地缓存获取
        RuleDefinition localRule = localCache.getIfPresent(ruleCode);
        if (Objects.nonNull(localRule)) {
            log.debug("从本地缓存获取规则: {}", ruleCode);
            return localRule;
        }

        // 第二级：从进程内缓存获取
        RuleDefinition remoteRule = remoteCache.get(ruleCode);
        if (Objects.nonNull(remoteRule)) {
            // 回填到本地缓存
            localCache.put(ruleCode, remoteRule);
            log.debug("从进程内缓存获取规则并回填本地缓存: {}", ruleCode);
            return remoteRule;
        }

        log.debug("缓存未命中: {}", ruleCode);
        return null;
    }

    /**
     * 缓存规则（同时更新本地缓存和进程内缓存）
     *
     * @param ruleCode 规则编码，不能为null
     * @param rule     规则定义，不能为null
     */
    @Override
    public void put(String ruleCode, RuleDefinition rule) {
        if (Objects.isNull(ruleCode)
                || !StrKit.hasText(ruleCode)
                || Objects.isNull(rule)) {
            return;
        }

        // 同时更新本地缓存和进程内缓存
        localCache.put(ruleCode, rule);
        remoteCache.put(ruleCode, rule);

        log.debug("更新规则缓存（本地+进程内）: {}", ruleCode);
    }

    /**
     * 清除指定规则缓存（同时清除本地缓存和进程内缓存）
     *
     * @param ruleCode 规则编码，不能为null
     */
    @Override
    public void evict(String ruleCode) {
        if (Objects.isNull(ruleCode) || !StrKit.hasText(ruleCode)) {
            return;
        }

        // 同时清除本地缓存和进程内缓存
        localCache.invalidate(ruleCode);
        remoteCache.remove(ruleCode);

        log.debug("清除规则缓存（本地+进程内）: {}", ruleCode);
    }

    /**
     * 清除所有规则缓存（同时清除本地缓存和进程内缓存）
     *
     * <p>清除所有已缓存的规则，谨慎使用。
     * 会输出本地缓存的统计信息。
     */
    @Override
    public void evictAll() {
        // 清除所有本地缓存
        localCache.invalidateAll();

        // 清除所有进程内缓存
        remoteCache.clear();

        log.info("清除所有规则缓存（本地+进程内），本地缓存统计: {}", localCache.stats());
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
