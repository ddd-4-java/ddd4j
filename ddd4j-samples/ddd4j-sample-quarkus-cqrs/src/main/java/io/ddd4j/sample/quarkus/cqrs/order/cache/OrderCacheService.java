package io.ddd4j.sample.quarkus.cqrs.order.cache;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.sample.quarkus.cqrs.order.domain.model.Order;
import io.ddd4j.sample.quarkus.cqrs.order.domain.repository.OrderRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;

/**
 * 订单缓存服务：演示 ddd4j-cache（{@link CacheKit}）在 Quarkus 下的使用。
 *
 * <p>基于 Caffeine 本地缓存，提供订单数据的读写穿透能力：
 * <ul>
 *   <li>CQRS 查询端优先从缓存读取，未命中再查 Repository</li>
 *   <li>写操作（创建/支付/发货）后同步更新缓存</li>
 *   <li>取消操作后驱逐缓存</li>
 * </ul>
 *
 * <p>缓存键规则：{@code order:{orderId}}
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@ApplicationScoped
public class OrderCacheService {

    /**
     * 缓存业务标识
     */
    private static final String CACHE_BIZ = "order";

    /**
     * 缓存过期时间（秒）
     */
    private static final long EXPIRE_SECONDS = 300;

    private final OrderRepository repository;

    /**
     * 构造函数：初始化缓存并注入仓库。
     *
     * @param repository 订单仓库
     */
    @Inject
    public OrderCacheService(OrderRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        // 注册本地缓存：Caffeine，写后 300 秒过期
        CacheKit.build(CACHE_BIZ, EXPIRE_SECONDS);
        log.info("[OrderCacheService] 本地缓存已注册: biz={}, expire={}s", CACHE_BIZ, EXPIRE_SECONDS);
    }

    /**
     * 按 ID 查询订单（缓存优先）。
     *
     * <p>CQRS 查询端使用：先查缓存，未命中则查 Repository 并回填缓存。
     *
     * @param orderId 订单 ID
     * @return 订单（可能为空）
     */
    public Optional<Order> getOrder(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return Optional.empty();
        }
        // 1. 查缓存
        Order cached = CacheKit.get(CACHE_BIZ, orderId);
        if (cached != null) {
            log.debug("[OrderCacheService] 缓存命中: orderId={}", orderId);
            return Optional.of(cached);
        }
        // 2. 未命中，查 Repository
        Optional<Order> found = repository.findById(orderId);
        found.ifPresent(order -> {
            CacheKit.put(CACHE_BIZ, orderId, order);
            log.debug("[OrderCacheService] 缓存回填: orderId={}", orderId);
        });
        return found;
    }

    /**
     * 写入/更新缓存（写操作后调用）。
     *
     * @param order 订单聚合
     */
    public void putOrder(Order order) {
        if (order == null) {
            return;
        }
        CacheKit.put(CACHE_BIZ, order.id(), order);
        log.debug("[OrderCacheService] 缓存更新: orderId={}", order.id());
    }

    /**
     * 驱逐缓存（取消/删除操作后调用）。
     *
     * @param orderId 订单 ID
     */
    public void evictOrder(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return;
        }
        CacheKit.invalidate(CACHE_BIZ, orderId);
        log.debug("[OrderCacheService] 缓存驱逐: orderId={}", orderId);
    }

    /**
     * 清空全部订单缓存。
     */
    public void evictAll() {
        CacheKit.invalidateAll(CACHE_BIZ);
        log.info("[OrderCacheService] 缓存全量驱逐");
    }
}
