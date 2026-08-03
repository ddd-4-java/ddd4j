package io.ddd4j.data.mybatis.repository;

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
import io.ddd4j.kit.lang.BeanKit;
import io.ddd4j.kit.lang.StrKit;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.ibatis.enhance.mapper.EnhanceMapper;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Objects;

/**
 * 原生 MyBatis 轨道的聚合仓储基类（五泛型，对齐 mybatisplus 模块）。
 *
 * <p>通过 {@link EnhanceMapper} 提供基础 CRUD，结合 ddd4j-core 的充血 {@link Query} 翻译
 * 和租户/系统隔离，适配 {@link Repository} SPI。不依赖 MyBatis-Plus。</p>
 *
 * @param <MP> Mapper 类型（须继承 {@link EnhanceMapper}）
 * @param <M>  聚合根类型
 * @param <P>  持久化对象类型
 * @param <Q>  充血查询类型
 * @param <ID> 标识类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
@Slf4j
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class MybatisAggregateRepository<MP extends EnhanceMapper<P>, M extends AggregateRoot<?>, P, Q extends Query<M>, ID extends Serializable>
        implements Repository<M, ID>, DomainObjectMapper<M, P>, Serializable {

    protected final Class<M> modelClass;
    protected final Class<P> persistenceClass;
    protected final Class<? extends Query<M>> queryClass;
    protected final DomainModelInfo<M> domainModelInfo;
    @Getter
    @Setter
    protected MP baseMapper;

    // ========================= 构造器 =========================

    protected MybatisAggregateRepository() {
        this.modelClass = resolveClassArg(0);
        this.persistenceClass = resolveClassArg(1);
        this.queryClass = resolveClassArg(2);
        this.domainModelInfo = DomainModelHelper.getModelInfo(modelClass, persistenceClass, this::toColumn);
        registerToRegistry();
    }

    protected MybatisAggregateRepository(MP baseMapper) {
        this();
        this.baseMapper = baseMapper;
    }

    protected MybatisAggregateRepository(MP baseMapper, Class<M> modelClass, Class<P> persistenceClass) {
        this.baseMapper = Objects.requireNonNull(baseMapper, "baseMapper must not be null");
        this.modelClass = Objects.requireNonNull(modelClass, "modelClass must not be null");
        this.persistenceClass = Objects.requireNonNull(persistenceClass, "persistenceClass must not be null");
        this.queryClass = null;
        this.domainModelInfo = DomainModelHelper.getModelInfo(modelClass, persistenceClass, this::toColumn);
        registerToRegistry();
    }

    private void registerToRegistry() {
        if (Objects.nonNull(modelClass) && Objects.nonNull(queryClass)) {
            RepositoryRegistry.register(modelClass, queryClass, this);
        } else if (Objects.nonNull(modelClass)) {
            RepositoryRegistry.register(modelClass, this);
        }
    }

    // ========================= Repository SPI 实现 =========================

    @Override
    public Optional<M> findById(ID id) {
        if (Objects.isNull(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(baseMapper.selectById(id)).map(this::toModel);
    }

    @Override
    public M save(M aggregate) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        P po = toPersistenceObject(aggregate);
        insertFill(po);
        if (shouldInsert(po)) {
            baseMapper.insert(po);
        } else {
            updateFill(po);
            baseMapper.updateById(po);
        }
        return toModel(po);
    }

    /**
     * 仅按主键更新（不插入），对齐 {@code BaseMapper.updateById}。
     */
    @Override
    public M updateById(M aggregate) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        P po = toPersistenceObject(aggregate);
        updateFill(po);
        baseMapper.updateById(po);
        return toModel(po);
    }

    /**
     * 主键存在则更新，否则插入（与 {@link #save(Object)} 同为 upsert 语义）。
     */
    @Override
    public M insertOrUpdate(M aggregate) {
        return save(aggregate);
    }

    /**
     * 按标识删除。
     *
     * <p><b>注意</b>：本模块基于 mybatis-enhance，其 {@code EnhanceMapper} 不提供
     * {@code deleteById} 能力（仅有 insert/updateById/select 系列）。
     * 旧实现以 {@code selectById} 占位，产生"假删除"（静默成功但不删数据），
     * 现改为显式抛出不支持异常；需要删除能力的业务请在 Mapper 接口上
     * 自行声明 {@code @Delete} 方法，或使用 ddd4j-data-mybatisplus / ddd4j-data-jpa 模块。
     */
    @Override
    public void deleteById(ID id) {
        throw new UnsupportedOperationException(
                "EnhanceMapper 不提供 deleteById 能力，请在业务 Mapper 上自行声明删除方法，"
                        + "或改用 ddd4j-data-mybatisplus / ddd4j-data-jpa 模块");
    }

    @Override
    public List<M> findByIds(Collection<ID> ids) {
        if (Objects.isNull(ids) || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return convert(baseMapper.selectBatchIds((Collection<? extends Serializable>) ids));
    }

    /**
     * 按标识批量删除。
     *
     * <p>与 {@link #deleteById(Serializable)} 相同，EnhanceMapper 无删除能力，显式抛出不支持异常。
     */
    @Override
    public int deleteByIds(Collection<ID> ids) {
        throw new UnsupportedOperationException(
                "EnhanceMapper 不提供 deleteByIds 能力，请在业务 Mapper 上自行声明删除方法，"
                        + "或改用 ddd4j-data-mybatisplus / ddd4j-data-jpa 模块");
    }

    @Override
    public List<M> saveBatch(Collection<M> aggregates) {
        if (Objects.isNull(aggregates) || aggregates.isEmpty()) {
            return Collections.emptyList();
        }
        List<M> result = new ArrayList<>(aggregates.size());
        for (M aggregate : aggregates) {
            P po = toPersistenceObject(aggregate);
            insertFill(po);
            baseMapper.insert(po);
            result.add(toModel(po));
        }
        return result;
    }

    @Override
    public int updateBatchById(Collection<M> aggregates) {
        if (Objects.isNull(aggregates) || aggregates.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (M aggregate : aggregates) {
            P po = toPersistenceObject(aggregate);
            updateFill(po);
            baseMapper.updateById(po);
            count++;
        }
        return count;
    }

    @Override
    public int insertOrUpdateBatch(Collection<M> aggregates) {
        if (Objects.isNull(aggregates) || aggregates.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (M aggregate : aggregates) {
            save(aggregate);
            count++;
        }
        return count;
    }

    @Override
    public Optional<M> findFirst() {
        List<M> all = findAll();
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    @Override
    public List<M> findAll() {
        return convert(baseMapper.selectList());
    }

    @Override
    public long count() {
        return baseMapper.selectList().size();
    }

    // ========================= 充血查询（需子类提供条件 SQL） =========================

    @Override
    public Page<M> page(Query<M> query) {
        List<M> all = findList(query);
        long current = Math.max(1L, query.getCurrent());
        long size = query.getSize();
        long total = all.size();
        if (size > 0) {
            int from = (int) Math.min((current - 1) * size, total);
            int to = (int) Math.min(from + size, total);
            all = all.subList(from, to);
        }
        return Page.succeed(all, total, current, size);
    }

    @Override
    public long count(Query<M> query) {
        return findList(query).size();
    }

    @Override
    public Optional<M> findFirst(Query<M> query) {
        List<M> list = findList(query);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<M> findList(Query<M> query) {
        // 原生 MyBatis 无 Wrapper，通过内存过滤实现条件查询
        // 生产环境建议子类覆盖此方法，通过 Mapper XML 的动态 SQL 实现
        List<M> all = findAll();
        return all.stream()
                .filter(model -> matchesConditions(model, query))
                .sorted((a, b) -> applySort(a, b, query))
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> maps(Query<M> query) {
        return findList(query).stream()
                .map(this::toMap)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public boolean update(AggregateRoot<?> aggregate, Query<M> query) {
        // 默认行为：逐条匹配后更新
        List<M> targets = findList(query);
        for (M target : targets) {
            P po = toPersistenceObject((M) aggregate);
            updateFill(po);
            setIdValue(po, target);
            baseMapper.updateById(po);
        }
        return !targets.isEmpty();
    }

    @Override
    public boolean deleteByQuery(Query<M> query) {
        List<M> targets = findList(query);
        for (M target : targets) {
            Serializable id = extractId(target);
            if (Objects.nonNull(id)) {
                deleteById((ID) id);
            }
        }
        return !targets.isEmpty();
    }

    @Override
    public void fill(Query<M> query, AggregateRoot<?> model) {
        // 业务仓储按需覆盖
    }

    // ========================= DomainObjectMapper =========================

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

    protected List<M> convert(List<P> persistenceObjects) {
        return persistenceObjects.stream().map(this::toModel).collect(java.util.stream.Collectors.toList());
    }

    // ========================= 字段翻译 =========================

    /**
     * 将领域属性名映射为持久化属性名（默认同名，子类可覆盖）。
     */
    protected String persistenceProperty(String domainProperty) {
        return domainProperty;
    }

    /**
     * 将领域属性名映射为数据库列名（默认驼峰转下划线）。
     */
    protected String toColumn(String propertyName) {
        if (StringUtils.isBlank(propertyName)) {
            return propertyName;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < propertyName.length(); i++) {
            char c = propertyName.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ========================= 自动填充 =========================

    /**
     * 插入前自动填充 {@code @OnCreate} / {@code @TenantId} / {@code @SystemId} 字段。
     */
    protected void insertFill(P po) {
        if (Objects.isNull(po)) {
            return;
        }
        for (Field field : FieldUtils.getAllFieldsList(po.getClass())) {
            if (field.isAnnotationPresent(io.ddd4j.annotation.orm.OnCreate.class)
                    || field.isAnnotationPresent(io.ddd4j.annotation.orm.TenantId.class)
                    || field.isAnnotationPresent(io.ddd4j.annotation.orm.SystemId.class)) {
                fillField(po, field);
            }
        }
    }

    /**
     * 更新前自动填充 {@code @OnUpdate} 字段。
     */
    protected void updateFill(P po) {
        if (Objects.isNull(po)) {
            return;
        }
        for (Field field : FieldUtils.getAllFieldsList(po.getClass())) {
            if (field.isAnnotationPresent(io.ddd4j.annotation.orm.OnUpdate.class)) {
                fillField(po, field);
            }
        }
    }

    private void fillField(P po, Field field) {
        try {
            field.setAccessible(true);
            if (field.getType() == LocalDateTime.class && Objects.isNull(field.get(po))) {
                field.set(po, LocalDateTime.now());
            } else if (field.isAnnotationPresent(io.ddd4j.annotation.orm.TenantId.class)) {
                String tenantId = ThreadContext.get(ContextConstants.TENANT_ID);
                if (Objects.nonNull(tenantId) && Objects.isNull(field.get(po))) {
                    setContextValue(po, field, tenantId);
                }
            } else if (field.isAnnotationPresent(io.ddd4j.annotation.orm.SystemId.class)) {
                String systemId = ThreadContext.get(ContextConstants.SYSTEM_ID);
                if (Objects.nonNull(systemId) && Objects.isNull(field.get(po))) {
                    setContextValue(po, field, systemId);
                }
            }
        } catch (IllegalAccessException e) {
            log.debug("Failed to fill field {}: {}", field.getName(), e.getMessage());
        }
    }

    private void setContextValue(P po, Field field, String value) throws IllegalAccessException {
        if (field.getType() == String.class) {
            field.set(po, value);
        } else if (field.getType() == Long.class) {
            field.set(po, Long.valueOf(value));
        } else if (field.getType() == Integer.class) {
            field.set(po, Integer.valueOf(value));
        }
    }

    // ========================= 内部工具 =========================

    protected boolean shouldInsert(P po) {
        Serializable id = extractId(po);
        return Objects.isNull(id) || Objects.isNull(baseMapper.selectById(id));
    }

    protected Serializable extractId(Object entity) {
        for (Field field : FieldUtils.getAllFieldsList(entity.getClass())) {
            if ("id".equals(field.getName())) {
                try {
                    field.setAccessible(true);
                    return (Serializable) field.get(entity);
                } catch (IllegalAccessException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private void setIdValue(P po, M template) {
        Serializable id = extractId(template);
        if (Objects.nonNull(id)) {
            for (Field field : FieldUtils.getAllFieldsList(po.getClass())) {
                if ("id".equals(field.getName())) {
                    try {
                        field.setAccessible(true);
                        field.set(po, id);
                    } catch (IllegalAccessException ignored) {
                    }
                    break;
                }
            }
        }
    }

    private boolean matchesConditions(M model, Query<M> query) {
        for (LambdaCondition condition : query.getWhereConditions()) {
            String property = condition.property();
            Object expected = condition.value();
            Object actual = getPropertyValue(model, property);
            if (!matchesOperator(actual, expected, condition.operator())) {
                return false;
            }
        }
        // 租户隔离
        if (!query.isIgnoreTenantId()) {
            String tenantId = ThreadContext.get(ContextConstants.TENANT_ID);
            if (Objects.nonNull(tenantId)) {
                Object actualTenant = getPropertyValue(model, "tenantId");
                return Objects.equals(tenantId, actualTenant);
            }
        }
        return true;
    }

    private boolean matchesOperator(Object actual, Object expected, String operator) {
        switch (operator) {
            case "=" -> {
                return Objects.equals(actual, expected);
            }
            case "<>" -> {
                return !Objects.equals(actual, expected);
            }
            case "IS_NULL" -> {
                return Objects.isNull(actual);
            }
            case "IS_NOT_NULL" -> {
                return Objects.nonNull(actual);
            }
            case "LIKE" -> {
                return Objects.nonNull(actual) && actual.toString().contains(String.valueOf(expected));
            }
            case "IN" -> {
                return toCollection(expected).contains(actual);
            }
            case "NOT_IN" -> {
                return !toCollection(expected).contains(actual);
            }
            default -> {
                return true;
            }
        }
    }

    private int applySort(M a, M b, Query<M> query) {
        for (LambdaCondition condition : query.getOrderByConditions()) {
            Object va = getPropertyValue(a, condition.property());
            Object vb = getPropertyValue(b, condition.property());
            int cmp = compareValues(va, vb);
            if (cmp != 0) {
                return "ASC".equals(condition.operator()) ? cmp : -cmp;
            }
        }
        return 0;
    }

    private int compareValues(Object a, Object b) {
        if (Objects.isNull(a) && Objects.isNull(b)) return 0;
        if (Objects.isNull(a)) return -1;
        if (Objects.isNull(b)) return 1;
        if (a instanceof Comparable && b instanceof Comparable) {
            return ((Comparable) a).compareTo(b);
        }
        return 0;
    }

    private Object getPropertyValue(Object bean, String property) {
        try {
            for (Field field : FieldUtils.getAllFieldsList(bean.getClass())) {
                if (field.getName().equals(property)) {
                    field.setAccessible(true);
                    return field.get(bean);
                }
            }
        } catch (IllegalAccessException ignored) {
        }
        return null;
    }

    private Map<String, Object> toMap(M model) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Field field : FieldUtils.getAllFieldsList(model.getClass())) {
            try {
                field.setAccessible(true);
                map.put(field.getName(), field.get(model));
            } catch (IllegalAccessException ignored) {
            }
        }
        return map;
    }

    private Collection<?> toCollection(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection;
        }
        if (value instanceof String str && StrKit.isNotEmpty(str)) {
            return Arrays.asList(str.split(","));
        }
        return Collections.emptyList();
    }

    /**
     * 反射解析泛型参数的实际类型。
     */
    private <C> Class<C> resolveClassArg(int index) {
        Class<?> current = getClass();
        while (Objects.nonNull(current) && current != MybatisAggregateRepository.class) {
            Type superclass = current.getGenericSuperclass();
            if (superclass instanceof ParameterizedType pt) {
                Type[] args = pt.getActualTypeArguments();
                if (index < args.length && args[index] instanceof Class) {
                    return (Class<C>) args[index];
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}
