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
package io.ddd4j.data.mybatis.repository;

import io.ddd4j.core.api.Page;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.ddd.model.DomainModel;
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
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.binding.MapperProxy;
import org.apache.ibatis.enhance.mapper.EnhanceMapper;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 原生 MyBatis 轨道的聚合仓储基类（五泛型，对齐 mybatisplus 模块）。
 *
 * <p>通过 {@link EnhanceMapper} 执行基础读写。具体仓储绑定条件查询、计数和删除 SQL；
 * 基类负责分页、聚合与持久化对象的转换及查询结果回填。</p>
 *
 * @param <MP> Mapper 类型（须继承 {@link EnhanceMapper} 并提供 SQL 映射）
 * @param <M>  聚合根类型
 * @param <P>  持久化对象类型
 * @param <Q>  充血查询类型
 * @param <ID> 标识类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
@Slf4j
@SuppressWarnings({"unchecked"})
public abstract class MybatisAggregateRepository<MP extends EnhanceMapper<P>, M extends AggregateRoot<?>, P, Q extends Query<M>, ID extends Serializable>
        implements Repository<M, ID>, DomainObjectMapper<M, P>, Serializable {

    /**
     * 聚合根模型类型。
     */
    protected final Class<M> modelClass;
    /**
     * 持久化对象类型。
     */
    protected final Class<P> persistenceClass;
    /**
     * 查询对象类型。
     */
    protected final Class<? extends Query<M>> queryClass;
    /**
     * 领域模型的属性映射信息。
     */
    protected final DomainModelInfo<M> domainModelInfo;
    /**
     * MyBatis 持久化映射器。
     */
    @Getter
    @Setter
    protected MP baseMapper;

    // ========================= 构造器 =========================

    /**
     * 根据泛型参数推断模型与持久化对象类型。
     */
    protected MybatisAggregateRepository() {
        this.modelClass = resolveClassArg(1);
        this.persistenceClass = resolveClassArg(2);
        this.queryClass = resolveClassArg(3);
        this.domainModelInfo = DomainModelHelper.getModelInfo(modelClass, persistenceClass, this::toColumn);
        registerToRegistry();
    }

    /**
     * 使用指定 MyBatis 映射器创建仓库。
     *
     * @param baseMapper MyBatis 映射器
     */
    protected MybatisAggregateRepository(MP baseMapper) {
        this();
        this.baseMapper = baseMapper;
    }

    /**
     * 使用显式模型类型和 MyBatis 映射器创建仓库。
     *
     * @param baseMapper       MyBatis 映射器
     * @param modelClass       聚合根模型类型
     * @param persistenceClass 持久化对象类型
     */
    protected MybatisAggregateRepository(MP baseMapper, Class<M> modelClass, Class<P> persistenceClass) {
        this.baseMapper = Objects.requireNonNull(baseMapper, "baseMapper must not be null");
        this.modelClass = Objects.requireNonNull(modelClass, "modelClass must not be null");
        this.persistenceClass = Objects.requireNonNull(persistenceClass, "persistenceClass must not be null");
        this.queryClass = null;
        this.domainModelInfo = DomainModelHelper.getModelInfo(modelClass, persistenceClass, this::toColumn);
        registerToRegistry();
    }

    /** inherit\u3002 */
    private void registerToRegistry() {
        if (Objects.nonNull(modelClass) && Objects.nonNull(queryClass)) {
            RepositoryRegistry.register(modelClass, queryClass, this);
        } else if (Objects.nonNull(modelClass)) {
            RepositoryRegistry.register(modelClass, this);
        }
    }

    // ========================= Repository SPI 实现 =========================

    /** {@inheritDoc} */
    @Override
    public Optional<M> findById(ID id) {
        if (Objects.isNull(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(baseMapper.selectById(id)).map(this::toModel);
    }

    /** {@inheritDoc} */
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
     * \u4ec5\u6309\u4e3b\u952e\u66f4\u65b0\uff08\u4e0d\u63d2\u5165\uff09\uff0c\u5bf9\u9f50 {@code BaseMapper.updateById}\u3002
     *
     * @param aggregate \u5f85\u5199\u5165\u7684\u805a\u5408
     * @return \u66f4\u65b0\u540e\u7684\u805a\u5408
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
     * \u4e3b\u952e\u5b58\u5728\u5219\u66f4\u65b0\uff0c\u5426\u5219\u63d2\u5165\uff08\u4e0e {@code save} \u540c\u4e3a upsert \u8bed\u4e49\uff09\u3002
     *
     * @param aggregate \u5f85\u5199\u5165\u7684\u805a\u5408
     * @return \u63d2\u5165\u6216\u66f4\u65b0\u540e\u7684\u805a\u5408
     */
    @Override
    public M insertOrUpdate(M aggregate) {
        return save(aggregate);
    }

    /**
     * \u6309\u6807\u8bc6\u5220\u9664\u3002
     *
     *  <p>\u5bf9\u6709\u6548\u4e3b\u952e\u8c03\u7528\u5177\u4f53\u4ed3\u50a8\u7ed1\u5b9a\u7684\u5220\u9664 SQL\u3002</p>
     *
     * @param id \u805a\u5408\u4e3b\u952e
     */
    @Override
    public void deleteById(ID id) {
        if (Objects.nonNull(id)) {
            deleteRow(id);
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<M> findByIds(Collection<ID> ids) {
        if (Objects.isNull(ids) || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return convert(baseMapper.selectBatchIds((Collection<? extends Serializable>) ids));
    }

    /**
     * \u6309\u6807\u8bc6\u6279\u91cf\u5220\u9664\u3002
     *
     *  <p>\u4e0e {@link #deleteById(Serializable)} \u76f8\u540c\uff0c\u5220\u9664 SQL \u5fc5\u987b\u7531\u4e1a\u52a1\u4ed3\u50a8\u663e\u5f0f\u5b9e\u73b0\u3002
     *
     * @param ids \u5f85\u5220\u9664\u7684\u805a\u5408\u4e3b\u952e\u96c6\u5408
     * @return \u5220\u9664\u64cd\u4f5c\u5f71\u54cd\u7684\u8bb0\u5f55\u6570
     */
    @Override
    public int deleteByIds(Collection<ID> ids) {
        if (Objects.isNull(ids) || ids.isEmpty()) {
            return 0;
        }
        return deleteRows(ids);
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public Optional<M> findFirst() {
        return selectRows(null, 0, 1).stream().findFirst().map(this::toModel);
    }

    /** {@inheritDoc} */
    @Override
    public List<M> findAll() {
        return convert(baseMapper.selectList());
    }

    /** {@inheritDoc} */
    @Override
    public long count() {
        return countRows(null);
    }

    // ========================= 充血查询（需子类提供条件 SQL） =========================

    /** {@inheritDoc} */
    @Override
    public Page<M> page(Query<M> query) {
        Objects.requireNonNull(query, "query");
        long current = Math.max(1L, query.getCurrent());
        long size = query.getSize();
        long offset = size > 0 ? Math.multiplyExact(current - 1, size) : 0;
        List<M> records = convert(selectRows(query, offset, size));
        fill(query, records);
        return Page.succeed(records, countRows(query), current, size);
    }

    /** {@inheritDoc} */
    @Override
    public long count(Query<M> query) {
        return countRows(Objects.requireNonNull(query, "query"));
    }

    /** {@inheritDoc} */
    @Override
    public Optional<M> findFirst(Query<M> query) {
        List<M> list = convert(selectRows(Objects.requireNonNull(query, "query"), 0, 1));
        fill(query, list);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** {@inheritDoc} */
    @Override
    public List<M> findList(Query<M> query) {
        List<M> records = convert(selectRows(Objects.requireNonNull(query, "query"), 0, -1));
        fill(query, records);
        return records;
    }

    /** {@inheritDoc} */
    @Override
    public List<Map<String, Object>> maps(Query<M> query) {
        return findList(query).stream()
                .map(this::toMap)
                .collect(java.util.stream.Collectors.toList());
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public void fill(Query<M> query, AggregateRoot<?> model) {
        // 业务仓储按需覆盖
    }

    /**
     * 执行业务 Mapper 的查询 SQL。实现须拒绝不能翻译的条件，并为分页指定稳定顺序。
     *
     * @param query  领域查询，null 表示无额外条件
     * @param offset 从零开始的偏移量
     * @param limit  最大行数，负数表示不分页
     * @return 满足条件的持久化对象
     */
    protected abstract List<P> selectRows(Query<M> query, long offset, long limit);

    /**
     * 用与 selectRows 相同的过滤条件统计可见记录，忽略分页参数。
     *
     * @param query 领域查询，null 表示无额外条件
     * @return 数据库中的匹配记录数
     */
    protected abstract long countRows(Query<M> query);

    /**
     * 执行业务 Mapper 的单条删除 SQL。
     *
     * @param id 非空主键
     * @return 受影响行数
     */
    protected abstract int deleteRow(ID id);

    /**
     * 执行业务 Mapper 的批量删除 SQL。
     *
     * @param ids 非空主键集合
     * @return 受影响行数
     */
    protected abstract int deleteRows(Collection<ID> ids);

    // ========================= DomainObjectMapper =========================

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
     * 将持久化对象列表转换为聚合根列表。
     *
     * @param persistenceObjects 持久化对象列表
     * @return 聚合根列表
     */
    protected List<M> convert(List<P> persistenceObjects) {
        return persistenceObjects.stream().map(this::toModel).collect(java.util.stream.Collectors.toList());
    }

    // ========================= 字段翻译 =========================

    /**
     * 将领域属性名映射为持久化属性名（默认同名，子类可覆盖）。
     *
     * @param domainProperty 领域属性名
     * @return 持久化属性名
     */
    protected String persistenceProperty(String domainProperty) {
        return domainProperty;
    }

    /**
     * 将领域属性名映射为数据库列名（默认驼峰转下划线）。
     *
     * @param propertyName 持久化属性名
     * @return 数据库列名
     */
    protected String toColumn(String propertyName) {
        if (StrKit.isBlank(propertyName)) {
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
     *
     * @param po 待插入的持久化对象
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
     *
     * @param po 待更新的持久化对象
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

    /**
     * \u6309\u5b57\u6bb5\u7c7b\u578b\u548c\u6ce8\u89e3\u5199\u5165\u7f3a\u7701\u65f6\u95f4\u6216\u5f53\u524d\u79df\u6237\u4fe1\u606f\u3002
     * @param po \u5f53\u524d\u8981\u5199\u5165\u6216\u586b\u5145\u7684\u6301\u4e45\u5316\u5bf9\u8c61
     * @param field \u5f85\u5904\u7406\u7684 Java \u53cd\u5c04\u5b57\u6bb5
     */
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

    /**
     * \u628a\u6587\u672c\u4e0a\u4e0b\u6587\u503c\u8f6c\u6362\u4e3a\u5b57\u6bb5\u58f0\u660e\u652f\u6301\u7684\u5b57\u7b26\u4e32\u6216\u6574\u6570\u7c7b\u578b\u3002
     * @param po \u5f53\u524d\u8981\u5199\u5165\u6216\u586b\u5145\u7684\u6301\u4e45\u5316\u5bf9\u8c61
     * @param field \u5f85\u5904\u7406\u7684 Java \u53cd\u5c04\u5b57\u6bb5
     * @param value \u5f53\u524d\u6761\u4ef6\u64cd\u4f5c\u6570\u6216\u5f85\u5199\u5165\u5b57\u6bb5\u503c
     * @throws IllegalAccessException \u65e0\u6cd5\u5199\u5165\u76ee\u6807\u53cd\u5c04\u5b57\u6bb5
     */
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

    /**
     * 判断持久化对象需要插入还是更新。
     *
     * @param po 持久化对象
     * @return 需要插入时为 true
     */
    protected boolean shouldInsert(P po) {
        Serializable id = extractId(po);
        return Objects.isNull(id) || Objects.isNull(baseMapper.selectById(id));
    }

    /**
     * 从对象中提取可序列化标识。
     *
     * @param entity 待检查的对象
     * @return 对象标识
     */
    protected Serializable extractId(Object entity) {
        if (Objects.isNull(entity)) {
            return null;
        }
        if (entity instanceof DomainModel) {
            return (Serializable) ((DomainModel<?>) entity).id();
        }
        return (Serializable) SystemMetaObject.forObject(entity).getValue(persistenceIdProperty());
    }

    /**
     * \u5c06\u9886\u57df\u6a21\u578b\u4e3b\u952e\u590d\u5236\u5230\u6301\u4e45\u5316\u5bf9\u8c61\u7684\u4e3b\u952e\u5b57\u6bb5\u3002
     * @param po \u5f53\u524d\u8981\u5199\u5165\u6216\u586b\u5145\u7684\u6301\u4e45\u5316\u5bf9\u8c61
     * @param template \u63d0\u4f9b\u4e3b\u952e\u503c\u7684\u9886\u57df\u6a21\u578b
     */
    private void setIdValue(P po, M template) {
        Serializable id = Objects.requireNonNull(extractId(template), "Matched model must have an identity");
        SystemMetaObject.forObject(po).setValue(persistenceIdProperty(), id);
    }

    /**
     * 复用 Mapper 已解析的主键映射；自定义 Mapper 装饰器可覆盖此方法提供其映射属性。
     *
     * @return ResultMap 的显式 ID 属性，或 insert 的 keyProperty；均未声明时兼容 id 约定
     */
    protected String persistenceIdProperty() {
        if (Proxy.isProxyClass(baseMapper.getClass())
                && Proxy.getInvocationHandler(baseMapper) instanceof MapperProxy) {
            // MyBatis 没有公开 Mapper 对应的 SqlSession，使用其 MetaObject 读取代理上下文。
            MetaObject proxy = SystemMetaObject.forObject(Proxy.getInvocationHandler(baseMapper));
            Configuration configuration = ((SqlSession) proxy.getValue("sqlSession")).getConfiguration();
            Class<?> mapperType = (Class<?>) proxy.getValue("mapperInterface");
            Set<String> properties = new LinkedHashSet<>();
            MappedStatement select = mappedStatement(configuration, mapperType, "selectById", Serializable.class);
            for (ResultMap resultMap : select.getResultMaps()) {
                if (persistenceClass.isAssignableFrom(resultMap.getType())) {
                    for (ResultMapping mapping : resultMap.getIdResultMappings()) {
                        // 未声明 ID 时 MyBatis 会将所有结果字段作为 idResultMappings，必须检查标志。
                        if (mapping.getFlags().contains(ResultFlag.ID) && StrKit.isNotBlank(mapping.getProperty())) {
                            properties.add(mapping.getProperty());
                        }
                    }
                }
            }
            if (properties.isEmpty()) {
                String[] keys = mappedStatement(configuration, mapperType, "insert", Object.class).getKeyProperties();
                if (Objects.nonNull(keys)) {
                    Collections.addAll(properties, keys);
                }
            }
            if (properties.size() > 1) {
                throw new IllegalStateException("Repository requires one primary-key property: " + properties);
            }
            if (!properties.isEmpty()) {
                return properties.iterator().next();
            }
        }
        return "id";
    }

    /**
     * \u7531 MyBatis Mapper \u65b9\u6cd5\u53ca\u53c2\u6570\u7c7b\u578b\u5b9a\u4f4d\u5df2\u6ce8\u518c\u7684 SQL \u8bed\u53e5\u3002
     * @param configuration \u5f53\u524d MyBatis \u6620\u5c04\u914d\u7f6e
     * @param mapperType \u58f0\u660e\u6301\u4e45\u5316\u64cd\u4f5c\u7684 Mapper \u63a5\u53e3\u7c7b\u578b
     * @param methodName \u8981\u67e5\u627e\u7684 Mapper \u65b9\u6cd5\u540d\u79f0
     * @param parameterType Mapper \u65b9\u6cd5\u7684\u53c2\u6570\u7c7b\u578b
     * @return \u89e3\u6790\u5f97\u5230\u7684 MyBatis \u8bed\u53e5\u914d\u7f6e
     */
    private MappedStatement mappedStatement(Configuration configuration, Class<?> mapperType,
                                            String methodName, Class<?> parameterType) {
        try {
            // 由 MyBatis 自身处理继承 Mapper 的 statement 命名空间。
            MapperMethod.SqlCommand command = new MapperMethod.SqlCommand(configuration, mapperType,
                    mapperType.getMethod(methodName, parameterType));
            return configuration.getMappedStatement(command.getName());
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("Missing mapper method: " + methodName, exception);
        }
    }

    /**
     * \u5c06\u805a\u5408\u5bf9\u8c61\u8f6c\u6362\u4e3a\u5b57\u6bb5\u540d\u548c\u503c\u7ec4\u6210\u7684\u6620\u5c04\u3002
     * @param model \u5f85\u5904\u7406\u7684\u9886\u57df\u6a21\u578b
     * @return \u805a\u5408\u7684\u5c5e\u6027\u6620\u5c04
     */
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

    /**
     * \u53cd\u5c04\u89e3\u6790\u6cdb\u578b\u53c2\u6570\u7684\u5b9e\u9645\u7c7b\u578b\u3002
     *
     * @param index \u6cdb\u578b\u5b9e\u53c2\u7684\u7d22\u5f15
     * @param <C> \u89e3\u6790\u5f97\u5230\u7684\u6cdb\u578b\u5b9e\u53c2\u7c7b\u578b
     * @return \u89e3\u6790\u51fa\u7684\u5b9e\u53c2\u7c7b\u578b
     */
    private <C> Class<C> resolveClassArg(int index) {
        Class<?> current = getClass();
        while (Objects.nonNull(current) && current != MybatisAggregateRepository.class) {
            Type superclass = current.getGenericSuperclass();
            if (superclass instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) superclass;
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
