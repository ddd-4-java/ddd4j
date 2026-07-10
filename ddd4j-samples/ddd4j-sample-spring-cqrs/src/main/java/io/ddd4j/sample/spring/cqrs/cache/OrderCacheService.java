package io.ddd4j.sample.spring.cqrs.cache;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.sample.spring.cqrs.order.domain.model.Order;
import io.ddd4j.sample.spring.cqrs.order.domain.model.OrderStatus;
import io.ddd4j.sample.spring.cqrs.order.domain.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 订单缓存服务。
 *
 * <p>使用 ddd4j {@link CacheKit} 缓存门面管理订单统计数据的缓存。
 * CacheKit 屏蔽底层缓存实现（Caffeine / Redis / Redisson），
 * 业务代码只需调用 {@code CacheKit.get / CacheKit.put}，
 * 切换缓存后端时业务代码完全不变。
 *
 * <p>演示的缓存域：
 * <ul>
 *   <li>{@code ORDER_STATS}：订单统计快照（各状态计数）</li>
 *   <li>{@code BUYER_ORDER_COUNT}：买家订单计数</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@Service
public class OrderCacheService {

    /**
     * 缓存域：订单统计
     */
    public static final String BIZ_ORDER_STATS = "order-stats";
    /**
     * 缓存域：买家订单计数
     */
    public static final String BIZ_BUYER_ORDER_COUNT = "buyer-order-count";

    /**
     * 统计缓存 key
     */
    private static final String STATS_KEY = "all-stats";

    private final OrderRepository orderRepository;

    /**
     * 构造函数。
     *
     * @param orderRepository 订单仓储
     */
    @Autowired
    public OrderCacheService(OrderRepository orderRepository) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
    }

    /**
     * 获取订单统计（优先从缓存读取，缓存未命中则实时计算）。
     *
     * @return 订单统计信息
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getOrderStats() {
        // 先尝试从缓存获取
        Object cached = CacheKit.get(BIZ_ORDER_STATS, STATS_KEY);
        if (cached instanceof Map) {
            log.debug("Cache hit: ORDER_STATS");
            return (Map<String, Object>) cached;
        }

        // 缓存未命中，实时计算
        log.debug("Cache miss: ORDER_STATS, computing...");
        List<Order> all = orderRepository.findAll();
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", all.size());
        for (OrderStatus status : OrderStatus.values()) {
            long count = all.stream().filter(o -> o.status() == status).count();
            stats.put(status.name().toLowerCase(), count);
        }

        // 写入缓存
        CacheKit.put(BIZ_ORDER_STATS, STATS_KEY, stats);
        return stats;
    }

    /**
     * 获取买家订单计数（缓存演示）。
     *
     * @param buyerId 买家 ID
     * @return 订单数量
     */
    public long getBuyerOrderCount(String buyerId) {
        String cacheKey = "buyer:" + buyerId;

        // 先尝试从缓存获取
        Object cached = CacheKit.get(BIZ_BUYER_ORDER_COUNT, cacheKey);
        if (cached instanceof Long count) {
            log.debug("Cache hit: BUYER_ORDER_COUNT for buyerId={}", buyerId);
            return count;
        }

        // 缓存未命中，实时计算
        log.debug("Cache miss: BUYER_ORDER_COUNT for buyerId={}, computing...", buyerId);
        long count = orderRepository.findAll().stream()
                .filter(o -> Objects.equals(buyerId, o.buyerId()))
                .count();

        CacheKit.put(BIZ_BUYER_ORDER_COUNT, cacheKey, count);
        return count;
    }

    /**
     * 清除订单统计缓存（在订单状态变更后调用）。
     */
    public void evictOrderStats() {
        CacheKit.invalidate(BIZ_ORDER_STATS, STATS_KEY);
        log.debug("Evicted ORDER_STATS cache");
    }

    /**
     * 清除买家订单计数缓存。
     *
     * @param buyerId 买家 ID
     */
    public void evictBuyerOrderCount(String buyerId) {
        CacheKit.invalidate(BIZ_BUYER_ORDER_COUNT, "buyer:" + buyerId);
        log.debug("Evicted BUYER_ORDER_COUNT cache for buyerId={}", buyerId);
    }
}
