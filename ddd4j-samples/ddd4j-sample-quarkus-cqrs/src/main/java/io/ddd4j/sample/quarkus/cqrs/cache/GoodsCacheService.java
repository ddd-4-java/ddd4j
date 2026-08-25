/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ddd4j.sample.quarkus.cqrs.cache;

import java.util.Objects;

import io.ddd4j.cache.CacheKit;
import io.ddd4j.sample.quarkus.cqrs.goods.domain.Goods;
import io.ddd4j.sample.quarkus.cqrs.goods.domain.GoodsId;
import io.ddd4j.sample.quarkus.cqrs.goods.infrastructure.InMemoryGoodsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 商品缓存服务（CQRS 读侧增强 - Quarkus）。
 *
 * <p>基于 ddd4j-cache {@link CacheKit}，提供：
 * <ul>
 *   <li>{@code GOODS_DETAIL}：商品详情缓存</li>
 *   <li>{@code GOODS_LIST}：商品列表快照</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@ApplicationScoped
public class GoodsCacheService {

    /**
     * 缓存域：商品详情
     */
    public static final String BIZ_GOODS_DETAIL = "quarkus-cqrs-goods-detail";
    /**
     * 缓存域：商品列表
     */
    public static final String BIZ_GOODS_LIST = "quarkus-cqrs-goods-list";

    /**
     * 全部商品列表固定 key
     */
    private static final String LIST_ALL_KEY = "all";

    private final InMemoryGoodsRepository goodsRepository;

    /**
     * 构造函数。
     *
     * @param goodsRepository 内存商品仓储
     */
    @Inject
    public GoodsCacheService(InMemoryGoodsRepository goodsRepository) {
        this.goodsRepository = Objects.requireNonNull(goodsRepository, "goodsRepository must not be null");
    }

    /**
     * 按 ID 读取商品（缓存优先）。
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
        log.debug("Cache miss: GOODS_DETAIL id={}", id);
        Goods goods = goodsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("goods not found: " + id));
        CacheKit.put(BIZ_GOODS_DETAIL, cacheKey, goods);
        return goods;
    }

    /**
     * 读取全部商品列表（缓存优先）。
     *
     * @return 商品列表
     */
    @SuppressWarnings("unchecked")
    public List<Goods> listAll() {
        Object cached = CacheKit.get(BIZ_GOODS_LIST, LIST_ALL_KEY);
        if (cached instanceof List) {
            log.debug("Cache hit: GOODS_LIST all");
            return new ArrayList<>((List<Goods>) cached);
        }
        log.debug("Cache miss: GOODS_LIST all, loading from repository");
        List<Goods> all = new ArrayList<>(goodsRepository.findAll());
        CacheKit.put(BIZ_GOODS_LIST, LIST_ALL_KEY, all);
        return all;
    }

    /**
     * 按状态读取商品（缓存优先）。
     *
     * @param status 状态字符串
     * @return 商品列表
     */
    @SuppressWarnings("unchecked")
    public List<Goods> listByStatus(String status) {
        String cacheKey = "status:" + status;
        List<Goods> cached = (List<Goods>) CacheKit.get(BIZ_GOODS_LIST, cacheKey);
        if (Objects.nonNull(cached)) {
            log.debug("Cache hit: GOODS_LIST status={}", status);
            return cached;
        }
        log.debug("Cache miss: GOODS_LIST status={}", status);
        List<Goods> list = new ArrayList<>();
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
    }

    /**
     * 缓存统计快照（CQRS 监控）。
     *
     * @return 统计 Map
     */
    public Map<String, Object> stats() {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("domain", "goods");
        snapshot.put("detailBiz", BIZ_GOODS_DETAIL);
        snapshot.put("listBiz", BIZ_GOODS_LIST);
        return snapshot;
    }
}