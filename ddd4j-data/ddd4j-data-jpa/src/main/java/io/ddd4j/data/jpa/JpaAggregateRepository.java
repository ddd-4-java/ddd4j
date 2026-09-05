package io.ddd4j.data.jpa;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.cqrs.query.LambdaCondition;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.ddd.model.DomainObjectMapper;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.kit.lang.BeanKit;
import io.ddd4j.kit.lang.StrKit;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.Tuple;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 标准 Jakarta Persistence 聚合仓储适配器。
 *
 * <p>业务 Query 只引用领域模型属性，本适配器将 ORM 无关的
 * {@link LambdaCondition} 编译为 JPA Criteria。实现只依赖 Jakarta
 * Persistence API，不依赖 Hibernate、Spring Data 或 Quarkus Panache。
 *
 * @param <M>  聚合根类型
 * @param <P>  JPA 持久化对象类型
 * @param <ID> 聚合根标识类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
@Slf4j
public abstract class JpaAggregateRepository<M extends AggregateRoot<?>, P, ID extends Serializable>
        implements Repository<M, ID>, DomainObjectMapper<M, P>, Serializable {

    private static final long serialVersionUID = 1L;

    protected final EntityManager entityManager;
    protected final Class<M> modelClass;
    protected final Class<P> persistenceClass;

    protected JpaAggregateRepository(EntityManager entityManager, Class<M> modelClass, Class<P> persistenceClass) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
        this.modelClass = Objects.requireNonNull(modelClass, "modelClass must not be null");
        this.persistenceClass = Objects.requireNonNull(persistenceClass, "persistenceClass must not be null");
        RepositoryRegistry.register(modelClass, this);
    }

    @Override
    public Optional<M> findById(ID id) {
        if (Objects.isNull(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(entityManager.find(persistenceClass, id)).map(this::toModel);
    }

    @Override
    public M save(M aggregate) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        P managed = entityManager.merge(toPersistenceObject(aggregate));
        return toModel(managed);
    }

    @Override
    public void deleteById(ID id) {
        if (Objects.isNull(id)) {
            return;
        }
        P persistenceObject = entityManager.find(persistenceClass, id);
        if (Objects.nonNull(persistenceObject)) {
            entityManager.remove(persistenceObject);
        }
    }

    @Override
    public List<M> findByIds(Collection<ID> ids) {
        if (Objects.isNull(ids) || ids.isEmpty()) {
            return Collections.emptyList();
        }
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<P> cq = cb.createQuery(persistenceClass);
        Root<P> root = cq.from(persistenceClass);
        cq.where(root.<ID>get("id").in(ids));
        return convert(entityManager.createQuery(cq).getResultList());
    }

    @Override
    public List<M> saveBatch(Collection<M> aggregates) {
        if (Objects.isNull(aggregates) || aggregates.isEmpty()) {
            return Collections.emptyList();
        }
        List<M> result = new ArrayList<>(aggregates.size());
        for (M aggregate : aggregates) {
            P managed = entityManager.merge(toPersistenceObject(aggregate));
            result.add(toModel(managed));
        }
        entityManager.flush();
        return result;
    }

    @Override
    public int updateBatchById(Collection<M> aggregates) {
        if (Objects.isNull(aggregates) || aggregates.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (M aggregate : aggregates) {
            entityManager.merge(toPersistenceObject(aggregate));
            count++;
        }
        entityManager.flush();
        return count;
    }

    @Override
    public int insertOrUpdateBatch(Collection<M> aggregates) {
        return updateBatchById(aggregates);
    }

    @Override
    public int deleteByIds(Collection<ID> ids) {
        if (Objects.isNull(ids) || ids.isEmpty()) {
            return 0;
        }
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaDelete<P> cd = cb.createCriteriaDelete(persistenceClass);
        Root<P> root = cd.from(persistenceClass);
        cd.where(root.<ID>get("id").in(ids));
        return entityManager.createQuery(cd).executeUpdate();
    }

    @Override
    public Optional<M> findFirst() {
        java.util.List<P> __ps = entityManager
                .createQuery("SELECT p FROM " + persistenceClass.getSimpleName() + " p", persistenceClass)
                .setMaxResults(1)
                .getResultList();
        return __ps.isEmpty() ? java.util.Optional.empty() : java.util.Optional.ofNullable(this.toModel(__ps.get(0)));
    }

    @Override
    public List<M> findAll() {
        return convert(entityManager
                .createQuery("SELECT p FROM " + persistenceClass.getSimpleName() + " p", persistenceClass)
                .getResultList());
    }

    @Override
    public long count() {
        Long result = entityManager
                .createQuery("SELECT COUNT(p) FROM " + persistenceClass.getSimpleName() + " p", Long.class)
                .getSingleResult();
        return Objects.nonNull(result) ? result : 0L;
    }

    @Override
    public Page<M> page(Query<M> query) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<P> criteriaQuery = criteriaBuilder.createQuery(persistenceClass);
        Root<P> root = criteriaQuery.from(persistenceClass);
        applyWhere(criteriaQuery, buildPredicates(criteriaBuilder, root, query));
        applyOrder(criteriaQuery, criteriaBuilder, root, query);

        TypedQuery<P> typedQuery = entityManager.createQuery(criteriaQuery);
        long current = Math.max(1L, query.getCurrent());
        if (query.getSize() >= 0L) {
            int size = Math.toIntExact(query.getSize());
            typedQuery.setFirstResult(Math.toIntExact((current - 1L) * query.getSize()));
            typedQuery.setMaxResults(size);
        }
        List<M> records = convert(typedQuery.getResultList());
        return Page.succeed(records, count(query), current, query.getSize());
    }

    @Override
    public long count(Query<M> query) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> criteriaQuery = criteriaBuilder.createQuery(Long.class);
        Root<P> root = criteriaQuery.from(persistenceClass);
        criteriaQuery.select(criteriaBuilder.count(root));
        applyWhere(criteriaQuery, buildPredicates(criteriaBuilder, root, query));
        Long result = entityManager.createQuery(criteriaQuery).getSingleResult();
        return Objects.nonNull(result) ? result : 0L;
    }

    @Override
    public Optional<M> findFirst(Query<M> query) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<P> criteriaQuery = criteriaBuilder.createQuery(persistenceClass);
        Root<P> root = criteriaQuery.from(persistenceClass);
        applyWhere(criteriaQuery, buildPredicates(criteriaBuilder, root, query));
        applyOrder(criteriaQuery, criteriaBuilder, root, query);
        java.util.List<P> __rs = entityManager.createQuery(criteriaQuery)
                .setMaxResults(1)
                .getResultList();
        return __rs.isEmpty() ? java.util.Optional.empty() : java.util.Optional.ofNullable(this.toModel(__rs.get(0)));
    }

    @Override
    public List<M> findList(Query<M> query) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<P> criteriaQuery = criteriaBuilder.createQuery(persistenceClass);
        Root<P> root = criteriaQuery.from(persistenceClass);
        applyWhere(criteriaQuery, buildPredicates(criteriaBuilder, root, query));
        applyOrder(criteriaQuery, criteriaBuilder, root, query);
        return convert(entityManager.createQuery(criteriaQuery).getResultList());
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> maps(Query<M> query) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<P> root = cq.from(persistenceClass);
        cq.multiselect(root.get("id").alias("id"), root.get("tenantId").alias("tenantId"));
        applyWhere(cq, buildPredicates(cb, root, query));
        applyOrderTuple(cq, cb, root, query);
        TypedQuery<Tuple> typedQuery = entityManager.createQuery(cq);
        long current = Math.max(1L, query.getCurrent());
        if (query.getSize() >= 0L) {
            int size = Math.toIntExact(query.getSize());
            typedQuery.setFirstResult(Math.toIntExact((current - 1L) * query.getSize()));
            typedQuery.setMaxResults(size);
        }
        return typedQuery.getResultList().stream()
                .map(tuple -> {
                    Map<String, Object> map = new java.util.LinkedHashMap<>();
                    for (jakarta.persistence.TupleElement<?> element : tuple.getElements()) {
                        map.put(element.getAlias(), tuple.get(element));
                    }
                    return map;
                })
                .collect(Collectors.toList());
    }

    @Override
    public boolean deleteByQuery(Query<M> query) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaDelete<P> criteriaDelete = criteriaBuilder.createCriteriaDelete(persistenceClass);
        Root<P> root = criteriaDelete.from(persistenceClass);
        List<Predicate> predicates = buildPredicates(criteriaBuilder, root, query);
        if (predicates.isEmpty()) {
            throw new IllegalArgumentException("Refusing to delete without query predicates");
        }
        criteriaDelete.where(predicates.toArray(new Predicate[0]));
        return entityManager.createQuery(criteriaDelete).executeUpdate() > 0;
    }

    @Override
    public void fill(Query<M> query, AggregateRoot<?> model) {
        // 业务仓储按需覆盖
    }

    /**
     * 将领域属性名映射为 JPA 持久化属性名。
     *
     * <p>默认同名；当 Domain 与 PO 命名不同时由具体仓储覆盖。本方法返回
     * JPA property，而不是数据库 column。
     */
    protected String persistenceProperty(String domainProperty) {
        return domainProperty;
    }

    /**
     * 根据 Query 属性空间选择领域映射或 PO 直接属性，并校验类型边界。
     */
    protected String persistenceProperty(LambdaCondition condition) {
        Objects.requireNonNull(condition, "condition must not be null");
        condition.propertyRef().requireCompatible(modelClass, persistenceClass);
        if (condition.propertyRef().isPersistence()) {
            return condition.property();
        }
        return persistenceProperty(condition.property());
    }

    protected List<Predicate> buildPredicates(CriteriaBuilder criteriaBuilder, Root<P> root, Query<M> query) {
        List<Predicate> predicates = new ArrayList<>();
        if (!query.isIgnoreTenantId()) {
            addContextPredicate(criteriaBuilder, root, predicates, "tenantId",
                    ThreadContext.get(ContextConstants.TENANT_ID));
        }
        addContextPredicate(criteriaBuilder, root, predicates, "systemId",
                ThreadContext.get(ContextConstants.SYSTEM_ID));

        for (LambdaCondition condition : query.getWhereConditions()) {
            buildPredicate(criteriaBuilder, root, predicates, condition);
        }
        return predicates;
    }

    private void addContextPredicate(CriteriaBuilder criteriaBuilder, Root<P> root,
                                     List<Predicate> predicates, String property, String value) {
        if (Objects.isNull(value)) {
            return;
        }
        try {
            predicates.add(criteriaBuilder.equal(root.get(property), value));
        } catch (IllegalArgumentException exception) {
            log.debug("Persistence type {} has no {} property", persistenceClass.getName(), property);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void buildPredicate(CriteriaBuilder criteriaBuilder, Root<P> root,
                                List<Predicate> predicates, LambdaCondition condition) {
        String property = persistenceProperty(condition);
        switch (condition.operator()) {
            case "=":
                predicates.add(criteriaBuilder.equal(root.get(property), condition.value()));
                break;
            case "<>":
                predicates.add(criteriaBuilder.notEqual(root.get(property), condition.value()));
                break;
            case ">":
                predicates.add(criteriaBuilder.greaterThan(root.get(property), (Comparable) condition.value()));
                break;
            case ">=":
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get(property), (Comparable) condition.value()));
                break;
            case "<":
                predicates.add(criteriaBuilder.lessThan(root.get(property), (Comparable) condition.value()));
                break;
            case "<=":
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get(property), (Comparable) condition.value()));
                break;
            case "LIKE":
                predicates.add(criteriaBuilder.like(root.get(property), "%" + condition.value() + "%"));
                break;
            case "LIKE_LEFT":
                predicates.add(criteriaBuilder.like(root.get(property), condition.value() + "%"));
                break;
            case "LIKE_RIGHT":
                predicates.add(criteriaBuilder.like(root.get(property), "%" + condition.value()));
                break;
            case "NOT_LIKE":
                predicates.add(criteriaBuilder.notLike(root.get(property), "%" + condition.value() + "%"));
                break;
            case "IN":
                predicates.add(root.get(property).in(toCollection(condition.value())));
                break;
            case "NOT_IN":
                predicates.add(root.get(property).in(toCollection(condition.value())).not());
                break;
            case "IS_NULL":
                predicates.add(criteriaBuilder.isNull(root.get(property)));
                break;
            case "IS_NOT_NULL":
                predicates.add(criteriaBuilder.isNotNull(root.get(property)));
                break;
            default:
                throw new IllegalArgumentException("Unsupported query operator: " + condition.operator());
        }
    }

    private void applyOrder(CriteriaQuery<P> criteriaQuery, CriteriaBuilder criteriaBuilder,
                            Root<P> root, Query<M> query) {
        List<Order> orders = new ArrayList<>();
        for (LambdaCondition condition : query.getOrderByConditions()) {
            String property = persistenceProperty(condition);
            if (Objects.equals("ASC", condition.operator())) {
                orders.add(criteriaBuilder.asc(root.get(property)));
            } else {
                orders.add(criteriaBuilder.desc(root.get(property)));
            }
        }
        if (!orders.isEmpty()) {
            criteriaQuery.orderBy(orders);
        }
    }

    @SuppressWarnings("rawtypes")
    private void applyOrderTuple(CriteriaQuery<Tuple> criteriaQuery, CriteriaBuilder criteriaBuilder,
                                 Root<P> root, Query<M> query) {
        List<Order> orders = new ArrayList<>();
        for (LambdaCondition condition : query.getOrderByConditions()) {
            String property = persistenceProperty(condition);
            if (Objects.equals("ASC", condition.operator())) {
                orders.add(criteriaBuilder.asc(root.get(property)));
            } else {
                orders.add(criteriaBuilder.desc(root.get(property)));
            }
        }
        if (!orders.isEmpty()) {
            criteriaQuery.orderBy(orders);
        }
    }

    private void applyWhere(CriteriaQuery<?> criteriaQuery, List<Predicate> predicates) {
        if (!predicates.isEmpty()) {
            criteriaQuery.where(predicates.toArray(new Predicate[0]));
        }
    }

    private Collection<?> toCollection(Object value) {
        if (value instanceof Collection<?>) {
            return (Collection<?>) value;
        }
        if (value instanceof String) {
            String string = (String) value;
            if (StrKit.isNotEmpty(string)) {
                return Arrays.asList(string.split(","));
            }
        }
        return Collections.emptyList();
    }

    @Override
    public M toModel(P persistenceObject) {
        if (Objects.isNull(persistenceObject)) {
            return null;
        }
        return BeanKit.copy(persistenceObject, modelClass);
    }

    @Override
    public P toPersistenceObject(M model) {
        if (Objects.isNull(model)) {
            return null;
        }
        return BeanKit.copy(model, persistenceClass);
    }

    private List<M> convert(List<P> persistenceObjects) {
        return persistenceObjects.stream().map(this::toModel).collect(Collectors.toList());
    }
}
