package io.ddd4j.sample.javalin.cqrs.cache;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.sample.javalin.cqrs.order.domain.model.Order;
import io.ddd4j.sample.javalin.cqrs.order.domain.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 订单缓存服务（CQRS 读侧增强 - Javalin）。
 *
 * <p>Javalin 无 DI 容器，本类在 main 中手动 {@code new} 即可。
 *
 * <p>缓存域：
 * <ul>
 *   <li>{@code ORDER_STATS}：订单统计</li>
 *   <li>{@code BUYER_ORDER_COUNT}：买家订单计数</li>
 *   <li>{@code ORDER_DETAIL}：订单详情</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class OrderCacheService {

    /**
     * 缓存域：订单统计
     */
    public static final String BIZ_ORDER_STATS = "javalin-cqrs-order-stats";
    /**
     * 缓存域：买家订单计数
     */
    public static final String BIZ_BUYER_ORDER_COUNT = "javalin-cqrs-buyer-order-count";
    /**
     * 缓存域：订单详情
     */
    public static final String BIZ_ORDER_DETAIL = "javalin-cqrs-order-detail";

    private static final String STATS_KEY = "all-stats";

    private final OrderRepository repository;

    public OrderCacheService(OrderRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /**
     * 订单统计（缓存优先）。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getOrderStats() {
        Object cached = CacheKit.get(BIZ_ORDER_STATS, STATS_KEY);
        if (cached instanceof Map) {
            log.debug("Cache hit: ORDER_STATS");
            return (Map<String, Object>) cached;
        }
        log.debug("Cache miss: ORDER_STATS, computing...");
        List<Order> all = repository.findAll();
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", all.size());
        Map<String, Long> byStatus = new HashMap<>();
        for (Order o : all) {
            byStatus.merge(o.status().name(), 1L, Long::sum);
        }
        stats.put("byStatus", byStatus);
        CacheKit.put(BIZ_ORDER_STATS, STATS_KEY, stats);
        return stats;
    }

    /**
     * 买家订单计数（缓存优先）。
     */
    public long getBuyerOrderCount(String buyerId) {
        String cacheKey = "buyer:" + buyerId;
        Object cached = CacheKit.get(BIZ_BUYER_ORDER_COUNT, cacheKey);
        if (cached instanceof Long count) {
            log.debug("Cache hit: BUYER_ORDER_COUNT buyerId={}", buyerId);
            return count;
        }
        log.debug("Cache miss: BUYER_ORDER_COUNT buyerId={}, computing...", buyerId);
        long count = repository.findAll().stream()
                .filter(o -> Objects.equals(buyerId, o.buyerId()))
                .count();
        CacheKit.put(BIZ_BUYER_ORDER_COUNT, cacheKey, count);
        return count;
    }

    /**
     * 订单详情（缓存优先）。
     */
    public Optional<Order> getOrderDetail(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return Optional.empty();
        }
        Object cached = CacheKit.get(BIZ_ORDER_DETAIL, orderId);
        if (cached instanceof Order) {
            log.debug("Cache hit: ORDER_DETAIL orderId={}", orderId);
            return Optional.of((Order) cached);
        }
        log.debug("Cache miss: ORDER_DETAIL orderId={}", orderId);
        Optional<Order> found = repository.findById(orderId);
        found.ifPresent(o -> CacheKit.put(BIZ_ORDER_DETAIL, orderId, o));
        return found;
    }

    /**
     * 写入订单后更新缓存与失效统计。
     */
    public void putOrder(Order order) {
        if (order == null) {
            return;
        }
        CacheKit.put(BIZ_ORDER_DETAIL, order.id(), order);
        CacheKit.invalidate(BIZ_ORDER_STATS, STATS_KEY);
    }

    /**
     * 失效统计缓存。
     */
    public void evictStats() {
        CacheKit.invalidate(BIZ_ORDER_STATS, STATS_KEY);
    }
}