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
import io.ddd4j.data.mybatis.mapper.Ddd4jMapper;

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
 * <p>通过 {@link Ddd4jMapper} 提供基础 CRUD，结合 ddd4j-core 的充血 {@link Query} 翻译
 * 和租户/系统隔离，适配 {@link Repository} SPI。不依赖 MyBatis-Plus。</p>
 *
 * @param <MP> Mapper 类型（须继承 {@link Ddd4jMapper} 并提供 SQL 映射）
 * @param <M>  聚合根类型
 * @param <P>  持久化对象类型
 * @param <Q>  充血查询类型
 * @param <ID> 标识类型
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 4.0.0
 */
@Slf4j
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class MybatisAggregateRepository<MP extends Ddd4jMapper<P>, M extends AggregateRoot<?>, P, Q extends Query<M>, ID extends Serializable>
        implements Repository<M, ID>, DomainObjectMapper<M, P>, Serializable {

    /** \u805a\u5408\u6839\u6a21\u578b\u7c7b\u578b\u3002 */
    protected final Class<M> modelClass;
    /** \u6301\u4e45\u5316\u5bf9\u8c61\u7c7b\u578b\u3002 */
    protected final Class<P> persistenceClass;
    /** \u67e5\u8be2\u5bf9\u8c61\u7c7b\u578b\u3002 */
    protected final Class<? extends Query<M>> queryClass;
    /** \u9886\u57df\u6a21\u578b\u7684\u5c5e\u6027\u6620\u5c04\u4fe1\u606f\u3002 */
    protected final DomainModelInfo<M> domainModelInfo;
    /** MyBatis \u6301\u4e45\u5316\u6620\u5c04\u5668\u3002 */
    @Getter
    @Setter
    protected MP baseMapper;

    // ========================= 构造器 =========================

    /** \u6839\u636e\u6cdb\u578b\u53c2\u6570\u63a8\u65ad\u6a21\u578b\u4e0e\u6301\u4e45\u5316\u5bf9\u8c61\u7c7b\u578b\u3002 */
    protected MybatisAggregateRepository() {
        this.modelClass = resolveClassArg(0);
        this.persistenceClass = resolveClassArg(1);
        this.queryClass = resolveClassArg(2);
        this.domainModelInfo = DomainModelHelper.getModelInfo(modelClass, persistenceClass, this::toColumn);
        registerToRegistry();
    }

    /**
     * \u4f7f\u7528\u6307\u5b9a MyBatis \u6620\u5c04\u5668\u521b\u5efa\u4ed3\u5e93\u3002
     * @param baseMapper MyBatis \u6620\u5c04\u5668
     */
    protected MybatisAggregateRepository(MP baseMapper) {
        this();
        this.baseMapper = baseMapper;
    }

    /**
     * \u4f7f\u7528\u663e\u5f0f\u6a21\u578b\u7c7b\u578b\u548c MyBatis \u6620\u5c04\u5668\u521b\u5efa\u4ed3\u5e93\u3002
     * @param baseMapper MyBatis \u6620\u5c04\u5668
     * @param modelClass \u805a\u5408\u6839\u6a21\u578b\u7c7b\u578b
     * @param persistenceClass \u6301\u4e45\u5316\u5bf9\u8c61\u7c7b\u578b
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
     * \u4e3b\u952e\u5b58\u5728\u5219\u66f4\u65b0\uff0c\u5426\u5219\u63d2\u5165\uff08\u4e0e {@link #save(Object)} \u540c\u4e3a upsert \u8bed\u4e49\uff09\u3002
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
     *  <p>\u539f\u751f MyBatis \u7684\u5220\u9664 SQL \u7531\u4e1a\u52a1 Mapper \u663e\u5f0f\u7ef4\u62a4\u3002\u4e3a\u907f\u514d\u5728\u4ed3\u50a8\u57fa\u7c7b\u4e2d\u731c\u6d4b\u8868\u540d\u6216\u4e3b\u952e\u5217\uff0c
     *  \u672c\u9ed8\u8ba4\u5b9e\u73b0\u660e\u786e\u62d2\u7edd\u5220\u9664\uff1b\u4e1a\u52a1\u65b9\u53ef\u5728\u5177\u4f53\u4ed3\u50a8\u4e2d\u8986\u76d6\u8be5\u65b9\u6cd5\uff0c\u6216\u4f7f\u7528 MyBatis-Plus/JPA \u8f68\u9053\u3002
     *
     * @param id \u805a\u5408\u4e3b\u952e
     */
    @Override
    public void deleteById(ID id) {
        throw new UnsupportedOperationException(
                "Ddd4jMapper 不定义删除 SQL，请在业务仓储中显式实现 deleteById，"
                        + "或改用 ddd4j-data-mybatisplus / ddd4j-data-jpa 模块");
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
     * \u6309\u6807\u8bc6\u6279\u91cf\u5220\u9664\u3002
     *
     *  <p>\u4e0e {@link #deleteById(Serializable)} \u76f8\u540c\uff0c\u5220\u9664 SQL \u5fc5\u987b\u7531\u4e1a\u52a1\u4ed3\u50a8\u663e\u5f0f\u5b9e\u73b0\u3002
     *
     * @param ids \u5f85\u5220\u9664\u7684\u805a\u5408\u4e3b\u952e\u96c6\u5408
     * @return \u5220\u9664\u64cd\u4f5c\u5f71\u54cd\u7684\u8bb0\u5f55\u6570
     */
    @Override
    public int deleteByIds(Collection<ID> ids) {
        throw new UnsupportedOperationException(
                "Ddd4jMapper 不定义批量删除 SQL，请在业务仓储中显式实现 deleteByIds，"
                        + "或改用 ddd4j-data-mybatisplus / ddd4j-data-jpa 模块");
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
     * <p>MyBatis 实现调用 <code>findAll()</code>。</p>
     *
     * @return 第一个匹配条件的结果
     */
    @Override
    public Optional<M> findFirst() {
        List<M> all = findAll();
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
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
     * <p>MyBatis 实现调用 <code>baseMapper.selectList().size()</code>。</p>
     *
     * @return 满足筛选条件的记录数量
     */
    @Override
    public long count() {
        return baseMapper.selectList().size();
    }

    // ========================= 充血查询（需子类提供条件 SQL） =========================

    /**
     * 根据分页参数和查询条件读取结果。
     *
     * <p>MyBatis 实现调用 <code>findList(query)</code>。</p>
     *
     * @param query 交给查询处理器或仓储的查询条件
     * @return 实际返回<code>Page.succeed(all, total, current, size)</code>
     */
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

    /**
     * 统计符合查询范围的数据数量。
     *
     * <p>MyBatis 实现调用 <code>findList(query).size()</code>。</p>
     *
     * @param query 交给查询处理器或仓储的查询条件
     * @return 满足筛选条件的记录数量
     */
    @Override
    public long count(Query<M> query) {
        return findList(query).size();
    }

    /**
     * 读取满足条件的第一条记录。
     *
     * <p>MyBatis 实现调用 <code>findList(query)</code>。</p>
     *
     * @param query 交给查询处理器或仓储的查询条件
     * @return 第一个匹配条件的结果
     */
    @Override
    public Optional<M> findFirst(Query<M> query) {
        List<M> list = findList(query);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * 按查询条件读取记录列表。
     *
     * <p>MyBatis 实现调用 <code>findAll()</code>。</p>
     *
     * @param query 交给查询处理器或仓储的查询条件
     * @return 满足条件的结果列表
     */
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
     * @param query 交给查询处理器或仓储的查询条件
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
     * \u5c06\u6301\u4e45\u5316\u5bf9\u8c61\u5217\u8868\u8f6c\u6362\u4e3a\u805a\u5408\u6839\u5217\u8868\u3002
     * @param persistenceObjects \u6301\u4e45\u5316\u5bf9\u8c61\u5217\u8868
     * @return \u805a\u5408\u6839\u5217\u8868
     */
    protected List<M> convert(List<P> persistenceObjects) {
        return persistenceObjects.stream().map(this::toModel).collect(java.util.stream.Collectors.toList());
    }

    // ========================= 字段翻译 =========================

    /**
     * \u5c06\u9886\u57df\u5c5e\u6027\u540d\u6620\u5c04\u4e3a\u6301\u4e45\u5316\u5c5e\u6027\u540d\uff08\u9ed8\u8ba4\u540c\u540d\uff0c\u5b50\u7c7b\u53ef\u8986\u76d6\uff09\u3002
     *
     * @param domainProperty \u9886\u57df\u5c5e\u6027\u540d
     * @return \u6301\u4e45\u5316\u5c5e\u6027\u540d
     */
    protected String persistenceProperty(String domainProperty) {
        return domainProperty;
    }

    /**
     * \u5c06\u9886\u57df\u5c5e\u6027\u540d\u6620\u5c04\u4e3a\u6570\u636e\u5e93\u5217\u540d\uff08\u9ed8\u8ba4\u9a7c\u5cf0\u8f6c\u4e0b\u5212\u7ebf\uff09\u3002
     *
     * @param propertyName \u6301\u4e45\u5316\u5c5e\u6027\u540d
     * @return \u6570\u636e\u5e93\u5217\u540d
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
     * \u63d2\u5165\u524d\u81ea\u52a8\u586b\u5145 {@code @OnCreate} / {@code @TenantId} / {@code @SystemId} \u5b57\u6bb5\u3002
     *
     * @param po \u5f85\u63d2\u5165\u7684\u6301\u4e45\u5316\u5bf9\u8c61
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
     * \u66f4\u65b0\u524d\u81ea\u52a8\u586b\u5145 {@code @OnUpdate} \u5b57\u6bb5\u3002
     *
     * @param po \u5f85\u66f4\u65b0\u7684\u6301\u4e45\u5316\u5bf9\u8c61
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
     * \u5224\u65ad\u6301\u4e45\u5316\u5bf9\u8c61\u9700\u8981\u63d2\u5165\u8fd8\u662f\u66f4\u65b0\u3002
     * @param po \u6301\u4e45\u5316\u5bf9\u8c61
     * @return \u9700\u8981\u63d2\u5165\u65f6\u4e3a true
     */
    protected boolean shouldInsert(P po) {
        Serializable id = extractId(po);
        return Objects.isNull(id) || Objects.isNull(baseMapper.selectById(id));
    }

    /**
     * \u4ece\u5bf9\u8c61\u4e2d\u63d0\u53d6\u53ef\u5e8f\u5217\u5316\u6807\u8bc6\u3002
     * @param entity \u5f85\u68c0\u67e5\u7684\u5bf9\u8c61
     * @return \u5bf9\u8c61\u6807\u8bc6
     */
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

    /**
     * \u5c06\u9886\u57df\u6a21\u578b\u4e3b\u952e\u590d\u5236\u5230\u6301\u4e45\u5316\u5bf9\u8c61\u7684\u4e3b\u952e\u5b57\u6bb5\u3002
     * @param po \u5f53\u524d\u8981\u5199\u5165\u6216\u586b\u5145\u7684\u6301\u4e45\u5316\u5bf9\u8c61
     * @param template \u63d0\u4f9b\u4e3b\u952e\u503c\u7684\u9886\u57df\u6a21\u578b
     */
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

    /**
     * 执行 <code>matches Conditions</code>，调用 <code>query.getWhereConditions</code>、<code>condition.property</code>、<code>condition.value</code>。
     *
     * @param model 传入此操作的 <code>M</code> 类型 <code>model</code>
     * @param query 传入此操作的 <code>Query&lt;M&gt;</code> 类型 <code>query</code>
     * @return 按条件返回 <code>false</code>、<code>Objects.equals(tenantId, actualTenant)</code>、<code>true</code>
     */
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

    /**
     * 执行 <code>matches Operator</code>，调用 <code>actual.toString</code>。
     *
     * @param actual 传入此操作的 <code>Object</code> 类型 <code>actual</code>
     * @param expected 传入此操作的 <code>Object</code> 类型 <code>expected</code>
     * @param operator 传入此操作的 <code>String</code> 类型 <code>operator</code>
     * @return 按条件返回 <code>Objects.equals(actual, expected)</code>、<code>!Objects.equals(actual, expected)</code>、<code>Objects.isNull(actual)</code>
     */
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

    /**
     * 执行 <code>apply Sort</code>，调用 <code>query.getOrderByConditions</code>、<code>condition.property</code>、<code>condition.operator</code>。
     *
     * @param a 传入此操作的 <code>M</code> 类型 <code>a</code>
     * @param b 传入此操作的 <code>M</code> 类型 <code>b</code>
     * @param query 传入此操作的 <code>Query&lt;M&gt;</code> 类型 <code>query</code>
     * @return 按条件返回 <code>"ASC".equals(condition.operator()) ? cmp : -cmp</code>、<code>0</code>
     */
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

    /**
     * 按 <code>compare Values</code> 的契约计算并返回结果。
     *
     * @param a 传入此操作的 <code>Object</code> 类型 <code>a</code>
     * @param b 传入此操作的 <code>Object</code> 类型 <code>b</code>
     * @return 按条件返回 <code>0</code>、<code>-1</code>、<code>1</code>
     */
    private int compareValues(Object a, Object b) {
        if (Objects.isNull(a) && Objects.isNull(b)) return 0;
        if (Objects.isNull(a)) return -1;
        if (Objects.isNull(b)) return 1;
        if (a instanceof Comparable && b instanceof Comparable) {
            return ((Comparable) a).compareTo(b);
        }
        return 0;
    }

    /**
     * 执行 <code>get Property Value</code>，调用 <code>FieldUtils.getAllFieldsList</code>、<code>bean.getClass</code>、<code>field.getName</code>。
     *
     * @param bean 传入此操作的 <code>Object</code> 类型 <code>bean</code>
     * @param property 传入此操作的 <code>String</code> 类型 <code>property</code>
     * @return 按条件返回 <code>field.get(bean)</code>、<code>null</code>
     */
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
     * 执行 <code>to Collection</code>，调用 <code>StrKit.isNotEmpty</code>、<code>Arrays.asList</code>、<code>str.split</code>。
     *
     * @param value 由当前成员保存或用于计算的值
     * @return 按条件返回 <code>collection</code>、<code>Arrays.asList(str.split(","))</code>、<code>Collections.emptyList()</code>
     */
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
