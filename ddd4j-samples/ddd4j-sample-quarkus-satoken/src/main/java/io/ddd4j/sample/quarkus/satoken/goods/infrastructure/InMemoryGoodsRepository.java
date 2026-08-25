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
package io.ddd4j.sample.quarkus.satoken.goods.infrastructure;

import java.util.Objects;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.cqrs.query.LambdaCondition;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.quarkus.satoken.goods.domain.Goods;
import io.ddd4j.sample.quarkus.satoken.goods.domain.GoodsQuery;
import io.ddd4j.sample.quarkus.satoken.goods.domain.GoodsRepository;
import io.ddd4j.sample.quarkus.satoken.goods.domain.GoodsStatus;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * 基于内存的商品仓储实现（第三轨：Model/Query 快速 CRUD 模式）。
 *
 * <p>Quarkus 下用 {@link ApplicationScoped} 取代 Spring 的 {@code @Repository} / {@code @Component}。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ApplicationScoped
public class InMemoryGoodsRepository implements GoodsRepository, Repository<Goods, Long> {

    private final ConcurrentMap<Long, Goods> rows = new ConcurrentHashMap<>();

    @Override
    public Optional<Goods> findById(Long id) {
        if (Objects.isNull(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(id)).map(this::copy);
    }

    @Override
    public Goods save(Goods aggregate) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        Objects.requireNonNull(aggregate.id(), "id must not be null");
        rows.put(aggregate.id(), copy(aggregate));
        return copy(aggregate);
    }

    @Override
    public void deleteById(Long id) {
        if (Objects.nonNull(id)) {
            rows.remove(id);
        }
    }

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

    @Override
    public List<Goods> findByStatus(GoodsStatus status) {
        if (Objects.isNull(status)) {
            return List.of();
        }
        return rows.values().stream()
                .filter(p -> status.equals(p.getStatus()))
                .map(this::copy)
                .collect(Collectors.toList());
    }

    // ========================= Repository =========================

    @Override
    public Optional<Goods> findFirst() {
        return rows.values().stream().findFirst().map(this::copy);
    }

    @Override
    public List<Goods> findAll() {
        return rows.values().stream().map(this::copy).collect(Collectors.toList());
    }

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

    @Override
    public long count(Query<Goods> query) {
        Objects.requireNonNull(query, "query must not be null");
        return filter(query).size();
    }

    @Override
    public Optional<Goods> findFirst(Query<Goods> query) {
        Objects.requireNonNull(query, "query must not be null");
        return filter(query).stream().findFirst().map(this::copy);
    }

    @Override
    public List<Goods> findList(Query<Goods> query) {
        Objects.requireNonNull(query, "query must not be null");
        return filter(query);
    }

    @Override
    public boolean update(AggregateRoot<?> aggregate, Query<Goods> query) {
        if (Objects.nonNull(aggregate) && aggregate instanceof Goods) {
            save((Goods) aggregate);
            return true;
        }
        return false;
    }

    @Override
    public boolean deleteByQuery(Query<Goods> query) {
        Objects.requireNonNull(query, "query must not be null");
        List<Goods> matched = filter(query);
        matched.forEach(p -> rows.remove(p.id()));
        return !matched.isEmpty();
    }

    @Override
    public void fill(Query<Goods> query, AggregateRoot<?> model) {
    }

    // ========================= 私有辅助方法 =========================

    private List<Goods> filter(Query<Goods> query) {
        GoodsQuery goodsQuery = (query instanceof GoodsQuery) ? (GoodsQuery) query : new GoodsQuery();
        return rows.values().stream()
                .filter(p -> matches(p, goodsQuery))
                .sorted(orderBy(goodsQuery))
                .map(this::copy)
                .collect(Collectors.toList());
    }

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

    private Comparator<Goods> orderBy(GoodsQuery query) {
        List<LambdaCondition> orderByConditions = query.getOrderByConditions();
        if (orderByConditions.isEmpty()) {
            return Comparator.comparing(Goods::id);
        }
        Comparator<Goods> comparator = null;
        for (LambdaCondition orderBy : orderByConditions) {
            String field = orderBy.property();
            boolean desc = "DESC".equalsIgnoreCase(orderBy.operator());
            Comparator<Goods> current = switch (field) {
                case "id" -> Comparator.comparing(Goods::id);
                case "createTime" -> Comparator.comparing(Goods::getCreateTime,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "updateTime" -> Comparator.comparing(Goods::getUpdateTime,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                case "price" -> Comparator.comparing(Goods::getPrice,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                default -> null;
            };
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
     * 浅拷贝：避免返回内部状态的引用（防御性复制）。
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