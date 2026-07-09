package io.ddd4j.data.mybatis.repository;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.ReflectionKit;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.conditions.query.ChainQuery;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.ChainUpdate;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.api.Page;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.ddd.model.DomainObjectMapper;
import io.ddd4j.core.cqrs.query.LambdaCondition;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.repository.Repository;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;

import io.ddd4j.core.util.MappingKit;
import io.ddd4j.annotation.orm.BizKey;
import io.ddd4j.annotation.orm.OnCreate;
import io.ddd4j.annotation.orm.OnUpdate;
import io.ddd4j.annotation.orm.OrderBy;
import io.ddd4j.annotation.orm.SystemId;
import io.ddd4j.annotation.orm.TenantId;

import io.ddd4j.kit.lang.BeanKit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.ddd4j.kit.lang.StrKit;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * MyBatis-Plus repository base for rich aggregate roots.
 *
 * <p>该类吸收旧 ddd4j {@code BaseRepositoryImpl} 的 Query 后缀条件、租户、
 * BizKey、fill、分页和保存后回填语义，但只暴露 {@link Repository} SPI。
 * MyBatis-Plus 仍限定在基础设施层，聚合根不感知 ORM。</p>
 *
 * @param <M>  aggregate root type
 * @param <P>  persistence object type, usually named {@code *PO}
 * @param <ID> aggregate identity type
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j
public abstract class MybatisAggregateRepository<M extends AggregateRoot<?>, P, ID extends Serializable>
        implements Repository<M, P, ID>, DomainObjectMapper<M, P>, Serializable {

    private BaseMapper<P> mapper;
    private Class<M> modelClass;
    private Class<P> persistenceObjectClass;
    private TableScheme tableScheme;

    protected MybatisAggregateRepository() {
    }

    protected MybatisAggregateRepository(BaseMapper<P> mapper) {
        configureTypes(resolveModelClass(), resolvePersistenceObjectClass(), null);
        setMapper(mapper);
    }

    protected MybatisAggregateRepository(BaseMapper<P> mapper, Class<M> modelClass, Class<P> persistenceObjectClass) {
        configureTypes(modelClass, persistenceObjectClass, null);
        setMapper(mapper);
    }

    @SuppressWarnings("unchecked")
    private Class<M> resolveModelClass() {
        return (Class<M>) ReflectionKit.getSuperClassGenericType(this.getClass(), MybatisAggregateRepository.class, 0);
    }

    @SuppressWarnings("unchecked")
    private Class<P> resolvePersistenceObjectClass() {
        return (Class<P>) ReflectionKit.getSuperClassGenericType(this.getClass(), MybatisAggregateRepository.class, 1);
    }

    protected final void configureTypes(Class<M> modelClass, Class<P> persistenceObjectClass, Class<? extends Query> queryClass) {
        this.modelClass = modelClass;
        this.persistenceObjectClass = persistenceObjectClass;
        if (Objects.nonNull(modelClass) && Objects.nonNull(persistenceObjectClass)) {
            this.tableScheme = TableScheme.build(persistenceObjectClass);
            MappingKit.map("MODEL_PO", modelClass, persistenceObjectClass);
            MappingKit.map("MODEL_PO", persistenceObjectClass, modelClass);
            if (Objects.nonNull(queryClass)) {
                MappingKit.map("MODEL_QUERY", modelClass, queryClass);
                MappingKit.map("MODEL_QUERY", queryClass, modelClass);
            }
            String modelClassName = modelClass.getSimpleName().toLowerCase().substring(0, 1) + modelClass.getSimpleName().substring(1);
            MappingKit.map("MODEL_NAME", modelClassName, modelClass);
        }
        if (Objects.nonNull(modelClass) && Objects.nonNull(queryClass)) {
            RepositoryRegistry.register(modelClass, queryClass, this);
        } else if (Objects.nonNull(modelClass)) {
            RepositoryRegistry.register(modelClass, this);
        }
    }

    public BaseMapper<P> getMapper() {
        return mapper();
    }

    public void setMapper(BaseMapper<P> mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    // ========================= MyBatis-Plus 原生 Wrapper API（CQRS 查询优势） =========================
    // 以下方法暴露 MyBatis-Plus 的 QueryWrapper / LambdaQueryWrapper / UpdateWrapper / LambdaUpdateWrapper，
    // 让业务方在需要复杂条件查询时直接使用原生链式 API，自动注入租户/系统隔离条件。

    /**
     * 创建带租户/系统隔离的 {@link QueryWrapper}（CQRS 读侧复杂查询入口）。
     *
     * <pre>{@code
     * List<UserPO> list = repo.queryWrapper()
     *     .select("id", "name", "phone")
     *     .eq("status", "ACTIVE")
     *     .like("name", "张")
     *     .ge("create_time", startTime)
     *     .orderByDesc("create_time")
     *     .last("LIMIT 10")
     *     .getEntityList(mapper());
     * }</pre>
     *
     * @return 已注入租户/系统条件的 QueryWrapper
     */
    public QueryWrapper<P> queryWrapper() {
        return getDefaultWrapper(false);
    }

    /**
     * 创建带租户/系统隔离的 {@link QueryWrapper}（忽略租户隔离）。
     *
     * @return 已注入系统条件但忽略租户的 QueryWrapper
     */
    public QueryWrapper<P> queryWrapperIgnoreTenant() {
        return getDefaultWrapper(true);
    }

    /**
     * 创建 {@link LambdaQueryWrapper}（类型安全的字段引用，编译期检查）。
     *
     * <pre>{@code
     * UserPO user = repo.lambdaQuery()
     *     .eq(UserPO::getPhone, "13800138000")
     *     .one();
     * }</pre>
     *
     * @return LambdaQueryWrapper（注意：Lambda 模式不自动注入租户/系统条件，
     *         如需隔离请在链式调用中手动追加 {@code .eq(Entity::getTenantId, ThreadContext.get(...))}）
     */
    public LambdaQueryWrapper<P> lambdaQuery() {
        return Wrappers.lambdaQuery(persistenceObjectClass());
    }

    /**
     * 创建 {@link LambdaUpdateWrapper}（类型安全的更新条件，CQRS 写侧复杂更新入口）。
     *
     * <pre>{@code
     * repo.lambdaUpdate()
     *     .eq(UserPO::getStatus, "INACTIVE")
     *     .set(UserPO::getStatus, "DELETED")
     *     .update();
     * }</pre>
     *
     * @return LambdaUpdateWrapper
     */
    public LambdaUpdateWrapper<P> lambdaUpdate() {
        return Wrappers.lambdaUpdate(persistenceObjectClass());
    }

    /**
     * 创建 {@link UpdateWrapper}（字符串字段名的更新条件）。
     *
     * <pre>{@code
     * repo.updateWrapper()
     *     .eq("status", "INACTIVE")
     *     .set("status", "DELETED")
     *     .update();
     * }</pre>
     *
     * @return UpdateWrapper
     */
    public UpdateWrapper<P> updateWrapper() {
        return new UpdateWrapper<>();
    }

    /**
     * 创建 {@link QueryChainWrapper}（链式查询，自动执行）。
     *
     * <pre>{@code
     * List<P> list = repo.queryChain().eq("status", "ACTIVE").list();
     * long count = repo.queryChain().eq("status", "ACTIVE").count();
     * }</pre>
     *
     * @return QueryChainWrapper（绑定到当前 mapper，链式调用末尾自动执行 SQL）
     */
    public QueryChainWrapper<P> queryChain() {
        return new QueryChainWrapper<>(mapper());
    }

    /**
     * 创建 {@link LambdaQueryChainWrapper}（类型安全的链式查询，自动执行）。
     *
     * <pre>{@code
     * P user = repo.lambdaQueryChain().eq(P::getPhone, "13800138000").one();
     * }</pre>
     *
     * @return LambdaQueryChainWrapper（绑定到当前 mapper）
     */
    public LambdaQueryChainWrapper<P> lambdaQueryChain() {
        return new LambdaQueryChainWrapper<>(mapper());
    }

    /**
     * 创建 {@link UpdateChainWrapper}（链式更新，自动执行）。
     *
     * <pre>{@code
     * boolean success = repo.updateChain()
     *     .eq("status", "INACTIVE")
     *     .set("status", "DELETED")
     *     .update();
     * }</pre>
     *
     * @return UpdateChainWrapper（绑定到当前 mapper，链式调用末尾自动执行 UPDATE）
     */
    public UpdateChainWrapper<P> updateChain() {
        return new UpdateChainWrapper<>(mapper());
    }

    /**
     * 创建 {@link LambdaUpdateChainWrapper}（类型安全的链式更新，自动执行）。
     *
     * <pre>{@code
     * boolean success = repo.lambdaUpdateChain()
     *     .eq(P::getStatus, "INACTIVE")
     *     .set(P::getStatus, "DELETED")
     *     .update();
     * }</pre>
     *
     * @return LambdaUpdateChainWrapper（绑定到当前 mapper）
     */
    public LambdaUpdateChainWrapper<P> lambdaUpdateChain() {
        return new LambdaUpdateChainWrapper<>(mapper());
    }


    protected BaseMapper<P> mapper() {
        return Objects.requireNonNull(mapper, "mapper must not be null");
    }

    protected Class<M> modelClass() {
        return Objects.requireNonNull(modelClass, "modelClass must not be null");
    }

    protected Class<P> persistenceObjectClass() {
        return Objects.requireNonNull(persistenceObjectClass, "persistenceObjectClass must not be null");
    }

    protected TableScheme tableScheme() {
        return Objects.requireNonNull(tableScheme, "tableScheme must not be null");
    }

    @Override
    public Optional<M> findById(ID id) {
        if (Objects.isNull(id)) {
            return Optional.empty();
        }
        P persistenceObject = mapper().selectById(id);
        if (Objects.isNull(persistenceObject)) {
            return Optional.empty();
        }
        return Optional.of(toModel(persistenceObject));
    }

    @Override
    public boolean existsById(ID id) {
        return Objects.nonNull(id) && Objects.nonNull(mapper().selectById(id));
    }

    @Override
    public M save(M aggregate) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        P persistenceObject = toPersistenceObject(aggregate);
        if (shouldInsert(aggregate)) {
            insertFill(persistenceObject);
            mapper().insert(persistenceObject);
        } else {
            updateFill(persistenceObject);
            mapper().updateById(persistenceObject);
        }
        BeanKit.copy(persistenceObject, aggregate);
        return aggregate;
    }

    public M update(M aggregate) {
        return save(aggregate);
    }

    public M saveOrUpdate(M aggregate) {
        return save(aggregate);
    }

    @Override
    public void deleteById(ID id) {
        if (Objects.nonNull(id)) {
            mapper().deleteById(id);
        }
    }

    public boolean delete(Serializable id) {
        if (Objects.isNull(id)) {
            return false;
        }
        return SqlHelper.retBool(mapper().deleteById(id));
    }

    protected boolean shouldInsert(M aggregate) {
        Serializable id = aggregate.id();
        return Objects.isNull(id) || Objects.isNull(mapper().selectById(id));
    }

    @Override
    public Optional<M> findFirst() {
        QueryWrapper<P> wrapper = getDefaultWrapper(false);
        wrapper.last("LIMIT 1");
        List<P> persistenceObjects = mapper().selectList(wrapper);
        if (persistenceObjects.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toModel(persistenceObjects.get(0)));
    }

    @Override
    public List<M> findAll() {
        return convert(mapper().selectList(getDefaultWrapper(false)));
    }

    @Override
    public long count() {
        Long count = mapper().selectCount(getDefaultWrapper(false));
        return Objects.nonNull(count) ? count : 0L;
    }

    @Override
    public Page<M> page(Query<P> query) {
        QueryWrapper<P> wrapper = getBaseWrapper(query);
        if (query.getSize() < 0) {
            List<M> records = convert(mapper().selectList(wrapper));
            Page<M> page = Page.succeed(records, records.size(), 1, records.size());
            fillIfNecessary(query, records);
            return page;
        }

        long current = query.getCurrent() < 1 ? 1 : query.getCurrent();
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<P> mybatisPage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, query.getSize());
        IPage<P> result = mapper().selectPage(mybatisPage, wrapper);
        List<M> records = convert(result.getRecords());
        Page<M> page = Page.succeed(records, result.getTotal(), query.getCurrent(), query.getSize());
        fillIfNecessary(query, records);
        return page;
    }

    @Override
    public long count(Query query) {
        Long count = mapper().selectCount(getBaseWrapper(query));
        return Objects.nonNull(count) ? count : 0L;
    }

    @Override
    public Optional<M> findFirst(Query query) {
        QueryWrapper<P> wrapper = getBaseWrapper(query);
        wrapper.last("LIMIT 1");
        List<P> persistenceObjects = mapper().selectList(wrapper);
        if (persistenceObjects.isEmpty()) {
            return Optional.empty();
        }
        M model = toModel(persistenceObjects.get(0));
        fill(query, model);
        return Optional.of(model);
    }

    @Override
    public List<M> findList(Query query) {
        List<M> models = convert(mapper().selectList(getBaseWrapper(query)));
        fillIfNecessary(query, models);
        return models;
    }

    @Override
    public List<Map<String, Object>> maps(Query query) {
        return mapper().selectMaps(getBaseWrapper(query));
    }

    @Override
    public boolean update(AggregateRoot<?> aggregate, Query<P> query) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        P persistenceObject = toPersistenceObject(modelClass().cast(aggregate));
        updateFill(persistenceObject);
        boolean updated = SqlHelper.retBool(mapper().update(persistenceObject, getBaseWrapper(query)));
        if (updated) {
            copyUpdatedPersistenceObject(persistenceObject, aggregate);
        }
        return updated;
    }

    @Override
    public boolean deleteByQuery(Query query) {
        return SqlHelper.retBool(mapper().delete(getBaseWrapper(query)));
    }

    public boolean delete(Query query) {
        return deleteByQuery(query);
    }

    public M get(Serializable id) {
        return findById((ID) id).orElse(null);
    }

    public List<M> list(Query query) {
        return findList(query);
    }

    public M first(Query query) {
        return findFirst(query).orElse(null);
    }

    public M one(Query query) {
        return findFirst(query).orElse(null);
    }

    public boolean exist(Query query) {
        return exists(query);
    }

    public boolean updateByKey(M model) {
        P persistenceObject = toPersistenceObject(model);
        String key = getKeyValue(persistenceObject);
        if (Objects.isNull(key) || !StrKit.hasText(key)) {
            throw new IllegalArgumentException("当前entity实体业务key字段为null，无法适用当前方法更新！");
        }
        updateFill(persistenceObject);
        return SqlHelper.retBool(mapper().update(persistenceObject, getKeyWrapper(key)));
    }

    public M getByKey(String key) {
        if (Objects.isNull(key) || !StrKit.hasText(key)) {
            log.warn("The key annotated by @BizKey must not blank");
            return null;
        }
        P persistenceObject = mapper().selectOne(getKeyWrapper(key));
        return Objects.nonNull(persistenceObject) ? toModel(persistenceObject) : null;
    }

    public List<M> listByKey(List<Serializable> keys) {
        if (Objects.isNull(keys) || keys.isEmpty()) {
            return Collections.emptyList();
        }
        if (keys.size() >= 100) {
            throw new IllegalArgumentException("批量查询的业务key不能大于100");
        }
        return convert(mapper().selectList(getKeyWrapper(keys)));
    }

    @Override
    public void fill(Query<P> query, AggregateRoot<?> model) {
        if (modelClass().isInstance(model)) {
            fill(query, List.of(modelClass().cast(model)));
        }
    }

    @Override
    public void fill(Query<P> query, List<M> models) {
    }

    @Override
    public M toModel(P persistenceObject) {
        if (Objects.isNull(persistenceObject)) {
            return null;
        }
        return BeanKit.copy(persistenceObject, modelClass());
    }

    @Override
    public P toPersistenceObject(M model) {
        if (Objects.isNull(model)) {
            return null;
        }
        return BeanKit.copy(model, persistenceObjectClass());
    }

    protected List<M> convert(List<P> persistenceObjects) {
        if (Objects.isNull(persistenceObjects) || persistenceObjects.isEmpty()) {
            return new ArrayList<>();
        }
        return BeanKit.copy(persistenceObjects, modelClass());
    }

    protected QueryWrapper<P> getDefaultWrapper(boolean ignoreTenantId) {
        QueryWrapper<P> wrapper = new QueryWrapper<>();
        TableScheme scheme = tableScheme();
        if (!ignoreTenantId) {
            String tenantId = ThreadContext.get(ContextConstants.TENANT_ID);
            if (Objects.nonNull(scheme.getTenantId()) && Objects.nonNull(tenantId)) {
                wrapper.eq(TableScheme.getColumn(scheme.getTenantId()), tenantId);
            }
        }
        String systemId = ThreadContext.get(ContextConstants.SYSTEM_ID);
        if (Objects.nonNull(scheme.getSystemId()) && Objects.nonNull(systemId)) {
            wrapper.eq(TableScheme.getColumn(scheme.getSystemId()), systemId);
        }
        return wrapper;
    }

    @SuppressWarnings("unchecked")
    protected QueryWrapper<P> getBaseWrapper(Query query) {
        // AbstractMybatisQuery 直接持有 LambdaQueryWrapper，深度整合 MyBatis-Plus
        if (query instanceof io.ddd4j.data.mybatis.query.AbstractMybatisQuery<?> amq && amq.hasManualWrapper()) {
            // 直接使用 AbstractMybatisQuery 内部的 LambdaQueryWrapper
            LambdaQueryWrapper<P> lambdaWrapper = amq.getLambdaQueryWrapper();
            QueryWrapper<P> baseWrapper = getDefaultWrapper(query.isIgnoreTenantId());
            // 将 Lambda 条件合并到 baseWrapper（保留租户/系统隔离）
            baseWrapper.and(w -> lambdaWrapper.getCustomSqlSegment());
            having(query, baseWrapper);
            return baseWrapper;
        }
        // 非 AbstractMybatisQuery：从 Query 通用条件转换
        QueryWrapper<P> wrapper = getDefaultWrapper(query.isIgnoreTenantId());
        applyConditions(wrapper, query);
        applySelect(query, wrapper);
        applyGroupBy(query, wrapper);
        having(query, wrapper);
        applyOrderBy(query, wrapper);
        return wrapper;
    }

    /**
     * 将 Query.getWhereConditions() 转换为 MyBatis-Plus QueryWrapper 条件。
     */
    @SuppressWarnings("unchecked")
    private void applyConditions(QueryWrapper<P> wrapper, Query query) {
        for (Object obj : query.getWhereConditions()) {
            LambdaCondition condition = (LambdaCondition) obj;
            String column = tableScheme().containsField(condition.property())
                    ? tableScheme().getField(condition.property())
                    : TableScheme.toUnderline(condition.property());
            switch (condition.operator()) {
                case "=" -> wrapper.eq(column, condition.value());
                case "<>" -> wrapper.ne(column, condition.value());
                case ">" -> wrapper.gt(column, condition.value());
                case ">=" -> wrapper.ge(column, condition.value());
                case "<" -> wrapper.lt(column, condition.value());
                case "<=" -> wrapper.le(column, condition.value());
                case "LIKE" -> wrapper.like(column, condition.value());
                case "LIKE_LEFT" -> wrapper.likeLeft(column, condition.value());
                case "LIKE_RIGHT" -> wrapper.likeRight(column, condition.value());
                case "NOT_LIKE" -> wrapper.notLike(column, condition.value());
                case "IN" -> wrapper.in(column, (Collection<?>) condition.value());
                case "NOT_IN" -> wrapper.notIn(column, (Collection<?>) condition.value());
                case "IS_NULL" -> wrapper.isNull(column);
                case "IS_NOT_NULL" -> wrapper.isNotNull(column);
            }
        }
    }

    /**
     * 将 Query.getOrderByConditions() 转换为 QueryWrapper 排序。
     */
    private void applyOrderBy(Query<P> query, QueryWrapper<P> wrapper) {
        for (Object obj : query.getOrderByConditions()) {
            LambdaCondition orderBy = (LambdaCondition) obj;
            String column = tableScheme().containsField(orderBy.property())
                    ? tableScheme().getField(orderBy.property())
                    : TableScheme.toUnderline(orderBy.property());
            wrapper.orderBy(true, "ASC".equals(orderBy.operator()), column);
        }
    }

    protected QueryWrapper<P> getKeyWrapper(Serializable key) {
        TableScheme scheme = tableScheme();
        if (Objects.isNull(scheme.getBizKeyField())) {
            throw new IllegalArgumentException("当前entity实体没找到业务key字段，请在entity实体中使用@BizKey注解标记对应的字段！");
        }
        QueryWrapper<P> wrapper = getDefaultWrapper(false);
        wrapper.eq(TableScheme.getColumn(scheme.getBizKeyField()), key);
        return wrapper;
    }

    protected QueryWrapper<P> getKeyWrapper(List<Serializable> keys) {
        TableScheme scheme = tableScheme();
        if (Objects.isNull(scheme.getBizKeyField())) {
            throw new IllegalArgumentException("当前entity实体没找到业务key字段，请在entity实体中使用@BizKey注解标记对应的字段！");
        }
        QueryWrapper<P> wrapper = getDefaultWrapper(false);
        wrapper.in(TableScheme.getColumn(scheme.getBizKeyField()), keys);
        return wrapper;
    }

    private void applySelect(Query<P> query, QueryWrapper<P> wrapper) {
        if (query.hasSelect()) {
            for (Object obj : query.getSelectColumns()) {
                String property = String.valueOf(obj);
                String column = tableScheme().containsField(property)
                        ? tableScheme().getField(property)
                        : TableScheme.toUnderline(property);
                wrapper.select(column);
            }
        }
    }

    private void applyGroupBy(Query<P> query, QueryWrapper<P> wrapper) {
        if (query.hasGroupBy()) {
            for (Object obj : query.getGroupByColumns()) {
                String property = String.valueOf(obj);
                String column = tableScheme().containsField(property)
                        ? tableScheme().getField(property)
                        : TableScheme.toUnderline(property);
                wrapper.groupBy(column);
            }
        }
    }

    private void having(Query<P> query, QueryWrapper<P> wrapper) {
        if (Objects.nonNull(query) && Objects.nonNull(query.getHaving()) && StrKit.hasText(query.getHaving())) {
            wrapper.having(query.getHaving());
        }
    }

    protected String getKeyValue(P persistenceObject) {
        try {
            Object value = tableScheme().getBizKeyField().get(persistenceObject);
            return Objects.nonNull(value) ? value.toString() : null;
        } catch (IllegalAccessException | IllegalArgumentException exception) {
            log.error("获取当前实体对象的key值出现错误！", exception);
            throw new IllegalArgumentException("获取当前实体对象的key值出现错误!");
        }
    }

    private void copyUpdatedPersistenceObject(P persistenceObject, AggregateRoot<?> aggregate) {
        TableScheme scheme = tableScheme();
        if (Objects.isNull(scheme.getId())) {
            return;
        }
        Serializable id = TableScheme.findFieldValue(persistenceObject, scheme.getId());
        if (Objects.nonNull(id)) {
            P updated = mapper().selectById(id);
            if (Objects.nonNull(updated)) {
                BeanKit.copy(updated, aggregate);
            }
        }
    }

    private void fillIfNecessary(Query<P> query, List<M> models) {
        if (Objects.nonNull(models) && !models.isEmpty()) {
            fill(query, models);
        }
    }

    private void insertFill(P persistenceObject) {
        try {
            TableScheme scheme = tableScheme();
            fillContextValue(scheme.getTenantId(), persistenceObject, ThreadContext.get(ContextConstants.TENANT_ID));
            fillContextValue(scheme.getSystemId(), persistenceObject, ThreadContext.get(ContextConstants.SYSTEM_ID));
            fillLogicDeleteValue(persistenceObject);
            for (Field field : scheme.getOnCreateFields()) {
                fillDateField(field, persistenceObject);
            }
        } catch (IllegalAccessException ignored) {
            log.debug("Ignore insert fill failure: {}", ignored.getMessage());
        }
    }

    private void updateFill(P persistenceObject) {
        try {
            for (Field field : tableScheme().getOnUpdateFields()) {
                fillDateField(field, persistenceObject);
            }
        } catch (IllegalAccessException ignored) {
            log.debug("Ignore update fill failure: {}", ignored.getMessage());
        }
    }

    private void fillContextValue(Field field, P persistenceObject, String value) throws IllegalAccessException {
        if (Objects.isNull(field) || Objects.isNull(value)) {
            return;
        }
        if (field.getType() == Long.class) {
            field.set(persistenceObject, Long.valueOf(value));
        } else if (field.getType() == Integer.class) {
            field.set(persistenceObject, Integer.valueOf(value));
        } else if (field.getType() == String.class) {
            field.set(persistenceObject, value);
        }
    }

    private void fillLogicDeleteValue(P persistenceObject) throws IllegalAccessException {
        Field field = tableScheme().getTableLogic();
        if (Objects.isNull(field)) {
            return;
        }
        TableLogic tableLogic = field.getAnnotation(TableLogic.class);
        String defaultValue = tableLogic.value();
        if (Objects.isNull(defaultValue) || !StrKit.hasText(defaultValue)) {
            if (field.getType().equals(Boolean.class) || field.getType().equals(boolean.class)) {
                field.set(persistenceObject, false);
            } else if (field.getType().equals(Integer.class) || field.getType().equals(int.class)) {
                field.set(persistenceObject, 0);
            }
        } else if (field.getType().equals(Boolean.class) || field.getType().equals(boolean.class)) {
            field.set(persistenceObject, defaultValue.equals("0"));
        } else if (field.getType().equals(Integer.class) || field.getType().equals(int.class)) {
            field.set(persistenceObject, Integer.valueOf(defaultValue));
        }
    }

    private void fillDateField(Field field, P persistenceObject) throws IllegalAccessException {
        if (field.getType().equals(LocalDateTime.class) && Objects.isNull(field.get(persistenceObject))) {
            field.set(persistenceObject, LocalDateTime.now());
        } else if (field.getType().equals(LocalDate.class) && Objects.isNull(field.get(persistenceObject))) {
            field.set(persistenceObject, LocalDate.now());
        }
    }

    public static class TableScheme {

        private static final Pattern HUMP_PATTERN = Pattern.compile("[A-Z]");

        private String tableName;
        private Field bizKeyField;
        private Map<String, String> field2Column;
        private Field id;
        private Field tenantId;
        private Field systemId;
        private Field tableLogic;
        private List<Field> onCreateFields = new ArrayList<>();
        private List<Field> onUpdateFields = new ArrayList<>();
        private String[] defaultOrderBy;

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public Field getBizKeyField() {
            return bizKeyField;
        }

        public Field getId() {
            return id;
        }

        public Field getTenantId() {
            return tenantId;
        }

        public Field getSystemId() {
            return systemId;
        }

        public Field getTableLogic() {
            return tableLogic;
        }

        public List<Field> getOnCreateFields() {
            return onCreateFields;
        }

        public List<Field> getOnUpdateFields() {
            return onUpdateFields;
        }

        public String[] getDefaultOrderBy() {
            return defaultOrderBy;
        }

        public void setDefaultOrderBy(String[] defaultOrderBy) {
            this.defaultOrderBy = defaultOrderBy;
        }

        protected static String toUnderline(String humpString) {
            if (Objects.isNull(humpString) || !StrKit.hasText(humpString)) {
                return humpString;
            }
            Matcher matcher = HUMP_PATTERN.matcher(humpString);
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                matcher.appendReplacement(buffer, "_" + matcher.group(0).toLowerCase());
            }
            matcher.appendTail(buffer);
            return buffer.toString();
        }

        protected static <T> T findFieldValue(Object object, Field field) {
            try {
                if (!field.canAccess(object)) {
                    field.setAccessible(true);
                }
                return (T) field.get(object);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Unable to read field: " + field.getName(), exception);
            }
        }

        protected static String getColumn(Field field) {
            TableField tableField = field.getAnnotation(TableField.class);
            return Objects.nonNull(tableField) && Objects.nonNull(tableField.value()) && StrKit.hasText(tableField.value())
                    ? tableField.value()
                    : TableScheme.toUnderline(field.getName());
        }

        protected static TableScheme build(Class<?> persistenceObjectClass) {
            if (Objects.isNull(persistenceObjectClass)) {
                return null;
            }
            TableName table = persistenceObjectClass.getAnnotation(TableName.class);
            if (Objects.isNull(table)) {
                throw new IllegalArgumentException("PO class must annotated with @TableName(\"table_name\")");
            }
            TableScheme scheme = new TableScheme();
            scheme.setTableName(table.value());
            List<Field> fields = FieldUtils.getAllFieldsList(persistenceObjectClass)
                    .stream()
                    .filter(field -> !Modifier.isStatic(field.getModifiers()))
                    .collect(Collectors.toList());
            scheme.field2Column = new HashMap<>(fields.size());
            for (Field field : fields) {
                field.setAccessible(true);
                String fieldName = field.getName();
                scheme.field2Column.put(fieldName.toLowerCase(), getColumn(field));
                if (Objects.nonNull(field.getAnnotation(BizKey.class))) {
                    scheme.bizKeyField = field;
                }
                if (field.isAnnotationPresent(TableId.class)) {
                    scheme.id = field;
                }
                if (field.isAnnotationPresent(TenantId.class)) {
                    scheme.tenantId = field;
                }
                if (field.isAnnotationPresent(SystemId.class)) {
                    scheme.systemId = field;
                }
                if (field.isAnnotationPresent(TableLogic.class)) {
                    scheme.tableLogic = field;
                }
                if (field.isAnnotationPresent(OnCreate.class)) {
                    scheme.onCreateFields.add(field);
                }
                if (field.isAnnotationPresent(OnUpdate.class)) {
                    scheme.onUpdateFields.add(field);
                }
            }
            OrderBy orderBy = persistenceObjectClass.getAnnotation(OrderBy.class);
            if (Objects.isNull(orderBy) && Objects.nonNull(persistenceObjectClass.getSuperclass())) {
                orderBy = persistenceObjectClass.getSuperclass().getAnnotation(OrderBy.class);
            }
            if (Objects.nonNull(orderBy) && Objects.nonNull(orderBy.value()) && orderBy.value().length != 0) {
                scheme.setDefaultOrderBy(orderBy.value());
            }
            return scheme;
        }

        protected boolean containsField(String field) {
            return Objects.nonNull(field2Column) && Objects.nonNull(field) && field2Column.containsKey(field.toLowerCase());
        }

        protected String getField(String field) {
            return field2Column.get(field.toLowerCase());
        }

        protected boolean containsColumn(String column) {
            return Objects.nonNull(field2Column) && Objects.nonNull(column) && field2Column.containsValue(column.toLowerCase());
        }
    }
}
