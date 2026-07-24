package io.ddd4j.data.mybatis.repository;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.enums.SqlMethod;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.core.toolkit.ReflectionKit;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.baomidou.mybatisplus.extension.repository.AbstractRepository;
import com.baomidou.mybatisplus.extension.repository.IRepository;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import io.ddd4j.annotation.orm.*;
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
import io.ddd4j.core.util.MappingKit;
import io.ddd4j.kit.lang.BeanKit;
import io.ddd4j.kit.lang.StrKit;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.slf4j.Logger;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Objects;
import java.util.function.Function;

/**
 * MyBatis-Plus 仓储抽象基类（PO↔Domain 映射 + 充血查询 + 自动填充）。
 *
 * <h3>类继承链</h3>
 * <pre>{@code
 * MybatisAggregateRepository<MP, M, P, Q, ID>
 *   └─ extends AbstractRepository<MP, P>             // mybatis-plus-extension（无 Spring 依赖）
 *   └─ implements DomainObjectMapper<M, P>            // PO↔Domain 映射
 *   └─ implements Repository<M, ID>                   // ddd4j 聚合根仓储（含充血查询方法）
 * }</pre>
 *
 * <h3>充血查询与 COLA 合规</h3>
 * <p>充血查询对象 {@link Query}{@code <M>} 绑定<b>聚合根类型</b>（不是 PO），
 * 因此 Domain 层（如 ddd4j-boot-sample-layered 的 {@code UserQuery}）无需 import PO 类。
 * 字段引用 {@code User::getStatus} 由基础设施层（本类）翻译为 PO 列名
 * （{@code userPO.status}），翻译逻辑通过 {@code DomainModelHelper} 缓存的
 * {@link DomainModelInfo} 完成（{@code @DomainField} 注解 + 默认约定）。
 *
 * <h3>充血查询链路注册（父类构造器完成）</h3>
 * <p>本类在 3 个构造器中调用 {@link #registerToRepositoryRegistry()}，
 * 让所有继承本类的仓储自动注册到 {@link RepositoryRegistry}，
 * 实现 {@code new XxxQuery().list()} 充血查询零配置。
 *
 * <h3>为什么不直接继承 ServiceImpl</h3>
 * <p>{@code ServiceImpl.updateBatchById(Collection<T>)} 返回 {@code boolean}，
 * ddd4j {@code Repository.batchUpdateById(Collection<M>)} 返回 {@code int}，
 * 同时实现两者将触发 JLS §8.4.8.3 重复方法错误。
 *
 * @param <MP> MyBatis-Plus Mapper interface (extends {@code BaseMapper<P>})
 * @param <M>  aggregate root type（<b>领域模型，不是 PO</b>）
 * @param <P>  persistence object type, usually named {@code *PO}
 * @param <Q>  query object type (extends {@link Query}{@code <M>}) — <b>充血查询反向索引 key</b>
 * @param <ID> aggregate identity type
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@SuppressWarnings("unchecked")
public abstract class MybatisAggregateRepository<MP extends BaseMapper<P>, M extends AggregateRoot<?>, P, Q extends Query<M>, ID extends Serializable>
        extends AbstractRepository<MP, P>
        implements DomainObjectMapper<M, P>, Repository<M, ID> {

    @Slf4j
    private static final class ApplicationLog {

        private ApplicationLog() {
        }

        private static Logger logger() {
            return log;
        }
    }

    /**
     * MyBatis-Plus Mapper 实例（无 Spring 注入，业务方通过 {@link #setBaseMapper} 或构造器手动注入）。
     * 对应 MyBatis-Plus {@code CrudRepository.@Autowired M baseMapper} 的非 Spring 版本。
     */
    @Setter
    protected MP baseMapper;

    private Class<M> modelClass;
    private Class<P> persistenceObjectClass;
    private Class<? extends Query<M>> queryClass;

    /**
     * Domain Model 元数据（充血查询翻译：Domain 字段名 → PO 列名）。
     * 由 {@link DomainModelHelper} 缓存。
     */
    private DomainModelInfo<M> domainModelInfo;

    /**
     * PO 元数据（auto-fill、bizKey、tenantId、tableLogic 等），委托 MP {@code TableInfoHelper}。
     */
    private TableInfo tableInfo;

    protected MybatisAggregateRepository() {
        this.configureTypes(resolveModelClass(), resolvePersistenceObjectClass(), resolveQueryClass());
        this.registerToRepositoryRegistry();
    }

    protected MybatisAggregateRepository(MP baseMapper) {
        this.configureTypes(resolveModelClass(), resolvePersistenceObjectClass(), resolveQueryClass());
        this.setBaseMapper(baseMapper);
        this.registerToRepositoryRegistry();
    }

    protected MybatisAggregateRepository(MP baseMapper, Class<M> modelClass, Class<P> persistenceObjectClass) {
        this.configureTypes(modelClass, persistenceObjectClass, null);
        this.setBaseMapper(baseMapper);
        this.registerToRepositoryRegistry();
    }

    private static String toUnderline(String camel) {
        if (StrKit.isEmpty(camel)) {
            return camel;
        }
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                buf.append('_');
            }
            buf.append(Character.toLowerCase(c));
        }
        return buf.toString();
    }

    /**
     * 解析聚合根类型（泛型参数 M，索引 0 — 在子类 BaseRepositoryImpl 中索引变为 1）。
     */
    protected Class<M> resolveModelClass() {
        return (Class<M>) ReflectionKit.getSuperClassGenericType(this.getClass(), MybatisAggregateRepository.class, 0);
    }

    /**
     * 解析持久化对象类型（泛型参数 P，索引 1 — 在子类 BaseRepositoryImpl 中索引变为 2）。
     */
    protected Class<P> resolvePersistenceObjectClass() {
        return (Class<P>) ReflectionKit.getSuperClassGenericType(this.getClass(), MybatisAggregateRepository.class, 1);
    }

    /**
     * 解析查询对象类型（泛型参数 Q，索引 2 — 在子类 BaseRepositoryImpl 中索引变为 3）。
     */
    protected Class<? extends Query<M>> resolveQueryClass() {
        return (Class<? extends Query<M>>) ReflectionKit.getSuperClassGenericType(this.getClass(), MybatisAggregateRepository.class, 2);
    }

    protected final void configureTypes(Class<M> modelClass, Class<P> persistenceObjectClass, Class<? extends Query<M>> queryClass) {
        this.modelClass = modelClass;
        this.persistenceObjectClass = persistenceObjectClass;
        this.queryClass = queryClass;
        if (Objects.nonNull(modelClass) && Objects.nonNull(persistenceObjectClass)) {
            // PO 元数据（auto-fill、bizKey、tenantId、tableLogic 等，委托给 MP TableInfoHelper）
            this.tableInfo = resolveTableInfo(persistenceObjectClass);
            // 充血查询 Domain→PO 字段映射元数据（缓存于 DomainModelHelper，零框架依赖）
            this.domainModelInfo = DomainModelHelper.getModelInfo(modelClass, persistenceObjectClass, poProperty -> {
                if (Objects.nonNull(tableInfo)) {
                    for (TableFieldInfo tfi : tableInfo.getFieldList()) {
                        if (tfi.getProperty().equals(poProperty)) {
                            return tfi.getColumn();
                        }
                    }
                }
                return null;
            });
            MappingKit.map("MODEL_PO", modelClass, persistenceObjectClass);
            MappingKit.map("MODEL_PO", persistenceObjectClass, modelClass);
            if (Objects.nonNull(queryClass)) {
                MappingKit.map("MODEL_QUERY", modelClass, queryClass);
                MappingKit.map("MODEL_QUERY", queryClass, modelClass);
            }
            String modelClassName = modelClass.getSimpleName().toLowerCase().charAt(0) + modelClass.getSimpleName().substring(1);
            MappingKit.map("MODEL_NAME", modelClassName, modelClass);
        }
    }

    /**
     * 获取或初始化 PO 元数据。
     *
     * <p>Spring/Quarkus 的 Mapper 扫描通常会提前初始化 {@link TableInfo}；
     * 手动构造 Repository 或在轻量运行时中使用时没有该前置步骤，因此这里
     * 以独立 MyBatis 配置完成兜底初始化。之后接入真实 Configuration 时，
     * MyBatis-Plus 会按其自身规则重新绑定元数据。
     */
    private TableInfo resolveTableInfo(Class<P> type) {
        TableInfo resolved = TableInfoHelper.getTableInfo(type);
        if (Objects.nonNull(resolved)) {
            return resolved;
        }
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, type.getName());
        assistant.setCurrentNamespace(type.getName());
        return TableInfoHelper.initTableInfo(assistant, type);
    }

    /**
     * 注册本仓储到 {@link RepositoryRegistry}（充血查询反向索引）。
     *
     * <p>充血查询链路：{@code new XxxQuery().list()} → {@link Query#repository()} →
     * {@link RepositoryRegistry#repositoryForQuery(Class)} → 通过 {@code Q.class} 找到本仓储。
     */
    protected void registerToRepositoryRegistry() {
        Class<M> mc = resolveModelClass();
        Class<? extends Query<M>> qc = resolveQueryClass();
        if (Objects.nonNull(mc) && Objects.nonNull(qc)) {
            RepositoryRegistry.register(mc, qc, this);
        } else if (Objects.nonNull(mc)) {
            RepositoryRegistry.register(mc, this);
        }
    }

    /**
     * {@link IRepository} 抽象方法实现 —— 返回当前仓储持有的 MyBatis-Plus Mapper。
     */
    @Override
    public MP getBaseMapper() {
        if (Objects.isNull(this.baseMapper)) {
            throw new IllegalStateException(
                    "baseMapper must not be null — call setBaseMapper() or pass mapper to constructor");
        }
        return this.baseMapper;
    }

    /**
     * Domain Model 元数据访问器（充血查询翻译：Domain 字段 → PO 列名）。
     * 缓存于 {@link DomainModelHelper}。
     */
    protected DomainModelInfo<M> domainModelInfo() {
        return Objects.requireNonNull(domainModelInfo, "domainModelInfo must not be null");
    }

    /**
     * PO 元数据访问器（委托 MP {@code TableInfoHelper}），用于 auto-fill、bizKey、tenantId、tableLogic 等。
     */
    protected TableInfo tableInfo() {
        return Objects.requireNonNull(tableInfo, "tableInfo must not be null");
    }

    /**
     * 充血查询字段翻译：Domain 字段名 → PO 数据库列名。
     *
     * <p>翻译优先级链：
     * <ol>
     *   <li>查 {@link DomainModelInfo}（{@code @DomainField} 注解 + 默认约定）→ 用列名</li>
     *   <li>fallback：直接驼峰转下划线（保持向后兼容）</li>
     * </ol>
     */
    protected String translateProperty(String property) {
        if (StrKit.isEmpty(property)) {
            return property;
        }
        String column = domainModelInfo().getPoColumn(property);
        if (Objects.nonNull(column)) {
            return column;
        }
        return toUnderline(property);
    }

    /**
     * 按属性空间翻译查询条件，并校验 Query 绑定的 Domain/PO 类型。
     */
    protected String translateProperty(LambdaCondition condition) {
        Objects.requireNonNull(condition, "condition must not be null");
        condition.propertyRef().requireCompatible(modelClass(), persistenceObjectClass());
        if (condition.propertyRef().isPersistence()) {
            return persistenceColumn(condition.property());
        }
        return translateProperty(condition.property());
    }

    private String persistenceColumn(String property) {
        TableInfo persistenceTableInfo = tableInfo();
        if (Objects.equals(property, persistenceTableInfo.getKeyProperty())) {
            return persistenceTableInfo.getKeyColumn();
        }
        return persistenceTableInfo.getFieldList().stream()
                .filter(fieldInfo -> Objects.equals(property, fieldInfo.getProperty()))
                .map(TableFieldInfo::getColumn)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Persistence property " + property
                        + " does not exist on " + persistenceObjectClass().getName()));
    }

    public void setMapper(MP mapper) {
        this.baseMapper = Objects.requireNonNull(mapper, "mapper must not be null");
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
     *     .getEntityList(getBaseMapper());
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
     * 如需隔离请在链式调用中手动追加 {@code .eq(Entity::getTenantId, ThreadContext.get(...))}）
     */
    public LambdaQueryWrapper<P> lambdaQueryWrapper() {
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
    public LambdaUpdateWrapper<P> lambdaUpdateWrapper() {
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
        return new QueryChainWrapper<>(getBaseMapper());
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
        return new LambdaQueryChainWrapper<>(getBaseMapper());
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
        return new UpdateChainWrapper<>(getBaseMapper());
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
        return new LambdaUpdateChainWrapper<>(getBaseMapper());
    }


    protected Class<M> modelClass() {
        return Objects.requireNonNull(modelClass, "modelClass must not be null");
    }

    protected Class<P> persistenceObjectClass() {
        return Objects.requireNonNull(persistenceObjectClass, "persistenceObjectClass must not be null");
    }

    // ========================= IRepository 抽象方法实现（替代 CrudRepository 父类，去 Spring 依赖） =========================

    /**
     * IRepository 抽象方法：单条 insert or update。
     */
    @Override
    public boolean saveOrUpdate(P entity) {
        return getBaseMapper().insertOrUpdate(entity);
    }

    /**
     * IRepository 抽象方法：{@link Wrapper} 查询单条（throwEx 决定抛异常还是返回 null）。
     */
    @Override
    public P getOne(Wrapper<P> queryWrapper, boolean throwEx) {
        return getBaseMapper().selectOne(queryWrapper, throwEx);
    }

    /**
     * IRepository 抽象方法：{@link Wrapper} 查询单条（Optional 形式）。
     */
    @Override
    public Optional<P> getOneOpt(Wrapper<P> queryWrapper, boolean throwEx) {
        return Optional.ofNullable(getBaseMapper().selectOne(queryWrapper, throwEx));
    }

    /**
     * IRepository 抽象方法：{@link Wrapper} 查询单条 Map。
     */
    @Override
    public Map<String, Object> getMap(Wrapper<P> queryWrapper) {
        return SqlHelper.getObject(this.log, getBaseMapper().selectMaps(queryWrapper));
    }

    /**
     * IRepository 抽象方法：{@link Wrapper} 查询单条对象（带转换函数）。
     */
    @Override
    public <V> V getObj(Wrapper<P> queryWrapper, Function<? super Object, V> mapper) {
        List<V> list = listObjs(queryWrapper, mapper);
        return SqlHelper.getObject(this.log, list);
    }

    /**
     * IRepository 抽象方法：按 ID 删除（支持自动填充）。
     */
    @Override
    public boolean removeById(Serializable id, boolean useFill) {
        return SqlHelper.retBool(getBaseMapper().deleteById(id, useFill));
    }

    // ========================= CrudRepository 批量方法（PO 维度，无 Spring 依赖） =========================

    /**
     * 批量插入（对应 MyBatis-Plus {@code CrudRepository.saveBatch}，去 Spring 版本）。
     * <p>JdbcBatch 执行 INSERT_ONE，事务由 {@code @Transactional} 在子类或调用方控制。
     */
    @Override
    public boolean saveBatch(Collection<P> entityList, int batchSize) {
        String sqlStatement = getSqlStatement(SqlMethod.INSERT_ONE);
        return executeBatch(entityList, batchSize, (sqlSession, entity) -> sqlSession.insert(sqlStatement, entity));
    }

    /**
     * 批量保存或更新（PO 维度）。
     */
    @Override
    public boolean saveOrUpdateBatch(Collection<P> entityList, int batchSize) {
        TableInfo tableInfoLocal = tableInfo();
        if (Objects.isNull(tableInfoLocal)) {
            throw new IllegalArgumentException(
                    "error: can not execute. because can not find cache of TableInfo for entity " + this.getEntityClass());
        }
        String keyProperty = tableInfoLocal.getKeyProperty();
        if (!StringUtils.isNotEmpty(keyProperty)) {
            throw new IllegalArgumentException(
                    "error: can not execute. because can not find column for id from entity " + this.getEntityClass());
        }
        return SqlHelper.saveOrUpdateBatch(getSqlSessionFactory(), (Class<?>) this.getMapperClass(), this.log, entityList, batchSize,
                (sqlSession, entity) -> {
                    Object idVal = Objects.isNull(tableInfoLocal.getKeyProperty()) ? null : tableInfoLocal.getKeyProperty();
                    try {
                        Field keyField = entity.getClass().getDeclaredField(keyProperty);
                        keyField.setAccessible(true);
                        idVal = keyField.get(entity);
                    } catch (Exception ignored) {
                    }
                    return StringUtils.isEmpty((String) idVal)
                            || CollUtil.isEmpty(sqlSession.selectList(getSqlStatement(SqlMethod.SELECT_BY_ID), entity));
                },
                (sqlSession, entity) -> {
                    MapperMethod.ParamMap<P> param = new MapperMethod.ParamMap<>();
                    param.put(Constants.ENTITY, entity);
                    sqlSession.update(getSqlStatement(SqlMethod.UPDATE_BY_ID), param);
                });
    }

    /**
     * 批量按 ID 更新（PO 维度）。
     */
    @Override
    public boolean updateBatchById(Collection<P> entityList, int batchSize) {
        String sqlStatement = getSqlStatement(SqlMethod.UPDATE_BY_ID);
        return executeBatch(entityList, batchSize, (sqlSession, entity) -> {
            MapperMethod.ParamMap<P> param = new MapperMethod.ParamMap<>();
            param.put(Constants.ENTITY, entity);
            sqlSession.update(sqlStatement, param);
        });
    }

    /**
     * 获取 mapperStatementId（CrudRepository.getSqlStatement 的非 Spring 版本）。
     */
    protected String getSqlStatement(SqlMethod sqlMethod) {
        return SqlHelper.getSqlStatement(this.getMapperClass(), sqlMethod);
    }

    @Override
    public Optional<M> findById(ID id) {
        if (Objects.isNull(id)) {
            return Optional.empty();
        }
        P persistenceObject = getBaseMapper().selectById(id);
        if (Objects.isNull(persistenceObject)) {
            return Optional.empty();
        }
        return Optional.of(toModel(persistenceObject));
    }

    @Override
    public boolean existsById(ID id) {
        return Objects.nonNull(id) && Objects.nonNull(getBaseMapper().selectById(id));
    }

    @Override
    public M save(M aggregate) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        P persistenceObject = toPersistenceObject(aggregate);
        if (shouldInsert(aggregate)) {
            insertFill(persistenceObject);
            getBaseMapper().insert(persistenceObject);
        } else {
            updateFill(persistenceObject);
            getBaseMapper().updateById(persistenceObject);
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
            getBaseMapper().deleteById(id);
        }
    }

    public boolean delete(Serializable id) {
        if (Objects.isNull(id)) {
            return false;
        }
        return SqlHelper.retBool(getBaseMapper().deleteById(id));
    }

    protected boolean shouldInsert(M aggregate) {
        Serializable id = aggregate.id();
        return Objects.isNull(id) || Objects.isNull(getBaseMapper().selectById(id));
    }

    @Override
    public Optional<M> findFirst() {
        QueryWrapper<P> wrapper = getDefaultWrapper(false);
        wrapper.last("LIMIT 1");
        List<P> persistenceObjects = getBaseMapper().selectList(wrapper);
        if (persistenceObjects.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toModel(persistenceObjects.get(0)));
    }

    @Override
    public List<M> findAll() {
        return convert(getBaseMapper().selectList(getDefaultWrapper(false)));
    }

    @Override
    public long count() {
        Long count = getBaseMapper().selectCount(getDefaultWrapper(false));
        return Objects.nonNull(count) ? count : 0L;
    }

    @Override
    public Page<M> page(Query<M> query) {
        QueryWrapper<P> wrapper = getBaseWrapper(query);
        if (query.getSize() < 0) {
            List<M> records = convert(getBaseMapper().selectList(wrapper));
            Page<M> page = Page.succeed(records, records.size(), 1, records.size());
            fillIfNecessary(query, records);
            return page;
        }

        long current = query.getCurrent() < 1 ? 1 : query.getCurrent();
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<P> mybatisPage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, query.getSize());
        IPage<P> result = getBaseMapper().selectPage(mybatisPage, wrapper);
        List<M> records = convert(result.getRecords());
        Page<M> page = Page.succeed(records, result.getTotal(), query.getCurrent(), query.getSize());
        fillIfNecessary(query, records);
        return page;
    }

    @Override
    public long count(Query query) {
        Long count = getBaseMapper().selectCount(getBaseWrapper(query));
        return Objects.nonNull(count) ? count : 0L;
    }

    @Override
    public Optional<M> findFirst(Query query) {
        QueryWrapper<P> wrapper = getBaseWrapper(query);
        wrapper.last("LIMIT 1");
        List<P> persistenceObjects = getBaseMapper().selectList(wrapper);
        if (persistenceObjects.isEmpty()) {
            return Optional.empty();
        }
        M model = toModel(persistenceObjects.get(0));
        fill(query, model);
        return Optional.of(model);
    }

    @Override
    public List<M> findList(Query query) {
        List<M> models = convert(getBaseMapper().selectList(getBaseWrapper(query)));
        fillIfNecessary(query, models);
        return models;
    }

    @Override
    public List<Map<String, Object>> maps(Query query) {
        return getBaseMapper().selectMaps(getBaseWrapper(query));
    }

    @Override
    public boolean update(AggregateRoot<?> aggregate, Query<M> query) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        P persistenceObject = toPersistenceObject(modelClass().cast(aggregate));
        updateFill(persistenceObject);
        boolean updated = SqlHelper.retBool(getBaseMapper().update(persistenceObject, getBaseWrapper(query)));
        if (updated) {
            copyUpdatedPersistenceObject(persistenceObject, aggregate);
        }
        return updated;
    }

    @Override
    public boolean deleteByQuery(Query query) {
        return SqlHelper.retBool(getBaseMapper().delete(getBaseWrapper(query)));
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
        return SqlHelper.retBool(getBaseMapper().update(persistenceObject, getKeyWrapper(key)));
    }

    public M getByKey(String key) {
        if (Objects.isNull(key) || !StrKit.hasText(key)) {
            log.warn("The key annotated by @BizKey must not blank");
            return null;
        }
        P persistenceObject = getBaseMapper().selectOne(getKeyWrapper(key));
        return Objects.nonNull(persistenceObject) ? toModel(persistenceObject) : null;
    }

    public List<M> listByKey(List<Serializable> keys) {
        if (Objects.isNull(keys) || keys.isEmpty()) {
            return Collections.emptyList();
        }
        if (keys.size() >= 100) {
            throw new IllegalArgumentException("批量查询的业务key不能大于100");
        }
        return convert(getBaseMapper().selectList(getKeyWrapper(keys)));
    }

    @Override
    public void fill(Query<M> query, AggregateRoot<?> model) {
        if (modelClass().isInstance(model)) {
            fill(query, List.of(modelClass().cast(model)));
        }
    }

    @Override
    public void fill(Query<M> query, List<M> models) {
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
        TableInfo ti = tableInfo();
        if (!ignoreTenantId) {
            String tenantId = ThreadContext.get(ContextConstants.TENANT_ID);
            TableFieldInfo tenantField = findFieldInfoByAnnotation(ti, TenantId.class);
            if (Objects.nonNull(tenantField) && Objects.nonNull(tenantId)) {
                wrapper.eq(tenantField.getColumn(), tenantId);
            }
        }
        String systemId = ThreadContext.get(ContextConstants.SYSTEM_ID);
        TableFieldInfo systemField = findFieldInfoByAnnotation(ti, SystemId.class);
        if (Objects.nonNull(systemField) && Objects.nonNull(systemId)) {
            wrapper.eq(systemField.getColumn(), systemId);
        }
        return wrapper;
    }

    @SuppressWarnings("unchecked")
    protected QueryWrapper<P> getBaseWrapper(Query<M> query) {
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
    private void applyConditions(QueryWrapper<P> wrapper, Query<M> query) {
        for (Object obj : query.getWhereConditions()) {
            LambdaCondition condition = (LambdaCondition) obj;
            String column = translateProperty(condition);
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
    private void applyOrderBy(Query<M> query, QueryWrapper<P> wrapper) {
        for (Object obj : query.getOrderByConditions()) {
            LambdaCondition orderBy = (LambdaCondition) obj;
            String column = translateProperty(orderBy);
            wrapper.orderBy(true, "ASC".equals(orderBy.operator()), column);
        }
    }

    protected QueryWrapper<P> getKeyWrapper(Serializable key) {
        TableInfo ti = tableInfo();
        TableFieldInfo bizKeyField = findFieldInfoByAnnotation(ti, BizKey.class);
        if (Objects.isNull(bizKeyField)) {
            throw new IllegalArgumentException("当前entity实体没找到业务key字段，请在entity实体中使用@BizKey注解标记对应的字段！");
        }
        QueryWrapper<P> wrapper = getDefaultWrapper(false);
        wrapper.eq(bizKeyField.getColumn(), key);
        return wrapper;
    }

    protected QueryWrapper<P> getKeyWrapper(List<Serializable> keys) {
        TableInfo ti = tableInfo();
        TableFieldInfo bizKeyField = findFieldInfoByAnnotation(ti, BizKey.class);
        if (Objects.isNull(bizKeyField)) {
            throw new IllegalArgumentException("当前entity实体没找到业务key字段，请在entity实体中使用@BizKey注解标记对应的字段！");
        }
        QueryWrapper<P> wrapper = getDefaultWrapper(false);
        wrapper.in(bizKeyField.getColumn(), keys);
        return wrapper;
    }

    /**
     * 在 TableInfo.fieldList 中查找标注了指定注解的字段。
     */
    private TableFieldInfo findFieldInfoByAnnotation(TableInfo ti, Class<? extends java.lang.annotation.Annotation> annotationType) {
        if (Objects.isNull(ti)) {
            return null;
        }
        for (TableFieldInfo tfi : ti.getFieldList()) {
            if (Objects.nonNull(tfi.getField()) && tfi.getField().isAnnotationPresent(annotationType)) {
                return tfi;
            }
        }
        return null;
    }

    /**
     * 在 TableInfo.fieldList 中查找指定 PO 字段（反射）。
     */
    private Field findFieldByProperty(TableInfo ti, Class<? extends java.lang.annotation.Annotation> annotationType) {
        if (Objects.isNull(ti)) {
            return null;
        }
        for (TableFieldInfo tfi : ti.getFieldList()) {
            if (Objects.nonNull(tfi.getField()) && tfi.getField().isAnnotationPresent(annotationType)) {
                return tfi.getField();
            }
        }
        return null;
    }

    private void applySelect(Query<M> query, QueryWrapper<P> wrapper) {
        if (query.hasSelect()) {
            for (Object obj : query.getSelectColumns()) {
                String property = String.valueOf(obj);
                String column = translateProperty(property);
                wrapper.select(column);
            }
        }
    }

    private void applyGroupBy(Query<M> query, QueryWrapper<P> wrapper) {
        if (query.hasGroupBy()) {
            for (Object obj : query.getGroupByColumns()) {
                String property = String.valueOf(obj);
                String column = translateProperty(property);
                wrapper.groupBy(column);
            }
        }
    }

    private void having(Query<M> query, QueryWrapper<P> wrapper) {
        if (Objects.nonNull(query) && Objects.nonNull(query.getHaving()) && StrKit.hasText(query.getHaving())) {
            wrapper.having(query.getHaving());
        }
    }

    protected String getKeyValue(P persistenceObject) {
        try {
            TableFieldInfo bizKeyField = findFieldInfoByAnnotation(tableInfo(), BizKey.class);
            if (Objects.isNull(bizKeyField) || Objects.isNull(bizKeyField.getField())) {
                return null;
            }
            Field keyField = bizKeyField.getField();
            keyField.setAccessible(true);
            Object value = keyField.get(persistenceObject);
            return Objects.nonNull(value) ? value.toString() : null;
        } catch (IllegalAccessException | IllegalArgumentException exception) {
            ApplicationLog.logger().error("获取当前实体对象的key值出现错误！", exception);
            throw new IllegalArgumentException("获取当前实体对象的key值出现错误!");
        }
    }

    private void copyUpdatedPersistenceObject(P persistenceObject, AggregateRoot<?> aggregate) {
        TableInfo ti = tableInfo();
        if (Objects.isNull(ti.getKeyProperty())) {
            return;
        }
        Field idField;
        try {
            idField = persistenceObjectClass().getDeclaredField(ti.getKeyProperty());
            idField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            return;
        }
        try {
            Serializable id = (Serializable) idField.get(persistenceObject);
            if (Objects.nonNull(id)) {
                P updated = getBaseMapper().selectById(id);
                if (Objects.nonNull(updated)) {
                    BeanKit.copy(updated, aggregate);
                }
            }
        } catch (IllegalAccessException e) {
            // ignore
        }
    }

    private void fillIfNecessary(Query<M> query, List<M> models) {
        if (Objects.nonNull(models) && !models.isEmpty()) {
            fill(query, models);
        }
    }

    private void insertFill(P persistenceObject) {
        try {
            TableInfo ti = tableInfo();
            fillContextValue(findFieldByProperty(ti, TenantId.class), persistenceObject, ThreadContext.get(ContextConstants.TENANT_ID));
            fillContextValue(findFieldByProperty(ti, SystemId.class), persistenceObject, ThreadContext.get(ContextConstants.SYSTEM_ID));
            fillLogicDeleteValue(persistenceObject);
            // @OnCreate fields: 通过反射 PO 字段（保留原有 ddd4j 语义）
            for (Field field : FieldUtils.getAllFieldsList(persistenceObjectClass())) {
                if (field.isAnnotationPresent(OnCreate.class)) {
                    fillDateField(field, persistenceObject);
                }
            }
        } catch (IllegalAccessException ignored) {
            ApplicationLog.logger().debug("Ignore insert fill failure: {}", ignored.getMessage());
        }
    }

    private void updateFill(P persistenceObject) {
        try {
            for (Field field : FieldUtils.getAllFieldsList(persistenceObjectClass())) {
                if (field.isAnnotationPresent(OnUpdate.class)) {
                    fillDateField(field, persistenceObject);
                }
            }
        } catch (IllegalAccessException ignored) {
            ApplicationLog.logger().debug("Ignore update fill failure: {}", ignored.getMessage());
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
        TableInfo ti = tableInfo();
        if (Objects.isNull(ti) || !ti.isWithLogicDelete()) {
            return;
        }
        TableFieldInfo logicField = ti.getLogicDeleteFieldInfo();
        if (Objects.isNull(logicField) || Objects.isNull(logicField.getField())) {
            return;
        }
        Field field = logicField.getField();
        TableLogic tableLogic = field.getAnnotation(TableLogic.class);
        if (Objects.isNull(tableLogic)) {
            return;
        }
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

}
