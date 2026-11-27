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
package io.ddd4j.sample.javalin.satoken.goods.infrastructure;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.cqrs.query.LambdaCondition;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.javalin.satoken.goods.domain.Goods;
import io.ddd4j.sample.javalin.satoken.goods.domain.GoodsQuery;
import io.ddd4j.sample.javalin.satoken.goods.domain.GoodsRepository;
import io.ddd4j.sample.javalin.satoken.goods.domain.GoodsStatus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * 基于内存的商品仓储实现（第三轨：Model/Query 快速 CRUD 模式）。
 *
 * <p>Javalin 运行时由应用入口手动装配仓储。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class InMemoryGoodsRepository implements GoodsRepository, Repository<Goods, Long> {

    /** \u6309\u4e3b\u952e\u4fdd\u5b58\u5f53\u524d\u5185\u5b58\u4ed3\u50a8\u4e2d\u53ef\u8bfb\u5199\u7684\u793a\u4f8b\u8bb0\u5f55\u3002 */
    private final ConcurrentMap<Long, Goods> rows = new ConcurrentHashMap<>();

    /**
     * \u6309\u4e3b\u952e\u5728\u5f53\u524d\u5185\u5b58\u4ed3\u50a8\u67e5\u627e\u5bf9\u5e94\u805a\u5408\u3002
     * @param id \u5f85\u67e5\u8be2\u6216\u79fb\u9664\u7684\u4e1a\u52a1\u8bb0\u5f55\u6807\u8bc6
     * @return \u5df2\u627e\u5230\u7684\u805a\u5408\uff0c\u4e0d\u5b58\u5728\u65f6\u8fd4\u56de\u7a7a\u7ed3\u679c
     */
    @Override
    public Optional<Goods> findById(Long id) {
        if (Objects.isNull(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(id)).map(this::copy);
    }

    /** {@inheritDoc} */
    @Override
    public Goods save(Goods aggregate) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        Objects.requireNonNull(aggregate.id(), "id must not be null");
        rows.put(aggregate.id(), copy(aggregate));
        return copy(aggregate);
    }

    /**
     * \u6309\u4e3b\u952e\u4ece\u5f53\u524d\u5185\u5b58\u4ed3\u50a8\u79fb\u9664\u5bf9\u5e94\u8bb0\u5f55\u3002
     * @param id \u5f85\u67e5\u8be2\u6216\u79fb\u9664\u7684\u4e1a\u52a1\u8bb0\u5f55\u6807\u8bc6
     */
    @Override
    public void deleteById(Long id) {
        if (Objects.nonNull(id)) {
            rows.remove(id);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Goods> findByCode(String code) {
        if (StrKit.isBlank(code)) {
            return Optional.empty();
        }
        return rows.values().stream()
                .filter(p -> Objects.equals(code, p.getCode()))
                .findFirst()
                .map(this::copy);
    }

    /** {@inheritDoc} */
    @Override
    public List<Goods> findByStatus(GoodsStatus status) {
        if (Objects.isNull(status)) {
            return Collections.emptyList();
        }
        return rows.values().stream()
                .filter(p -> status.equals(p.getStatus()))
                .map(this::copy)
                .collect(Collectors.toList());
    }

    // ========================= Repository =========================

    /** {@inheritDoc} */
    @Override
    public Optional<Goods> findFirst() {
        return rows.values().stream().findFirst().map(this::copy);
    }

    /** {@inheritDoc} */
    @Override
    public List<Goods> findAll() {
        return rows.values().stream().map(this::copy).collect(Collectors.toList());
    }

    /** {@inheritDoc} */
    @Override
    public Page<Goods> page(Query<Goods> query) {
        Objects.requireNonNull(query, "query must not be null");
        List<Goods> filtered = filter(query);
        long total = filtered.size();
        long current = query.getCurrent() <= 0 ? 1 : query.getCurrent();
        long size = query.getSize() <= 0 ? 10 : query.getSize();
        long startIndex = (current - 1) * size;

        List<Goods> pageData = new ArrayList<>();
        if (startIndex < total) {
            long endIndex = Math.min(startIndex + size, total);
            pageData = new ArrayList<>(filtered.subList((int) startIndex, (int) endIndex));
        }
        return Page.succeed(pageData, total, current, size);
    }

    /** {@inheritDoc} */
    @Override
    public long count(Query<Goods> query) {
        Objects.requireNonNull(query, "query must not be null");
        return filter(query).size();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Goods> findFirst(Query<Goods> query) {
        Objects.requireNonNull(query, "query must not be null");
        return filter(query).stream().findFirst().map(this::copy);
    }

    /** {@inheritDoc} */
    @Override
    public List<Goods> findList(Query<Goods> query) {
        Objects.requireNonNull(query, "query must not be null");
        return filter(query);
    }

    /** {@inheritDoc} */
    @Override
    public boolean update(AggregateRoot<?> aggregate, Query<Goods> query) {
        if (Objects.nonNull(aggregate) && aggregate instanceof Goods) {
            save((Goods) aggregate);
            return true;
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean deleteByQuery(Query<Goods> query) {
        Objects.requireNonNull(query, "query must not be null");
        List<Goods> matched = filter(query);
        matched.forEach(p -> rows.remove(p.id()));
        return !matched.isEmpty();
    }

    /** {@inheritDoc} */
    @Override
    public void fill(Query<Goods> query, AggregateRoot<?> model) {
    }

    // ========================= 私有辅助方法 =========================

    /**
     * \u4ece\u5185\u5b58\u5546\u54c1\u8bb0\u5f55\u4e2d\u6309\u67e5\u8be2\u6761\u4ef6\u7b5b\u9009\u5e76\u5e94\u7528\u6392\u5e8f\u3002
     * @param query \u5f85\u5e94\u7528\u7684\u5546\u54c1\u7b5b\u9009\u548c\u6392\u5e8f\u6761\u4ef6
     * @return \u6ee1\u8db3\u6761\u4ef6\u4e14\u6309\u914d\u7f6e\u6392\u5e8f\u7684\u5546\u54c1\u6d41
     */
    private List<Goods> filter(Query<Goods> query) {
        GoodsQuery goodsQuery = (query instanceof GoodsQuery) ? (GoodsQuery) query : new GoodsQuery();
        return rows.values().stream()
                .filter(p -> matches(p, goodsQuery))
                .sorted(orderBy(goodsQuery))
                .map(this::copy)
                .collect(Collectors.toList());
    }

    /**
     * \u6839\u636e\u5546\u54c1\u67e5\u8be2\u7684\u7f16\u7801\u3001\u540d\u79f0\u3001\u4ef7\u683c\u6216\u72b6\u6001\u6761\u4ef6\u5224\u5b9a\u8bb0\u5f55\u662f\u5426\u53ef\u89c1\u3002
     * @param goods \u9700\u8981\u4e0e\u67e5\u8be2\u6761\u4ef6\u6bd4\u8f83\u7684\u5546\u54c1
     * @param query \u5f85\u5e94\u7528\u7684\u5546\u54c1\u7b5b\u9009\u548c\u6392\u5e8f\u6761\u4ef6
     * @return \u5f53\u524d\u5546\u54c1\u6ee1\u8db3\u5df2\u63d0\u4f9b\u6761\u4ef6\u65f6\u8fd4\u56de true
     */
    private boolean matches(Goods goods, GoodsQuery query) {
        if (StrKit.isNotBlank(query.getCode()) && !Objects.equals(query.getCode(), goods.getCode())) {
            return false;
        }
        if (StrKit.isNotBlank(query.getNameLike())
                && (Objects.isNull(goods.getName()) || !goods.getName().contains(query.getNameLike()))) {
            return false;
        }
        if (Objects.nonNull(query.getStatus()) && !Objects.equals(query.getStatus(), goods.getStatus())) {
            return false;
        }
        if (Objects.nonNull(query.getPriceMin())
                && (Objects.isNull(goods.getPrice()) || goods.getPrice().compareTo(query.getPriceMin()) < 0)) {
            return false;
        }
        if (Objects.nonNull(query.getPriceMax())
                && (Objects.isNull(goods.getPrice()) || goods.getPrice().compareTo(query.getPriceMax()) > 0)) {
            return false;
        }
        return true;
    }

    /**
     * \u6784\u9020\u5546\u54c1\u7ed3\u679c\u7684\u7ec4\u5408\u6bd4\u8f83\u5668\uff0c\u6ca1\u6709\u6392\u5e8f\u6761\u4ef6\u65f6\u6309\u4e3b\u952e\u987a\u5e8f\u3002
     * @param query \u5f85\u5e94\u7528\u7684\u5546\u54c1\u7b5b\u9009\u548c\u6392\u5e8f\u6761\u4ef6
     * @return \u7528\u4e8e\u6574\u7406\u5185\u5b58\u67e5\u8be2\u7ed3\u679c\u7684\u6bd4\u8f83\u5668
     */
    private Comparator<Goods> orderBy(GoodsQuery query) {
        List<LambdaCondition> orderByConditions = query.getOrderByConditions();
        if (orderByConditions.isEmpty()) {
            return Comparator.comparing(Goods::id);
        }
        Comparator<Goods> comparator = null;
        for (LambdaCondition orderBy : orderByConditions) {
            String field = orderBy.property();
            boolean desc = "DESC".equalsIgnoreCase(orderBy.operator());
            Comparator<Goods> current;
            switch (field) {
                case "id":
                    current = Comparator.comparing(Goods::id);
                    break;
                case "createTime":
                    current = Comparator.comparing(Goods::getCreateTime,
                            Comparator.nullsLast(Comparator.naturalOrder()));
                    break;
                case "updateTime":
                    current = Comparator.comparing(Goods::getUpdateTime,
                            Comparator.nullsLast(Comparator.naturalOrder()));
                    break;
                case "price":
                    current = Comparator.comparing(Goods::getPrice,
                            Comparator.nullsLast(Comparator.naturalOrder()));
                    break;
                default:
                    current = null;
                    break;
            }
            if (Objects.isNull(current)) {
                continue;
            }
            if (desc) {
                current = current.reversed();
            }
            comparator = (Objects.isNull(comparator)) ? current : comparator.thenComparing(current);
        }
        return Objects.nonNull(comparator) ? comparator : Comparator.comparing(Goods::id);
    }

    /**
     * \u6d45\u62f7\u8d1d\uff1a\u907f\u514d\u8fd4\u56de\u5185\u90e8\u72b6\u6001\u7684\u5f15\u7528\uff08\u9632\u5fa1\u6027\u590d\u5236\uff09\u3002
     *
     * @param source \u88ab\u590d\u5236\u7684\u5546\u54c1\u5bf9\u8c61
     * @return \u72ec\u7acb\u7684\u5546\u54c1\u526f\u672c
     */
    private Goods copy(Goods source) {
        return Goods.builder()
                .id(source.getId())
                .code(source.getCode())
                .name(source.getName())
                .price(source.getPrice())
                .stock(source.getStock())
                .status(source.getStatus())
                .createTime(source.getCreateTime())
                .updateTime(source.getUpdateTime())
                .build();
    }
}
