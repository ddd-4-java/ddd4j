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

    /** 集中保存 <code>ApplicationLog</code> 的声明，供当前模块使用。 */
    @Slf4j
    private static final class ApplicationLog {

        /** 初始化 <code>ApplicationLog</code> 并委托其默认构造路径。 */
        private ApplicationLog() {
        }

        /**
         * 读取<code>log</code> 的当前值。
         *
         * @return 返回表达式 <code>log</code>
         */
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

    /** \u4ed3\u50a8\u7ba1\u7406\u7684\u805a\u5408\u6a21\u578b\u7c7b\u578b\u3002 */
    private Class<M> modelClass;
    /** \u6620\u5c04\u5230\u6570\u636e\u8868\u7684\u6301\u4e45\u5316\u5bf9\u8c61\u7c7b\u578b\u3002 */
    private Class<P> persistenceObjectClass;
    /** \u5f53\u524d\u4ed3\u50a8\u53ef\u5904\u7406\u7684\u67e5\u8be2\u7c7b\u578b\u3002 */
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

    /** \u521b\u5efa MyBatis-Plus \u805a\u5408\u4ed3\u5e93\u3002 */
    protected MybatisAggregateRepository() {
        this.configureTypes(resolveModelClass(), resolvePersistenceObjectClass(), resolveQueryClass());
        this.registerToRepositoryRegistry();
    }

    /**
     * \u521b\u5efa MyBatis-Plus \u805a\u5408\u4ed3\u5e93\u3002
     * @param baseMapper MyBatis-Plus \u6620\u5c04\u5668
     */
    protected MybatisAggregateRepository(MP baseMapper) {
        this.configureTypes(resolveModelClass(), resolvePersistenceObjectClass(), resolveQueryClass());
        this.setBaseMapper(baseMapper);
        this.registerToRepositoryRegistry();
    }

    /**
     * \u521b\u5efa MyBatis-Plus \u805a\u5408\u4ed3\u5e93\u3002
     * @param baseMapper MyBatis-Plus \u6620\u5c04\u5668
     * @param modelClass \u805a\u5408\u6839\u6a21\u578b\u7c7b\u578b
     * @param persistenceObjectClass \u6301\u4e45\u5316\u5bf9\u8c61\u7c7b\u578b
     */
    protected MybatisAggregateRepository(MP baseMapper, Class<M> modelClass, Class<P> persistenceObjectClass) {
        this.configureTypes(modelClass, persistenceObjectClass, null);
        this.setBaseMapper(baseMapper);
        this.registerToRepositoryRegistry();
    }

    /**
     * \u5c06\u9a7c\u5cf0\u5c5e\u6027\u540d\u8f6c\u6362\u4e3a\u4e0b\u5212\u7ebf\u5217\u540d\u3002
     * @param camel \u5f85\u8f6c\u6362\u7684\u9a7c\u5cf0\u540d\u79f0
     * @return \u4e0b\u5212\u7ebf\u547d\u540d\u7684\u5c5e\u6027
     */
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
     * \u89e3\u6790\u805a\u5408\u6839\u7c7b\u578b\uff08\u6cdb\u578b\u53c2\u6570 M\uff0c\u7d22\u5f15 0 \u2014 \u5728\u5b50\u7c7b BaseRepositoryImpl \u4e2d\u7d22\u5f15\u53d8\u4e3a 1\uff09\u3002
     *
     * @return \u805a\u5408\u6839\u6a21\u578b\u7c7b\u578b
     */
    protected Class<M> resolveModelClass() {
        return (Class<M>) ReflectionKit.getSuperClassGenericType(this.getClass(), MybatisAggregateRepository.class, 0);
    }

    /**
     * \u89e3\u6790\u6301\u4e45\u5316\u5bf9\u8c61\u7c7b\u578b\uff08\u6cdb\u578b\u53c2\u6570 P\uff0c\u7d22\u5f15 1 \u2014 \u5728\u5b50\u7c7b BaseRepositoryImpl \u4e2d\u7d22\u5f15\u53d8\u4e3a 2\uff09\u3002
     *
     * @return \u6301\u4e45\u5316\u5bf9\u8c61\u7c7b\u578b
     */
    protected Class<P> resolvePersistenceObjectClass() {
        return (Class<P>) ReflectionKit.getSuperClassGenericType(this.getClass(), MybatisAggregateRepository.class, 1);
    }

    /**
     * \u89e3\u6790\u67e5\u8be2\u5bf9\u8c61\u7c7b\u578b\uff08\u6cdb\u578b\u53c2\u6570 Q\uff0c\u7d22\u5f15 2 \u2014 \u5728\u5b50\u7c7b BaseRepositoryImpl \u4e2d\u7d22\u5f15\u53d8\u4e3a 3\uff09\u3002
     *
     * @return \u67e5\u8be2\u5bf9\u8c61\u7c7b\u578b
     */
    protected Class<? extends Query<M>> resolveQueryClass() {
        return (Class<? extends Query<M>>) ReflectionKit.getSuperClassGenericType(this.getClass(), MybatisAggregateRepository.class, 2);
    }

    /**
     * \u914d\u7f6e\u805a\u5408\u6839\u548c\u6301\u4e45\u5316\u5bf9\u8c61\u7c7b\u578b\u3002
     * @param modelClass \u805a\u5408\u6839\u6a21\u578b\u7c7b\u578b
     * @param persistenceObjectClass \u6301\u4e45\u5316\u5bf9\u8c61\u7c7b\u578b
     * @param queryClass \u67e5\u8be2\u5bf9\u8c61\u7c7b\u578b
     */
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
     * \u83b7\u53d6\u6216\u521d\u59cb\u5316 PO \u5143\u6570\u636e\u3002
     *
     *  <p>Spring/Quarkus \u7684 Mapper \u626b\u63cf\u901a\u5e38\u4f1a\u63d0\u524d\u521d\u59cb\u5316 {@link TableInfo}\uff1b
     *  \u624b\u52a8\u6784\u9020 Repository \u6216\u5728\u8f7b\u91cf\u8fd0\u884c\u65f6\u4e2d\u4f7f\u7528\u65f6\u6ca1\u6709\u8be5\u524d\u7f6e\u6b65\u9aa4\uff0c\u56e0\u6b64\u8fd9\u91cc
     *  \u4ee5\u72ec\u7acb MyBatis \u914d\u7f6e\u5b8c\u6210\u515c\u5e95\u521d\u59cb\u5316\u3002\u4e4b\u540e\u63a5\u5165\u771f\u5b9e Configuration \u65f6\uff0c
     *  MyBatis-Plus \u4f1a\u6309\u5176\u81ea\u8eab\u89c4\u5219\u91cd\u65b0\u7ed1\u5b9a\u5143\u6570\u636e\u3002
     *
     * @param type \u6301\u4e45\u5316\u5b9e\u4f53\u7c7b\u578b
     * @return \u5bf9\u5e94\u5b9e\u4f53\u7684 MyBatis \u8868\u5143\u6570\u636e
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
     * {@link IRepository} \u62bd\u8c61\u65b9\u6cd5\u5b9e\u73b0 \u2014\u2014 \u8fd4\u56de\u5f53\u524d\u4ed3\u50a8\u6301\u6709\u7684 MyBatis-Plus Mapper\u3002
     *
     * @return MyBatis-Plus Mapper
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
     * Domain Model \u5143\u6570\u636e\u8bbf\u95ee\u5668\uff08\u5145\u8840\u67e5\u8be2\u7ffb\u8bd1\uff1aDomain \u5b57\u6bb5 \u2192 PO \u5217\u540d\uff09\u3002
     *  \u7f13\u5b58\u4e8e {@link DomainModelHelper}\u3002
     *
     * @return \u9886\u57df\u6a21\u578b\u5c5e\u6027\u6620\u5c04\u4fe1\u606f
     */
    protected DomainModelInfo<M> domainModelInfo() {
        return Objects.requireNonNull(domainModelInfo, "domainModelInfo must not be null");
    }

    /**
     * PO \u5143\u6570\u636e\u8bbf\u95ee\u5668\uff08\u59d4\u6258 MP {@code TableInfoHelper}\uff09\uff0c\u7528\u4e8e auto-fill\u3001bizKey\u3001tenantId\u3001tableLogic \u7b49\u3002
     *
     * @return MyBatis-Plus \u8868\u5143\u4fe1\u606f
     */
    protected TableInfo tableInfo() {
        return Objects.requireNonNull(tableInfo, "tableInfo must not be null");
    }

    /**
     * \u5145\u8840\u67e5\u8be2\u5b57\u6bb5\u7ffb\u8bd1\uff1aDomain \u5b57\u6bb5\u540d \u2192 PO \u6570\u636e\u5e93\u5217\u540d\u3002
     *
     *  <p>\u7ffb\u8bd1\u4f18\u5148\u7ea7\u94fe\uff1a
     *  <ol>
     *    <li>\u67e5 {@link DomainModelInfo}\uff08{@code @DomainField} \u6ce8\u89e3 + \u9ed8\u8ba4\u7ea6\u5b9a\uff09\u2192 \u7528\u5217\u540d</li>
     *    <li>fallback\uff1a\u76f4\u63a5\u9a7c\u5cf0\u8f6c\u4e0b\u5212\u7ebf\uff08\u4fdd\u6301\u5411\u540e\u517c\u5bb9\uff09</li>
     *  </ol>
     *
     * @param property \u9886\u57df\u5c5e\u6027\u6216\u6301\u4e45\u5316\u5c5e\u6027
     * @return \u6301\u4e45\u5316\u5b57\u6bb5\u6216\u5217\u540d
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
     * \u6309\u5c5e\u6027\u7a7a\u95f4\u7ffb\u8bd1\u67e5\u8be2\u6761\u4ef6\uff0c\u5e76\u6821\u9a8c Query \u7ed1\u5b9a\u7684 Domain/PO \u7c7b\u578b\u3002
     *
     * @param condition \u67e5\u8be2\u6761\u4ef6
     * @return \u6301\u4e45\u5316\u5b57\u6bb5\u6216\u5217\u540d
     */
    protected String translateProperty(LambdaCondition condition) {
        Objects.requireNonNull(condition, "condition must not be null");
        condition.propertyRef().requireCompatible(modelClass(), persistenceObjectClass());
        if (condition.propertyRef().isPersistence()) {
            return persistenceColumn(condition.property());
        }
        return translateProperty(condition.property());
    }

    /**
     * \u6309\u6301\u4e45\u5316\u6620\u5c04\u89e3\u6790\u9886\u57df\u5c5e\u6027\u5bf9\u5e94\u7684 SQL \u5217\u3002
     * @param property \u7528\u4e8e\u5b9a\u4f4d SQL \u5217\u7684 Java \u5c5e\u6027\u540d
     * @return \u6301\u4e45\u5316\u5217\u540d\u79f0
     */
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

    /**
     * 执行 <code>set Mapper</code> 对应的处理。
     *
     * @param mapper 传入此操作的 <code>MP</code> 类型 <code>mapper</code>
     */
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


    /**
     * \u83b7\u53d6\u805a\u5408\u6839\u6a21\u578b\u7c7b\u578b\u3002
     * @return \u805a\u5408\u6839\u6a21\u578b\u7c7b\u578b
     */
    protected Class<M> modelClass() {
        return Objects.requireNonNull(modelClass, "modelClass must not be null");
    }

    /**
     * \u83b7\u53d6\u6301\u4e45\u5316\u5bf9\u8c61\u7c7b\u578b\u3002
     * @return \u6301\u4e45\u5316\u5bf9\u8c61\u7c7b\u578b
     */
    protected Class<P> persistenceObjectClass() {
        return Objects.requireNonNull(persistenceObjectClass, "persistenceObjectClass must not be null");
    }

    // ========================= IRepository 抽象方法实现（替代 CrudRepository 父类，去 Spring 依赖） =========================

    /**
     * IRepository \u62bd\u8c61\u65b9\u6cd5\uff1a\u5355\u6761 insert or update\u3002
     *
     * @param entity 传入此操作的 <code>P</code> 类型 <code>entity</code>
     * @return 返回表达式 <code>getBaseMapper().insertOrUpdate(entity)</code>
     */
    @Override
    public boolean saveOrUpdate(P entity) {
        return getBaseMapper().insertOrUpdate(entity);
    }

    /**
     * IRepository \u62bd\u8c61\u65b9\u6cd5\uff1a{@link Wrapper} \u67e5\u8be2\u5355\u6761\uff08throwEx \u51b3\u5b9a\u629b\u5f02\u5e38\u8fd8\u662f\u8fd4\u56de null\uff09\u3002
     *
     * @param queryWrapper 传入此操作的 <code>Wrapper&lt;P&gt;</code> 类型 <code>queryWrapper</code>
     * @param throwEx 传入此操作的 <code>boolean</code> 类型 <code>throwEx</code>
     * @return 返回表达式 <code>getBaseMapper().selectOne(queryWrapper, throwEx)</code>
     */
    @Override
    public P getOne(Wrapper<P> queryWrapper, boolean throwEx) {
        return getBaseMapper().selectOne(queryWrapper, throwEx);
    }

    /**
     * IRepository \u62bd\u8c61\u65b9\u6cd5\uff1a{@link Wrapper} \u67e5\u8be2\u5355\u6761\uff08Optional \u5f62\u5f0f\uff09\u3002
     *
     * @param queryWrapper 传入此操作的 <code>Wrapper&lt;P&gt;</code> 类型 <code>queryWrapper</code>
     * @param throwEx 传入此操作的 <code>boolean</code> 类型 <code>throwEx</code>
     * @return 返回表达式 <code>Optional.ofNullable(getBaseMapper().selectOne(queryWrapper, throwEx))</code>
     */
    @Override
    public Optional<P> getOneOpt(Wrapper<P> queryWrapper, boolean throwEx) {
        return Optional.ofNullable(getBaseMapper().selectOne(queryWrapper, throwEx));
    }

    /**
     * IRepository \u62bd\u8c61\u65b9\u6cd5\uff1a{@link Wrapper} \u67e5\u8be2\u5355\u6761 Map\u3002
     *
     * @param queryWrapper 传入此操作的 <code>Wrapper&lt;P&gt;</code> 类型 <code>queryWrapper</code>
     * @return 返回表达式 <code>SqlHelper.getObject(this.log, getBaseMapper().selectMaps(queryWrapper))</code>
     */
    @Override
    public Map<String, Object> getMap(Wrapper<P> queryWrapper) {
        return SqlHelper.getObject(this.log, getBaseMapper().selectMaps(queryWrapper));
    }

    /**
     * IRepository \u62bd\u8c61\u65b9\u6cd5\uff1a{@link Wrapper} \u67e5\u8be2\u5355\u6761\u5bf9\u8c61\uff08\u5e26\u8f6c\u6362\u51fd\u6570\uff09\u3002
     *
     * @param <V> 当前操作使用的泛型类型
     * @param queryWrapper 传入此操作的 <code>Wrapper&lt;P&gt;</code> 类型 <code>queryWrapper</code>
     * @param mapper 传入此操作的 <code>Function&lt;? super Object, V&gt;</code> 类型 <code>mapper</code>
     * @return 返回表达式 <code>SqlHelper.getObject(this.log, list)</code>
     */
    @Override
    public <V> V getObj(Wrapper<P> queryWrapper, Function<? super Object, V> mapper) {
        List<V> list = listObjs(queryWrapper, mapper);
        return SqlHelper.getObject(this.log, list);
    }

    /**
     * IRepository \u62bd\u8c61\u65b9\u6cd5\uff1a\u6309 ID \u5220\u9664\uff08\u652f\u6301\u81ea\u52a8\u586b\u5145\uff09\u3002
     *
     * @param id 传入此操作的 <code>Serializable</code> 类型 <code>id</code>
     * @param useFill 传入此操作的 <code>boolean</code> 类型 <code>useFill</code>
     * @return 返回表达式 <code>SqlHelper.retBool(getBaseMapper().deleteById(id, useFill))</code>
     */
    @Override
    public boolean removeById(Serializable id, boolean useFill) {
        return SqlHelper.retBool(getBaseMapper().deleteById(id, useFill));
    }

    // ========================= CrudRepository 批量方法（PO 维度，无 Spring 依赖） =========================

    /**
     * \u6279\u91cf\u63d2\u5165\uff08\u5bf9\u5e94 MyBatis-Plus {@code CrudRepository.saveBatch}\uff0c\u53bb Spring \u7248\u672c\uff09\u3002
     *  <p>JdbcBatch \u6267\u884c INSERT_ONE\uff0c\u4e8b\u52a1\u7531 {@code @Transactional} \u5728\u5b50\u7c7b\u6216\u8c03\u7528\u65b9\u63a7\u5236\u3002
     *
     * @param entityList 传入此操作的 <code>Collection&lt;P&gt;</code> 类型 <code>entityList</code>
     * @param batchSize 传入此操作的 <code>int</code> 类型 <code>batchSize</code>
     * @return 该判断是否成立
     */
    @Override
    public boolean saveBatch(Collection<P> entityList, int batchSize) {
        String sqlStatement = getSqlStatement(SqlMethod.INSERT_ONE);
        return executeBatch(entityList, batchSize, (sqlSession, entity) -> sqlSession.insert(sqlStatement, entity));
    }

    /**
     * \u6279\u91cf\u4fdd\u5b58\u6216\u66f4\u65b0\uff08PO \u7ef4\u5ea6\uff09\u3002
     *
     * @param entityList 传入此操作的 <code>Collection&lt;P&gt;</code> 类型 <code>entityList</code>
     * @param batchSize 传入此操作的 <code>int</code> 类型 <code>batchSize</code>
     * @return 该判断是否成立
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
     * \u6279\u91cf\u6309 ID \u66f4\u65b0\uff08PO \u7ef4\u5ea6\uff09\u3002
     *
     * @param entityList 传入此操作的 <code>Collection&lt;P&gt;</code> 类型 <code>entityList</code>
     * @param batchSize 传入此操作的 <code>int</code> 类型 <code>batchSize</code>
     * @return 该判断是否成立
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
     * \u83b7\u53d6 mapperStatementId\uff08CrudRepository.getSqlStatement \u7684\u975e Spring \u7248\u672c\uff09\u3002
     *
     * @param sqlMethod 传入此操作的 <code>SqlMethod</code> 类型 <code>sqlMethod</code>
     * @return 返回表达式 <code>SqlHelper.getSqlStatement(this.getMapperClass(), sqlMethod)</code>
     */
    protected String getSqlStatement(SqlMethod sqlMethod) {
        return SqlHelper.getSqlStatement(this.getMapperClass(), sqlMethod);
    }

    /**
     * 按主键读取领域对象或持久化记录。
     *
     * <p>MyBatis 实现调用 <code>Optional.empty()</code>。</p>
     *
     * @param id 聚合根标识
     * @return 根据执行路径返回<code>Optional.empty()</code>、<code>Optional.of(toModel(persistenceObject))</code>
     */
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

    /**
     * 检查指定主键是否存在。
     *
     * <p>MyBatis 实现调用 <code>getBaseMapper().selectById(id)</code>。</p>
     *
     * @param id 聚合根标识
     * @return 实际返回<code>Objects.nonNull(id) &amp;&amp; Objects.nonNull(getBaseMapper().selectById(id))</code>
     */
    @Override
    public boolean existsById(ID id) {
        return Objects.nonNull(id) && Objects.nonNull(getBaseMapper().selectById(id));
    }

    /**
     * 写入或更新给定的领域对象或存储记录。
     *
     * <p>MyBatis 实现调用 <code>toPersistenceObject(aggregate)</code>。</p>
     *
     * @param aggregate 待处理聚合根
     * @return 实际返回<code>aggregate</code>
     */
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

    /**
     * \u4ec5\u6309\u4e3b\u952e\u66f4\u65b0\uff08\u4e0d\u63d2\u5165\uff09\uff0c\u5bf9\u9f50 {@code BaseMapper.updateById}\u3002
     *
     * @param aggregate \u5f85\u5199\u5165\u7684\u805a\u5408
     * @return \u66f4\u65b0\u540e\u7684\u805a\u5408
     */
    @Override
    public M updateById(M aggregate) {
        Objects.requireNonNull(aggregate, "aggregate must not be null");
        P persistenceObject = toPersistenceObject(aggregate);
        updateFill(persistenceObject);
        getBaseMapper().updateById(persistenceObject);
        BeanKit.copy(persistenceObject, aggregate);
        return aggregate;
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
     * 按 <code>update</code> 的契约计算并返回结果。
     *
     * @param aggregate 传入此操作的 <code>M</code> 类型 <code>aggregate</code>
     * @return 返回表达式 <code>updateById(aggregate)</code>
     */
    public M update(M aggregate) {
        return updateById(aggregate);
    }

    /**
     * 按 <code>save Or Update</code> 的契约计算并返回结果。
     *
     * @param aggregate 传入此操作的 <code>M</code> 类型 <code>aggregate</code>
     * @return 返回表达式 <code>insertOrUpdate(aggregate)</code>
     */
    public M saveOrUpdate(M aggregate) {
        return insertOrUpdate(aggregate);
    }

    /**
     * 按主键删除记录。
     *
     * <p>MyBatis 实现调用 <code>getBaseMapper().deleteById(id)</code>。</p>
     *
     * @param id 聚合根标识
     */
    @Override
    public void deleteById(ID id) {
        if (Objects.nonNull(id)) {
            getBaseMapper().deleteById(id);
        }
    }

    /**
     * 执行 <code>delete</code>，调用 <code>SqlHelper.retBool</code>。
     *
     * @param id 传入此操作的 <code>Serializable</code> 类型 <code>id</code>
     * @return 按条件返回 <code>false</code>、<code>SqlHelper.retBool(getBaseMapper().deleteById(id))</code>
     */
    public boolean delete(Serializable id) {
        if (Objects.isNull(id)) {
            return false;
        }
        return SqlHelper.retBool(getBaseMapper().deleteById(id));
    }

    /**
     * \u5224\u65ad\u805a\u5408\u6839\u662f\u5426\u9700\u8981\u63d2\u5165\u3002
     * @param aggregate \u805a\u5408\u6839
     * @return \u662f\u5426\u9700\u8981\u63d2\u5165
     */
    protected boolean shouldInsert(M aggregate) {
        Serializable id = aggregate.id();
        return Objects.isNull(id) || Objects.isNull(getBaseMapper().selectById(id));
    }

    /**
     * 读取满足条件的第一条记录。
     *
     * <p>MyBatis 实现调用 <code>getDefaultWrapper(false)</code>。</p>
     *
     * @return 第一个匹配条件的结果
     */
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

    /**
     * 查询存储中符合条件的记录。
     *
     * <p>MyBatis 实现调用 <code>convert(getBaseMapper().selectList(getDefaultWrapper(false)))</code>。</p>
     *
     * @return 满足条件的结果列表
     */
    @Override
    public List<M> findAll() {
        return convert(getBaseMapper().selectList(getDefaultWrapper(false)));
    }

    /**
     * 统计符合查询范围的数据数量。
     *
     * <p>MyBatis 实现调用 <code>getBaseMapper().selectCount(getDefaultWrapper(false))</code>。</p>
     *
     * @return 满足筛选条件的记录数量
     */
    @Override
    public long count() {
        Long count = getBaseMapper().selectCount(getDefaultWrapper(false));
        return Objects.nonNull(count) ? count : 0L;
    }

    /**
     * 根据分页参数和查询条件读取结果。
     *
     * <p>MyBatis 实现调用 <code>getBaseWrapper(query)</code>。</p>
     *
     * @param query 查询条件
     * @return 实际返回<code>page</code>
     */
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

    /**
     * 按 <code>count</code> 的契约计算并返回结果。
     *
     * @param query 传入此操作的 <code>Query</code> 类型 <code>query</code>
     * @return 返回表达式 <code>Objects.nonNull(count) ? count : 0L</code>
     */
    @Override
    public long count(Query query) {
        Long count = getBaseMapper().selectCount(getBaseWrapper(query));
        return Objects.nonNull(count) ? count : 0L;
    }

    /**
     * 执行 <code>find First</code>，调用 <code>wrapper.last</code>、<code>persistenceObjects.isEmpty</code>、<code>Optional.empty</code>。
     *
     * @param query 传入此操作的 <code>Query</code> 类型 <code>query</code>
     * @return 按条件返回 <code>Optional.empty()</code>、<code>Optional.of(model)</code>
     */
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

    /**
     * 读取<code>models</code> 的当前值。
     *
     * @param query 传入此操作的 <code>Query</code> 类型 <code>query</code>
     * @return 返回表达式 <code>models</code>
     */
    @Override
    public List<M> findList(Query query) {
        List<M> models = convert(getBaseMapper().selectList(getBaseWrapper(query)));
        fillIfNecessary(query, models);
        return models;
    }

    /**
     * 按 <code>maps</code> 的契约计算并返回结果。
     *
     * @param query 传入此操作的 <code>Query</code> 类型 <code>query</code>
     * @return 返回表达式 <code>getBaseMapper().selectMaps(getBaseWrapper(query))</code>
     */
    @Override
    public List<Map<String, Object>> maps(Query query) {
        return getBaseMapper().selectMaps(getBaseWrapper(query));
    }

    /**
     * 更新已经存在的记录。
     *
     * <p>MyBatis 实现调用 <code>toPersistenceObject(modelClass().cast(aggregate))</code>。</p>
     *
     * @param aggregate 待处理聚合根
     * @param query 查询条件
     * @return 实际返回<code>updated</code>
     */
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

    /**
     * 执行 <code>delete By Query</code>，调用 <code>SqlHelper.retBool</code>。
     *
     * @param query 传入此操作的 <code>Query</code> 类型 <code>query</code>
     * @return 返回表达式 <code>SqlHelper.retBool(getBaseMapper().delete(getBaseWrapper(query)))</code>
     */
    @Override
    public boolean deleteByQuery(Query query) {
        return SqlHelper.retBool(getBaseMapper().delete(getBaseWrapper(query)));
    }

    /**
     * 按 <code>delete</code> 的契约计算并返回结果。
     *
     * @param query 传入此操作的 <code>Query</code> 类型 <code>query</code>
     * @return 返回表达式 <code>deleteByQuery(query)</code>
     */
    public boolean delete(Query query) {
        return deleteByQuery(query);
    }

    /**
     * 按 <code>get</code> 的契约计算并返回结果。
     *
     * @param id 传入此操作的 <code>Serializable</code> 类型 <code>id</code>
     * @return 返回表达式 <code>findById((ID) id).orElse(null)</code>
     */
    public M get(Serializable id) {
        return findById((ID) id).orElse(null);
    }

    /**
     * 按 <code>list</code> 的契约计算并返回结果。
     *
     * @param query 传入此操作的 <code>Query</code> 类型 <code>query</code>
     * @return 返回表达式 <code>findList(query)</code>
     */
    public List<M> list(Query query) {
        return findList(query);
    }

    /**
     * 按 <code>first</code> 的契约计算并返回结果。
     *
     * @param query 传入此操作的 <code>Query</code> 类型 <code>query</code>
     * @return 返回表达式 <code>findFirst(query).orElse(null)</code>
     */
    public M first(Query query) {
        return findFirst(query).orElse(null);
    }

    /**
     * 按 <code>one</code> 的契约计算并返回结果。
     *
     * @param query 传入此操作的 <code>Query</code> 类型 <code>query</code>
     * @return 返回表达式 <code>findFirst(query).orElse(null)</code>
     */
    public M one(Query query) {
        return findFirst(query).orElse(null);
    }

    /**
     * 按 <code>exist</code> 的契约计算并返回结果。
     *
     * @param query 传入此操作的 <code>Query</code> 类型 <code>query</code>
     * @return 返回表达式 <code>exists(query)</code>
     */
    public boolean exist(Query query) {
        return exists(query);
    }

    /**
     * \u6309\u4e1a\u52a1\u952e\u66f4\u65b0\u805a\u5408\u6839\u3002
     * @param model \u805a\u5408\u6839\u6a21\u578b
     * @return \u662f\u5426\u66f4\u65b0\u6210\u529f
     */
    public boolean updateByKey(M model) {
        P persistenceObject = toPersistenceObject(model);
        String key = getKeyValue(persistenceObject);
        if (Objects.isNull(key) || !StrKit.hasText(key)) {
            throw new IllegalArgumentException("当前entity实体业务key字段为null，无法适用当前方法更新！");
        }
        updateFill(persistenceObject);
        return SqlHelper.retBool(getBaseMapper().update(persistenceObject, getKeyWrapper(key)));
    }

    /**
     * \u6309\u4e1a\u52a1\u952e\u67e5\u8be2\u805a\u5408\u6839\u3002
     * @param key \u4e1a\u52a1\u952e
     * @return \u5339\u914d\u7684\u805a\u5408\u6839
     */
    public M getByKey(String key) {
        if (Objects.isNull(key) || !StrKit.hasText(key)) {
            log.warn("The key annotated by @BizKey must not blank");
            return null;
        }
        P persistenceObject = getBaseMapper().selectOne(getKeyWrapper(key));
        return Objects.nonNull(persistenceObject) ? toModel(persistenceObject) : null;
    }

    /**
     * \u6309\u4e1a\u52a1\u952e\u6279\u91cf\u67e5\u8be2\u805a\u5408\u6839\u3002
     * @param keys \u4e1a\u52a1\u952e\u5217\u8868
     * @return \u5339\u914d\u7684\u805a\u5408\u6839\u5217\u8868
     */
    public List<M> listByKey(List<Serializable> keys) {
        if (Objects.isNull(keys) || keys.isEmpty()) {
            return Collections.emptyList();
        }
        if (keys.size() >= 100) {
            throw new IllegalArgumentException("批量查询的业务key不能大于100");
        }
        return convert(getBaseMapper().selectList(getKeyWrapper(keys)));
    }

    /**
     * 为目标对象填充此适配器负责的数据。
     *
     * <p>MyBatis 实现调用 <code>modelClass().isInstance(model)</code>。</p>
     *
     * @param query 查询条件
     * @param model 待填充聚合根
     */
    @Override
    public void fill(Query<M> query, AggregateRoot<?> model) {
        if (modelClass().isInstance(model)) {
            fill(query, List.of(modelClass().cast(model)));
        }
    }

    /**
     * 接收<code>fill</code>回调，当前实现无需在此步骤更新状态。
     *
     * @param query 查询条件
     * @param models 聚合根列表
     */
    @Override
    public void fill(Query<M> query, List<M> models) {
    }

    /**
     * 将持久化对象转换为领域模型。
     *
     * <p>MyBatis 实现调用 <code>BeanKit.copy(persistenceObject, modelClass())</code>。</p>
     *
     * @param persistenceObject persistence object
     * @return 由持久化数据构造的领域模型
     */
    @Override
    public M toModel(P persistenceObject) {
        if (Objects.isNull(persistenceObject)) {
            return null;
        }
        return BeanKit.copy(persistenceObject, modelClass());
    }

    /**
     * 将领域模型转换为仓储使用的持久化对象。
     *
     * <p>MyBatis 实现调用 <code>BeanKit.copy(model, persistenceObjectClass())</code>。</p>
     *
     * @param model domain model
     * @return 由领域模型生成的持久化对象
     */
    @Override
    public P toPersistenceObject(M model) {
        if (Objects.isNull(model)) {
            return null;
        }
        return BeanKit.copy(model, persistenceObjectClass());
    }

    /**
     * \u5c06\u6301\u4e45\u5316\u5bf9\u8c61\u5217\u8868\u8f6c\u6362\u4e3a\u805a\u5408\u6839\u5217\u8868\u3002
     * @param persistenceObjects \u6301\u4e45\u5316\u5bf9\u8c61\u5217\u8868
     * @return \u805a\u5408\u6839\u5217\u8868
     */
    protected List<M> convert(List<P> persistenceObjects) {
        if (Objects.isNull(persistenceObjects) || persistenceObjects.isEmpty()) {
            return new ArrayList<>();
        }
        return BeanKit.copy(persistenceObjects, modelClass());
    }

    /**
     * \u6784\u9020\u9ed8\u8ba4\u67e5\u8be2\u6761\u4ef6\u3002
     * @param ignoreTenantId \u662f\u5426\u5ffd\u7565\u79df\u6237\u6761\u4ef6
     * @return \u9ed8\u8ba4\u67e5\u8be2\u5305\u88c5\u5668
     */
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

    /**
     * \u6784\u9020\u57fa\u7840\u67e5\u8be2\u6761\u4ef6\u3002
     * @param query \u67e5\u8be2\u6761\u4ef6
     * @return \u57fa\u7840\u67e5\u8be2\u5305\u88c5\u5668
     */
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
     * \u5c06 Query.getWhereConditions() \u8f6c\u6362\u4e3a MyBatis-Plus QueryWrapper \u6761\u4ef6\u3002
     *
     * @param wrapper \u63a5\u6536\u751f\u6210\u6761\u4ef6\u7684 MyBatis \u67e5\u8be2\u5c01\u88c5\u5668
     * @param query \u7528\u4e8e\u751f\u6210\u7b5b\u9009\u6761\u4ef6\u7684\u805a\u5408\u67e5\u8be2
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
     * \u5c06 Query.getOrderByConditions() \u8f6c\u6362\u4e3a QueryWrapper \u6392\u5e8f\u3002
     *
     * @param query \u7528\u4e8e\u751f\u6210\u7b5b\u9009\u6761\u4ef6\u7684\u805a\u5408\u67e5\u8be2
     * @param wrapper \u63a5\u6536\u751f\u6210\u6761\u4ef6\u7684 MyBatis \u67e5\u8be2\u5c01\u88c5\u5668
     */
    private void applyOrderBy(Query<M> query, QueryWrapper<P> wrapper) {
        for (Object obj : query.getOrderByConditions()) {
            LambdaCondition orderBy = (LambdaCondition) obj;
            String column = translateProperty(orderBy);
            wrapper.orderBy(true, "ASC".equals(orderBy.operator()), column);
        }
    }

    /**
     * \u6784\u9020\u4e1a\u52a1\u952e\u67e5\u8be2\u6761\u4ef6\u3002
     * @param key \u4e1a\u52a1\u952e
     * @return \u4e1a\u52a1\u952e\u67e5\u8be2\u5305\u88c5\u5668
     */
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

    /**
     * \u6784\u9020\u4e1a\u52a1\u952e\u67e5\u8be2\u6761\u4ef6\u3002
     * @param keys \u4e1a\u52a1\u952e\u5217\u8868
     * @return \u4e1a\u52a1\u952e\u67e5\u8be2\u5305\u88c5\u5668
     */
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
     * \u5728 TableInfo.fieldList \u4e2d\u67e5\u627e\u6807\u6ce8\u4e86\u6307\u5b9a\u6ce8\u89e3\u7684\u5b57\u6bb5\u3002
     *
     * @param ti \u5bf9\u5e94\u6301\u4e45\u5316\u5b9e\u4f53\u7684 MyBatis \u8868\u5143\u6570\u636e
     * @param annotationType \u5f85\u5339\u914d\u7684\u5b57\u6bb5\u6ce8\u89e3\u7c7b\u578b
     * @return \u5339\u914d\u6ce8\u89e3\u7684\u5b57\u6bb5\u5143\u6570\u636e
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
     * \u5728 TableInfo.fieldList \u4e2d\u67e5\u627e\u6307\u5b9a PO \u5b57\u6bb5\uff08\u53cd\u5c04\uff09\u3002
     *
     * @param ti \u5bf9\u5e94\u6301\u4e45\u5316\u5b9e\u4f53\u7684 MyBatis \u8868\u5143\u6570\u636e
     * @param annotationType \u7528\u4e8e\u5b9a\u4f4d\u76ee\u6807\u5c5e\u6027\u7684\u6ce8\u89e3\u7c7b\u578b
     * @return \u5339\u914d\u5c5e\u6027\u7684\u5b57\u6bb5\u5143\u6570\u636e
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

    /**
     * \u628a\u4e1a\u52a1\u67e5\u8be2\u6307\u5b9a\u7684\u6295\u5f71\u5217\u5199\u5165 MyBatis-Plus \u5305\u88c5\u5668\u3002
     * @param query \u4e1a\u52a1\u67e5\u8be2\u53ca\u5176\u7b5b\u9009\u3001\u6295\u5f71\u6216\u6392\u5e8f\u6761\u4ef6
     * @param wrapper \u63a5\u6536\u751f\u6210 SQL \u6761\u4ef6\u548c\u6392\u5e8f\u7684 MyBatis-Plus \u67e5\u8be2\u5305\u88c5\u5668
     */
    private void applySelect(Query<M> query, QueryWrapper<P> wrapper) {
        if (query.hasSelect()) {
            for (Object obj : query.getSelectColumns()) {
                String property = String.valueOf(obj);
                String column = translateProperty(property);
                wrapper.select(column);
            }
        }
    }

    /**
     * \u4e3a\u5305\u88c5\u5668\u8ffd\u52a0\u4e1a\u52a1\u67e5\u8be2\u6307\u5b9a\u7684\u5206\u7ec4\u5217\u3002
     * @param query \u4e1a\u52a1\u67e5\u8be2\u53ca\u5176\u7b5b\u9009\u3001\u6295\u5f71\u6216\u6392\u5e8f\u6761\u4ef6
     * @param wrapper \u63a5\u6536\u751f\u6210 SQL \u6761\u4ef6\u548c\u6392\u5e8f\u7684 MyBatis-Plus \u67e5\u8be2\u5305\u88c5\u5668
     */
    private void applyGroupBy(Query<M> query, QueryWrapper<P> wrapper) {
        if (query.hasGroupBy()) {
            for (Object obj : query.getGroupByColumns()) {
                String property = String.valueOf(obj);
                String column = translateProperty(property);
                wrapper.groupBy(column);
            }
        }
    }

    /**
     * \u4e3a\u5df2\u5206\u7ec4\u7684\u67e5\u8be2\u8ffd\u52a0\u805a\u5408\u7b5b\u9009\u6761\u4ef6\u3002
     * @param query \u4e1a\u52a1\u67e5\u8be2\u53ca\u5176\u7b5b\u9009\u3001\u6295\u5f71\u6216\u6392\u5e8f\u6761\u4ef6
     * @param wrapper \u63a5\u6536\u751f\u6210 SQL \u6761\u4ef6\u548c\u6392\u5e8f\u7684 MyBatis-Plus \u67e5\u8be2\u5305\u88c5\u5668
     */
    private void having(Query<M> query, QueryWrapper<P> wrapper) {
        if (Objects.nonNull(query) && Objects.nonNull(query.getHaving()) && StrKit.hasText(query.getHaving())) {
            wrapper.having(query.getHaving());
        }
    }

    /**
     * \u8bfb\u53d6\u6301\u4e45\u5316\u5bf9\u8c61\u7684\u4e1a\u52a1\u952e\u3002
     * @param persistenceObject \u6301\u4e45\u5316\u5bf9\u8c61
     * @return \u4e1a\u52a1\u952e\u503c
     */
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

    /**
     * \u6309\u4e3b\u952e\u91cd\u65b0\u8bfb\u53d6\u5199\u5165\u7ed3\u679c\u5e76\u56de\u586b\u805a\u5408\u3002
     * @param persistenceObject \u6570\u636e\u5e93\u6301\u4e45\u5316\u5bf9\u8c61
     * @param aggregate \u9700\u8981\u6301\u4e45\u5316\u6216\u56de\u586b\u7684\u805a\u5408\u5bf9\u8c61
     */
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

    /**
     * \u5728\u67e5\u8be2\u5f00\u542f\u7ed3\u679c\u586b\u5145\u65f6\u5bf9\u8fd4\u56de\u6a21\u578b\u8865\u9f50\u6269\u5c55\u5b57\u6bb5\u3002
     * @param query \u4e1a\u52a1\u67e5\u8be2\u53ca\u5176\u7b5b\u9009\u3001\u6295\u5f71\u6216\u6392\u5e8f\u6761\u4ef6
     * @param models \u672c\u6b21\u5199\u5165\u6216\u67e5\u8be2\u5bf9\u5e94\u7684\u9886\u57df\u6a21\u578b\u5217\u8868
     */
    private void fillIfNecessary(Query<M> query, List<M> models) {
        if (Objects.nonNull(models) && !models.isEmpty()) {
            fill(query, models);
        }
    }

    /**
     * \u8865\u9f50\u63d2\u5165\u524d\u9700\u8981\u5199\u5165\u7684\u65f6\u95f4\u548c\u5ba1\u8ba1\u4e0a\u4e0b\u6587\u5b57\u6bb5\u3002
     * @param persistenceObject \u6570\u636e\u5e93\u6301\u4e45\u5316\u5bf9\u8c61
     */
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

    /**
     * \u8865\u9f50\u66f4\u65b0\u524d\u9700\u8981\u5199\u5165\u7684\u65f6\u95f4\u548c\u5ba1\u8ba1\u4e0a\u4e0b\u6587\u5b57\u6bb5\u3002
     * @param persistenceObject \u6570\u636e\u5e93\u6301\u4e45\u5316\u5bf9\u8c61
     */
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

    /**
     * \u5728\u5b57\u6bb5\u548c\u503c\u5747\u5b58\u5728\u65f6\uff0c\u8f6c\u6362\u5e76\u5199\u5165\u652f\u6301\u7684\u4e0a\u4e0b\u6587\u503c\u3002
     * @param field \u5f85\u5904\u7406\u7684 Java \u53cd\u5c04\u5b57\u6bb5
     * @param persistenceObject \u6570\u636e\u5e93\u6301\u4e45\u5316\u5bf9\u8c61
     * @param value \u5f53\u524d\u6761\u4ef6\u64cd\u4f5c\u6570\u6216\u5f85\u5199\u5165\u5b57\u6bb5\u503c
     * @throws IllegalAccessException \u65e0\u6cd5\u5199\u5165\u76ee\u6807\u53cd\u5c04\u5b57\u6bb5
     */
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

    /**
     * \u6309 MyBatis-Plus \u8868\u5143\u6570\u636e\u521d\u59cb\u5316\u903b\u8f91\u5220\u9664\u6807\u8bb0\u3002
     * @param persistenceObject \u6570\u636e\u5e93\u6301\u4e45\u5316\u5bf9\u8c61
     * @throws IllegalAccessException \u65e0\u6cd5\u8bbf\u95ee\u903b\u8f91\u5220\u9664\u5b57\u6bb5
     */
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

    /**
     * \u4ec5\u5411\u672a\u8d4b\u503c\u7684\u53d7\u652f\u6301\u65f6\u95f4\u5b57\u6bb5\u5199\u5165\u5f53\u524d\u65f6\u95f4\u3002
     * @param field \u5f85\u5904\u7406\u7684 Java \u53cd\u5c04\u5b57\u6bb5
     * @param persistenceObject \u6570\u636e\u5e93\u6301\u4e45\u5316\u5bf9\u8c61
     * @throws IllegalAccessException \u65e0\u6cd5\u8bfb\u5199\u76ee\u6807\u53cd\u5c04\u5b57\u6bb5
     */
    private void fillDateField(Field field, P persistenceObject) throws IllegalAccessException {
        if (field.getType().equals(LocalDateTime.class) && Objects.isNull(field.get(persistenceObject))) {
            field.set(persistenceObject, LocalDateTime.now());
        } else if (field.getType().equals(LocalDate.class) && Objects.isNull(field.get(persistenceObject))) {
            field.set(persistenceObject, LocalDate.now());
        }
    }

}
