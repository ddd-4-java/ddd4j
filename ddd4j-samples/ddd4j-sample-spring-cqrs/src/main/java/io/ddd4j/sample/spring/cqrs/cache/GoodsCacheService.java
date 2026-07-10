package io.ddd4j.sample.spring.cqrs.cache;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.sample.spring.cqrs.goods.domain.Goods;
import io.ddd4j.sample.spring.cqrs.goods.domain.GoodsId;
import io.ddd4j.sample.spring.cqrs.goods.infrastructure.InMemoryGoodsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 商品缓存服务（CQRS 读侧增强）。
 *
 * <p>演示如何为第三轨（Model/Query）业务挂上 ddd4j {@link CacheKit} 缓存门面，
 * 让 CQRS 读侧优先命中缓存、降低数据库压力。
 *
 * <p>演示的缓存域：
 * <ul>
 *   <li>{@code GOODS_DETAIL}：按 ID 缓存商品详情</li>
 *   <li>{@code GOODS_LIST}：按条件缓存商品列表快照</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@Service
public class GoodsCacheService {

    /**
     * 缓存域：商品详情
     */
    public static final String BIZ_GOODS_DETAIL = "goods-detail";
    /**
     * 缓存域：商品列表快照
     */
    public static final String BIZ_GOODS_LIST = "goods-list";

    /**
     * 全部商品列表的固定 key
     */
    private static final String LIST_ALL_KEY = "all";

    private final InMemoryGoodsRepository goodsRepository;

    /**
     * 构造函数。
     *
     * @param goodsRepository 内存商品仓储（实现 {@code Repository} 提供 findAll）
     */
    @Autowired
    public GoodsCacheService(InMemoryGoodsRepository goodsRepository) {
        this.goodsRepository = Objects.requireNonNull(goodsRepository, "goodsRepository must not be null");
    }

    /**
     * 从缓存中读取商品（缓存优先）。
     *
     * @param id 商品 ID
     * @return 商品
     */
    public Goods getById(Long id) {
        String cacheKey = String.valueOf(id);
        Object cached = CacheKit.get(BIZ_GOODS_DETAIL, cacheKey);
        if (cached instanceof Goods) {
            log.debug("Cache hit: GOODS_DETAIL id={}", id);
            return (Goods) cached;
        }
        log.debug("Cache miss: GOODS_DETAIL id={}, loading from repository", id);
        Goods goods = goodsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("goods not found: " + id));
        CacheKit.put(BIZ_GOODS_DETAIL, cacheKey, goods);
        return goods;
    }

    /**
     * 从缓存中读取商品列表快照（缓存优先）。
     *
     * @return 全部商品列表
     */
    @SuppressWarnings("unchecked")
    public List<Goods> listAll() {
        Object cached = CacheKit.get(BIZ_GOODS_LIST, LIST_ALL_KEY);
        if (cached instanceof List) {
            log.debug("Cache hit: GOODS_LIST all");
            return new java.util.ArrayList<>((List<Goods>) cached);
        }
        log.debug("Cache miss: GOODS_LIST all, loading from repository");
        List<Goods> all = new java.util.ArrayList<>(goodsRepository.findAll());
        CacheKit.put(BIZ_GOODS_LIST, LIST_ALL_KEY, all);
        return all;
    }

    /**
     * 按状态缓存分组查询结果。
     *
     * @param status 状态字符串
     * @return 该状态下的商品列表
     */
    @SuppressWarnings("unchecked")
    public List<Goods> listByStatus(String status) {
        String cacheKey = "status:" + status;
        List<Goods> cached = (List<Goods>) CacheKit.get(BIZ_GOODS_LIST, cacheKey);
        if (cached != null) {
            log.debug("Cache hit: GOODS_LIST status={}", status);
            return cached;
        }
        log.debug("Cache miss: GOODS_LIST status={}, loading from repository", status);
        List<Goods> list = new java.util.ArrayList<>();
        for (Goods goods : goodsRepository.findAll()) {
            if (status.equalsIgnoreCase(goods.getStatus().name())) {
                list.add(goods);
            }
        }
        CacheKit.put(BIZ_GOODS_LIST, cacheKey, list);
        return list;
    }

    /**
     * 写入商品时清理相关缓存（防止脏读）。
     *
     * @param id 商品 ID
     */
    public void evictOnWrite(GoodsId id) {
        CacheKit.invalidate(BIZ_GOODS_DETAIL, String.valueOf(id.value()));
        CacheKit.invalidate(BIZ_GOODS_LIST, LIST_ALL_KEY);
        log.debug("Evicted GOODS_DETAIL id={} and GOODS_LIST all", id.value());
    }

    /**
     * 获取缓存统计快照（CQRS 监控用）。
     *
     * @return 缓存统计 Map
     */
    public Map<String, Object> stats() {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("domain", "goods");
        snapshot.put("detailBiz", BIZ_GOODS_DETAIL);
        snapshot.put("listBiz", BIZ_GOODS_LIST);
        return snapshot;
    }
}