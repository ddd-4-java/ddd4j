package io.ddd4j.data.mybatis.repository.impl;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.ReflectionKit;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.contract.BaseRepository;
import io.ddd4j.core.contract.Model;
import io.ddd4j.core.contract.Page;
import io.ddd4j.core.contract.Query;
import io.ddd4j.core.contract.constant.ContextConstants;
import io.ddd4j.kit.lang.BeanKit;
import io.ddd4j.kit.lang.JsonKit;
import io.ddd4j.core.util.MappingKit;
import io.ddd4j.data.mybatis.annotation.*;
import io.ddd4j.data.mybatis.config.BaseDataProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.ibatis.binding.MapperMethod.ParamMap;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.ddd4j.core.contract.Query.*;

@Slf4j(topic = "### BASE-DATA : BaseRepository ###")
public abstract class BaseRepositoryImpl<MP extends BaseMapper<P>, M extends Model, P, Q extends Query> implements BaseRepository<M, Q>, Serializable {
    private MP mapper;
    private final TableScheme tableScheme;
    private BaseDataProperties baseDataProperties;
    private static final String[] LOG_IGNORE_FIELDS = new String[]{"limit", "page", "orderBy"};

    /**
     * 获取 mapper（子类/桥接层通过 {@link #setMapper} 注入）。
     */
    public MP getMapper() {
        return mapper;
    }

    /**
     * Spring 桥接层（ddd4j-data-spring 的 RepositoryBeanPostProcessor）通过此方法注入 mapper。
     */
    public void setMapper(MP mapper) {
        this.mapper = mapper;
    }

    /**
     * Spring 桥接层通过此方法注入配置属性。
     */
    public void setBaseDataProperties(BaseDataProperties baseDataProperties) {
        this.baseDataProperties = baseDataProperties;
    }

    public BaseRepositoryImpl() {
        final Class<M> modelClass = (Class<M>) ReflectionKit.getSuperClassGenericType(this.getClass(), BaseRepositoryImpl.class, 1);
        final Class<P> poClass = (Class<P>) ReflectionKit.getSuperClassGenericType(this.getClass(), BaseRepositoryImpl.class, 2);
        final Class<Q> queryClass = (Class<Q>) ReflectionKit.getSuperClassGenericType(this.getClass(), BaseRepositoryImpl.class, 3);
        this.tableScheme = TableScheme.build(poClass);
        BaseRepository.inject(modelClass, this.getClass());
        BaseRepository.inject(queryClass, this.getClass());
        MappingKit.map("MODEL_PO", modelClass, poClass);
        MappingKit.map("MODEL_PO", poClass, modelClass);
        MappingKit.map("MODEL_QUERY", modelClass, queryClass);
        MappingKit.map("MODEL_QUERY", queryClass, modelClass);
        // 首字母设为小写
        String modelClassName = modelClass.getSimpleName().toLowerCase().substring(0, 1) + modelClass.getSimpleName().substring(1);
        MappingKit.map("MODEL_NAME", modelClassName, modelClass);
    }

    @Override
    public boolean save(M model) {
        P po = convert(model);
        insertFill(po);
        boolean result = SqlHelper.retBool(this.mapper.insert(po));
        if (result) {
            BeanKit.copy(po, model);
        }
        return result;
    }

    @Override
    public boolean save(List<? extends Model> models) {
        if (models == null || models.size() == 0) {
            return false;
        }
        List<P> pos = convert(models);
        boolean result = insertBatch(pos);
        if (result) {
            BeanKit.copy(pos, models);
        }
        return result;
    }

    @Override
    public boolean update(M model) {
        P po = convert(model);
        updateFill(po);
        boolean result = SqlHelper.retBool(this.mapper.updateById(po));
        if (result) {
            P updated = this.mapper.selectById(TableScheme.findFieldValue(po, tableScheme.getId()));
            BeanKit.copy(updated, model);
        }
        return result;
    }

    @Override
    public boolean update(M model, Q query) {
        P po = convert(model);
        updateFill(po);
        boolean result = SqlHelper.retBool(this.mapper.update(po, this.getBaseWrapper(query)));
        if (result) {
            if (tableScheme.getId() != null) {
                Serializable id = TableScheme.findFieldValue(po, tableScheme.getId());
                if (id != null) {
                    P updated = this.mapper.selectById(id);
                    BeanKit.copy(updated, model);
                }
            }
        }
        log.debug("Query: {}, Params: {}", query.getClass().getSimpleName(), JsonKit.toJson(BeanKit.toMap(query, false, LOG_IGNORE_FIELDS)));
        return result;
    }

    @Override
    public boolean update(List<? extends Model> models) {
        if (models == null || models.isEmpty()) {
            return false;
        }
        List<P> pos = convert(models);
        boolean result = updateBatch(pos, 100);
        if (result) {
            BeanKit.copy(pos, models);
        }
        return result;
    }

    @Override
    public boolean saveOrUpdate(M model) {
        QueryWrapper<P> defaultWrapper = this.getDefaultWrapper(false);
        defaultWrapper.eq(TableScheme.getColumn(tableScheme.getId()), TableScheme.findFieldValue(model, tableScheme.getId()));
        Long exist = this.mapper.selectCount(defaultWrapper);
        if (Objects.equals(exist, 0L)) {
            return save(model);
        } else {
            return update(model);
        }
    }

    @Override
    public boolean delete(Serializable id) {
        return id != null && SqlHelper.retBool(this.mapper.deleteById(id));
    }

    @Override
    public boolean delete(Q query) {
        boolean result = SqlHelper.retBool(this.mapper.delete(this.getBaseWrapper(query)));
        log.debug("Query: {}, Params: {}", query.getClass().getSimpleName(), JsonKit.toJson(BeanKit.toMap(query, false, LOG_IGNORE_FIELDS)));
        return result;
    }

    @Override
    public boolean delete(List<? extends Serializable> ids) {
        if (ids == null || ids.isEmpty()) {
            log.warn("batch function query is empty or null");
            return false;
        }
        if (ids.size() >= 100) {
            throw new IllegalArgumentException("当前批量删除的ID不能大于100");
        }
        return SqlHelper.retBool(this.mapper.deleteBatchIds(ids));
    }

    @Override
    public List<Map<String, Object>> maps(Q query) {
        List<Map<String, Object>> maps = this.mapper.selectMaps(this.getBaseWrapper(query));
        log.debug("Query: {}, Params: {}", query.getClass().getSimpleName(), JsonKit.toJson(BeanKit.toMap(query, false, LOG_IGNORE_FIELDS)));
        return maps;
    }

    @Override
    public List<M> list(List<? extends Serializable> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("Error: ids must not be empty");
        }
        if (ids.size() >= 100) {
            throw new IllegalArgumentException("当前批量查询的ID不能大于100");
        }
        return convert(this.mapper.selectBatchIds(ids));
    }

    @Override
    public List<M> list(Q query) {
        List<M> models = convert(this.mapper.selectList(this.getBaseWrapper(query)));
        log.debug("Query: {}, Params: {}", query.getClass().getSimpleName(), JsonKit.toJson(BeanKit.toMap(query, false, LOG_IGNORE_FIELDS)));
        if (models != null && !models.isEmpty()) {
            fill(query, models);
        }
        return models;
    }

    @Override
    public M first(Q query) {
        QueryWrapper<P> wrapper = this.getBaseWrapper(query);
        wrapper.last("LIMIT 1");
        List<P> list = this.mapper.selectList(wrapper);
        M model = convert(list.isEmpty() ? null : list.get(0));
        log.debug("Query: {}, Params: {}", query.getClass().getSimpleName(), JsonKit.toJson(BeanKit.toMap(query, false, LOG_IGNORE_FIELDS)));
        if (model != null) {
            fill(query, model);
        }
        return model;
    }

    @Override
    public M one(Q query) {
        M model = convert(this.mapper.selectOne(this.getBaseWrapper(query)));
        log.debug("Query: {}, Params: {}", query.getClass().getSimpleName(), JsonKit.toJson(BeanKit.toMap(query, false, LOG_IGNORE_FIELDS)));
        if (model != null) {
            fill(query, model);
        }
        return model;
    }

    @Override
    public M get(@Nonnull Serializable id) {
        return convert(this.mapper.selectById(id));
    }

    @Override
    public int count(Q query) {
        Long count = this.mapper.selectCount(this.getBaseWrapper(query));
        log.debug("Query: {}, Params: {}", query.getClass().getSimpleName(), JsonKit.toJson(BeanKit.toMap(query, false, LOG_IGNORE_FIELDS)));
        return count != null ? count.intValue() : 0;
    }

    @Override
    public boolean exist(Q query) {
        return SqlHelper.retBool(count(query));
    }

    @Override
    public Page<M> page(Q query) {
        QueryWrapper<P> wrapper = this.getBaseWrapper(query);
        // 如果忽略分页，则直接查询列表
        if (query.getSize() < 0) {
            List<P> list = this.mapper.selectList(wrapper);
            List<M> records = convert(list);
            Page<M> modelPage = Page.succeed(records, records.size(), 1, records.size());
            if (records != null && !records.isEmpty()) {
                fill(query, records);
            }
            return modelPage;
        }

        // 使用 MyBatis Plus 官方分页（PaginationInnerInterceptor 自动注入 LIMIT + COUNT）
        long current = query.getCurrent() < 1 ? 1 : query.getCurrent();
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<P> mpPage = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, query.getSize());
        com.baomidou.mybatisplus.core.metadata.IPage<P> result = this.mapper.selectPage(mpPage, wrapper);

        List<M> records = convert(result.getRecords());
        Page<M> modelPage = Page.succeed(records, result.getTotal(), query.getCurrent(), query.getSize());
        if (records != null && !records.isEmpty()) {
            fill(query, records);
        }
        return modelPage;
    }

    @Override
    public boolean deleteByKey(@Nonnull Serializable key) {
        return SqlHelper.retBool(this.mapper.delete(this.getKeyWrapper(key)));
    }

    @Override
    public boolean deleteByKeys(List<Serializable> keys) {
        return keys != null && !keys.isEmpty() && SqlHelper.retBool(this.mapper.delete(this.getKeyWrapper(keys)));
    }

    @Override
    public boolean updateByKey(M model) {
        P po = convert(model);
        String key = this.getKeyValue(po);
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("当前entity实体业务key字段为null，无法适用当前方法更新！");
        } else {
            updateFill(po);
            return SqlHelper.retBool(this.mapper.update(po, this.getKeyWrapper(key)));
        }
    }

    @Override
    public boolean updateByKey(List<? extends Model> models) {
        if (models == null || models.isEmpty()) {
            log.warn("batch function query is empty or null");
            return false;
        }
        for (Model model : models) {
            this.updateByKey((M) model);
        }
        return true;
    }

    @Override
    public M getByKey(String key) {
        if (key == null || key.isEmpty()) {
            log.warn("The key annotated by @BizKey must not blank");
            return null;
        }
        return convert(this.mapper.selectOne(this.getKeyWrapper(key)));
    }

    @Override
    public List<M> listByKey(List<Serializable> keys) {
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        if (keys.size() >= 100) {
            throw new IllegalArgumentException("批量查询的业务key不能大于100");
        }
        return convert(this.mapper.selectList(this.getKeyWrapper(keys)));
    }

    @Override
    public void fill(Q query, M model) {
        fill(query, Collections.singletonList(model));
    }

    @Override
    public void fill(Q query, List<M> models) {
    }

    protected QueryWrapper<P> getDefaultWrapper(boolean ignoreTenantId) {
        QueryWrapper<P> wrapper = new QueryWrapper<>();
        if (!ignoreTenantId) {
            String tenantId = ThreadContext.get(ContextConstants.TENANT_ID);
            if (tableScheme.getTenantId() != null && tenantId != null) {
                wrapper.eq(TableScheme.getColumn(tableScheme.getTenantId()), tenantId);
            }
        }
        String systemId = ThreadContext.get(ContextConstants.SYSTEM_ID);
        if (tableScheme.getSystemId() != null && systemId != null) {
            wrapper.eq(TableScheme.getColumn(tableScheme.getSystemId()), systemId);
        }
        return wrapper;
    }

    protected QueryWrapper<P> getKeyWrapper(Serializable key) {
        if (tableScheme.getBizKeyField() == null) {
            throw new IllegalArgumentException("当前entity实体没找到业务key字段，请在entity实体中使用@BizKey注解标记对应的字段！");
        } else {
            QueryWrapper<P> wrapper = getDefaultWrapper(false);
            wrapper.eq(TableScheme.getColumn(tableScheme.getBizKeyField()), key);
            return wrapper;
        }
    }

    protected QueryWrapper<P> getKeyWrapper(List<Serializable> keys) {
        if (tableScheme.getBizKeyField() == null) {
            throw new IllegalArgumentException("当前entity实体没找到业务key字段，请在entity实体中使用@BizKey注解标记对应的字段！");
        } else {
            QueryWrapper<P> wrapper = getDefaultWrapper(false);
            wrapper.in(TableScheme.getColumn(tableScheme.getBizKeyField()), keys);
            return wrapper;
        }
    }

    protected QueryWrapper<P> getBaseWrapper(Q query) {
        QueryWrapper<P> wrapper = getDefaultWrapper(query.isIgnoreTenantId());
        this.select(query, wrapper).where(query, wrapper).groupBy(query, wrapper).having(query, wrapper).orderBy(query, wrapper);
        return wrapper;
    }

    protected boolean insertBatch(List<P> pos) {
        if (pos == null || pos.isEmpty()) {
            return false;
        }
        for (P po : pos) {
            insertFill(po);
            this.mapper.insert(po);
        }
        return true;
    }

    public boolean updateBatch(List<P> pos, int batchSize) {
        if (pos == null || pos.isEmpty()) {
            log.warn("batch function query is empty or null");
            return false;
        }
        for (P po : pos) {
            updateFill(po);
            this.mapper.updateById(po);
        }
        return true;
    }

    protected boolean isCollectionType(Object o) {
        return o instanceof Collection;
    }

    protected boolean isDateType(Object o) {
        return o instanceof Date || o instanceof LocalDateTime || o instanceof LocalDate || o instanceof LocalTime;
    }

    private boolean isStringNotBlank(Object value) {
        return value != null && (!(value instanceof CharSequence) || ((CharSequence) value).length() != 0);
    }

    private String getFieldName(String fieldName, String queryAction) {
        String replaceLast = replaceLast(fieldName, queryAction, "");
        return replaceLast != null && !replaceLast.isEmpty() ? replaceLast.toLowerCase() : replaceLast;
    }

    protected void getColumnByField(String fieldName, Consumer<String> action) {
        if (this.tableScheme.containsField(fieldName)) {
            action.accept(this.tableScheme.getField(fieldName));
        }
    }

    protected String getKeyValue(P po) {
        try {
            Object value = this.tableScheme.bizKeyField.get(po);
            return value != null ? value.toString() : null;
        } catch (IllegalAccessException | IllegalArgumentException var3) {
            log.error("获取当前实体对象的key值出现错误！", var3);
            throw new IllegalArgumentException("获取当前实体对象的key值出现错误!");
        }
    }

    protected void setKeyValue(P po, String key) {
        try {
            this.tableScheme.bizKeyField.set(po, key);
        } catch (IllegalAccessException | IllegalArgumentException var4) {
            log.error("设置当前实体对象的key值出现错误！", var4);
            throw new IllegalArgumentException("设置当前实体对象的key值出现错误!");
        }
    }

    private BaseRepositoryImpl<MP, M, P, Q> select(Q query, QueryWrapper<P> baseWrapper) {
        if (query.getSelect() != null && !query.getSelect().isEmpty()) {
            baseWrapper.select(query.getSelect().split(query.getSplit()));
        }
        return this;
    }

    private BaseRepositoryImpl<MP, M, P, Q> where(Q query, QueryWrapper<P> baseWrapper) {
        // 关键字查询：keyword + fields 转 ors
        if (query.getKeyword() != null && query.getFields() != null && !query.getFields().isEmpty()) {
            for (String field : query.getFields().split(query.getSplit())) {
                if (!field.isEmpty()) {
                    if (query.getOrs() == null) {
                        query.setOrs(new HashMap<>());
                    }
                    query.getOrs().putIfAbsent(field, query.getKeyword());
                }
            }
        }
        // ors 查询：a = xx or b = yy
        if (query.getOrs() != null && !query.getOrs().isEmpty()) {
            Map<String, Object> ors = query.getOrs();
            if (ors.values().stream().anyMatch(this::isStringNotBlank)) {
                baseWrapper.and(w -> ors.forEach((k, v) -> {
                    if (isStringNotBlank(v)) {
                        getColumnByField(k.toLowerCase(), (p) -> w.eq(p, v).or());
                    }
                }));
            }
        }
        // 字段后缀自动条件构建：遍历 Query 所有字段，按后缀（Not/In/Like/Min/Max/Start/End/IsNull/InJson 等）自动生成查询条件
        Map<String, Object> mapParams = BeanKit.toMapClean(query);
        if (mapParams != null) {
            mapParams.forEach((k, v) -> this.setCondition(query, baseWrapper, k, v));
        }
        return this;
    }

    /**
     * 根据字段名后缀自动构建查询条件。
     *
     * <p>约定驱动：Query 字段名后缀决定 SQL 操作符：
     * <ul>
     *   <li>{@code xxxMin} → {@code xxx > value}</li>
     *   <li>{@code xxxMax} → {@code xxx < value}</li>
     *   <li>{@code xxxMinEq} → {@code xxx >= value}</li>
     *   <li>{@code xxxMaxEq} → {@code xxx <= value}</li>
     *   <li>{@code xxxStart} → {@code xxx >= value}（时间范围）</li>
     *   <li>{@code xxxEnd} → {@code xxx <= value}（时间范围）</li>
     *   <li>{@code xxxLike} → {@code xxx LIKE '%value%'}</li>
     *   <li>{@code xxxLikeLeft} → {@code xxx LIKE 'value%'}</li>
     *   <li>{@code xxxLikeRight} → {@code xxx LIKE '%value'}</li>
     *   <li>{@code xxxNotLike} → {@code xxx NOT LIKE '%value%'}</li>
     *   <li>{@code xxxNot} → {@code xxx != value}</li>
     *   <li>{@code xxxIn} → {@code xxx IN (values)}（集合或逗号分隔字符串）</li>
     *   <li>{@code xxxNotIn} → {@code xxx NOT IN (values)}</li>
     *   <li>{@code xxxIsNull} → {@code xxx IS NULL / IS NOT NULL}（true/false）</li>
     *   <li>{@code xxxInJson Yyy} → {@code JSON_CONTAINS(yyy, value, 'xxx')}</li>
     *   <li>其他 → {@code xxx = value}（精确匹配）</li>
     * </ul>
     */
    private QueryWrapper<P> setCondition(Query query, QueryWrapper<P> wrapper, String key, Object value) {
        if (value == null || EXCLUDE_FIELDS.contains(key)) {
            return wrapper;
        }
        // 处理 or 查询
        if (Objects.equals(key, ORS_QUERY) && value instanceof Map) {
            Map<String, Object> ors = (Map<String, Object>) value;
            if (!ors.isEmpty() && ors.values().stream().anyMatch(this::isStringNotBlank)) {
                wrapper.and(w -> ors.forEach((k, v) -> {
                    if (isStringNotBlank(v)) {
                        setSimpleCondition(query, w, k, v);
                    }
                    w.or();
                }));
            }
            return wrapper;
        }
        return setSimpleCondition(query, wrapper, key, value);
    }

    /**
     * 单字段条件构建（不含 or 逻辑）。
     */
    @SuppressWarnings("unchecked")
    private QueryWrapper<P> setSimpleCondition(Query query, QueryWrapper<P> wrapper, String key, Object value) {
        if (value == null) {
            return wrapper;
        }
        if (key.endsWith(START_QUERY) && this.isDateType(value)) {
            this.getColumnByField(this.getFieldName(key, START_QUERY), (p) -> wrapper.ge(p, value));
        } else if (key.endsWith(END_QUERY) && this.isDateType(value)) {
            this.getColumnByField(this.getFieldName(key, END_QUERY), (p) -> wrapper.le(p, value));
        } else if (key.endsWith(MIN_EQUALS_QUERY)) {
            this.getColumnByField(this.getFieldName(key, MIN_EQUALS_QUERY), (p) -> wrapper.ge(p, value));
        } else if (key.endsWith(MAX_EQUALS_QUERY)) {
            this.getColumnByField(this.getFieldName(key, MAX_EQUALS_QUERY), (p) -> wrapper.le(p, value));
        } else if (key.endsWith(MIN_QUERY)) {
            this.getColumnByField(this.getFieldName(key, MIN_QUERY), (p) -> wrapper.gt(p, value));
        } else if (key.endsWith(MAX_QUERY)) {
            this.getColumnByField(this.getFieldName(key, MAX_QUERY), (p) -> wrapper.lt(p, value));
        } else if (key.endsWith(NOT_IN_QUERY) && this.isCollectionType(value) && !((Collection<?>) value).isEmpty()) {
            List<?> list = ((Collection<?>) value).stream().distinct().collect(Collectors.toList());
            this.getColumnByField(this.getFieldName(key, NOT_IN_QUERY), (p) -> wrapper.notIn(p, list));
        } else if (key.endsWith(NOT_IN_QUERY) && value instanceof String && !((String) value).isEmpty()) {
            List<String> list = Arrays.stream(((String) value).split(query.getSplit())).distinct().filter(s -> !s.isEmpty()).collect(Collectors.toList());
            this.getColumnByField(this.getFieldName(key, NOT_IN_QUERY), (p) -> wrapper.notIn(p, list));
        } else if (key.endsWith(IN_QUERY) && this.isCollectionType(value) && !((Collection<?>) value).isEmpty()) {
            List<?> list = ((Collection<?>) value).stream().distinct().collect(Collectors.toList());
            this.getColumnByField(this.getFieldName(key, IN_QUERY), (p) -> wrapper.in(p, list));
        } else if (key.endsWith(IN_QUERY) && value instanceof String && !((String) value).isEmpty()) {
            List<String> list = Arrays.stream(((String) value).split(query.getSplit())).distinct().filter(s -> !s.isEmpty()).collect(Collectors.toList());
            this.getColumnByField(this.getFieldName(key, IN_QUERY), (p) -> wrapper.in(p, list));
        } else if (key.endsWith(LIKE_QUERY) && value instanceof CharSequence && this.isStringNotBlank(value)) {
            this.getColumnByField(this.getFieldName(key, LIKE_QUERY), (p) -> wrapper.like(p, value));
        } else if (key.endsWith(LIKE_LEFT_QUERY) && value instanceof CharSequence && this.isStringNotBlank(value)) {
            this.getColumnByField(this.getFieldName(key, LIKE_LEFT_QUERY), (p) -> wrapper.likeLeft(p, value));
        } else if (key.endsWith(LIKE_RIGHT_QUERY) && value instanceof CharSequence && this.isStringNotBlank(value)) {
            this.getColumnByField(this.getFieldName(key, LIKE_RIGHT_QUERY), (p) -> wrapper.likeRight(p, value));
        } else if (key.endsWith(NOT_LIKE_QUERY) && value instanceof CharSequence && this.isStringNotBlank(value)) {
            this.getColumnByField(this.getFieldName(key, NOT_LIKE_QUERY), (p) -> wrapper.notLike(p, value));
        } else if (key.endsWith(NOT_QUERY)) {
            this.getColumnByField(this.getFieldName(key, NOT_QUERY), (p) -> wrapper.ne(p, value));
        } else if (key.contains(IN_JSON_QUERY)) {
            // JSON 字段查询：如 extrasInJsonName=value → JSON_CONTAINS(extras, '"value"', 'name')
            String[] parts = key.split(IN_JSON_QUERY);
            if (parts.length == 2) {
                String jsonField = parts[0];
                String jsonColumn = parts[1].toLowerCase();
                String formattedValue = formatJsonValue(value);
                if (formattedValue != null) {
                    this.getColumnByField(jsonField, (col) -> wrapper.apply("JSON_CONTAINS({0}, '{1}', '${2}')", col, formattedValue, jsonColumn));
                }
            }
        } else if (key.endsWith(NULL_QUERY)) {
            if (Objects.equals(Boolean.TRUE, value)) {
                this.getColumnByField(this.getFieldName(key, NULL_QUERY), wrapper::isNull);
            } else if (Objects.equals(Boolean.FALSE, value)) {
                this.getColumnByField(this.getFieldName(key, NULL_QUERY), wrapper::isNotNull);
            }
        } else {
            // 默认精确匹配
            this.getColumnByField(key, (p) -> wrapper.eq(p, value));
        }
        return wrapper;
    }

    /**
     * 将查询值格式化为 JSON 字符串（用于 JSON_CONTAINS）。
     */
    private String formatJsonValue(Object value) {
        if (value instanceof String) {
            return "\"" + value + "\"";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof LocalDateTime) {
            return "\"" + DateTimeFormatter.ISO_LOCAL_DATE_TIME.format((LocalDateTime) value) + "\"";
        } else if (value instanceof LocalDate) {
            return "\"" + DateTimeFormatter.ISO_LOCAL_DATE.format((LocalDate) value) + "\"";
        } else if (value instanceof LocalTime) {
            return "\"" + DateTimeFormatter.ISO_LOCAL_TIME.format((LocalTime) value) + "\"";
        }
        return null;
    }

    private BaseRepositoryImpl<MP, M, P, Q> groupBy(Q query, QueryWrapper<P> wrapper) {
        if (query != null && query.getGroupBy() != null && !query.getGroupBy().isEmpty()) {
            wrapper.groupBy(query.getGroupBy());
        }
        return this;
    }

    private BaseRepositoryImpl<MP, M, P, Q> having(Q query, QueryWrapper<P> wrapper) {
        if (query != null && query.getHaving() != null && !query.getHaving().isEmpty()) {
            wrapper.having(query.getHaving());
        }
        return this;
    }

    private BaseRepositoryImpl<MP, M, P, Q> orderBy(Q query, QueryWrapper<P> wrapper) {
        if (query.getOrderBys() == null || query.getOrderBys().isEmpty()) {
            if (tableScheme.getDefaultOrderBy() != null && tableScheme.getDefaultOrderBy().length > 0) {
                // 设置默认排序
                query.setOrderBys(String.join(query.getSplit(), tableScheme.getDefaultOrderBy()));
            }
        }
        if (query.getOrderBys() != null) {
            String[] orderBys = query.getOrderBys().split(query.getSplit());
            for (String orderBy : orderBys) {
                if (orderBy != null && !orderBy.isEmpty()) {
                    // 统一转成下划线形式，传参可以是驼峰式，也可以是下划线
                    String column = TableScheme.toUnderline(orderBy.replace("_asc", "").replace("_ASC", "").replace("_desc", "").replace("_DESC", ""));
                    wrapper.orderBy(this.tableScheme.containsColumn(column), !orderBy.toLowerCase().endsWith("_desc"), column);
                }
            }
        }
        return this;
    }

    protected boolean checkIsDataColumn(String fieldName, Class<P> tClass) {
        if (fieldName == null || fieldName.isEmpty()) {
            return false;
        } else {
            Field field = FieldUtils.getField(tClass, fieldName, true);
            return field != null;
        }
    }

    private void insertFill(P po) {
        try {
            if (tableScheme.getTenantId() != null && ThreadContext.contains(ContextConstants.TENANT_ID)) {
                String tenantId = ThreadContext.get(ContextConstants.TENANT_ID);
                if (tableScheme.getTenantId().getType() == Long.class) {
                    tableScheme.getTenantId().set(po, Long.valueOf(tenantId));
                } else if (tableScheme.getTenantId().getType() == Integer.class) {
                    tableScheme.getTenantId().set(po, Integer.valueOf(tenantId));
                } else if (tableScheme.getTenantId().getType() == String.class) {
                    tableScheme.getTenantId().set(po, tenantId);
                }
            }
            if (tableScheme.getSystemId() != null && ThreadContext.contains(ContextConstants.SYSTEM_ID)) {
                String systemId = ThreadContext.get(ContextConstants.SYSTEM_ID);
                if (tableScheme.getSystemId().getType() == Long.class) {
                    tableScheme.getSystemId().set(po, Long.valueOf(systemId));
                } else if (tableScheme.getSystemId().getType() == Integer.class) {
                    tableScheme.getSystemId().set(po, Integer.valueOf(systemId));
                } else if (tableScheme.getSystemId().getType() == String.class) {
                    tableScheme.getSystemId().set(po, systemId);
                }
            }
            if (tableScheme.getTableLogic() != null) {
                String defaultValue = tableScheme.getTableLogic().getAnnotation(TableLogic.class).value();
                if (defaultValue == null || defaultValue.isEmpty()) {
                    if (tableScheme.getTableLogic().getType().equals(Boolean.class) || tableScheme.getTableLogic().getType().equals(boolean.class)) {
                        tableScheme.getTableLogic().set(po, false);
                    } else if (tableScheme.getTableLogic().getType().equals(Integer.class) || tableScheme.getTableLogic().getType().equals(int.class)) {
                        tableScheme.getTableLogic().set(po, 0);
                    }
                } else {
                    if (tableScheme.getTableLogic().getType().equals(Boolean.class) || tableScheme.getTableLogic().getType().equals(boolean.class)) {
                        tableScheme.getTableLogic().set(po, defaultValue.equals("0"));
                    } else if (tableScheme.getTableLogic().getType().equals(Integer.class) || tableScheme.getTableLogic().getType().equals(int.class)) {
                        tableScheme.getTableLogic().set(po, Integer.valueOf(defaultValue));
                    }
                }
            }
            for (Field onCreateField : tableScheme.getOnCreateFields()) {
                if (onCreateField.getType().equals(LocalDateTime.class) && onCreateField.get(po) == null) {
                    onCreateField.set(po, LocalDateTime.now());
                } else if (onCreateField.getType().equals(LocalDate.class) && onCreateField.get(po) == null) {
                    onCreateField.set(po, LocalDate.now());
                }
            }
        } catch (IllegalAccessException ignore) {
        }
    }

    private void updateFill(P po) {
        try {
            for (Field onUpdateField : tableScheme.getOnUpdateFields()) {
                if (onUpdateField.getType().equals(LocalDateTime.class) && onUpdateField.get(po) == null) {
                    onUpdateField.set(po, LocalDateTime.now());
                } else if (onUpdateField.getType().equals(LocalDate.class) && onUpdateField.get(po) == null) {
                    onUpdateField.set(po, LocalDate.now());
                }
            }
        } catch (IllegalAccessException ignore) {
        }
    }

    private static String replaceLast(String raw, String match, String replace) {
        if (raw == null || raw.isEmpty() || null == replace) {
            //参数不合法，原样返回
            return raw;
        }
        StringBuilder sBuilder = new StringBuilder(raw);
        int lastIndexOf = sBuilder.lastIndexOf(match);
        if (-1 == lastIndexOf) {
            return raw;
        }

        return sBuilder.replace(lastIndexOf, lastIndexOf + match.length(), replace).toString();
    }

    public static <T, S> T convert(S source) {
        if (source == null) {
            return null;
        }
        Class<T> targetClass = MappingKit.get("MODEL_PO", source.getClass());
        return BeanKit.copy(source, targetClass);
    }

    public static <T, S> List<T> convert(List<S> source) {
        if (source == null || source.isEmpty() || source.get(0) == null) {
            return new ArrayList<>();
        }
        Class<T> targetClass = MappingKit.get("MODEL_PO", source.get(0).getClass());
        return BeanKit.copy(source, targetClass);
    }

    @Data
    public static class TableScheme {
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

        private static final Pattern HUMP_PATTERN = Pattern.compile("[A-Z]");

        /**
         * 驼峰转下划线
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
        protected static String toUnderline(String humpString) {
            if (humpString == null || humpString.isEmpty()) return humpString;
            Matcher matcher = HUMP_PATTERN.matcher(humpString);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                matcher.appendReplacement(sb, "_" + matcher.group(0).toLowerCase());
            }
            matcher.appendTail(sb);
            return sb.toString();
        }

        protected boolean containsField(String field) {
            return field2Column != null && field2Column.containsKey(field.toLowerCase());
        }

        protected String getField(String field) {
            return field2Column.get(field.toLowerCase());
        }

        protected boolean containsColumn(String column) {
            return field2Column != null && field2Column.containsValue(column.toLowerCase());
        }

        protected static <T> T findFieldValue(Object object, Field field) {
            return (T) ReflectionKit.getFieldValue(object, field.getName());
        }

        protected static String getColumn(Field field) {
            TableField tableField = field.getAnnotation(TableField.class);
            return tableField != null && tableField.value() != null && !tableField.value().isEmpty() ? tableField.value() : TableScheme.toUnderline(field.getName());
        }

        protected static TableScheme build(Class<?> poClass) {
            if (poClass == null) {
                return null;
            } else {
                TableScheme tableScheme = new TableScheme();
                TableName table = poClass.getAnnotation(TableName.class);
                if (table == null) {
                    throw new IllegalArgumentException("PO class must annotated with @TableName(\"table_name\")");
                } else {
                    tableScheme.setTableName(table.value());
                    List<Field> poFields = FieldUtils.getAllFieldsList(poClass);
                    poFields = poFields.stream().filter((p) -> !Modifier.isStatic(p.getModifiers())).collect(Collectors.toList());
                    if (!poFields.isEmpty()) {
                        tableScheme.field2Column = new HashMap<>(poFields.size());
                        for (Field poField : poFields) {
                            poField.setAccessible(true);
                            String fieldName = poField.getName();
                            // 优先读取TableField.value字段，否则把字段从驼峰式转换为下划线
                            TableField tableField = poField.getAnnotation(TableField.class);
                            String column = tableField != null && tableField.value() != null && !tableField.value().isEmpty() ? tableField.value() : TableScheme.toUnderline(fieldName);
                            tableScheme.field2Column.put(fieldName.toLowerCase(), column);
                            if (poField.getAnnotation(BizKey.class) != null) {
                                tableScheme.bizKeyField = poField;
                            }
                            if (poField.isAnnotationPresent(TableId.class)) {
                                tableScheme.id = poField;
                            }
                            if (poField.isAnnotationPresent(TenantId.class)) {
                                tableScheme.tenantId = poField;
                            }
                            if (poField.isAnnotationPresent(SystemId.class)) {
                                tableScheme.systemId = poField;
                            }
                            if (poField.isAnnotationPresent(TableLogic.class)) {
                                tableScheme.tableLogic = poField;
                            }
                            if (poField.isAnnotationPresent(OnCreate.class)) {
                                tableScheme.getOnCreateFields().add(poField);
                            }
                            if (poField.isAnnotationPresent(OnUpdate.class)) {
                                tableScheme.getOnUpdateFields().add(poField);
                            }
                        }

                    }
                    OrderBy orderBy = poClass.getAnnotation(OrderBy.class);
                    if (orderBy == null && poClass.getSuperclass() != null) {
                        orderBy = poClass.getSuperclass().getAnnotation(OrderBy.class);
                    }
                    if (orderBy != null && orderBy.value() != null && orderBy.value().length != 0) {
                        tableScheme.setDefaultOrderBy(orderBy.value());
                    }
                    return tableScheme;
                }
            }
        }

    }

}