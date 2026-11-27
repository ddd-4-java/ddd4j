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
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.*;
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

    /** Java 序列化版本标识。 */
    private static final long serialVersionUID = 1L;

    /** JPA \u5b9e\u4f53\u7ba1\u7406\u5668\u3002 */
    protected final EntityManager entityManager;
    /** \u9886\u57df\u6a21\u578b\u7c7b\u578b\u3002 */
    protected final Class<M> modelClass;
    /** \u6301\u4e45\u5316\u5bf9\u8c61\u7c7b\u578b\u3002 */
    protected final Class<P> persistenceClass;

    /**
     * \u521b\u5efa\u805a\u5408\u4ed3\u50a8\u5e76\u6ce8\u518c\u9886\u57df\u6a21\u578b\u7c7b\u578b\u3002
     * @param entityManager JPA \u5b9e\u4f53\u7ba1\u7406\u5668
     * @param modelClass \u9886\u57df\u6a21\u578b\u7c7b\u578b
     * @param persistenceClass \u6301\u4e45\u5316\u5bf9\u8c61\u7c7b\u578b
     */
    protected JpaAggregateRepository(EntityManager entityManager, Class<M> modelClass, Class<P> persistenceClass) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
        this.modelClass = Objects.requireNonNull(modelClass, "modelClass must not be null");
        this.persistenceClass = Objects.requireNonNull(persistenceClass, "persistenceClass must not be null");
        RepositoryRegistry.register(modelClass, this);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<M> findById(ID id) {
        if (Objects.isNull(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(entityManager.find(persistenceClass, id)).map(this::toModel);
    }

    /** {@inheritDoc} */
    @Override
    public M save(M aggregate) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        P managed = entityManager.merge(toPersistenceObject(aggregate));
        return toModel(managed);
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public int insertOrUpdateBatch(Collection<M> aggregates) {
        return updateBatchById(aggregates);
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public Optional<M> findFirst() {
        java.util.List<P> __ps = entityManager
                .createQuery("SELECT p FROM " + persistenceClass.getSimpleName() + " p", persistenceClass)
                .setMaxResults(1)
                .getResultList();
        return __ps.isEmpty() ? java.util.Optional.empty() : java.util.Optional.ofNullable(this.toModel(__ps.get(0)));
    }

    /** {@inheritDoc} */
    @Override
    public List<M> findAll() {
        return convert(entityManager
                .createQuery("SELECT p FROM " + persistenceClass.getSimpleName() + " p", persistenceClass)
                .getResultList());
    }

    /** {@inheritDoc} */
    @Override
    public long count() {
        Long result = entityManager
                .createQuery("SELECT COUNT(p) FROM " + persistenceClass.getSimpleName() + " p", Long.class)
                .getSingleResult();
        return Objects.nonNull(result) ? result : 0L;
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public List<M> findList(Query<M> query) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<P> criteriaQuery = criteriaBuilder.createQuery(persistenceClass);
        Root<P> root = criteriaQuery.from(persistenceClass);
        applyWhere(criteriaQuery, buildPredicates(criteriaBuilder, root, query));
        applyOrder(criteriaQuery, criteriaBuilder, root, query);
        return convert(entityManager.createQuery(criteriaQuery).getResultList());
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public void fill(Query<M> query, AggregateRoot<?> model) {
        // 业务仓储按需覆盖
    }

    /**
     * 将领域属性名映射为 JPA 持久化属性名。
     *
     * <p>默认同名；当 Domain 与 PO 命名不同时由具体仓储覆盖。本方法返回
     * JPA property，而不是数据库 column。
     * @param domainProperty 领域属性
     * @return 返回的字符串内容
     */
    protected String persistenceProperty(String domainProperty) {
        return domainProperty;
    }

    /**
     * 根据 Query 属性空间选择领域映射或 PO 直接属性，并校验类型边界。
     * @param condition 条件
     * @return 返回的字符串内容
     */
    protected String persistenceProperty(LambdaCondition condition) {
        Objects.requireNonNull(condition, "condition must not be null");
        condition.propertyRef().requireCompatible(modelClass, persistenceClass);
        if (condition.propertyRef().isPersistence()) {
            return condition.property();
        }
        return persistenceProperty(condition.property());
    }

    /**
     * \u6784\u9020\u79df\u6237\u3001\u7cfb\u7edf\u548c\u67e5\u8be2\u6761\u4ef6\u5bf9\u5e94\u7684 JPA \u8c13\u8bcd\u3002
     * @param criteriaBuilder JPA \u6761\u4ef6\u6784\u9020\u5668
     * @param root \u6301\u4e45\u5316\u5bf9\u8c61\u67e5\u8be2\u6839
     * @param query \u9886\u57df\u67e5\u8be2
     * @return JPA \u8c13\u8bcd\u5217\u8868
     */
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

    /**
     * \u6709\u4e0a\u4e0b\u6587\u503c\u4e14\u76ee\u6807\u5c5e\u6027\u53ef\u8bbf\u95ee\u65f6\u52a0\u5165\u7b49\u503c\u6761\u4ef6\u3002
     * @param criteriaBuilder \u521b\u5efa JPA \u67e5\u8be2\u3001\u8c13\u8bcd\u548c\u6392\u5e8f\u7684\u6784\u5efa\u5668
     * @param root \u5f53\u524d\u67e5\u8be2\u6307\u5411\u7684 JPA \u5b9e\u4f53\u6839
     * @param predicates \u7d2f\u79ef\u5f53\u524d\u67e5\u8be2\u6761\u4ef6\u7684\u8c13\u8bcd\u5217\u8868
     * @param property \u7528\u4e8e\u5b9a\u4f4d SQL \u5217\u7684 Java \u5c5e\u6027\u540d
     * @param value \u5f53\u524d\u6761\u4ef6\u64cd\u4f5c\u6570\u6216\u5f85\u5199\u5165\u5b57\u6bb5\u503c
     */
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

    /**
     * \u6839\u636e\u67e5\u8be2\u8fd0\u7b97\u7b26\u628a\u9886\u57df\u6761\u4ef6\u8f6c\u6362\u4e3a JPA \u8c13\u8bcd\u3002
     * @param criteriaBuilder \u521b\u5efa JPA \u67e5\u8be2\u3001\u8c13\u8bcd\u548c\u6392\u5e8f\u7684\u6784\u5efa\u5668
     * @param root \u5f53\u524d\u67e5\u8be2\u6307\u5411\u7684 JPA \u5b9e\u4f53\u6839
     * @param predicates \u7d2f\u79ef\u5f53\u524d\u67e5\u8be2\u6761\u4ef6\u7684\u8c13\u8bcd\u5217\u8868
     * @param condition \u5f53\u524d\u67e5\u8be2\u6761\u4ef6
     */
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

    /**
     * \u6309\u4e1a\u52a1\u67e5\u8be2\u7684\u6392\u5e8f\u89c4\u5219\u8bbe\u7f6e\u5b9e\u4f53\u7ed3\u679c\u6392\u5e8f\u3002
     * @param criteriaQuery \u5f85\u88c5\u914d\u7684 JPA \u67e5\u8be2
     * @param criteriaBuilder \u521b\u5efa JPA \u67e5\u8be2\u3001\u8c13\u8bcd\u548c\u6392\u5e8f\u7684\u6784\u5efa\u5668
     * @param root \u5f53\u524d\u67e5\u8be2\u6307\u5411\u7684 JPA \u5b9e\u4f53\u6839
     * @param query \u4e1a\u52a1\u67e5\u8be2\u53ca\u5176\u7b5b\u9009\u3001\u6295\u5f71\u6216\u6392\u5e8f\u6761\u4ef6
     */
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

    /**
     * \u6309\u4e1a\u52a1\u67e5\u8be2\u7684\u6392\u5e8f\u89c4\u5219\u8bbe\u7f6e\u6295\u5f71\u7ed3\u679c\u6392\u5e8f\u3002
     * @param criteriaQuery \u5f85\u88c5\u914d\u7684 JPA \u67e5\u8be2
     * @param criteriaBuilder \u521b\u5efa JPA \u67e5\u8be2\u3001\u8c13\u8bcd\u548c\u6392\u5e8f\u7684\u6784\u5efa\u5668
     * @param root \u5f53\u524d\u67e5\u8be2\u6307\u5411\u7684 JPA \u5b9e\u4f53\u6839
     * @param query \u4e1a\u52a1\u67e5\u8be2\u53ca\u5176\u7b5b\u9009\u3001\u6295\u5f71\u6216\u6392\u5e8f\u6761\u4ef6
     */
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

    /**
     * \u5728\u5b58\u5728\u6709\u6548\u8c13\u8bcd\u65f6\u8bbe\u7f6e JPA \u67e5\u8be2\u7684\u7b5b\u9009\u6761\u4ef6\u3002
     * @param criteriaQuery \u5f85\u88c5\u914d\u7684 JPA \u67e5\u8be2
     * @param predicates \u7d2f\u79ef\u5f53\u524d\u67e5\u8be2\u6761\u4ef6\u7684\u8c13\u8bcd\u5217\u8868
     */
    private void applyWhere(CriteriaQuery<?> criteriaQuery, List<Predicate> predicates) {
        if (!predicates.isEmpty()) {
            criteriaQuery.where(predicates.toArray(new Predicate[0]));
        }
    }

    /**
     * \u5c06\u96c6\u5408\u6216\u5206\u9694\u6587\u672c\u6574\u7406\u6210\u4f9b\u591a\u503c\u6761\u4ef6\u4f7f\u7528\u7684\u96c6\u5408\u3002
     * @param value \u5f53\u524d\u6761\u4ef6\u64cd\u4f5c\u6570\u6216\u5f85\u5199\u5165\u5b57\u6bb5\u503c
     * @return \u8f6c\u6362\u540e\u7684\u6761\u4ef6\u503c\u96c6\u5408
     */
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

    /** {@inheritDoc} */
    @Override
    public M toModel(P persistenceObject) {
        if (Objects.isNull(persistenceObject)) {
            return null;
        }
        return BeanKit.copy(persistenceObject, modelClass);
    }

    /** {@inheritDoc} */
    @Override
    public P toPersistenceObject(M model) {
        if (Objects.isNull(model)) {
            return null;
        }
        return BeanKit.copy(model, persistenceClass);
    }

    /**
     * \u5c06\u67e5\u8be2\u8fd4\u56de\u7684\u6301\u4e45\u5316\u5bf9\u8c61\u4f9d\u5e8f\u8f6c\u6362\u4e3a\u9886\u57df\u6a21\u578b\u3002
     * @param persistenceObjects \u6570\u636e\u5e93\u8fd4\u56de\u7684\u6301\u4e45\u5316\u5bf9\u8c61\u5217\u8868
     * @return \u9886\u57df\u6a21\u578b\u5217\u8868
     */
    private List<M> convert(List<P> persistenceObjects) {
        return persistenceObjects.stream().map(this::toModel).collect(Collectors.toList());
    }
}
