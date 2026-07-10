package io.ddd4j.sample.quarkus.cqrs.cache;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.sample.quarkus.cqrs.order.domain.model.Order;
import io.ddd4j.sample.quarkus.cqrs.order.domain.repository.OrderRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 订单缓存服务（CQRS 读侧增强 - Quarkus）。
 *
 * <p>基于 ddd4j-cache {@link CacheKit}，提供：
 * <ul>
 *   <li>{@code ORDER_STATS}：订单统计快照（按状态计数）</li>
 *   <li>{@code BUYER_ORDER_COUNT}：买家订单计数</li>
 *   <li>{@code ORDER_DETAIL}：订单详情缓存</li>
 * </ul>
 *
 * <p>CQRS 查询端优先从缓存读取，未命中再查 Repository 并回填缓存。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@ApplicationScoped
public class OrderCacheService {

    /**
     * 缓存域：订单统计
     */
    public static final String BIZ_ORDER_STATS = "quarkus-cqrs-order-stats";
    /**
     * 缓存域：买家订单计数
     */
    public static final String BIZ_BUYER_ORDER_COUNT = "quarkus-cqrs-buyer-order-count";
    /**
     * 缓存域：订单详情
     */
    public static final String BIZ_ORDER_DETAIL = "quarkus-cqrs-order-detail";

    /**
     * 统计缓存 key
     */
    private static final String STATS_KEY = "all-stats";

    private final OrderRepository repository;

    /**
     * 构造函数。
     *
     * @param repository 订单仓储
     */
    @Inject
    public OrderCacheService(OrderRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /**
     * 获取订单统计（缓存优先）。
     *
     * @return 订单统计 Map
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
        // 按状态聚合统计
        Map<String, Long> byStatus = new HashMap<>();
        for (Order o : all) {
            String s = o.status().name();
            byStatus.merge(s, 1L, Long::sum);
        }
        stats.put("byStatus", byStatus);
        CacheKit.put(BIZ_ORDER_STATS, STATS_KEY, stats);
        return stats;
    }

    /**
     * 获取买家订单计数（缓存优先）。
     *
     * @param buyerId 买家 ID
     * @return 订单数量
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
     * 获取订单详情（缓存优先）。
     *
     * @param orderId 订单 ID
     * @return 订单（缓存命中或实时查询）
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
     * 写入/更新订单详情缓存（写操作后调用）。
     *
     * @param order 订单聚合
     */
    public void putOrder(Order order) {
        if (order == null) {
            return;
        }
        CacheKit.put(BIZ_ORDER_DETAIL, order.id(), order);
        // 统计类缓存失效，下次重算
        CacheKit.invalidate(BIZ_ORDER_STATS, STATS_KEY);
    }

    /**
     * 清除统计缓存（订单状态变更后调用）。
     */
    public void evictStats() {
        CacheKit.invalidate(BIZ_ORDER_STATS, STATS_KEY);
    }
}