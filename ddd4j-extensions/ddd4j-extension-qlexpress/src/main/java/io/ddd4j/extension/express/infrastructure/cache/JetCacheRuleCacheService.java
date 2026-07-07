package io.ddd4j.extension.express.infrastructure.cache;

import io.ddd4j.extension.express.application.service.RuleCacheService;
import io.ddd4j.extension.express.domain.model.entity.RuleDefinition;
import io.ddd4j.kit.lang.StrKit;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存多级缓存服务实现
 *
 * <p>基础设施层：使用进程内 {@link ConcurrentHashMap} 实现缓存。
 *
 * <p>该实现为纯 Java 版本。原 JetCache 多级缓存（本地 + 远程）可通过上层
 * 基于 {@link RuleCacheService} 接口，引入 {@code com.alicp.jetcache:jetcache-core}
 * 及对应 starter 后自行实现替换。
 *
 * <p>查询顺序：进程内缓存 -> 数据库（由调用方处理）
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @version 1.0
 * @since 1.0
 */
@Slf4j
public class JetCacheRuleCacheService implements RuleCacheService {

    private final ConcurrentHashMap<String, RuleDefinition> cache = new ConcurrentHashMap<>();

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

        try {
            RuleDefinition rule = cache.get(ruleCode);
            if (Objects.nonNull(rule)) {
                log.debug("从内存缓存获取规则: {}", ruleCode);
            } else {
                log.debug("内存缓存未命中: {}", ruleCode);
            }
            return rule;
        } catch (Exception e) {
            log.error("从内存缓存获取规则失败: {}", ruleCode, e);
            return null;
        }
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

        try {
            cache.put(ruleCode, rule);
            log.debug("更新内存缓存: {}", ruleCode);
        } catch (Exception e) {
            log.error("更新内存缓存失败: {}", ruleCode, e);
        }
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

        try {
            cache.remove(ruleCode);
            log.debug("清除内存缓存: {}", ruleCode);
        } catch (Exception e) {
            log.error("清除内存缓存失败: {}", ruleCode, e);
        }
    }

    /**
     * 清除所有规则缓存，谨慎使用。
     */
    @Override
    public void evictAll() {
        try {
            cache.clear();
            log.info("清除所有内存缓存");
        } catch (Exception e) {
            log.error("清除所有内存缓存失败", e);
        }
    }
}
