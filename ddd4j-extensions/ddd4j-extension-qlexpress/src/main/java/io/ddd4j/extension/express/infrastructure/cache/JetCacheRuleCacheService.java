package io.ddd4j.extension.express.infrastructure.cache;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.CreateCache;
import io.ddd4j.extension.express.application.service.RuleCacheService;
import io.ddd4j.extension.express.domain.model.entity.RuleDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;


/**
 * JetCache多级缓存服务实现
 *
 * <p>基础设施层：使用JetCache实现多级缓存。
 * JetCache自动管理本地缓存（Caffeine）和远程缓存（Redis）的二级缓存。
 *
 * <p>缓存策略：
 * <ol>
 *   <li>第一级：本地缓存（Caffeine，内存缓存，速度快）</li>
 *   <li>第二级：远程缓存（Redis，分布式缓存，跨进程共享）</li>
 * </ol>
 *
 * <p>查询顺序：本地缓存 -> Redis缓存 -> 数据库（由调用方处理）
 * JetCache会自动处理缓存穿透、缓存回填、缓存同步等逻辑。
 *
 * <p>配置说明：
 * <ul>
 *   <li>area: 缓存区域名称</li>
 *   <li>name: 缓存实例名称</li>
 *   <li>cacheType: BOTH表示同时使用本地缓存和远程缓存</li>
 *   <li>localLimit: 本地缓存最大容量</li>
 *   <li>expire: 缓存过期时间（秒）</li>
 * </ul>
 *
 * <p>注意：需要在配置文件中配置JetCache的相关配置，例如：
 * <pre>
 * jetcache:
 *   statIntervalMinutes: 15
 *   areaInCacheName: false
 *   hidePackages: io.ddd4j
 *   local:
 *     default:
 *       type: caffeine
 *       limit: 1000
 *       keyConvertor: fastjson
 *   remote:
 *     default:
 *       type: redis
 *       host: localhost
 *       port: 6379
 *       keyConvertor: fastjson
 * </pre>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @version 1.0
 * @since 1.0
 */
@Component
@Slf4j
public class JetCacheRuleCacheService implements RuleCacheService {

    private static final String CACHE_AREA = "rule_engine";
    private static final String CACHE_NAME = "rule_cache";

    /**
     * JetCache多级缓存实例
     *
     * <p>使用 @CreateCache 注解创建缓存实例，JetCache会自动注入。
     * cacheType = BOTH 表示同时使用本地缓存和远程缓存。
     *
     * <p>配置说明：
     * - area: 缓存区域，对应配置文件中的 area 配置
     * - name: 缓存名称，用于区分不同的缓存实例
     * - cacheType: BOTH 表示多级缓存（本地+远程）
     * - localLimit: 本地缓存最大容量
     * - expire: 缓存过期时间（秒），300秒=5分钟
     *
     * <p>注意：@CreateCache 在 JetCache 2.7+ 中可能已废弃，建议使用 CacheManager 获取缓存实例。
     * 但为了兼容性，这里仍使用 @CreateCache 注解。
     */
    @CreateCache(
            area = CACHE_AREA,
            name = CACHE_NAME,
            cacheType = CacheType.BOTH,
            localLimit = 1000,
            expire = 300
    )
    @SuppressWarnings("deprecation")
    private Cache<String, RuleDefinition> cache;

    /**
     * 获取规则（多级缓存查询）
     *
     * <p>JetCache会自动处理多级缓存查询：
     * 1. 先查询本地缓存
     * 2. 如果本地缓存未命中，查询远程缓存（Redis）
     * 3. 如果远程缓存命中，自动回填到本地缓存
     * 4. 如果都未命中，返回null（由调用方从数据库加载）
     *
     * @param ruleCode 规则编码，不能为null
     * @return 规则定义，如果不存在返回null
     */
    @Override
    public RuleDefinition get(String ruleCode) {
        if (Objects.isNull(ruleCode) || !org.springframework.util.StringUtils.hasText(ruleCode)) {
            return null;
        }

        try {
            RuleDefinition rule = cache.get(ruleCode);
            if (Objects.nonNull(rule)) {
                log.debug("从JetCache多级缓存获取规则: {}", ruleCode);
            } else {
                log.debug("JetCache缓存未命中: {}", ruleCode);
            }
            return rule;
        } catch (Exception e) {
            log.error("从JetCache获取规则失败: {}", ruleCode, e);
            return null;
        }
    }

    /**
     * 缓存规则（同时更新本地缓存和远程缓存）
     *
     * <p>JetCache会自动将数据写入本地缓存和远程缓存。
     *
     * @param ruleCode 规则编码，不能为null
     * @param rule     规则定义，不能为null
     */
    @Override
    public void put(String ruleCode, RuleDefinition rule) {
        if (Objects.isNull(ruleCode)
                || !org.springframework.util.StringUtils.hasText(ruleCode)
                || Objects.isNull(rule)) {
            return;
        }

        try {
            cache.put(ruleCode, rule);
            log.debug("更新JetCache多级缓存: {}", ruleCode);
        } catch (Exception e) {
            log.error("更新JetCache缓存失败: {}", ruleCode, e);
        }
    }

    /**
     * 清除指定规则缓存（同时清除本地缓存和远程缓存）
     *
     * <p>JetCache会自动清除本地缓存和远程缓存中的对应数据。
     *
     * @param ruleCode 规则编码，不能为null
     */
    @Override
    public void evict(String ruleCode) {
        if (Objects.isNull(ruleCode) || !org.springframework.util.StringUtils.hasText(ruleCode)) {
            return;
        }

        try {
            cache.remove(ruleCode);
            log.debug("清除JetCache多级缓存: {}", ruleCode);
        } catch (Exception e) {
            log.error("清除JetCache缓存失败: {}", ruleCode, e);
        }
    }

    /**
     * 清除所有规则缓存（同时清除本地缓存和远程缓存）
     *
     * <p>清除所有已缓存的规则，谨慎使用。
     * JetCache会自动清除本地缓存和远程缓存中的所有数据。
     *
     * <p>注意：JetCache 的 Cache 接口可能不支持 removeAll() 和 keySet() 方法。
     * 如果需要清除所有缓存，建议通过配置的缓存区域来清除，或者使用 CacheManager。
     * 这里提供一个兼容的实现，如果方法不存在则记录警告。
     */
    @Override
    public void evictAll() {
        try {
            // 尝试使用 removeAll() 方法（如果支持）
            // JetCache 2.x 的 API 可能不同，这里使用反射调用
            try {
                java.lang.reflect.Method removeAllMethod = cache.getClass().getMethod("removeAll");
                removeAllMethod.invoke(cache);
                log.info("清除所有JetCache多级缓存");
            } catch (NoSuchMethodException e) {
                // 如果 removeAll() 方法不存在，尝试其他方式
                log.warn("JetCache Cache 不支持 removeAll() 方法，无法清除所有缓存");
                log.warn("建议通过 JetCache 的 CacheManager 或配置的缓存区域来清除所有缓存");
            }
        } catch (Exception e) {
            log.error("清除所有JetCache缓存失败", e);
            log.warn("建议通过 JetCache 的 CacheManager 或配置的缓存区域来清除所有缓存");
        }
    }
}
