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

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.MybatisUtils;
import com.baomidou.mybatisplus.core.toolkit.ReflectionKit;
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
import io.ddd4j.kit.lang.BeanKit;
import io.ddd4j.kit.lang.MappingKit;
import io.ddd4j.kit.lang.StrKit;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.exceptions.PersistenceException;

import java.io.Serializable;
import java.sql.Statement;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * MyBatis-Plus 聚合仓储：组合 Mapper，负责领域映射、查询翻译和业务字段填充。
 *
 * <p>业务仓储可通过 {@link io.ddd4j.data.mybatis.repository.impl.BaseRepositoryImpl} 接入，
 * 原生 PO CRUD 使用 {@link #getBaseMapper()}。
 * 领域批量方法委托 {@link BaseMapper} 集合 API，不再继承面向 PO 的 Repository。
 * 批量会话及事务行为遵循组件实现，本类不声明自动加入调用方事务。</p>
 *
 * @param <MP> Mapper 类型
 * @param <M> 聚合根类型，必须使用同一个 ID 类型
 * @param <P> 持久化对象类型
 * @param <Q> 领域查询类型
 * @param <ID> 聚合根标识类型
 */
@Slf4j
@SuppressWarnings("unchecked")
public abstract class MybatisAggregateRepository<MP extends BaseMapper<P>, M extends AggregateRoot<ID>, P, Q extends Query<M>, ID extends Serializable>
        implements DomainObjectMapper<M, P>, Repository<M, ID> {

    /**
     * MyBatis-Plus Mapper 实例（无 Spring 注入，业务方通过 {@code setBaseMapper(...)} 或构造器手动注入）。
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

    /**
     * 创建 MyBatis-Plus 聚合仓库。
     *
     */
    protected MybatisAggregateRepository() {
        this.configureTypes(resolveModelClass(), resolvePersistenceObjectClass(), resolveQueryClass());
        this.registerToRepositoryRegistry();
    }

    /**
     * 创建 MyBatis-Plus 聚合仓库。
     *
     * @param baseMapper MyBatis-Plus 映射器
     */
    protected MybatisAggregateRepository(MP baseMapper) {
        this.configureTypes(resolveModelClass(), resolvePersistenceObjectClass(), resolveQueryClass());
        this.setBaseMapper(baseMapper);
        this.registerToRepositoryRegistry();
    }

    /**
     * 创建 MyBatis-Plus 聚合仓库。
     *
     * @param baseMapper MyBatis-Plus 映射器
     * @param modelClass 聚合根模型类型
     * @param persistenceObjectClass 持久化对象类型
     */
    protected MybatisAggregateRepository(MP baseMapper, Class<M> modelClass, Class<P> persistenceObjectClass) {
        this.configureTypes(modelClass, persistenceObjectClass, resolveQueryClass());
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
     * 解析聚合根类型（泛型参数 M，索引 1；索引 0 是 Mapper 类型）。
     * @return 聚合根模型类型
     */
    protected Class<M> resolveModelClass() {
        return (Class<M>) ReflectionKit.getSuperClassGenericType(this.getClass(), MybatisAggregateRepository.class, 1);
    }

    /**
     * 解析持久化对象类型（泛型参数 P，索引 2）。
     * @return 持久化对象类型
     */
    protected Class<P> resolvePersistenceObjectClass() {
        return (Class<P>) ReflectionKit.getSuperClassGenericType(this.getClass(), MybatisAggregateRepository.class, 2);
    }

    /**
     * 解析查询对象类型（泛型参数 Q，索引 3）。
     * @return 查询对象类型
     */
    protected Class<? extends Query<M>> resolveQueryClass() {
        return (Class<? extends Query<M>>) ReflectionKit.getSuperClassGenericType(this.getClass(), MybatisAggregateRepository.class, 3);
    }

    /**
     * 配置聚合根和持久化对象类型。
     *
     * @param modelClass 聚合根模型类型
     * @param persistenceObjectClass 持久化对象类型
     * @param queryClass 查询对象类型
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
                    if (Objects.equals(poProperty, tableInfo.getKeyProperty())) {
                        return tableInfo.getKeyColumn();
                    }
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
        if (Objects.isNull(type)) {
            return null;
        }
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
        Class<M> mc = modelClass;
        Class<? extends Query<M>> qc = queryClass;
        if (Objects.nonNull(mc) && Objects.nonNull(qc)) {
            RepositoryRegistry.register(mc, qc, this);
        } else if (Objects.nonNull(mc)) {
            RepositoryRegistry.register(mc, this);
        }
    }

    /**
     * 返回当前仓储持有的 Mapper；原生 PO 操作直接使用该组件。
     * @return MyBatis-Plus Mapper
     */
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
     * @return 领域模型属性映射信息
     */
    protected DomainModelInfo<M> domainModelInfo() {
        return Objects.requireNonNull(domainModelInfo, "domainModelInfo must not be null");
    }

    /**
     * PO 元数据访问器（委托 MP {@code TableInfoHelper}），用于 auto-fill、bizKey、tenantId、tableLogic 等。
     * @return MyBatis-Plus 表元信息
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
     * @return 持久化字段或列名
     * @param property 领域属性或持久化属性
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
     * @return 持久化字段或列名
     * @param condition 查询条件
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

    // ========================= MyBatis-Plus 原生 Wrapper API（CQRS 查询优势） =========================
    // 仅保留增加租户/系统条件的业务入口；其他 Wrapper 直接使用 MyBatis-Plus。

    /**
     * 创建带租户/系统隔离的 {@link QueryWrapper}（CQRS 读侧复杂查询入口）。
     *
     * <pre>{@code
     * QueryWrapper<UserPO> wrapper = repo.queryWrapper()
     *     .select("id", "name", "phone")
     *     .eq("status", "ACTIVE")
     *     .like("name", "张")
     *     .ge("create_time", startTime)
     *     .orderByDesc("create_time")
     *     .last("LIMIT 10");
     * List<UserPO> list = repo.getBaseMapper().selectList(wrapper);
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
     * 获取聚合根模型类型。
     *
     * @return 聚合根模型类型
     */
    protected Class<M> modelClass() {
        return Objects.requireNonNull(modelClass, "modelClass must not be null");
    }

    /**
     * 获取持久化对象类型。
     *
     * @return 持久化对象类型
     */
    protected Class<P> persistenceObjectClass() {
        return Objects.requireNonNull(persistenceObjectClass, "persistenceObjectClass must not be null");
    }

    /**
     * 批量插入聚合根，并将生成的主键和填充字段回写到原对象。
     * @param aggregates 聚合根集合；null 或空集合不执行 SQL
     * @return 按输入顺序排列的原聚合根
     */
    @Override
    public List<M> saveBatch(Collection<M> aggregates) {
        List<M> models = batchModels(aggregates);
        if (models.isEmpty()) {
            return models;
        }
        List<P> values = models.stream().map(this::toPersistenceObject).toList();
        values.forEach(this::insertFill);
        affectedRows(getBaseMapper().insert(values));
        completeBatch(values, models);
        return models;
    }

    /**
     * 批量按 ID 更新；不存在的记录不插入。
     * @param aggregates 待更新聚合根
     * @return JDBC 影响行数；SUCCESS_NO_INFO 按一条成功语句计数
     */
    @Override
    public int updateBatchById(Collection<M> aggregates) {
        List<M> models = batchModels(aggregates);
        if (models.isEmpty()) {
            return 0;
        }
        List<P> values = models.stream().map(this::toPersistenceObject).toList();
        values.forEach(this::updateFill);
        int count = affectedRows(getBaseMapper().updateById(values));
        completeBatch(values, models);
        return count;
    }

    /**
     * 批量保存或更新；主键识别与批量执行由 Mapper 负责。
     * @param aggregates 待保存聚合根
     * @return JDBC 影响行数；SUCCESS_NO_INFO 按一条成功语句计数
     */
    @Override
    public int insertOrUpdateBatch(Collection<M> aggregates) {
        List<M> models = batchModels(aggregates);
        if (models.isEmpty()) {
            return 0;
        }
        List<P> values = models.stream().map(this::toPersistenceObject).toList();
        // 沿用单个聚合保存的业务填充规则；不重新实现组件的主键读取或批量执行。
        for (int i = 0; i < models.size(); i++) {
            if (shouldInsert(models.get(i))) {
                insertFill(values.get(i));
            } else {
                updateFill(values.get(i));
            }
        }
        int count = affectedRows(getBaseMapper().insertOrUpdate(values));
        completeBatch(values, models);
        return count;
    }

    /**
     * \u5c06\u6279\u91cf\u5199\u5165\u8f93\u5165\u6574\u7406\u4e3a\u652f\u6301\u4f4d\u7f6e\u5bf9\u5e94\u7684\u5217\u8868\u3002
     * @param aggregates \u672c\u6b21\u6279\u91cf\u5199\u5165\u7684\u805a\u5408\u5bf9\u8c61
     * @return \u5f53\u524d\u6279\u6b21\u7684\u805a\u5408\u5bf9\u8c61\u5217\u8868
     */
    private List<M> batchModels(Collection<M> aggregates) {
        if (Objects.isNull(aggregates)) {
            return Collections.emptyList();
        }
        // 在任何查询或写入前验证全部元素，避免半途才遇到非法输入。
        return List.copyOf(aggregates);
    }

    /**
     * \u628a\u6301\u4e45\u5316\u5bf9\u8c61\u4e2d\u7684\u751f\u6210\u503c\u56de\u586b\u5230\u5bf9\u5e94\u7684\u9886\u57df\u6a21\u578b\u3002
     * @param values \u6279\u91cf\u64cd\u4f5c\u6240\u7528\u7684\u6301\u4e45\u5316\u5bf9\u8c61\u5217\u8868
     * @param models \u672c\u6b21\u5199\u5165\u6216\u67e5\u8be2\u5bf9\u5e94\u7684\u9886\u57df\u6a21\u578b\u5217\u8868
     */
    private void completeBatch(List<P> values, List<M> models) {
        // 组件批量写入使用独立会话，清除原会话缓存以保证下一次查询可见。
        MybatisUtils.getMapperProxy(getBaseMapper()).getSqlSession().clearCache();
        for (int i = 0; i < values.size(); i++) {
            BeanKit.copy(toModel(values.get(i)), models.get(i));
        }
    }

    /**
     * \u6c47\u603b\u5e95\u5c42\u6279\u5904\u7406\u7ed3\u679c\u4e2d\u7684\u53d7\u5f71\u54cd\u884c\u6570\u3002
     * @param results MyBatis \u6279\u5904\u7406\u8fd4\u56de\u7684\u7ed3\u679c\u96c6\u5408
     * @return \u672c\u6b21\u6279\u91cf\u5199\u5165\u5f71\u54cd\u7684\u884c\u6570
     */
    private int affectedRows(List<BatchResult> results) {
        int total = 0;
        // SqlHelper.retBool 只有布尔结果；领域接口需要计数并接受未命中更新的 0。
        for (BatchResult result : results) {
            for (int count : result.getUpdateCounts()) {
                if (count == Statement.SUCCESS_NO_INFO) {
                    total = Math.addExact(total, 1);
                } else if (count < 0) {
                    throw new PersistenceException("Batch statement failed: " + count);
                } else {
                    total = Math.addExact(total, count);
                }
            }
        }
        return total;
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
        BeanKit.copy(toModel(persistenceObject), aggregate);
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
        BeanKit.copy(toModel(persistenceObject), aggregate);
        return aggregate;
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
     * 判断聚合根是否需要插入。
     *
     * @param aggregate 聚合根
     * @return 是否需要插入
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
     * 统计符合查询范围的数据数量。
     *
     * <p>MyBatis 实现调用 <code>getBaseMapper().selectCount(getBaseWrapper(query))</code>。</p>
     *
     * @param query 查询条件
     * @return 满足筛选条件的记录数量
     */
    @Override
    public long count(Query<M> query) {
        Long count = getBaseMapper().selectCount(getBaseWrapper(query));
        return Objects.nonNull(count) ? count : 0L;
    }

    /**
     * 读取满足条件的第一条记录。
     *
     * <p>MyBatis 实现调用 <code>getBaseWrapper(query)</code>。</p>
     *
     * @param query 查询条件
     * @return 第一个匹配条件的结果
     */
    @Override
    public Optional<M> findFirst(Query<M> query) {
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
     * 按查询条件读取记录列表。
     *
     * <p>MyBatis 实现调用 <code>convert(getBaseMapper().selectList(getBaseWrapper(query)))</code>。</p>
     *
     * @param query 查询条件
     * @return 满足条件的结果列表
     */
    @Override
    public List<M> findList(Query<M> query) {
        List<M> models = convert(getBaseMapper().selectList(getBaseWrapper(query)));
        fillIfNecessary(query, models);
        return models;
    }

    /**
     * 按条件查询 Map 列表（对应 {@code selectMaps(Wrapper)}）。
     *
     * <p>MyBatis 实现调用 <code>getBaseMapper().selectMaps(getBaseWrapper(query))</code>。</p>
     *
     * @param query 查询条件
     * @return 实际返回<code>getBaseMapper().selectMaps(getBaseWrapper(query))</code>
     */
    @Override
    public List<Map<String, Object>> maps(Query<M> query) {
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
     * 删除匹配查询条件的记录。
     *
     * <p>MyBatis 实现调用 <code>SqlHelper.retBool(getBaseMapper().delete(getBaseWrapper(query)))</code>。</p>
     *
     * @param query 查询条件
     * @return 实际返回<code>SqlHelper.retBool(getBaseMapper().delete(getBaseWrapper(query)))</code>
     */
    @Override
    public boolean deleteByQuery(Query<M> query) {
        return SqlHelper.retBool(getBaseMapper().delete(getBaseWrapper(query)));
    }

    /**
     * 按业务键更新聚合根。
     *
     * @param model 聚合根模型
     * @return 是否更新成功
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
     * 按业务键查询聚合根。
     *
     * @param key 业务键
     * @return 匹配的聚合根
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
     * 按业务键批量查询聚合根。
     *
     * @param keys 业务键列表
     * @return 匹配的聚合根列表
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
     * 将持久化对象列表转换为聚合根列表。
     *
     * @param persistenceObjects 持久化对象列表
     * @return 聚合根列表
     */
    protected List<M> convert(List<P> persistenceObjects) {
        if (Objects.isNull(persistenceObjects) || persistenceObjects.isEmpty()) {
            return new ArrayList<>();
        }
        return persistenceObjects.stream().map(this::toModel).collect(Collectors.toList());
    }

    /**
     * 构造默认查询条件。
     *
     * @param ignoreTenantId 是否忽略租户条件
     * @return 默认查询包装器
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
     * 构造基础查询条件。
     *
     * @param query 查询条件
     * @return 基础查询包装器
     */
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
    private void applyConditions(QueryWrapper<P> wrapper, Query<M> query) {
        for (LambdaCondition condition : query.getWhereConditions()) {
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
        for (LambdaCondition orderBy : query.getOrderByConditions()) {
            String column = translateProperty(orderBy);
            wrapper.orderBy(true, "ASC".equals(orderBy.operator()), column);
        }
    }

    /**
     * 构造业务键查询条件。
     *
     * @param key 业务键
     * @return 业务键查询包装器
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
     * 构造业务键查询条件。
     *
     * @param keys 业务键列表
     * @return 业务键查询包装器
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
     * 读取持久化对象的业务键。
     *
     * @param persistenceObject 持久化对象
     * @return 业务键值
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
            log.error("获取当前实体对象的key值出现错误！", exception);
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
        Serializable id = (Serializable) ti.getPropertyValue(persistenceObject, ti.getKeyProperty());
        if (Objects.nonNull(id)) {
            P updated = getBaseMapper().selectById(id);
            if (Objects.nonNull(updated)) {
                BeanKit.copy(toModel(updated), aggregate);
            }
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
            log.debug("Ignore insert fill failure: {}", ignored.getMessage());
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
            log.debug("Ignore update fill failure: {}", ignored.getMessage());
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
        if (Objects.nonNull(FieldUtils.readField(field, persistenceObject, true))) {
            return;
        }
        if (field.getType().equals(LocalDateTime.class)) {
            FieldUtils.writeField(field, persistenceObject, LocalDateTime.now(), true);
        } else if (field.getType().equals(LocalDate.class)) {
            FieldUtils.writeField(field, persistenceObject, LocalDate.now(), true);
        }
    }

}
