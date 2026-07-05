package io.ddd4j.sample.quarkus.cqrs.goods.infrastructure;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.ddd.repository.RichRepository;
import io.ddd4j.kit.lang.StrKit;
import io.ddd4j.sample.quarkus.cqrs.goods.domain.Goods;
import io.ddd4j.sample.quarkus.cqrs.goods.domain.GoodsQuery;
import io.ddd4j.sample.quarkus.cqrs.goods.domain.GoodsRepository;
import io.ddd4j.sample.quarkus.cqrs.goods.domain.GoodsStatus;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * 基于内存的商品仓储实现（第三轨：Model/Query 快速 CRUD 模式，Quarkus CDI 风格）。
 *
 * <p>使用 {@link ConcurrentHashMap} 存储商品，并通过实现 {@link RichRepository}
 * 支持 {@link GoodsQuery} 的充血查询（{@code page()} / {@code list()} / {@code one()} / {@code count()}）。
 *
 * <p>特点：
 * <ul>
 *   <li>无 Model/PO 分离（与 rich-model 区别）：Goods 本身就是 PO，没有 OrderPO/OrderLinePO</li>
 *   <li>无 DomainObjectMapper：仓储直接操作 Goods，无映射开销</li>
 *   <li>实现 RichRepository：让 GoodsQuery 的充血查询方法可用</li>
 *   <li>{@code @ApplicationScoped}：与 Quarkus CDI 容器生命周期一致</li>
 * </ul>
 *
 * <p>本仓储必须配合 {@code GoodsConfig} 启动期注册到 ddd4j 的
 * {@link io.ddd4j.core.ddd.repository.RepositoryRegistry}，否则
 * {@link GoodsQuery#page()} 等充血查询方法无法找到仓储实例。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@ApplicationScoped
public class InMemoryGoodsRepository implements GoodsRepository, RichRepository<Goods, Long> {

    /** 内存存储：goodsId -> Goods 聚合根 */
    private final ConcurrentMap<Long, Goods> rows = new ConcurrentHashMap<>();

    // ========================= GoodsRepository =========================

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
        Goods snapshot = copy(aggregate);
        rows.put(aggregate.id(), snapshot);
        return copy(snapshot);
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

    // ========================= RichRepository =========================

    @Override
    public Optional<Goods> findFirst() {
        return rows.values().stream().findFirst().map(this::copy);
    }

    @Override
    public List<Goods> findAll() {
        return rows.values().stream().map(this::copy).collect(Collectors.toList());
    }

    @Override
    public Page<Goods> page(Query query) {
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
    public long count(Query query) {
        Objects.requireNonNull(query, "query must not be null");
        return filter(query).size();
    }

    @Override
    public Optional<Goods> findFirst(Query query) {
        Objects.requireNonNull(query, "query must not be null");
        return filter(query).stream().findFirst().map(this::copy);
    }

    @Override
    public List<Goods> findList(Query query) {
        Objects.requireNonNull(query, "query must not be null");
        return filter(query);
    }

    @Override
    public boolean update(AggregateRoot<?> aggregate, Query query) {
        // 内存实现：单条更新走 save，按条件更新简化为 find + save
        if (Objects.nonNull(aggregate) && aggregate instanceof Goods) {
            save((Goods) aggregate);
            return true;
        }
        return false;
    }

    @Override
    public boolean deleteByQuery(Query query) {
        Objects.requireNonNull(query, "query must not be null");
        List<Goods> matched = filter(query);
        matched.forEach(p -> rows.remove(p.id()));
        return !matched.isEmpty();
    }

    @Override
    public void fill(Query query, AggregateRoot<?> model) {
        // 内存实现无关联聚合，no-op
    }

    // ========================= 私有辅助方法 =========================

    /**
     * 按 {@link GoodsQuery} 条件过滤内存中的商品。
     * <p>
     * 演示 ddd4j Query 的充血过滤：业务侧只需定义查询字段，
     * 仓储实现根据字段值进行匹配。
     */
    private List<Goods> filter(Query query) {
        GoodsQuery productQuery = (query instanceof GoodsQuery) ? (GoodsQuery) query : new GoodsQuery();
        return rows.values().stream()
                .filter(p -> matches(p, productQuery))
                .sorted(orderBy(productQuery))
                .map(this::copy)
                .collect(Collectors.toList());
    }

    private boolean matches(Goods product, GoodsQuery query) {
        if (StrKit.isNotBlank(query.getCode()) && !Objects.equals(query.getCode(), product.getCode())) {
            return false;
        }
        if (StrKit.isNotBlank(query.getNameLike())
                && (Objects.isNull(product.getName()) || !product.getName().contains(query.getNameLike()))) {
            return false;
        }
        if (Objects.nonNull(query.getStatus()) && !Objects.equals(query.getStatus(), product.getStatus())) {
            return false;
        }
        if (Objects.nonNull(query.getPriceMin())
                && (Objects.isNull(product.getPrice()) || product.getPrice().compareTo(query.getPriceMin()) < 0)) {
            return false;
        }
        if (Objects.nonNull(query.getPriceMax())
                && (Objects.isNull(product.getPrice()) || product.getPrice().compareTo(query.getPriceMax()) > 0)) {
            return false;
        }
        return true;
    }

    private Comparator<Goods> orderBy(GoodsQuery query) {
        String orderBys = query.getOrderBys();
        if (StrKit.isBlank(orderBys)) {
            // 默认按 id 升序
            return Comparator.comparing(Goods::id);
        }
        // 简化支持 createTime_DESC / id_ASC 等
        String[] parts = orderBys.split(",");
        Comparator<Goods> comparator = null;
        for (String part : parts) {
            String[] tokens = part.trim().split("_");
            if (tokens.length != 2) {
                continue;
            }
            String field = tokens[0];
            boolean desc = "DESC".equalsIgnoreCase(tokens[1]);
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
            if (current == null) {
                continue;
            }
            if (desc) {
                current = current.reversed();
            }
            comparator = (comparator == null) ? current : comparator.thenComparing(current);
        }
        return comparator != null ? comparator : Comparator.comparing(Goods::id);
    }

    /**
     * 浅拷贝：避免返回内部状态的引用（防御性复制）。
     */
    private Goods copy(Goods source) {
        return new Goods(
                source.id(),
                source.getCode(),
                source.getName(),
                source.getPrice(),
                source.getStock(),
                source.getStatus(),
                source.getCreateTime(),
                source.getUpdateTime());
    }
}
