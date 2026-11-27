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
package io.ddd4j.sample.javalin.shiro.goods.infrastructure;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.cqrs.query.LambdaCondition;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.javalin.shiro.goods.domain.Goods;
import io.ddd4j.sample.javalin.shiro.goods.domain.GoodsQuery;
import io.ddd4j.sample.javalin.shiro.goods.domain.GoodsRepository;
import io.ddd4j.sample.javalin.shiro.goods.domain.GoodsStatus;

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

    /**
     * 按主键保存当前内存仓储中可读写的示例记录。
     */
    private final ConcurrentMap<Long, Goods> rows = new ConcurrentHashMap<>();

    /**
     * 按主键在当前内存仓储查找对应聚合。
     *
     * @param id 待查询或移除的业务记录标识
     * @return 已找到的聚合，不存在时返回空结果
     */
    @Override
    public Optional<Goods> findById(Long id) {
        if (Objects.isNull(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(id)).map(this::copy);
    }

    /**
     * 写入或更新给定的领域对象或存储记录。
     *
     * <p>此实现调用 <code>aggregate.id()</code>。</p>
     *
     * @param aggregate 待处理聚合根
     * @return 实际返回<code>copy(aggregate)</code>
     */
    @Override
    public Goods save(Goods aggregate) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        Objects.requireNonNull(aggregate.id(), "id must not be null");
        rows.put(aggregate.id(), copy(aggregate));
        return copy(aggregate);
    }

    /**
     * 按主键从当前内存仓储移除对应记录。
     *
     * @param id 待查询或移除的业务记录标识
     */
    @Override
    public void deleteById(Long id) {
        if (Objects.nonNull(id)) {
            rows.remove(id);
        }
    }

    /**
     * 按业务编码查找对应记录。
     *
     * <p>此实现调用 <code>StrKit.isBlank(code)</code>。</p>
     *
     * @param code 商品编码
     * @return 实际返回<code>Optional.empty()</code>
     */
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

    /**
     * 查询匹配指定状态的记录。
     *
     * <p>此实现调用 <code>Collections.emptyList()</code>。</p>
     *
     * @param status 商品状态
     * @return 实际返回<code>Collections.emptyList()</code>
     */
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

    /**
     * 读取满足条件的第一条记录。
     *
     * <p>此实现调用 <code>rows.values().stream().findFirst()</code>。</p>
     *
     * @return 第一个匹配条件的结果
     */
    @Override
    public Optional<Goods> findFirst() {
        return rows.values().stream().findFirst().map(this::copy);
    }

    /**
     * 查询存储中符合条件的记录。
     *
     * <p>此实现调用 <code>rows.values().stream()</code>。</p>
     *
     * @return 满足条件的结果列表
     */
    @Override
    public List<Goods> findAll() {
        return rows.values().stream().map(this::copy).collect(Collectors.toList());
    }

    /**
     * 根据分页参数和查询条件读取结果。
     *
     * <p>此实现调用 <code>filter(query)</code>。</p>
     *
     * @param query 查询条件
     * @return 实际返回<code>Page.succeed(pageData, total, current, size)</code>
     */
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

    /**
     * 统计符合查询范围的数据数量。
     *
     * <p>此实现调用 <code>filter(query).size()</code>。</p>
     *
     * @param query 查询条件
     * @return 满足筛选条件的记录数量
     */
    @Override
    public long count(Query<Goods> query) {
        Objects.requireNonNull(query, "query must not be null");
        return filter(query).size();
    }

    /**
     * 读取满足条件的第一条记录。
     *
     * <p>此实现调用 <code>filter(query).stream().findFirst()</code>。</p>
     *
     * @param query 查询条件
     * @return 第一个匹配条件的结果
     */
    @Override
    public Optional<Goods> findFirst(Query<Goods> query) {
        Objects.requireNonNull(query, "query must not be null");
        return filter(query).stream().findFirst().map(this::copy);
    }

    /**
     * 按查询条件读取记录列表。
     *
     * <p>此实现调用 <code>filter(query)</code>。</p>
     *
     * @param query 查询条件
     * @return 满足条件的结果列表
     */
    @Override
    public List<Goods> findList(Query<Goods> query) {
        Objects.requireNonNull(query, "query must not be null");
        return filter(query);
    }

    /**
     * 更新已经存在的记录。
     *
     * <p>此实现调用 <code>save((Goods)aggregate)</code>。</p>
     *
     * @param aggregate 待处理聚合根
     * @param query     查询条件
     * @return 根据执行路径返回<code>true</code>、<code>false</code>
     */
    @Override
    public boolean update(AggregateRoot<?> aggregate, Query<Goods> query) {
        if (Objects.nonNull(aggregate) && aggregate instanceof Goods) {
            save((Goods) aggregate);
            return true;
        }
        return false;
    }

    /**
     * 删除匹配查询条件的记录。
     *
     * <p>此实现调用 <code>filter(query)</code>。</p>
     *
     * @param query 查询条件
     * @return 实际返回<code>!matched.isEmpty()</code>
     */
    @Override
    public boolean deleteByQuery(Query<Goods> query) {
        Objects.requireNonNull(query, "query must not be null");
        List<Goods> matched = filter(query);
        matched.forEach(p -> rows.remove(p.id()));
        return !matched.isEmpty();
    }

    /**
     * 接收<code>fill</code>回调，当前实现无需在此步骤更新状态。
     *
     * @param query 查询条件
     * @param model 待填充聚合根
     */
    @Override
    public void fill(Query<Goods> query, AggregateRoot<?> model) {
    }

    // ========================= 私有辅助方法 =========================

    /**
     * 从内存商品记录中按查询条件筛选并应用排序。
     *
     * @param query 待应用的商品筛选和排序条件
     * @return 满足条件且按配置排序的商品流
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
     * 根据商品查询的编码、名称、价格或状态条件判定记录是否可见。
     *
     * @param goods 需要与查询条件比较的商品
     * @param query 待应用的商品筛选和排序条件
     * @return 当前商品满足已提供条件时返回 true
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
     * 构造商品结果的组合比较器，没有排序条件时按主键顺序。
     *
     * @param query 待应用的商品筛选和排序条件
     * @return 用于整理内存查询结果的比较器
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
     * 浅拷贝：避免返回内部状态的引用（防御性复制）。
     *
     * @param source 被复制的商品对象
     * @return 独立的商品副本
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
