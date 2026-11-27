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

    /**
     * inherit。
     */
    private void registerToRegistry() {
        if (Objects.nonNull(modelClass) && Objects.nonNull(queryClass)) {
            RepositoryRegistry.register(modelClass, queryClass, this);
        } else if (Objects.nonNull(modelClass)) {
            RepositoryRegistry.register(modelClass, this);
        }
    }

    // ========================= Repository SPI 实现 =========================

    /**
     * 按主键读取领域对象或持久化记录。
     *
     * <p>MyBatis 实现调用 <code>Optional.empty()</code>。</p>
     *
     * @param id 当前操作使用的记录主键或业务标识
     * @return 实际返回<code>Optional.empty()</code>
     */
    @Override
    public Optional<M> findById(ID id) {
        if (Objects.isNull(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(baseMapper.selectById(id)).map(this::toModel);
    }

    /**
     * 写入或更新给定的领域对象或存储记录。
     *
     * <p>MyBatis 实现调用 <code>toPersistenceObject(aggregate)</code>。</p>
     *
     * @param aggregate 传给 <code>toPersistenceObject(aggregate)</code>
     * @return 实际返回<code>toModel(po)</code>
     */
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
     *
     * @param aggregate 待写入的聚合
     * @return 更新后的聚合
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
     * 主键存在则更新，否则插入（与 {@code save} 同为 upsert 语义）。
     *
     * @param aggregate 待写入的聚合
     * @return 插入或更新后的聚合
     */
    @Override
    public M insertOrUpdate(M aggregate) {
        return save(aggregate);
    }

    /**
     * 按标识删除。
     *
     * <p>对有效主键调用具体仓储绑定的删除 SQL。</p>
     *
     * @param id 聚合主键
     */
    @Override
    public void deleteById(ID id) {
        if (Objects.nonNull(id)) {
            deleteRow(id);
        }
    }

    /**
     * 批量读取指定主键对应的记录。
     *
     * <p>MyBatis 实现调用 <code>ids.isEmpty()</code>。</p>
     *
     * @param ids 要查询、更新或删除的记录标识集合
     * @return 与给定主键相匹配的记录
     */
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
     * <p>与 {@link #deleteById(Serializable)} 相同，删除 SQL 必须由业务仓储显式实现。
     *
     * @param ids 待删除的聚合主键集合
     * @return 删除操作影响的记录数
     */
    @Override
    public int deleteByIds(Collection<ID> ids) {
        if (Objects.isNull(ids) || ids.isEmpty()) {
            return 0;
        }
        return deleteRows(ids);
    }

    /**
     * 批量保存传入的对象。
     *
     * <p>MyBatis 实现调用 <code>aggregates.isEmpty()</code>。</p>
     *
     * @param aggregates 传给 <code>aggregates.isEmpty()</code>
     * @return 根据执行路径返回<code>Collections.emptyList()</code>、<code>result</code>
     */
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

    /**
     * 按主键批量更新数据。
     *
     * <p>MyBatis 实现调用 <code>aggregates.isEmpty()</code>。</p>
     *
     * @param aggregates 传给 <code>aggregates.isEmpty()</code>
     * @return 根据执行路径返回<code>0</code>、<code>count</code>
     */
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

    /**
     * 批量写入数据，根据各记录是否存在选择插入或更新。
     *
     * <p>MyBatis 实现调用 <code>aggregates.isEmpty()</code>。</p>
     *
     * @param aggregates 传给 <code>aggregates.isEmpty()</code>
     * @return 根据执行路径返回<code>0</code>、<code>count</code>
     */
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

    /**
     * 读取满足条件的第一条记录。
     *
     * <p>MyBatis 实现调用 <code>selectRows(null, 0, 1).stream().findFirst()</code>。</p>
     *
     * @return 第一个匹配条件的结果
     */
    @Override
    public Optional<M> findFirst() {
        return selectRows(null, 0, 1).stream().findFirst().map(this::toModel);
    }

    /**
     * 查询存储中符合条件的记录。
     *
     * <p>MyBatis 实现调用 <code>convert(baseMapper.selectList())</code>。</p>
     *
     * @return 满足条件的结果列表
     */
    @Override
    public List<M> findAll() {
        return convert(baseMapper.selectList());
    }

    /**
     * 统计符合查询范围的数据数量。
     *
     * <p>MyBatis 实现调用 <code>countRows(null)</code>。</p>
     *
     * @return 满足筛选条件的记录数量
     */
    @Override
    public long count() {
        return countRows(null);
    }

    // ========================= 充血查询（需子类提供条件 SQL） =========================

    /**
     * 根据分页参数和查询条件读取结果。
     *
     * <p>MyBatis 实现调用 <code>Math.max(1L, query.getCurrent())</code>。</p>
     *
     * @param query 交给查询处理器或仓储的查询条件
     * @return 实际返回<code>Page.succeed(records, countRows(query), current, size)</code>
     */
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

    /**
     * 统计符合查询范围的数据数量。
     *
     * <p>MyBatis 实现调用 <code>countRows(Objects.requireNonNull(query, "query"))</code>。</p>
     *
     * @param query 交给查询处理器或仓储的查询条件
     * @return 满足筛选条件的记录数量
     */
    @Override
    public long count(Query<M> query) {
        return countRows(Objects.requireNonNull(query, "query"));
    }

    /**
     * 读取满足条件的第一条记录。
     *
     * <p>MyBatis 实现调用 <code>convert(selectRows(Objects.requireNonNull(query, "query"), 0, 1))</code>。</p>
     *
     * @param query 交给查询处理器或仓储的查询条件
     * @return 第一个匹配条件的结果
     */
    @Override
    public Optional<M> findFirst(Query<M> query) {
        List<M> list = convert(selectRows(Objects.requireNonNull(query, "query"), 0, 1));
        fill(query, list);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * 按查询条件读取记录列表。
     *
     * <p>MyBatis 实现调用 <code>convert(selectRows(Objects.requireNonNull(query, "query"), 0, -1))</code>。</p>
     *
     * @param query 交给查询处理器或仓储的查询条件
     * @return 满足条件的结果列表
     */
    @Override
    public List<M> findList(Query<M> query) {
        List<M> records = convert(selectRows(Objects.requireNonNull(query, "query"), 0, -1));
        fill(query, records);
        return records;
    }

    /**
     * 通过<code>findList(query).stream()</code>、<code>findList(query)</code>处理当前调用。
     *
     * @param query 交给查询处理器或仓储的查询条件
     * @return 此操作产生的<code>List&lt;Map&lt;String, Object&gt;&gt;</code>结果
     */
    @Override
    public List<Map<String, Object>> maps(Query<M> query) {
        return findList(query).stream()
                .map(this::toMap)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 更新已经存在的记录。
     *
     * <p>MyBatis 实现调用 <code>findList(query)</code>。</p>
     *
     * @param aggregate 传给 <code>toPersistenceObject((M)aggregate)</code>
     * @param query     交给查询处理器或仓储的查询条件
     * @return 实际返回<code>!targets.isEmpty()</code>
     */
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

    /**
     * 删除匹配查询条件的记录。
     *
     * <p>MyBatis 实现调用 <code>findList(query)</code>。</p>
     *
     * @param query 交给查询处理器或仓储的查询条件
     * @return 实际返回<code>!targets.isEmpty()</code>
     */
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

    /**
     * 接收<code>fill</code>回调，当前实现无需在此步骤更新状态。
     *
     * @param query 交给查询处理器或仓储的查询条件
     * @param model 用于此操作的 <code>model</code>（<code>AggregateRoot&lt;?&gt;</code>）
     */
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

    /**
     * 将持久化对象转换为领域模型。
     *
     * <p>MyBatis 实现调用 <code>BeanKit.copy(persistenceObject, modelClass)</code>。</p>
     *
     * @param persistenceObject 传给 <code>BeanKit.copy(persistenceObject, modelClass)</code>
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
     * <p>MyBatis 实现调用 <code>BeanKit.copy(model, persistenceClass)</code>。</p>
     *
     * @param model 传给 <code>BeanKit.copy(model, persistenceClass)</code>
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
     * 按字段类型和注解写入缺省时间或当前租户信息。
     *
     * @param po    当前要写入或填充的持久化对象
     * @param field 待处理的 Java 反射字段
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
     * 把文本上下文值转换为字段声明支持的字符串或整数类型。
     *
     * @param po    当前要写入或填充的持久化对象
     * @param field 待处理的 Java 反射字段
     * @param value 当前条件操作数或待写入字段值
     * @throws IllegalAccessException 无法写入目标反射字段
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
     * 将领域模型主键复制到持久化对象的主键字段。
     *
     * @param po       当前要写入或填充的持久化对象
     * @param template 提供主键值的领域模型
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
     * 由 MyBatis Mapper 方法及参数类型定位已注册的 SQL 语句。
     *
     * @param configuration 当前 MyBatis 映射配置
     * @param mapperType    声明持久化操作的 Mapper 接口类型
     * @param methodName    要查找的 Mapper 方法名称
     * @param parameterType Mapper 方法的参数类型
     * @return 解析得到的 MyBatis 语句配置
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
     * 将聚合对象转换为字段名和值组成的映射。
     *
     * @param model 待处理的领域模型
     * @return 聚合的属性映射
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
     * 反射解析泛型参数的实际类型。
     *
     * @param index 泛型实参的索引
     * @param <C>   解析得到的泛型实参类型
     * @return 解析出的实参类型
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
