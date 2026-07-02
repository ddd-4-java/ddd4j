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
import io.ddd4j.core.domain.contract.Page;
import io.ddd4j.core.domain.contract.Query;
import io.ddd4j.core.domain.model.AggregateRoot;
import io.ddd4j.core.domain.model.DomainObjectMapper;
import io.ddd4j.core.domain.repository.RepositoryRegistry;
import io.ddd4j.core.domain.repository.RichRepository;
import io.ddd4j.core.util.MappingKit;
import io.ddd4j.data.mybatis.annotation.BizKey;
import io.ddd4j.data.mybatis.annotation.OnCreate;
import io.ddd4j.data.mybatis.annotation.OnUpdate;
import io.ddd4j.data.mybatis.annotation.OrderBy;
import io.ddd4j.data.mybatis.annotation.SystemId;
import io.ddd4j.data.mybatis.annotation.TenantId;
import io.ddd4j.data.mybatis.config.BaseDataProperties;
import io.ddd4j.kit.lang.BeanKit;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

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
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.ddd4j.core.domain.contract.Query.END_QUERY;
import static io.ddd4j.core.domain.contract.Query.EXCLUDE_FIELDS;
import static io.ddd4j.core.domain.contract.Query.IN_JSON_QUERY;
import static io.ddd4j.core.domain.contract.Query.IN_QUERY;
import static io.ddd4j.core.domain.contract.Query.LIKE_LEFT_QUERY;
import static io.ddd4j.core.domain.contract.Query.LIKE_QUERY;
import static io.ddd4j.core.domain.contract.Query.LIKE_RIGHT_QUERY;
import static io.ddd4j.core.domain.contract.Query.MAX_EQUALS_QUERY;
import static io.ddd4j.core.domain.contract.Query.MAX_QUERY;
import static io.ddd4j.core.domain.contract.Query.MIN_EQUALS_QUERY;
import static io.ddd4j.core.domain.contract.Query.MIN_QUERY;
import static io.ddd4j.core.domain.contract.Query.NOT_IN_QUERY;
import static io.ddd4j.core.domain.contract.Query.NOT_LIKE_QUERY;
import static io.ddd4j.core.domain.contract.Query.NOT_QUERY;
import static io.ddd4j.core.domain.contract.Query.NULL_QUERY;
import static io.ddd4j.core.domain.contract.Query.ORS_QUERY;
import static io.ddd4j.core.domain.contract.Query.START_QUERY;

/**
 * MyBatis-Plus repository base for rich aggregate roots.
 *
 * <p>该类吸收旧 ddd4j {@code BaseRepositoryImpl} 的 Query 后缀条件、租户、
 * BizKey、fill、分页和保存后回填语义，但只暴露 {@link RichRepository} SPI。
 * MyBatis-Plus 仍限定在基础设施层，聚合根不感知 ORM。</p>
 *
 * @param <M>  aggregate root type
 * @param <P>  persistence object type, usually named {@code *PO}
 * @param <ID> aggregate identity type
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public abstract class MybatisAggregateRepository<M extends AggregateRoot<?>, P, ID extends Serializable>
        implements RichRepository<M, ID>, DomainObjectMapper<M, P>, Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(MybatisAggregateRepository.class);

    private BaseMapper<P> mapper;
    private Class<M> modelClass;
    private Class<P> persistenceObjectClass;
    private Class<? extends Query> queryClass;
    private TableScheme tableScheme;
    private BaseDataProperties baseDataProperties;

    protected MybatisAggregateRepository() {
        configureTypes(resolveModelClass(), resolvePersistenceObjectClass(), null);
    }

    protected MybatisAggregateRepository(BaseMapper<P> mapper) {
        this();
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
        this.queryClass = queryClass;
        if (Objects.nonNull(persistenceObjectClass)) {
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

    public void setBaseDataProperties(BaseDataProperties baseDataProperties) {
        this.baseDataProperties = baseDataProperties;
    }

    protected BaseDataProperties baseDataProperties() {
        return baseDataProperties;
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
    public Page<M> page(Query query) {
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
    public boolean update(AggregateRoot<?> aggregate, Query query) {
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
        if (Objects.isNull(key) || !StringUtils.hasLength(key)) {
            throw new IllegalArgumentException("当前entity实体业务key字段为null，无法适用当前方法更新！");
        }
        updateFill(persistenceObject);
        return SqlHelper.retBool(mapper().update(persistenceObject, getKeyWrapper(key)));
    }

    public M getByKey(String key) {
        if (Objects.isNull(key) || !StringUtils.hasLength(key)) {
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
    public void fill(Query query, AggregateRoot<?> model) {
        if (modelClass().isInstance(model)) {
            fill(query, List.of(modelClass().cast(model)));
        }
    }

    @Override
    public void fill(Query query, List<M> models) {
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

    protected QueryWrapper<P> getBaseWrapper(Query query) {
        QueryWrapper<P> wrapper = getDefaultWrapper(query.isIgnoreTenantId());
        select(query, wrapper);
        where(query, wrapper);
        groupBy(query, wrapper);
        having(query, wrapper);
        orderBy(query, wrapper);
        return wrapper;
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

    private void select(Query query, QueryWrapper<P> wrapper) {
        if (Objects.nonNull(query.getSelect()) && StringUtils.hasLength(query.getSelect())) {
            wrapper.select(query.getSelect().split(query.getSplit()));
        }
    }

    private void where(Query query, QueryWrapper<P> wrapper) {
        if (Objects.nonNull(query.getKeyword()) && Objects.nonNull(query.getFields()) && StringUtils.hasLength(query.getFields())) {
            for (String field : query.getFields().split(query.getSplit())) {
                if (StringUtils.hasLength(field)) {
                    if (Objects.isNull(query.getOrs())) {
                        query.setOrs(new HashMap<>());
                    }
                    query.getOrs().putIfAbsent(field, query.getKeyword());
                }
            }
        }
        Map<String, Object> params = BeanKit.toMapClean(query);
        if (Objects.nonNull(params)) {
            params.forEach((key, value) -> setCondition(query, wrapper, key, value));
        }
    }

    @SuppressWarnings("unchecked")
    private QueryWrapper<P> setCondition(Query query, QueryWrapper<P> wrapper, String key, Object value) {
        if (Objects.isNull(value) || EXCLUDE_FIELDS.contains(key)) {
            return wrapper;
        }
        if (Objects.equals(key, ORS_QUERY) && value instanceof Map) {
            Map<String, Object> ors = (Map<String, Object>) value;
            if (!ors.isEmpty() && ors.values().stream().anyMatch(this::isStringNotBlank)) {
                wrapper.and(w -> ors.forEach((orKey, orValue) -> {
                    if (isStringNotBlank(orValue)) {
                        setSimpleCondition(query, w, orKey, orValue);
                    }
                    w.or();
                }));
            }
            return wrapper;
        }
        return setSimpleCondition(query, wrapper, key, value);
    }

    private QueryWrapper<P> setSimpleCondition(Query query, QueryWrapper<P> wrapper, String key, Object value) {
        if (Objects.isNull(value)) {
            return wrapper;
        }
        if (key.endsWith(START_QUERY) && isDateType(value)) {
            getColumnByField(getFieldName(key, START_QUERY), column -> wrapper.ge(column, value));
        } else if (key.endsWith(END_QUERY) && isDateType(value)) {
            getColumnByField(getFieldName(key, END_QUERY), column -> wrapper.le(column, value));
        } else if (key.endsWith(MIN_EQUALS_QUERY)) {
            getColumnByField(getFieldName(key, MIN_EQUALS_QUERY), column -> wrapper.ge(column, value));
        } else if (key.endsWith(MAX_EQUALS_QUERY)) {
            getColumnByField(getFieldName(key, MAX_EQUALS_QUERY), column -> wrapper.le(column, value));
        } else if (key.endsWith(MIN_QUERY)) {
            getColumnByField(getFieldName(key, MIN_QUERY), column -> wrapper.gt(column, value));
        } else if (key.endsWith(MAX_QUERY)) {
            getColumnByField(getFieldName(key, MAX_QUERY), column -> wrapper.lt(column, value));
        } else if (key.endsWith(NOT_IN_QUERY) && value instanceof Collection<?> collection && !collection.isEmpty()) {
            getColumnByField(getFieldName(key, NOT_IN_QUERY), column -> wrapper.notIn(column, distinct(collection)));
        } else if (key.endsWith(NOT_IN_QUERY) && value instanceof String text && StringUtils.hasLength(text)) {
            getColumnByField(getFieldName(key, NOT_IN_QUERY), column -> wrapper.notIn(column, splitValues(query, text)));
        } else if (key.endsWith(IN_QUERY) && value instanceof Collection<?> collection && !collection.isEmpty()) {
            getColumnByField(getFieldName(key, IN_QUERY), column -> wrapper.in(column, distinct(collection)));
        } else if (key.endsWith(IN_QUERY) && value instanceof String text && StringUtils.hasLength(text)) {
            getColumnByField(getFieldName(key, IN_QUERY), column -> wrapper.in(column, splitValues(query, text)));
        } else if (key.endsWith(LIKE_QUERY) && value instanceof CharSequence && isStringNotBlank(value)) {
            getColumnByField(getFieldName(key, LIKE_QUERY), column -> wrapper.like(column, value));
        } else if (key.endsWith(LIKE_LEFT_QUERY) && value instanceof CharSequence && isStringNotBlank(value)) {
            getColumnByField(getFieldName(key, LIKE_LEFT_QUERY), column -> wrapper.likeLeft(column, value));
        } else if (key.endsWith(LIKE_RIGHT_QUERY) && value instanceof CharSequence && isStringNotBlank(value)) {
            getColumnByField(getFieldName(key, LIKE_RIGHT_QUERY), column -> wrapper.likeRight(column, value));
        } else if (key.endsWith(NOT_LIKE_QUERY) && value instanceof CharSequence && isStringNotBlank(value)) {
            getColumnByField(getFieldName(key, NOT_LIKE_QUERY), column -> wrapper.notLike(column, value));
        } else if (key.endsWith(NOT_QUERY)) {
            getColumnByField(getFieldName(key, NOT_QUERY), column -> wrapper.ne(column, value));
        } else if (key.contains(IN_JSON_QUERY)) {
            applyJsonCondition(wrapper, key, value);
        } else if (key.endsWith(NULL_QUERY)) {
            if (Objects.equals(Boolean.TRUE, value)) {
                getColumnByField(getFieldName(key, NULL_QUERY), wrapper::isNull);
            } else if (Objects.equals(Boolean.FALSE, value)) {
                getColumnByField(getFieldName(key, NULL_QUERY), wrapper::isNotNull);
            }
        } else {
            getColumnByField(key, column -> wrapper.eq(column, value));
        }
        return wrapper;
    }

    private void applyJsonCondition(QueryWrapper<P> wrapper, String key, Object value) {
        String[] parts = key.split(IN_JSON_QUERY);
        if (parts.length != 2) {
            return;
        }
        String jsonField = parts[0];
        String jsonColumn = parts[1].toLowerCase();
        String formattedValue = formatJsonValue(value);
        if (Objects.nonNull(formattedValue)) {
            getColumnByField(jsonField, column -> wrapper.apply("JSON_CONTAINS({0}, '{1}', '${2}')", column, formattedValue, jsonColumn));
        }
    }

    private String formatJsonValue(Object value) {
        if (value instanceof String text) {
            return "\"" + text + "\"";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof LocalDateTime localDateTime) {
            return "\"" + DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(localDateTime) + "\"";
        } else if (value instanceof LocalDate localDate) {
            return "\"" + DateTimeFormatter.ISO_LOCAL_DATE.format(localDate) + "\"";
        } else if (value instanceof LocalTime localTime) {
            return "\"" + DateTimeFormatter.ISO_LOCAL_TIME.format(localTime) + "\"";
        }
        return null;
    }

    private void groupBy(Query query, QueryWrapper<P> wrapper) {
        if (Objects.nonNull(query) && Objects.nonNull(query.getGroupBy()) && StringUtils.hasLength(query.getGroupBy())) {
            wrapper.groupBy(query.getGroupBy());
        }
    }

    private void having(Query query, QueryWrapper<P> wrapper) {
        if (Objects.nonNull(query) && Objects.nonNull(query.getHaving()) && StringUtils.hasLength(query.getHaving())) {
            wrapper.having(query.getHaving());
        }
    }

    private void orderBy(Query query, QueryWrapper<P> wrapper) {
        TableScheme scheme = tableScheme();
        if (Objects.isNull(query.getOrderBys()) || !StringUtils.hasLength(query.getOrderBys())) {
            if (Objects.nonNull(scheme.getDefaultOrderBy()) && scheme.getDefaultOrderBy().length > 0) {
                query.setOrderBys(String.join(query.getSplit(), scheme.getDefaultOrderBy()));
            }
        }
        if (Objects.nonNull(query.getOrderBys())) {
            for (String orderBy : query.getOrderBys().split(query.getSplit())) {
                if (Objects.nonNull(orderBy) && StringUtils.hasLength(orderBy)) {
                    String column = TableScheme.toUnderline(orderBy
                            .replace("_asc", "")
                            .replace("_ASC", "")
                            .replace("_desc", "")
                            .replace("_DESC", ""));
                    wrapper.orderBy(scheme.containsColumn(column), !orderBy.toLowerCase().endsWith("_desc"), column);
                }
            }
        }
    }

    protected void getColumnByField(String fieldName, Consumer<String> action) {
        TableScheme scheme = tableScheme();
        if (scheme.containsField(fieldName)) {
            action.accept(scheme.getField(fieldName));
        }
    }

    private String getFieldName(String fieldName, String queryAction) {
        String replaced = replaceLast(fieldName, queryAction, "");
        return Objects.nonNull(replaced) && StringUtils.hasLength(replaced) ? replaced.toLowerCase() : replaced;
    }

    private static String replaceLast(String raw, String match, String replace) {
        if (Objects.isNull(raw) || !StringUtils.hasLength(raw) || Objects.isNull(replace)) {
            return raw;
        }
        StringBuilder builder = new StringBuilder(raw);
        int lastIndexOf = builder.lastIndexOf(match);
        if (lastIndexOf == -1) {
            return raw;
        }
        return builder.replace(lastIndexOf, lastIndexOf + match.length(), replace).toString();
    }

    private List<?> distinct(Collection<?> values) {
        return values.stream().distinct().collect(Collectors.toList());
    }

    private List<String> splitValues(Query query, String value) {
        return Arrays.stream(value.split(query.getSplit()))
                .distinct()
                .filter(StringUtils::hasLength)
                .collect(Collectors.toList());
    }

    protected boolean isDateType(Object value) {
        return value instanceof Date || value instanceof LocalDateTime || value instanceof LocalDate || value instanceof LocalTime;
    }

    private boolean isStringNotBlank(Object value) {
        return Objects.nonNull(value) && (!(value instanceof CharSequence text) || text.length() != 0);
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

    private void fillIfNecessary(Query query, List<M> models) {
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
        if (Objects.isNull(defaultValue) || !StringUtils.hasLength(defaultValue)) {
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

    protected QueryChainWrapper<P> query() {
        return new QueryChainWrapper<>(mapper());
    }

    protected LambdaQueryChainWrapper<P> lambdaQuery() {
        return new LambdaQueryChainWrapper<>(mapper());
    }

    protected UpdateChainWrapper<P> update() {
        return new UpdateChainWrapper<>(mapper());
    }

    protected LambdaUpdateChainWrapper<P> lambdaUpdate() {
        return new LambdaUpdateChainWrapper<>(mapper());
    }

    protected ChainQuery<P> chainQuery() {
        return lambdaQuery();
    }

    protected ChainUpdate<P> chainUpdate() {
        return lambdaUpdate();
    }

    protected LambdaQueryWrapper<P> lambdaQueryWrapper() {
        return Wrappers.lambdaQuery();
    }

    protected QueryWrapper<P> queryWrapper() {
        return Wrappers.query();
    }

    protected LambdaUpdateWrapper<P> lambdaUpdateWrapper() {
        return Wrappers.lambdaUpdate();
    }

    protected UpdateWrapper<P> updateWrapper() {
        return Wrappers.update();
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
            if (Objects.isNull(humpString) || !StringUtils.hasLength(humpString)) {
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
            return Objects.nonNull(tableField) && Objects.nonNull(tableField.value()) && StringUtils.hasLength(tableField.value())
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
