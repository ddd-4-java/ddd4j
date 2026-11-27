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

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import io.ddd4j.annotation.orm.DomainField;
import io.ddd4j.annotation.orm.TenantId;
import io.ddd4j.core.api.Page;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.core.exception.BizRuntimeException;
import io.ddd4j.kit.lang.BeanKit;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link MybatisAggregateRepository} 的 MySQL 集成测试。
 *
 * <p>使用 Testcontainers MySQL 模块（org.testcontainers:mysql，
 * 模块清单来源 <a href="https://testcontainers.com/modules/">testcontainers.com/modules</a>）
 * 启动真实 MySQL 8 容器，以独立（无 Spring）方式自举 MyBatis-Plus，
 * 验证仓储 CRUD 与充血模型的真实写语义。
 *
 * <p>重点回归：{@code AggregateRoot.update()} 必须是"仅更新不插入"（P0 语义修复）。
 *
 * <p>需要本地 Docker 可用；无 Docker 环境时 Testcontainers 会自动跳过。
 */
@Testcontainers(disabledWithoutDocker = true)
class MybatisAggregateRepositoryMySQLTest {

    /**
     * 为当前集成测试提供独立 MySQL 服务的容器。
     */
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            // 数据仅用于本轮集成验证，避免占用宿主 Docker 持久盘。
            .withTmpFs(Map.of("/var/lib/mysql", "rw"))
            .withDatabaseName("ddd4j_it")
            .withUsername("ddd4j")
            .withPassword("ddd4j");

    /**
     * 为 MyBatis 合同测试打开会话的工厂。
     */
    private static SqlSessionFactory sqlSessionFactory;
    /**
     * 当前被验证或替换的持久化 Mapper 实例。
     */
    private static OrderMapper mapper;
    /**
     * 被测仓储或用于提供测试数据的仓储实例。
     */
    private static TestRepository repository;
    /**
     * 测试中用于执行 Mapper 操作的数据库会话。
     */
    private static SqlSession session;

    /**
     * 测试执行前执行 <code>MYSQL.createConnection()</code>、<code>conn.createStatement()</code>、<code>stmt.execute()</code>、<code>MYSQL.getJdbcUrl()</code> 完成资源准备。
     *
     * @throws Exception 当前用例准备资源或调用测试入口失败
     */
    @BeforeAll
    static void setUp() throws Exception {
        // 1. 建表
        try (Connection conn = MYSQL.createConnection("");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS orders ("
                    + "id VARCHAR(64) PRIMARY KEY, "
                    + "order_status VARCHAR(32), "
                    + "deleted_at DATETIME NULL, "
                    // 仅临时容器夹具；原有 status/deletedAt 不具备租户语义。
                    + "tenant_id VARCHAR(64) NULL COMMENT '订单所属租户')");
            stmt.execute("CREATE TABLE order_batch ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '订单主键', "
                    + "name VARCHAR(40) COMMENT '订单名称', "
                    + "created_at DATETIME(6) NULL COMMENT '创建时间', "
                    + "updated_at DATETIME(6) NULL COMMENT '更新时间') COMMENT='批量仓储测试订单'");
        }

        // 主键映射回归只使用临时容器中的订单夹具。
        try (Connection conn = MYSQL.createConnection(""); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE order_identity (order_pk BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '订单主键', "
                    + "id BIGINT COMMENT '普通外部标识', name VARCHAR(40) COMMENT '订单名称') COMMENT='主键映射测试订单'");
        }

        // 2. 独立自举 MyBatis-Plus（无 Spring）
        PooledDataSource dataSource = new PooledDataSource(
                "com.mysql.cj.jdbc.Driver", MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("tc", new JdbcTransactionFactory(), dataSource));
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        configuration.addInterceptor(interceptor);
        configuration.addMapper(OrderMapper.class);
        configuration.addMapper(BatchMapper.class);
        configuration.addMapper(IdentityMapper.class);
        sqlSessionFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);

        session = sqlSessionFactory.openSession(true);
        mapper = session.getMapper(OrderMapper.class);
        repository = new TestRepository(mapper);
    }

    /**
     * 测试后依次关闭<code>session</code>、注销<code>RepositoryRegistry</code>。
     */
    @AfterAll
    static void closeSession() {
        if (Objects.nonNull(session)) {
            session.close();
        }
        RepositoryRegistry.unregister(Order.class);
        RepositoryRegistry.unregisterQuery(OrderQuery.class);
    }

    /**
     * 读取 <code>model</code> 供当前测试使用。
     *
     * @param key        用于在测试上下文或缓存中索引数据的键
     * @param ordinaryId 传给 <code>model.setId()</code> 的 <code>Long</code> 输入
     * @param name       当前测试指定的对象或属性名称
     * @return 当前测试使用的结果，取自 <code>model</code>
     */
    private static IdentityOrder identityOrder(Long key, Long ordinaryId, String name) {
        IdentityOrder model = new IdentityOrder();
        model.setBusinessId(key);
        model.setId(ordinaryId);
        model.setName(name);
        return model;
    }

    /**
     * 测试前依次清除<code>ThreadContext</code>。
     */
    @BeforeEach
    void cleanTable() {
        ThreadContext.clear();
        mapper.delete(null);
        session.getMapper(BatchMapper.class).delete(null);
    }

    /**
     * 测试后依次清除<code>ThreadContext</code>。
     */
    @AfterEach
    void clearContext() {
        ThreadContext.clear();
    }

    /**
     * save：新聚合插入数据库并可按 ID 读回。
     */
    @Test
    @DisplayName("save：新聚合插入数据库并可按 ID 读回")
    void saveInsertsNewAggregate() {
        Order order = new Order("o-1", "CREATED", null);

        repository.save(order);

        Optional<Order> found = repository.findById("o-1");
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo("CREATED");
    }

    /**
     * updateById（P0 回归）：已存在聚合仅更新，不触发插入分支。
     */
    @Test
    @DisplayName("updateById（P0 回归）：已存在聚合仅更新，不触发插入分支")
    void updateByIdUpdatesExistingOnly() {
        repository.save(new Order("o-2", "CREATED", null));

        Order changed = new Order("o-2", "PAID", null);
        repository.updateById(changed);

        Optional<Order> found = repository.findById("o-2");
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo("PAID");
        assertThat(repository.count()).isEqualTo(1);
    }

    /**
     * updateById（P0 回归）：不存在的 ID 不会隐式插入。
     */
    @Test
    @DisplayName("updateById（P0 回归）：不存在的 ID 不会隐式插入")
    void updateByIdDoesNotInsertWhenAbsent() {
        repository.updateById(new Order("o-ghost", "PAID", null));

        assertThat(repository.findById("o-ghost")).isEmpty();
        assertThat(repository.count()).isZero();
    }

    /**
     * insertOrUpdate：不存在插入、存在更新（upsert 语义）。
     */
    @Test
    @DisplayName("insertOrUpdate：不存在插入、存在更新（upsert 语义）")
    void insertOrUpdateBehavesAsUpsert() {
        repository.insertOrUpdate(new Order("o-3", "CREATED", null));
        assertThat(repository.count()).isEqualTo(1);

        repository.insertOrUpdate(new Order("o-3", "SHIPPED", null));
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findById("o-3")).isPresent()
                .get().extracting(Order::getStatus).isEqualTo("SHIPPED");
    }

    /**
     * deleteById / existsById：删除后不可查。
     */
    @Test
    @DisplayName("deleteById / existsById：删除后不可查")
    void deleteByIdRemovesRow() {
        repository.save(new Order("o-4", "CREATED", null));
        assertThat(repository.existsById("o-4")).isTrue();

        repository.deleteById("o-4");

        assertThat(repository.existsById("o-4")).isFalse();
        assertThat(repository.findAll()).isEmpty();
    }

    /**
     * 原版模型与 Query：直接保存更新、单条列表分页和存在判断，无命令总线或事件存储。
     */
    @Test
    @DisplayName("原版模型与 Query：直接保存更新、单条列表分页和存在判断，无命令总线或事件存储")
    void originalModelAndQueryUsagePersistsWithoutCqrsInfrastructure() {
        Order first = new Order("simple-1", "CREATED", null);
        assertThat(first.<Order>save()).isSameAs(first);
        first.setStatus("PAID");
        assertThat(first.<Order>update()).isSameAs(first);
        new Order("simple-2", "PAID", null).save();
        new Order("simple-3", "DRAFT", null).save();

        assertThat(new OrderQuery().eq(Order::getId, "simple-1").one().getStatus()).isEqualTo("PAID");
        assertThat(new OrderQuery().eq(Order::getStatus, "PAID").orderByAsc(Order::getId).list())
                .extracting(Order::getId).containsExactly("simple-1", "simple-2");
        Page<Order> page = new OrderQuery().eq(Order::getStatus, "PAID")
                .orderByAsc(Order::getId).current(2).size(1).page();
        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords()).extracting(Order::getId).containsExactly("simple-2");
        assertThat(new OrderQuery().eq(Order::getStatus, "PAID").exist()).isTrue();
        assertThat(new OrderQuery().eq(Order::getStatus, "MISSING").exist()).isFalse();
        assertThat(new OrderQuery().eq(Order::getStatus, "MISSING").one()).isNull();
        assertThatThrownBy(() -> new OrderQuery().eq(Order::getStatus, "MISSING").one("order missing"))
                .isInstanceOf(BizRuntimeException.class).hasMessageContaining("order missing");
        new Order("absent", "PAID", null).update();
        assertThat(AggregateRoot.get(Order.class, "absent")).isEmpty();
        assertThat(AggregateRoot.get(Order.class, "simple-1")).isPresent()
                .get().extracting(Order::getStatus).isEqualTo("PAID");
    }

    /**
     * 转换与填充：读回 PO 映射为领域对象，按需执行业务仓储填充。
     */
    @Test
    @DisplayName("转换与填充：读回 PO 映射为领域对象，按需执行业务仓储填充")
    void originalQueriesConvertAndFillPersistedModels() {
        LocalDateTime deletedAt = LocalDateTime.of(2026, 11, 18, 8, 30);
        new Order("fill-1", "PAID", deletedAt).save();

        OrderPO po = mapper.selectById("fill-1");
        assertThat(po.getStatus()).isEqualTo("PAID");
        assertThat(po.getDeletedAt()).isEqualTo(deletedAt);
        Order converted = repository.toModel(po);
        assertThat(converted.getStatus()).isEqualTo("PAID");
        assertThat(repository.toPersistenceObject(converted).getDeletedAt()).isEqualTo(deletedAt);
        assertThat(new OrderQuery().one().getStatusLabel()).isNull();
        assertThat(new OrderQuery().fills("statusLabel").one().getStatusLabel()).isEqualTo("已支付");
        assertThat(new OrderQuery().fills("statusLabel").list()).extracting(Order::getStatusLabel)
                .containsExactly("已支付");
        assertThat(new OrderQuery().fills("statusLabel").page().getRecords())
                .extracting(Order::getStatusLabel).containsExactly("已支付");
        converted.fill(new OrderQuery().fills("statusLabel"));
        assertThat(converted.getStatusLabel()).isEqualTo("已支付");
    }

    /**
     * 租户：原版保存自动填租户，Query 单条列表分页和存在判断隔离租户。
     */
    @Test
    @DisplayName("租户：原版保存自动填租户，Query 单条列表分页和存在判断隔离租户")
    void originalQueriesScopeRowsToCurrentTenant() {
        ThreadContext.set(ContextConstants.TENANT_ID, "tenant-a");
        new Order("tenant-a-1", "PAID", null).save();
        ThreadContext.set(ContextConstants.TENANT_ID, "tenant-b");
        new Order("tenant-b-1", "DRAFT", null).save();
        assertThat(mapper.selectById("tenant-a-1").getTenantId()).isEqualTo("tenant-a");
        assertThat(mapper.selectById("tenant-b-1").getTenantId()).isEqualTo("tenant-b");

        ThreadContext.set(ContextConstants.TENANT_ID, "tenant-a");
        assertThat(new OrderQuery().one().id()).isEqualTo("tenant-a-1");
        assertThat(new OrderQuery().list()).extracting(Order::getId).containsExactly("tenant-a-1");
        assertThat(new OrderQuery().page().getTotal()).isEqualTo(1);
        assertThat(new OrderQuery().eq(Order::getStatus, "DRAFT").exist()).isFalse();
        ThreadContext.set(ContextConstants.TENANT_ID, "tenant-b");
        assertThat(new OrderQuery().list()).extracting(Order::getId).containsExactly("tenant-b-1");
        assertThat(new OrderQuery().eq(Order::getStatus, "DRAFT").exist()).isTrue();
    }

    /**
     * 验证<code>batch.saveBatch(List.of(first, second))</code> 按顺序仅包含 <code>first</code>、<code>second</code>，<code>first.getId()</code>非空，<code>persisted.selectCount(null)</code> 等于 <code>3</code>。
     */
    @Test
    void longInheritedIdsSupportBatchInsertUpdateAndUpsert() {
        BatchRepository batch = new BatchRepository(session.getMapper(BatchMapper.class));
        BatchModel first = new BatchModel(null, "first");
        BatchModel second = new BatchModel(null, "second");
        assertThat(batch.saveBatch(List.of(first, second))).containsExactly(first, second);
        assertThat(first.getId()).isNotNull();
        assertThat(second.getId()).isNotNull().isNotEqualTo(first.getId());
        assertThat(first.getCreatedAt()).isNotNull();
        assertThat(batch.findById(first.getId()).orElseThrow().getName()).isEqualTo("first");

        first.setName("updated");
        assertThat(batch.updateBatchById(List.of(first, new BatchModel(999999L, "absent")))).isEqualTo(1);
        assertThat(first.getUpdatedAt()).isNotNull();
        assertThat(batch.findById(first.getId()).orElseThrow().getName()).isEqualTo("updated");
        BatchModel third = new BatchModel(null, "third");
        second.setName("upserted");
        assertThat(batch.insertOrUpdateBatch(List.of(second, third))).isEqualTo(2);
        assertThat(third.getId()).isNotNull();
        assertThat(third.getCreatedAt()).isNotNull();
        assertThat(second.getUpdatedAt()).isNotNull();
        assertThat(batch.findById(second.getId()).orElseThrow().getName()).isEqualTo("upserted");
        // 组件批量 API 使用独立会话；从新的查询会话验证提交后的数据库状态。
        try (SqlSession read = sqlSessionFactory.openSession()) {
            BatchMapper persisted = read.getMapper(BatchMapper.class);
            assertThat(persisted.selectById(first.getId()).getName()).isEqualTo("updated");
            assertThat(persisted.selectById(second.getId()).getName()).isEqualTo("upserted");
            assertThat(persisted.selectById(third.getId()).getName()).isEqualTo("third");
            assertThat(persisted.selectById(999999L)).isNull();
            assertThat(persisted.selectCount(null)).isEqualTo(3);
        }
    }

    /**
     * 验证<code>batch.saveBatch(List.of( new BatchModel(777L, "first"), new BatchModel(777L, "duplicate")))</code> 抛出 <code>RuntimeException.class</code>，<code>read.getMapper(BatchMapper.class).selectCount(null)</code>为零。
     */
    @Test
    void failedBatchRollsBackEarlierStatements() {
        BatchRepository batch = new BatchRepository(session.getMapper(BatchMapper.class));
        assertThatThrownBy(() -> batch.saveBatch(List.of(
                new BatchModel(777L, "first"), new BatchModel(777L, "duplicate"))))
                .isInstanceOf(RuntimeException.class);
        try (SqlSession read = sqlSessionFactory.openSession()) {
            assertThat(read.getMapper(BatchMapper.class).selectCount(null)).isZero();
        }
    }

    /**
     * 验证<code>identities.save(model)</code> 指向同一实例 <code>model</code>，<code>model.id()</code>非空，<code>identities.findById(model.id()).get().getName()</code> 等于 <code>"updated"</code>。
     */
    @Test
    void namedInheritedKeyIsMappedBackToOriginalDomainObject() {
        IdentityRepository identities = identityRepository();
        IdentityOrder model = identityOrder(null, 901L, "created");

        assertThat(identities.save(model)).isSameAs(model);
        assertThat(model.id()).isNotNull().isNotEqualTo(901L);
        assertThat(identities.findById(model.id()).get().getId()).isEqualTo(901L);
        model.setName("updated");
        identities.save(model);
        assertThat(identities.count()).isEqualTo(1);
        assertThat(identities.findById(model.id()).get().getName()).isEqualTo("updated");
    }

    /**
     * 验证<code>identities.count(query)</code> 等于 <code>1</code>，<code>identities.maps(query).get(0)</code> 含对应条目 <code>"order_pk"</code>、<code>101L</code>，<code>identities.findFirst(poQuery).get().id()</code> 等于 <code>101L</code>。
     */
    @Test
    void domainAndPersistenceQueriesUseAnnotatedPrimaryKeyColumn() {
        IdentityRepository identities = identityRepository();
        identities.getBaseMapper().insert(identities.toPersistenceObject(identityOrder(101L, 901L, "one")));
        IdentityQuery query = new IdentityQuery();
        query.eq(IdentityOrder::getBusinessId, 101L);

        assertThat(identities.findList(query)).extracting(IdentityOrder::id).containsExactly(101L);
        assertThat(identities.count(query)).isEqualTo(1);
        assertThat(identities.page(query).getRecords()).extracting(IdentityOrder::id).containsExactly(101L);
        query.select(IdentityOrder::getBusinessId);
        assertThat(identities.maps(query).get(0)).containsEntry("order_pk", 101L);
        IdentityQuery poQuery = new IdentityQuery();
        poQuery.withPO(IdentityPO.class).eq(IdentityPO::getOrderKey, 101L);
        assertThat(identities.findFirst(poQuery).get().id()).isEqualTo(101L);
    }

    /**
     * 在测试框架中执行 <code>identityRepository()</code>、<code>identities.getBaseMapper()</code>、<code>identities.toPersistenceObject()</code>，验证该场景。
     */
    @Test
    void listConversionUsesExistingDomainObjectMapper() {
        IdentityRepository identities = identityRepository();
        identities.getBaseMapper().insert(identities.toPersistenceObject(identityOrder(101L, 901L, "one")));

        assertThat(identities.findAll()).extracting(IdentityOrder::id).containsExactly(101L);
    }

    /**
     * 验证<code>first.id()</code>非空，<code>second.id()</code>非空，<code>identities.findById(third.id()).get().getName()</code> 等于 <code>"new despite ordinary id"</code>。
     */
    @Test
    void batchOperationsPreserveNamedIdentityAndMappedBackfill() {
        IdentityRepository identities = identityRepository();
        IdentityOrder first = identityOrder(null, 901L, "first");
        IdentityOrder second = identityOrder(null, 902L, "second");
        List<IdentityOrder> models = java.util.Arrays.asList(first, second);

        identities.saveBatch(models);
        assertThat(first.id()).isNotNull().isNotEqualTo(901L);
        assertThat(second.id()).isNotNull().isNotEqualTo(first.id());
        first.setName("changed");
        assertThat(identities.updateBatchById(models)).isEqualTo(2);
        IdentityOrder third = identityOrder(null, first.id(), "new despite ordinary id");
        assertThat(identities.insertOrUpdateBatch(java.util.Arrays.asList(first, third))).isEqualTo(2);
        assertThat(third.id()).isNotNull().isNotEqualTo(first.id());
        assertThat(identities.findById(first.id()).get().getName()).isEqualTo("changed");
        assertThat(identities.findById(third.id()).get().getName()).isEqualTo("new despite ordinary id");
    }

    /**
     * 验证<code>identities.update(patch, query)</code>为真，<code>patch.id()</code> 等于 <code>101L</code>，<code>identities.findById(901L)</code>存在值。
     */
    @Test
    void conditionalUpdateAndDeleteUseMappedIdentity() {
        IdentityRepository identities = identityRepository();
        identities.getBaseMapper().insert(identities.toPersistenceObject(identityOrder(101L, 901L, "one")));
        identities.getBaseMapper().insert(identities.toPersistenceObject(identityOrder(901L, 55L, "other")));
        IdentityQuery query = new IdentityQuery();
        query.eq(IdentityOrder::getBusinessId, 101L);
        IdentityOrder patch = identityOrder(101L, 901L, "changed");

        assertThat(identities.update(patch, query)).isTrue();
        assertThat(patch.id()).isEqualTo(101L);
        assertThat(identities.findById(101L).get().getName()).isEqualTo("changed");
        assertThat(identities.deleteByQuery(query)).isTrue();
        assertThat(identities.findById(101L)).isEmpty();
        assertThat(identities.findById(901L)).isPresent();
    }

    // =================== 测试夹具 ===================

    /**
     * 创建 <code>new IdentityRepository</code> 供当前测试路径调用。
     *
     * @return 当前测试使用的结果，取自 <code>new IdentityRepository(identityMapper)</code>
     */
    private IdentityRepository identityRepository() {
        IdentityMapper identityMapper = session.getMapper(IdentityMapper.class);
        identityMapper.delete(null);
        return new IdentityRepository(identityMapper);
    }

    /**
     * 替代数据库 Mapper，供仓储测试检查 CRUD 调用和映射结果。
     */
    interface BatchMapper extends BaseMapper<BatchPo> {
    }

    /**
     * 为订单持久化测试提供的映射入口。
     */
    interface OrderMapper extends BaseMapper<OrderPO> {
    }

    /**
     * 替代数据库 Mapper，供仓储测试检查 CRUD 调用和映射结果。
     */
    public interface IdentityMapper extends BaseMapper<IdentityPO> {
    }

    /**
     * 绑定测试聚合和持久化类型的批量仓储。
     */
    static final class BatchRepository
            extends MybatisAggregateRepository<BatchMapper, BatchModel, BatchPo, BatchQuery, Long> {
        /**
         * 使用 <code>super()</code> 初始化 <code>BatchRepository</code> 测试夹具。
         *
         * @param mapper 当前测试登记或使用的持久化映射组件
         */
        BatchRepository(BatchMapper mapper) {
            super(mapper);
        }
    }

    /**
     * 用于控制查询条件或路由类型的测试查询。
     */
    static final class BatchQuery extends Query<BatchModel> {
    }

    /**
     * 供测试验证事件应用和持久化的聚合样本。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class BatchModel extends AggregateRoot<Long> {
        /**
         * 用于构造和定位测试记录的主键。
         */
        private Long id;
        /**
         * 测试对象的显示名称。
         */
        private String name;
        /**
         * 记录创建的时间。
         */
        private LocalDateTime createdAt;
        /**
         * 最近更新记录的时间。
         */
        private LocalDateTime updatedAt;

        /**
         * 以给定状态构造 <code>BatchModel</code> 测试对象。
         *
         * @param id   用于构造和定位测试记录的主键
         * @param name 测试对象的显示名称
         */
        BatchModel(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        /**
         * 读取<code>id</code> 字段。
         *
         * @return <code>id</code> 字段
         */
        @Override
        public Long id() {
            return id;
        }
    }

    /**
     * 使用 <code>id</code> 验证当前测试的输入和数据映射。
     */
    @Getter
    @Setter
    public static class BatchIdentity {
        /**
         * 用于构造和定位测试记录的主键。
         */
        @TableId(type = IdType.AUTO)
        private Long id;
    }

    /**
     * 映射到测试表，以验证 ORM 字段或主键规则的持久化对象。
     */
    @Getter
    @Setter
    @TableName("order_batch")
    public static class BatchPo extends BatchIdentity {
        /**
         * 测试对象的显示名称。
         */
        private String name;
        /**
         * 记录创建的时间。
         */
        @io.ddd4j.annotation.orm.OnCreate
        private LocalDateTime createdAt;
        /**
         * 最近更新记录的时间。
         */
        @io.ddd4j.annotation.orm.OnUpdate
        private LocalDateTime updatedAt;
    }

    /**
     * 实现 <code>MybatisAggregateRepository</code>，供测试调用 <code>fill()</code> 并观察结果。
     */
    static final class TestRepository
            extends MybatisAggregateRepository<OrderMapper, Order, OrderPO, OrderQuery, String> {

        /**
         * 使用 <code>super()</code> 初始化 <code>TestRepository</code> 测试夹具。
         *
         * @param mapper 当前测试登记或使用的持久化映射组件
         */
        TestRepository(OrderMapper mapper) {
            super(mapper, Order.class, OrderPO.class);
        }

        /**
         * 根据本次调用更新 <code>order.statusLabel = "已支付"</code>，用于后续测试步骤。
         *
         * @param query  用来读取测试数据的查询对象
         * @param models 本次调用使用的 <code>List&lt;Order&gt;</code> 参数 <code>models</code>
         */
        @Override
        public void fill(Query<Order> query, List<Order> models) {
            if (query.fill("statusLabel")) {
                for (Order order : models) {
                    if ("PAID".equals(order.getStatus())) {
                        order.statusLabel = "已支付";
                    }
                }
            }
        }
    }

    /**
     * 订单测试查询对象，供构造仓储检索条件。
     */
    static final class OrderQuery extends Query<Order> {
    }

    /**
     * 供测试验证事件应用和持久化的聚合样本。
     */
    static final class Order extends AggregateRoot<String> {

        /**
         * 用于构造和定位测试记录的主键。
         */
        private String id;
        /**
         * 测试对象当前的业务状态。
         */
        private String status;
        /**
         * 记录被逻辑删除的时间。
         */
        private LocalDateTime deletedAt;
        /**
         * 暴露给观察器或写入响应的状态标签。
         */
        private String statusLabel;

        /**
         * 创建字段使用默认初始值的 <code>Order</code> 测试对象。
         */
        Order() {
        }

        /**
         * 用给定值初始化 <code>Order</code> 实例。
         *
         * @param id        <code>id</code> 字段
         * @param status    <code>status</code> 字段
         * @param deletedAt 记录被逻辑删除的时间
         */
        Order(String id, String status, LocalDateTime deletedAt) {
            this.id = id;
            this.status = status;
            this.deletedAt = deletedAt;
        }

        /**
         * 读取<code>id</code> 字段。
         *
         * @return <code>id</code> 字段
         */
        @Override
        public String id() {
            return id;
        }

        /**
         * 读取<code>id</code> 字段。
         *
         * @return <code>id</code> 字段
         */
        public String getId() {
            return id;
        }

        /**
         * 设置<code>id</code> 字段。
         *
         * @param id 要写入的值
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * 读取<code>status</code> 字段。
         *
         * @return <code>status</code> 字段
         */
        String getStatus() {
            return status;
        }

        /**
         * 设置<code>status</code> 字段。
         *
         * @param status 要写入的值
         */
        public void setStatus(String status) {
            this.status = status;
        }

        /**
         * 读取记录被逻辑删除的时间。
         *
         * @return 记录被逻辑删除的时间
         */
        LocalDateTime getDeletedAt() {
            return deletedAt;
        }

        /**
         * 设置记录被逻辑删除的时间。
         *
         * @param deletedAt 要写入的值
         */
        public void setDeletedAt(LocalDateTime deletedAt) {
            this.deletedAt = deletedAt;
        }

        /**
         * 读取<code>statusLabel</code> 字段。
         *
         * @return <code>statusLabel</code> 字段
         */
        String getStatusLabel() {
            return statusLabel;
        }
    }

    /**
     * 持久化层使用的订单测试数据模型。
     */
    @TableName("orders")
    static final class OrderPO {

        /**
         * 用于构造和定位测试记录的主键。
         */
        @TableId
        private String id;

        /**
         * 测试对象当前的业务状态。
         */
        @TableField("order_status")
        private String status;

        /**
         * 记录被逻辑删除的时间。
         */
        @TableField("deleted_at")
        private LocalDateTime deletedAt;

        /**
         * 当前业务数据所属的租户标识。
         */
        @TenantId
        private String tenantId;

        /**
         * 创建字段使用默认初始值的 <code>OrderPO</code> 测试对象。
         */
        OrderPO() {
        }

        /**
         * 读取<code>id</code> 字段。
         *
         * @return <code>id</code> 字段
         */
        public String getId() {
            return id;
        }

        /**
         * 设置<code>id</code> 字段。
         *
         * @param id 要写入的值
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * 读取<code>status</code> 字段。
         *
         * @return <code>status</code> 字段
         */
        public String getStatus() {
            return status;
        }

        /**
         * 设置<code>status</code> 字段。
         *
         * @param status 要写入的值
         */
        public void setStatus(String status) {
            this.status = status;
        }

        /**
         * 读取记录被逻辑删除的时间。
         *
         * @return 记录被逻辑删除的时间
         */
        public LocalDateTime getDeletedAt() {
            return deletedAt;
        }

        /**
         * 设置记录被逻辑删除的时间。
         *
         * @param deletedAt 要写入的值
         */
        public void setDeletedAt(LocalDateTime deletedAt) {
            this.deletedAt = deletedAt;
        }

        /**
         * 读取当前业务数据所属的租户标识。
         *
         * @return 当前业务数据所属的租户标识
         */
        public String getTenantId() {
            return tenantId;
        }

        /**
         * 设置当前业务数据所属的租户标识。
         *
         * @param tenantId 要写入的值
         */
        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }
    }

    /**
     * 供测试验证事件应用和持久化的聚合样本。
     */
    @Getter
    @Setter
    public static class IdentityModelBase extends AggregateRoot<Long> {
        /**
         * 区分测试交易记录的业务标识。
         */
        @DomainField(poField = "orderKey")
        private Long businessId;

        /**
         * 读取<code>businessId</code> 字段。
         *
         * @return <code>businessId</code> 字段
         */
        @Override
        public Long id() {
            return businessId;
        }
    }

    /**
     * 使用 <code>id</code>、<code>name</code> 验证当前测试的输入和数据映射。
     */
    @Getter
    @Setter
    public static class IdentityOrder extends IdentityModelBase {
        /**
         * 用于构造和定位测试记录的主键。
         */
        private Long id;
        /**
         * 测试对象的显示名称。
         */
        private String name;
    }

    /**
     * 将 <code>orderKey</code> 等状态一起传入当前测试，供调用或结果断言使用。
     */
    @Getter
    @Setter
    public static class IdentityPoBase {
        /**
         * 用于定位同一订单记录的测试业务键。
         */
        @TableId(value = "order_pk", type = IdType.AUTO)
        private Long orderKey;
    }

    /**
     * 映射到测试表，以验证 ORM 字段或主键规则的持久化对象。
     */
    @Getter
    @Setter
    @TableName("order_identity")
    public static class IdentityPO extends IdentityPoBase {
        /**
         * 用于构造和定位测试记录的主键。
         */
        private Long id;
        /**
         * 测试对象的显示名称。
         */
        private String name;
    }

    /**
     * 用于控制查询条件或路由类型的测试查询。
     */
    public static class IdentityQuery extends Query<IdentityOrder> {
    }

    /**
     * 实现 <code>MybatisAggregateRepository</code>，供测试调用 <code>toModel()</code>、<code>toPersistenceObject()</code> 并观察结果。
     */
    static class IdentityRepository extends MybatisAggregateRepository<IdentityMapper, IdentityOrder, IdentityPO, IdentityQuery, Long> {
        /**
         * 使用 <code>super()</code> 初始化 <code>IdentityRepository</code> 测试夹具。
         *
         * @param mapper 当前测试登记或使用的持久化映射组件
         */
        IdentityRepository(IdentityMapper mapper) {
            super(mapper, IdentityOrder.class, IdentityPO.class);
        }

        /**
         * 读取 <code>model</code> 供当前测试使用。
         *
         * @param po 传给 <code>BeanKit.copy()</code> 的 <code>IdentityPO</code> 输入
         * @return 当前测试使用的结果，取自 <code>model</code>
         */
        @Override
        public IdentityOrder toModel(IdentityPO po) {
            IdentityOrder model = BeanKit.copy(po, IdentityOrder.class);
            model.setBusinessId(po.getOrderKey());
            return model;
        }

        /**
         * 读取 <code>po</code> 供当前测试使用。
         *
         * @param model 传给 <code>BeanKit.copy()</code> 的 <code>IdentityOrder</code> 输入
         * @return 当前测试使用的结果，取自 <code>po</code>
         */
        @Override
        public IdentityPO toPersistenceObject(IdentityOrder model) {
            IdentityPO po = BeanKit.copy(model, IdentityPO.class);
            po.setOrderKey(model.id());
            return po;
        }
    }
}
