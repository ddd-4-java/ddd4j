package io.ddd4j.data.mybatis.repository.impl;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.cqrs.query.LambdaCondition;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.ddd.model.DomainObjectMapper;
import io.ddd4j.core.ddd.model.metadata.DomainModelHelper;
import io.ddd4j.core.ddd.model.metadata.DomainModelInfo;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.data.mybatis.repository.scheme.TableScheme;
import io.ddd4j.kit.lang.BeanKit;
import io.ddd4j.kit.lang.CollKit;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 原生 MyBatis 轨道的 Repository 实现（零 MyBatis-Plus 依赖）。
 *
 * <p>使用 {@link SqlSession} 直接操作，适配 {@link Repository} SPI。
 * 内置 {@link TableScheme} 元数据反射，支持：
 * <ul>
 *   <li>字段后缀自动映射（In/Like/Min/Max/Not/IsNull 等）</li>
 *   <li>租户/系统隔离（{@code @TenantId} / {@code @SystemId}）</li>
 *   <li>自动填充（{@code @OnCreate} / {@code @OnUpdate}）</li>
 *   <li>批量操作（SqlSession batch 模式）</li>
 *   <li>业务键操作（{@code @BizKey}）</li>
 * </ul>
 *
 * @param <M>  聚合根类型
 * @param <P>  持久化对象类型
 * @param <ID> 聚合根标识类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
@Slf4j
@SuppressWarnings("unchecked")
public abstract class MybatisAggregateRepository<M extends AggregateRoot<?>, P, ID extends Serializable>
        implements Repository<M, ID>, DomainObjectMapper<M, P>, Serializable {

    private static final int DEFAULT_BATCH_SIZE = 100;

    protected final SqlSession sqlSession;
    protected final Class<M> modelClass;
    protected final Class<P> persistenceClass;
    protected final TableScheme tableScheme;
    protected final DomainModelInfo<M> domainModelInfo;

    protected MybatisAggregateRepository(SqlSession sqlSession, Class<M> modelClass, Class<P> persistenceClass) {
        this.sqlSession = Objects.requireNonNull(sqlSession, "sqlSession must not be null");
        this.modelClass = Objects.requireNonNull(modelClass, "modelClass must not be null");
        this.persistenceClass = Objects.requireNonNull(persistenceClass, "persistenceClass must not be null");
        this.tableScheme = TableScheme.of(persistenceClass);
        this.domainModelInfo = Objects.requireNonNull(
                DomainModelHelper.getModelInfo(modelClass, persistenceClass, tableScheme::getColumnSafely),
                "domainModelInfo must not be null");
        RepositoryRegistry.register(modelClass, this);
    }

    // ========================= Repository 实现 =========================

    @Override
    public Optional<M> findById(ID id) {
        P po = sqlSession.selectOne(mapperNamespace() + ".findById", id);
        return Optional.ofNullable(po).map(this::toModel);
    }

    @Override
    public M save(M aggregate) {
        P po = toPersistenceObject(aggregate);
        tableScheme.insertFill(po);
        if (shouldInsert(po)) {
            sqlSession.insert(mapperNamespace() + ".insert", po);
        } else {
            tableScheme.updateFill(po);
            sqlSession.update(mapperNamespace() + ".update", po);
        }
        return toModel(po);
    }

    @Override
    public void deleteById(ID id) {
        sqlSession.delete(mapperNamespace() + ".deleteById", id);
    }

    @Override
    public Optional<M> findFirst() {
        List<P> list = sqlSession.selectList(mapperNamespace() + ".findAll");
        return list.isEmpty() ? Optional.empty() : Optional.of(toModel(list.get(0)));
    }

    @Override
    public List<M> findAll() {
        List<P> list = sqlSession.selectList(mapperNamespace() + ".findAll");
        return list.stream().map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public long count() {
        Long result = sqlSession.selectOne(mapperNamespace() + ".count");
        return result != null ? result : 0L;
    }

    @Override
    public Page<M> page(Query<M> query) {
        Map<String, Object> params = buildQueryParams(query);
        List<P> list = sqlSession.selectList(mapperNamespace() + ".findByQuery", params,
                new RowBounds((int) ((query.getCurrent() - 1) * query.getSize()), (int) query.getSize()));
        long total = count(query);
        List<M> models = list.stream().map(this::toModel).collect(Collectors.toList());
        return Page.succeed(models, total, query.getCurrent(), query.getSize());
    }

    @Override
    public long count(Query<M> query) {
        Map<String, Object> params = buildQueryParams(query);
        Long result = sqlSession.selectOne(mapperNamespace() + ".countByQuery", params);
        return result != null ? result : 0L;
    }

    @Override
    public Optional<M> findFirst(Query<M> query) {
        Map<String, Object> params = buildQueryParams(query);
        List<P> list = sqlSession.selectList(mapperNamespace() + ".findByQuery", params,
                new RowBounds(0, 1));
        return list.isEmpty() ? Optional.empty() : Optional.of(toModel(list.get(0)));
    }

    @Override
    public List<M> findList(Query<M> query) {
        Map<String, Object> params = buildQueryParams(query);
        List<P> list = sqlSession.selectList(mapperNamespace() + ".findByQuery", params);
        return list.stream().map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public boolean update(AggregateRoot<?> aggregate, Query<M> query) {
        P po = toPersistenceObject((M) aggregate);
        tableScheme.updateFill(po);
        Map<String, Object> params = buildQueryParams(query);
        params.put("entity", po);
        return sqlSession.update(mapperNamespace() + ".updateByQuery", params) > 0;
    }

    @Override
    public boolean deleteByQuery(Query<M> query) {
        Map<String, Object> params = buildQueryParams(query);
        return sqlSession.delete(mapperNamespace() + ".deleteByQuery", params) > 0;
    }

    @Override
    public void fill(Query<M> query, AggregateRoot<?> model) {
        // 默认空实现，业务方可覆盖
    }

    // ========================= BizKey 操作 =========================

    /**
     * 按业务键查询单条记录。
     */
    public Optional<M> getByKey(Serializable key) {
        if (!tableScheme.hasBizKey()) {
            throw new UnsupportedOperationException("PO class " + persistenceClass.getName() + " has no @BizKey field");
        }
        Map<String, Object> params = buildKeyWrapper(key);
        P po = sqlSession.selectOne(mapperNamespace() + ".findByQuery", params);
        return Optional.ofNullable(po).map(this::toModel);
    }

    /**
     * 按业务键批量查询。
     */
    public List<M> listByKey(List<Serializable> keys) {
        if (!tableScheme.hasBizKey()) {
            throw new UnsupportedOperationException("PO class " + persistenceClass.getName() + " has no @BizKey field");
        }
        if (CollKit.isEmpty(keys)) {
            return Collections.emptyList();
        }
        Map<String, Object> params = buildKeyWrapper(keys);
        List<P> list = sqlSession.selectList(mapperNamespace() + ".findByQuery", params);
        return list.stream().map(this::toModel).collect(Collectors.toList());
    }

    /**
     * 按业务键更新。
     */
    public boolean updateByKey(M model) {
        if (!tableScheme.hasBizKey()) {
            throw new UnsupportedOperationException("PO class " + persistenceClass.getName() + " has no @BizKey field");
        }
        P po = toPersistenceObject(model);
        tableScheme.updateFill(po);
        Object bizKeyValue = tableScheme.getFieldValue(po, tableScheme.getBizKeyField());
        if (bizKeyValue == null) {
            throw new IllegalArgumentException("BizKey value must not be null");
        }
        Map<String, Object> params = buildKeyWrapper((Serializable) bizKeyValue);
        params.put("entity", po);
        return sqlSession.update(mapperNamespace() + ".updateByQuery", params) > 0;
    }

    /**
     * 按业务键删除。
     */
    public boolean deleteByKey(Serializable key) {
        if (!tableScheme.hasBizKey()) {
            throw new UnsupportedOperationException("PO class " + persistenceClass.getName() + " has no @BizKey field");
        }
        Map<String, Object> params = buildKeyWrapper(key);
        return sqlSession.delete(mapperNamespace() + ".deleteByQuery", params) > 0;
    }

    /**
     * 按业务键批量删除。
     */
    public boolean deleteByKeys(List<Serializable> keys) {
        if (!tableScheme.hasBizKey()) {
            throw new UnsupportedOperationException("PO class " + persistenceClass.getName() + " has no @BizKey field");
        }
        if (CollKit.isEmpty(keys)) {
            return false;
        }
        Map<String, Object> params = buildKeyWrapper(keys);
        return sqlSession.delete(mapperNamespace() + ".deleteByQuery", params) > 0;
    }

    // ========================= 批量操作 =========================

    /**
     * 批量插入（SqlSession batch 模式，每 {@value DEFAULT_BATCH_SIZE} 条 flush）。
     */
    public int[] insertBatch(List<P> pos) {
        if (CollKit.isEmpty(pos)) {
            return new int[0];
        }
        int[] results = new int[pos.size()];
        String statement = mapperNamespace() + ".insert";
        for (int i = 0; i < pos.size(); i++) {
            P po = pos.get(i);
            tableScheme.insertFill(po);
            results[i] = sqlSession.insert(statement, po);
            if ((i + 1) % DEFAULT_BATCH_SIZE == 0) {
                sqlSession.flushStatements();
            }
        }
        sqlSession.flushStatements();
        return results;
    }

    /**
     * 批量更新（SqlSession batch 模式）。
     */
    public int[] updateBatch(List<P> pos) {
        if (CollKit.isEmpty(pos)) {
            return new int[0];
        }
        int[] results = new int[pos.size()];
        String statement = mapperNamespace() + ".update";
        for (int i = 0; i < pos.size(); i++) {
            P po = pos.get(i);
            tableScheme.updateFill(po);
            results[i] = sqlSession.update(statement, po);
            if ((i + 1) % DEFAULT_BATCH_SIZE == 0) {
                sqlSession.flushStatements();
            }
        }
        sqlSession.flushStatements();
        return results;
    }

    // ========================= 辅助方法 =========================

    /**
     * 获取 Mapper 的命名空间（业务子类需提供 Mapper 接口的全限定名）。
     */
    protected abstract String mapperNamespace();

    /**
     * 判断是否应执行 INSERT（PO 主键为空则 INSERT，否则 UPDATE）。
     */
    protected boolean shouldInsert(P po) {
        Field idField = tableScheme.getIdField();
        if (idField != null) {
            Object id = tableScheme.getFieldValue(po, idField);
            return id == null;
        }
        return true;
    }

    /**
     * 构建查询参数 Map（Lambda 条件 + 租户/系统隔离）。
     */
    protected Map<String, Object> buildQueryParams(Query<M> query) {
        Map<String, Object> params = new HashMap<>();

        // 租户隔离
        if (tableScheme.hasTenantId() && !query.isIgnoreTenantId()) {
            String tenantId = ThreadContext.get(ContextConstants.TENANT_ID);
            if (tenantId != null) {
                params.put("tenantId", tenantId);
            }
        }

        // 系统隔离
        if (tableScheme.hasSystemId()) {
            String systemId = ThreadContext.get(ContextConstants.SYSTEM_ID);
            if (systemId != null) {
                params.put("systemId", systemId);
            }
        }

        // Query 中直接定义的 Lambda 条件
        if (query.hasConditions()) {
            List<LambdaCondition> conditions = query.getWhereConditions();
            List<Map<String, Object>> condList = new ArrayList<>();
            for (LambdaCondition condition : conditions) {
                String column = translateProperty(condition);
                Map<String, Object> cond = new HashMap<>();
                cond.put("column", column);
                cond.put("operator", condition.operator());
                cond.put("value", condition.value());
                condList.add(cond);
            }
            params.put("_lambdaConditions", condList);
        }

        // Lambda 排序
        if (query.hasOrderBy()) {
            List<Map<String, Object>> orderList = new ArrayList<>();
            for (LambdaCondition order : query.getOrderByConditions()) {
                Map<String, Object> orderItem = new HashMap<>();
                orderItem.put("column", translateProperty(order));
                orderItem.put("direction", order.operator());
                orderList.add(orderItem);
            }
            params.put("_lambdaOrderBy", orderList);
        }

        // 默认排序（当未指定排序时，使用 PO 上 @OrderBy 注解的默认排序）
        if (!query.hasOrderBy() && tableScheme.getDefaultOrderBy() != null) {
            params.put("_defaultOrderBy", tableScheme.getDefaultOrderBy());
        }

        // 分页参数
        if (query.getCurrent() > 0) {
            params.put("current", query.getCurrent());
            params.put("size", query.getSize());
        }

        return params;
    }

    private String translateProperty(LambdaCondition condition) {
        condition.propertyRef().requireCompatible(modelClass, persistenceClass);
        String column = condition.propertyRef().isPersistence()
                ? tableScheme.getColumnSafely(condition.property())
                : domainModelInfo.getPoColumn(condition.property());
        if (Objects.isNull(column)) {
            throw new IllegalArgumentException("Query property " + condition.property() + " from "
                    + condition.propertyRef().space() + " does not map to " + persistenceClass.getName());
        }
        return column;
    }

    /**
     * 构建 BizKey 查询条件。
     */
    private Map<String, Object> buildKeyWrapper(Serializable key) {
        Map<String, Object> params = new HashMap<>();
        params.put(tableScheme.getBizKeyField().getName(), key);
        // 租户隔离
        if (tableScheme.hasTenantId()) {
            String tenantId = ThreadContext.get(ContextConstants.TENANT_ID);
            if (tenantId != null) {
                params.put("tenantId", tenantId);
            }
        }
        // 系统隔离
        if (tableScheme.hasSystemId()) {
            String systemId = ThreadContext.get(ContextConstants.SYSTEM_ID);
            if (systemId != null) {
                params.put("systemId", systemId);
            }
        }
        return params;
    }

    private Map<String, Object> buildKeyWrapper(List<Serializable> keys) {
        Map<String, Object> params = new HashMap<>();
        params.put(tableScheme.getBizKeyField().getName() + "In", keys);
        // 租户隔离
        if (tableScheme.hasTenantId()) {
            String tenantId = ThreadContext.get(ContextConstants.TENANT_ID);
            if (tenantId != null) {
                params.put("tenantId", tenantId);
            }
        }
        // 系统隔离
        if (tableScheme.hasSystemId()) {
            String systemId = ThreadContext.get(ContextConstants.SYSTEM_ID);
            if (systemId != null) {
                params.put("systemId", systemId);
            }
        }
        return params;
    }

    // ========================= DomainObjectMapper 实现 =========================

    @Override
    public M toModel(P persistenceObject) {
        if (persistenceObject == null) {
            return null;
        }
        return BeanKit.copy(persistenceObject, modelClass);
    }

    @Override
    @SuppressWarnings("unchecked")
    public P toPersistenceObject(M model) {
        if (model == null) {
            return null;
        }
        return BeanKit.copy(model, persistenceClass);
    }

    /**
     * 获取 TableScheme（供子类使用）。
     */
    protected TableScheme getTableScheme() {
        return tableScheme;
    }
}
