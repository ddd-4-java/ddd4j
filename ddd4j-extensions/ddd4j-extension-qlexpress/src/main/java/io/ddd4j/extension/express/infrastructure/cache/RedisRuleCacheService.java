package io.ddd4j.extension.express.infrastructure.cache;

import io.ddd4j.extension.express.application.service.RuleCacheService;
import io.ddd4j.extension.express.domain.model.entity.RuleDefinition;
import io.ddd4j.kit.lang.StrKit;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存规则缓存服务实现
 *
 * <p>基础设施层：使用进程内 {@link ConcurrentHashMap} 实现缓存。
 * 当 Caffeine 不可用时，作为回退方案使用。
 *
 * <p>该实现为纯 Java 版本，去除了对 Redis 的依赖。
 * 如需分布式缓存，可在上层基于 {@link RuleCacheService} 接口提供 Redis/Jedis 实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @version 1.0
 * @since 1.0
 */
public class RedisRuleCacheService implements RuleCacheService {

    private final ConcurrentHashMap<String, RuleDefinition> store = new ConcurrentHashMap<>();

    /**
     * 获取规则
     *
     * @param ruleCode 规则编码，不能为null
     * @return 规则定义，如果不存在返回null
     */
    @Override
    public RuleDefinition get(String ruleCode) {
        if (Objects.isNull(ruleCode) || !StrKit.hasText(ruleCode)) {
            return null;
        }
        return store.get(ruleCode);
    }

    /**
     * 缓存规则
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
        store.put(ruleCode, rule);
    }

    /**
     * 清除指定规则缓存
     *
     * @param ruleCode 规则编码，不能为null
     */
    @Override
    public void evict(String ruleCode) {
        if (Objects.isNull(ruleCode) || !StrKit.hasText(ruleCode)) {
            return;
        }
        store.remove(ruleCode);
    }

    /**
     * 清除所有规则缓存
     */
    @Override
    public void evictAll() {
        store.clear();
    }
}
