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

    /**
     * JPA 实体管理器。
     */
    protected final EntityManager entityManager;
    /**
     * 领域模型类型。
     */
    protected final Class<M> modelClass;
    /**
     * 持久化对象类型。
     */
    protected final Class<P> persistenceClass;

    /**
     * 创建聚合仓储并注册领域模型类型。
     *
     * @param entityManager    JPA 实体管理器
     * @param modelClass       领域模型类型
     * @param persistenceClass 持久化对象类型
     */
    protected JpaAggregateRepository(EntityManager entityManager, Class<M> modelClass, Class<P> persistenceClass) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
        this.modelClass = Objects.requireNonNull(modelClass, "modelClass must not be null");
        this.persistenceClass = Objects.requireNonNull(persistenceClass, "persistenceClass must not be null");
        RepositoryRegistry.register(modelClass, this);
    }

    /**
     * 按主键读取领域对象或持久化记录。
     *
     * <p>JPA 实现调用 <code>Optional.empty()</code>。</p>
     *
     * @param id 聚合根标识
     * @return 实际返回<code>Optional.empty()</code>
     */
    @Override
    public Optional<M> findById(ID id) {
        if (Objects.isNull(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(entityManager.find(persistenceClass, id)).map(this::toModel);
    }

    /**
     * 写入或更新给定的领域对象或存储记录。
     *
     * <p>JPA 实现调用 <code>entityManager.merge(toPersistenceObject(aggregate))</code>。</p>
     *
     * @param aggregate 待处理聚合根
     * @return 实际返回<code>toModel(managed)</code>
     */
    @Override
    public M save(M aggregate) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        P managed = entityManager.merge(toPersistenceObject(aggregate));
        return toModel(managed);
    }

    /**
     * 按主键删除记录。
     *
     * <p>JPA 实现调用 <code>entityManager.find(persistenceClass, id)</code>。</p>
     *
     * @param id 聚合根标识
     */
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

    /**
     * 批量读取指定主键对应的记录。
     *
     * <p>JPA 实现调用 <code>ids.isEmpty()</code>。</p>
     *
     * @param ids 聚合根标识集合
     * @return 与给定主键相匹配的记录
     */
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

    /**
     * 批量保存传入的对象。
     *
     * <p>JPA 实现调用 <code>aggregates.isEmpty()</code>。</p>
     *
     * @param aggregates 待处理聚合根集合
     * @return 根据执行路径返回<code>Collections.emptyList()</code>、<code>result</code>
     */
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

    /**
     * 按主键批量更新数据。
     *
     * <p>JPA 实现调用 <code>aggregates.isEmpty()</code>。</p>
     *
     * @param aggregates 待处理聚合根集合
     * @return 根据执行路径返回<code>0</code>、<code>count</code>
     */
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

    /**
     * 批量写入数据，根据各记录是否存在选择插入或更新。
     *
     * <p>JPA 实现调用 <code>updateBatchById(aggregates)</code>。</p>
     *
     * @param aggregates 待处理聚合根集合
     * @return 实际返回<code>updateBatchById(aggregates)</code>
     */
    @Override
    public int insertOrUpdateBatch(Collection<M> aggregates) {
        return updateBatchById(aggregates);
    }

    /**
     * 批量删除给定主键对应的记录。
     *
     * <p>JPA 实现调用 <code>ids.isEmpty()</code>。</p>
     *
     * @param ids 聚合根标识集合
     * @return 根据执行路径返回<code>0</code>、<code>entityManager.createQuery(cd).executeUpdate()</code>
     */
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

    /**
     * 读取满足条件的第一条记录。
     *
     * <p>JPA 实现调用 <code>entityManager.createQuery("SELECT p FROM " + persistenceClass.getSimpleName() + " p", persistenceClass)</code>。</p>
     *
     * @return 第一个匹配条件的结果
     */
    @Override
    public Optional<M> findFirst() {
        List<P> results = entityManager
                .createQuery("SELECT p FROM " + persistenceClass.getSimpleName() + " p", persistenceClass)
                .setMaxResults(1)
                .getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.ofNullable(results.get(0)).map(this::toModel);
    }

    /**
     * 查询存储中符合条件的记录。
     *
     * <p>JPA 实现调用 <code>entityManager.createQuery("SELECT p FROM " + persistenceClass.getSimpleName() + " p", persistenceClass)</code>。</p>
     *
     * @return 满足条件的结果列表
     */
    @Override
    public List<M> findAll() {
        return convert(entityManager
                .createQuery("SELECT p FROM " + persistenceClass.getSimpleName() + " p", persistenceClass)
                .getResultList());
    }

    /**
     * 统计符合查询范围的数据数量。
     *
     * <p>JPA 实现调用 <code>entityManager.createQuery("SELECT COUNT(p) FROM " + persistenceClass.getSimpleName() + " p", Long.class)</code>。</p>
     *
     * @return 满足筛选条件的记录数量
     */
    @Override
    public long count() {
        Long result = entityManager
                .createQuery("SELECT COUNT(p) FROM " + persistenceClass.getSimpleName() + " p", Long.class)
                .getSingleResult();
        return Objects.nonNull(result) ? result : 0L;
    }

    /**
     * 根据分页参数和查询条件读取结果。
     *
     * <p>JPA 实现调用 <code>entityManager.getCriteriaBuilder()</code>。</p>
     *
     * @param query 查询条件
     * @return 实际返回<code>Page.succeed(records, count(query), current, query.getSize())</code>
     */
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

    /**
     * 统计符合查询范围的数据数量。
     *
     * <p>JPA 实现调用 <code>entityManager.getCriteriaBuilder()</code>。</p>
     *
     * @param query 查询条件
     * @return 满足筛选条件的记录数量
     */
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

    /**
     * 读取满足条件的第一条记录。
     *
     * <p>JPA 实现调用 <code>entityManager.getCriteriaBuilder()</code>。</p>
     *
     * @param query 查询条件
     * @return 第一个匹配条件的结果
     */
    @Override
    public Optional<M> findFirst(Query<M> query) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<P> criteriaQuery = criteriaBuilder.createQuery(persistenceClass);
        Root<P> root = criteriaQuery.from(persistenceClass);
        applyWhere(criteriaQuery, buildPredicates(criteriaBuilder, root, query));
        applyOrder(criteriaQuery, criteriaBuilder, root, query);
        List<P> results = entityManager.createQuery(criteriaQuery)
                .setMaxResults(1)
                .getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.ofNullable(results.get(0)).map(this::toModel);
    }

    /**
     * 按查询条件读取记录列表。
     *
     * <p>JPA 实现调用 <code>entityManager.getCriteriaBuilder()</code>。</p>
     *
     * @param query 查询条件
     * @return 满足条件的结果列表
     */
    @Override
    public List<M> findList(Query<M> query) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<P> criteriaQuery = criteriaBuilder.createQuery(persistenceClass);
        Root<P> root = criteriaQuery.from(persistenceClass);
        applyWhere(criteriaQuery, buildPredicates(criteriaBuilder, root, query));
        applyOrder(criteriaQuery, criteriaBuilder, root, query);
        return convert(entityManager.createQuery(criteriaQuery).getResultList());
    }

    /**
     * 按条件查询 Map 列表（对应 {@code selectMaps(Wrapper)}）。
     *
     * <p>JPA 实现调用 <code>entityManager.getCriteriaBuilder()</code>。</p>
     *
     * @param query 查询条件
     * @return 查询结果
     */
    @Override
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

    /**
     * 删除匹配查询条件的记录。
     *
     * <p>JPA 实现调用 <code>entityManager.getCriteriaBuilder()</code>。</p>
     *
     * @param query 查询条件
     * @return 实际返回<code>entityManager.createQuery(criteriaDelete).executeUpdate() &gt; 0</code>
     */
    @Override
    public boolean deleteByQuery(Query<M> query) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaDelete<P> criteriaDelete = criteriaBuilder.createCriteriaDelete(persistenceClass);
        Root<P> root = criteriaDelete.from(persistenceClass);
        List<Predicate> predicates = buildPredicates(criteriaBuilder, root, query);
        if (predicates.isEmpty()) {
            throw new IllegalArgumentException("Refusing to delete without query predicates");
        }
        criteriaDelete.where(predicates.toArray(Predicate[]::new));
        return entityManager.createQuery(criteriaDelete).executeUpdate() > 0;
    }

    /**
     * 接收<code>fill</code>回调，当前实现无需在此步骤更新状态。
     *
     * @param query 查询条件
     * @param model 待填充聚合根
     */
    @Override
    public void fill(Query<M> query, AggregateRoot<?> model) {
        // 业务仓储按需覆盖
    }

    /**
     * 将领域属性名映射为 JPA 持久化属性名。
     *
     * <p>默认同名；当 Domain 与 PO 命名不同时由具体仓储覆盖。本方法返回
     * JPA property，而不是数据库 column。
     *
     * @param domainProperty 领域属性名
     * @return 对应的持久化属性名
     */
    protected String persistenceProperty(String domainProperty) {
        return domainProperty;
    }

    /**
     * 根据 Query 属性空间选择领域映射或 PO 直接属性，并校验类型边界。
     *
     * @param condition 查询条件
     * @return 对应的持久化属性名
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
     * 构造租户、系统和查询条件对应的 JPA 谓词。
     *
     * @param criteriaBuilder JPA 条件构造器
     * @param root            持久化对象查询根
     * @param query           领域查询
     * @return JPA 谓词列表
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
            case "=" -> predicates.add(criteriaBuilder.equal(root.get(property), condition.value()));
            case "<>" -> predicates.add(criteriaBuilder.notEqual(root.get(property), condition.value()));
            case ">" -> predicates.add(criteriaBuilder.greaterThan(root.get(property), (Comparable) condition.value()));
            case ">=" ->
                    predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get(property), (Comparable) condition.value()));
            case "<" -> predicates.add(criteriaBuilder.lessThan(root.get(property), (Comparable) condition.value()));
            case "<=" ->
                    predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get(property), (Comparable) condition.value()));
            case "LIKE" -> predicates.add(criteriaBuilder.like(root.get(property), "%" + condition.value() + "%"));
            case "LIKE_LEFT" -> predicates.add(criteriaBuilder.like(root.get(property), condition.value() + "%"));
            case "LIKE_RIGHT" -> predicates.add(criteriaBuilder.like(root.get(property), "%" + condition.value()));
            case "NOT_LIKE" ->
                    predicates.add(criteriaBuilder.notLike(root.get(property), "%" + condition.value() + "%"));
            case "IN" -> predicates.add(root.get(property).in(toCollection(condition.value())));
            case "NOT_IN" -> predicates.add(root.get(property).in(toCollection(condition.value())).not());
            case "IS_NULL" -> predicates.add(criteriaBuilder.isNull(root.get(property)));
            case "IS_NOT_NULL" -> predicates.add(criteriaBuilder.isNotNull(root.get(property)));
            default -> throw new IllegalArgumentException("Unsupported query operator: " + condition.operator());
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
            criteriaQuery.where(predicates.toArray(Predicate[]::new));
        }
    }

    /**
     * \u5c06\u96c6\u5408\u6216\u5206\u9694\u6587\u672c\u6574\u7406\u6210\u4f9b\u591a\u503c\u6761\u4ef6\u4f7f\u7528\u7684\u96c6\u5408\u3002
     * @param value \u5f53\u524d\u6761\u4ef6\u64cd\u4f5c\u6570\u6216\u5f85\u5199\u5165\u5b57\u6bb5\u503c
     * @return \u8f6c\u6362\u540e\u7684\u6761\u4ef6\u503c\u96c6\u5408
     */
    private Collection<?> toCollection(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection;
        }
        if (value instanceof String string && StrKit.isNotEmpty(string)) {
            return Arrays.asList(string.split(","));
        }
        return Collections.emptyList();
    }

    /**
     * 将持久化对象转换为领域模型。
     *
     * <p>JPA 实现调用 <code>BeanKit.copy(persistenceObject, modelClass)</code>。</p>
     *
     * @param persistenceObject persistence object
     * @return 由持久化数据构造的领域模型
     */
    @Override
    public M toModel(P persistenceObject) {
        if (Objects.isNull(persistenceObject)) {
            return null;
        }
        return BeanKit.copy(persistenceObject, modelClass);
    }

    /**
     * 将领域模型转换为仓储使用的持久化对象。
     *
     * <p>JPA 实现调用 <code>BeanKit.copy(model, persistenceClass)</code>。</p>
     *
     * @param model domain model
     * @return 由领域模型生成的持久化对象
     */
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
