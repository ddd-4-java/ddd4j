package io.ddd4j.data.jpa;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.ddd.model.DomainObjectMapper;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;

import io.ddd4j.data.jpa.query.AbstractJpaQuery;
import io.ddd4j.kit.lang.BeanKit;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import static io.ddd4j.core.cqrs.query.Query.*;

/**
 * JPA 轨道 Repository 实现（标准 JPA Criteria API）。
 *
 * <p>基于 ddd4j-core 的 {@link Repository} SPI，
 * 使用 Jakarta Persistence API（EntityManager + CriteriaBuilder）实现充血查询。
 * 支持字段后缀自动映射 + 手动 Criteria 条件构建。
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * @ApplicationScoped
 * public class OrderRepository extends JpaAggregateRepository<Order, OrderPO, Long> {
 *     public OrderRepository(EntityManager em) { super(em, Order.class, OrderPO.class); }
 * }
 * }</pre>
 *
 * @param <M>  聚合根类型
 * @param <P>  持久化对象类型（@Entity PO）
 * @param <ID> 聚合根标识类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
public abstract class JpaAggregateRepository<M extends AggregateRoot<?>, P, ID extends Serializable>
        implements Repository<M, P, ID>, DomainObjectMapper<M, P>, Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(JpaAggregateRepository.class);

    protected final EntityManager em;
    protected final Class<M> modelClass;
    protected final Class<P> persistenceClass;

    protected JpaAggregateRepository(EntityManager em, Class<M> modelClass, Class<P> persistenceClass) {
        this.em = Objects.requireNonNull(em, "entityManager must not be null");
        this.modelClass = Objects.requireNonNull(modelClass, "modelClass must not be null");
        this.persistenceClass = Objects.requireNonNull(persistenceClass, "persistenceClass must not be null");
        RepositoryRegistry.register(modelClass, this);
    }

    // ========================= Repository 实现 =========================

    @Override
    public Optional<M> findById(ID id) {
        return Optional.ofNullable(em.find(persistenceClass, id)).map(this::toModel);
    }

    @Override
    public M save(M aggregate) {
        P po = toPersistenceObject(aggregate);
        if (em.contains(po)) {
            em.merge(po);
        } else {
            em.persist(po);
        }
        return aggregate;
    }

    @Override
    public void deleteById(ID id) {
        P po = em.find(persistenceClass, id);
        if (po != null) {
            em.remove(po);
        }
    }

    @Override
    public Optional<M> findFirst() {
        return Optional.ofNullable(
            em.createQuery("SELECT p FROM " + persistenceClass.getSimpleName() + " p", persistenceClass)
              .setMaxResults(1)
              .getSingleResultOrNull()
        ).map(this::toModel);
    }

    @Override
    public List<M> findAll() {
        return em.createQuery("SELECT p FROM " + persistenceClass.getSimpleName() + " p", persistenceClass)
                .getResultList()
                .stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    @Override
    public long count() {
        return em.createQuery("SELECT COUNT(p) FROM " + persistenceClass.getSimpleName() + " p", Long.class)
                .getSingleResult();
    }

    @Override
    public Page<M> page(Query<P> query) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<P> cq = cb.createQuery(persistenceClass);
        Root<P> root = cq.from(persistenceClass);

        // 构建 WHERE 条件
        buildPredicates(cb, root, query);

        // 排序

        // 查询列表
        TypedQuery<P> typedQuery = em.createQuery(cq);
        int page = (int) Math.max(0, query.getCurrent() - 1);
        int size = (int) query.getSize();
        typedQuery.setFirstResult(page * size);
        typedQuery.setMaxResults(size);
        List<M> list = typedQuery.getResultList().stream().map(this::toModel).collect(Collectors.toList());

        // 查询总数
        long total = count(query);

        return Page.succeed(list, total, query.getCurrent(), size);
    }

    @Override
    public long count(Query query) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<P> root = cq.from(persistenceClass);
        cq.select(cb.count(root));
        buildPredicates(cb, root, query);
        return em.createQuery(cq).getSingleResult();
    }

    @Override
    public Optional<M> findFirst(Query query) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<P> cq = cb.createQuery(persistenceClass);
        Root<P> root = cq.from(persistenceClass);
        buildPredicates(cb, root, query);
        TypedQuery<P> typedQuery = em.createQuery(cq).setMaxResults(1);
        return Optional.ofNullable(typedQuery.getSingleResultOrNull()).map(this::toModel);
    }

    @Override
    public List<M> findList(Query query) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<P> cq = cb.createQuery(persistenceClass);
        Root<P> root = cq.from(persistenceClass);
        buildPredicates(cb, root, query);
        return em.createQuery(cq).getResultList().stream().map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> maps(Query query) {
        // JPA 不直接支持 Map 查询，使用原生 SQL
        return em.createNativeQuery("SELECT * FROM " + persistenceClass.getSimpleName())
                .setMaxResults(500)
                .getResultList()
                .stream()
                .map(o -> {
                    Object[] row = (Object[]) o;
                    Map<String, Object> m = new HashMap<>();
                    for (int i = 0; i < row.length; i++) m.put("col_" + i, row[i]);
                    return m;
                })
                .collect(Collectors.toList());
    }

    @Override
    public boolean update(AggregateRoot<?> aggregate, Query query) {
        try {
            save((M) aggregate);
            return true;
        } catch (Exception e) {
            log.error("JPA update failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean deleteByQuery(Query query) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaDelete<P> cd = cb.createCriteriaDelete(persistenceClass);
        Root<P> root = cd.from(persistenceClass);
        buildPredicates(cb, root, query);
        return em.createQuery(cd).executeUpdate() > 0;
    }

    @Override
    public void fill(Query<P> query, AggregateRoot<?> model) {
        // 业务方按需覆盖
    }

    // ========================= 条件构建 =========================

    /**
     * 构建 JPA Criteria WHERE 条件（Lambda 条件 + 租户隔离）。
     */
    @SuppressWarnings("unchecked")
    protected void buildPredicates(CriteriaBuilder cb, Root<P> root, Query query) {
        List<Predicate> predicates = new ArrayList<>();

        // 租户隔离
        String tenantId = ThreadContext.get(ContextConstants.TENANT_ID);
        if (tenantId != null && !query.isIgnoreTenantId()) {
            try {
                predicates.add(cb.equal(root.get("tenantId"), tenantId));
            } catch (Exception ignored) {
            }
        }

        // Query 中直接定义的 Lambda 条件
        for (Object obj : query.getWhereConditions()) {
            io.ddd4j.core.cqrs.query.LambdaCondition condition = (io.ddd4j.core.cqrs.query.LambdaCondition) obj;
            buildLambdaPredicate(cb, root, predicates, condition);
        }

        // Lambda 排序
        if (query.hasOrderBy()) {
            List<Order> orders = new ArrayList<>();
            for (Object obj : query.getOrderByConditions()) {
                io.ddd4j.core.cqrs.query.LambdaCondition orderBy = (io.ddd4j.core.cqrs.query.LambdaCondition) obj;
                if ("ASC".equals(orderBy.operator())) {
                    orders.add(cb.asc(root.get(orderBy.property())));
                } else {
                    orders.add(cb.desc(root.get(orderBy.property())));
                }
            }
            try {
                ((CriteriaQuery<?>) root.getParent()).orderBy(orders);
            } catch (ClassCastException ignored) {
            }
        }

        if (!predicates.isEmpty()) {
            try {
                ((CriteriaQuery<?>) root.getParent()).where(predicates.toArray(new Predicate[0]));
            } catch (ClassCastException e) {
                // CriteriaDelete
            }
        }
    }

    /**
     * 将 ddd4j LambdaCondition 转换为 JPA Criteria Predicate。
     */
    @SuppressWarnings("unchecked")
    private void buildLambdaPredicate(CriteriaBuilder cb, Root<P> root, List<Predicate> predicates,
                                      io.ddd4j.core.cqrs.query.LambdaCondition condition) {
        String property = condition.property();
        switch (condition.operator()) {
            case "=" -> predicates.add(cb.equal(root.get(property), condition.value()));
            case "<>" -> predicates.add(cb.notEqual(root.get(property), condition.value()));
            case ">" -> predicates.add(cb.greaterThan(root.get(property), (Comparable) condition.value()));
            case ">=" -> predicates.add(cb.greaterThanOrEqualTo(root.get(property), (Comparable) condition.value()));
            case "<" -> predicates.add(cb.lessThan(root.get(property), (Comparable) condition.value()));
            case "<=" -> predicates.add(cb.lessThanOrEqualTo(root.get(property), (Comparable) condition.value()));
            case "LIKE" -> predicates.add(cb.like(root.get(property), "%" + condition.value() + "%"));
            case "LIKE_LEFT" -> predicates.add(cb.like(root.get(property), condition.value() + "%"));
            case "LIKE_RIGHT" -> predicates.add(cb.like(root.get(property), "%" + condition.value()));
            case "NOT_LIKE" -> predicates.add(cb.notLike(root.get(property), "%" + condition.value() + "%"));
            case "IN" -> predicates.add(root.get(property).in(toCollection(condition.value())));
            case "NOT_IN" -> predicates.add(root.get(property).in(toCollection(condition.value())).not());
            case "IS_NULL" -> predicates.add(cb.isNull(root.get(property)));
            case "IS_NOT_NULL" -> predicates.add(cb.isNotNull(root.get(property)));
        }
    }

    private Collection<?> toCollection(Object value) {
        if (value instanceof Collection<?> col) return col;
        if (value instanceof String str && !str.isEmpty()) return Arrays.asList(str.split(","));
        return Collections.emptyList();
    }

    // ========================= DomainObjectMapper 实现 =========================

    @Override
    public M toModel(P persistenceObject) {
        if (persistenceObject == null) return null;
        return BeanKit.copy(persistenceObject, modelClass);
    }

    @Override
    @SuppressWarnings("unchecked")
    public P toPersistenceObject(M model) {
        if (model == null) return null;
        return BeanKit.copy(model, persistenceClass);
    }
}
