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

    /** \u4e3a\u5f53\u524d\u96c6\u6210\u6d4b\u8bd5\u63d0\u4f9b\u72ec\u7acb MySQL \u670d\u52a1\u7684\u5bb9\u5668\u3002 */
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            // 数据仅用于本轮集成验证，避免占用宿主 Docker 持久盘。
            .withTmpFs(Map.of("/var/lib/mysql", "rw"))
            .withDatabaseName("ddd4j_it")
            .withUsername("ddd4j")
            .withPassword("ddd4j");

    /** \u4e3a MyBatis \u5408\u540c\u6d4b\u8bd5\u6253\u5f00\u4f1a\u8bdd\u7684\u5de5\u5382\u3002 */
    private static SqlSessionFactory sqlSessionFactory;
    /** \u5f53\u524d\u88ab\u9a8c\u8bc1\u6216\u66ff\u6362\u7684\u6301\u4e45\u5316 Mapper \u5b9e\u4f8b\u3002 */
    private static OrderMapper mapper;
    /** \u88ab\u6d4b\u4ed3\u50a8\u6216\u7528\u4e8e\u63d0\u4f9b\u6d4b\u8bd5\u6570\u636e\u7684\u4ed3\u50a8\u5b9e\u4f8b\u3002 */
    private static TestRepository repository;
    /** \u6d4b\u8bd5\u4e2d\u7528\u4e8e\u6267\u884c Mapper \u64cd\u4f5c\u7684\u6570\u636e\u5e93\u4f1a\u8bdd\u3002 */
    private static SqlSession session;

    /**
     * \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>MYSQL.createConnection()</code>\u3001<code>conn.createStatement()</code>\u3001<code>stmt.execute()</code>\u3001<code>MYSQL.getJdbcUrl()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002
     * @throws Exception \u5f53\u524d\u7528\u4f8b\u51c6\u5907\u8d44\u6e90\u6216\u8c03\u7528\u6d4b\u8bd5\u5165\u53e3\u5931\u8d25
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

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u5173\u95ed<code>session</code>\u3001\u6ce8\u9500<code>RepositoryRegistry</code>\u3002 */
    @AfterAll
    static void closeSession() {
        if (Objects.nonNull(session)) {
            session.close();
        }
        RepositoryRegistry.unregister(Order.class);
        RepositoryRegistry.unregisterQuery(OrderQuery.class);
    }

    /**
     * \u8bfb\u53d6 <code>model</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param key \u7528\u4e8e\u5728\u6d4b\u8bd5\u4e0a\u4e0b\u6587\u6216\u7f13\u5b58\u4e2d\u7d22\u5f15\u6570\u636e\u7684\u952e
     * @param ordinaryId \u4f20\u7ed9 <code>model.setId()</code> \u7684 <code>Long</code> \u8f93\u5165
     * @param name \u5f53\u524d\u6d4b\u8bd5\u6307\u5b9a\u7684\u5bf9\u8c61\u6216\u5c5e\u6027\u540d\u79f0
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>model</code>
     */
    private static IdentityOrder identityOrder(Long key, Long ordinaryId, String name) {
        IdentityOrder model = new IdentityOrder();
        model.setBusinessId(key);
        model.setId(ordinaryId);
        model.setName(name);
        return model;
    }

    /** \u6d4b\u8bd5\u524d\u4f9d\u6b21\u6e05\u9664<code>ThreadContext</code>\u3002 */
    @BeforeEach
    void cleanTable() {
        ThreadContext.clear();
        mapper.delete(null);
        session.getMapper(BatchMapper.class).delete(null);
    }

    /** \u6d4b\u8bd5\u540e\u4f9d\u6b21\u6e05\u9664<code>ThreadContext</code>\u3002 */
    @AfterEach
    void clearContext() {
        ThreadContext.clear();
    }

    /** save\uff1a\u65b0\u805a\u5408\u63d2\u5165\u6570\u636e\u5e93\u5e76\u53ef\u6309 ID \u8bfb\u56de\u3002 */
    @Test
    @DisplayName("save：新聚合插入数据库并可按 ID 读回")
    void saveInsertsNewAggregate() {
        Order order = new Order("o-1", "CREATED", null);

        repository.save(order);

        Optional<Order> found = repository.findById("o-1");
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo("CREATED");
    }

    /** updateById\uff08P0 \u56de\u5f52\uff09\uff1a\u5df2\u5b58\u5728\u805a\u5408\u4ec5\u66f4\u65b0\uff0c\u4e0d\u89e6\u53d1\u63d2\u5165\u5206\u652f\u3002 */
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

    /** updateById\uff08P0 \u56de\u5f52\uff09\uff1a\u4e0d\u5b58\u5728\u7684 ID \u4e0d\u4f1a\u9690\u5f0f\u63d2\u5165\u3002 */
    @Test
    @DisplayName("updateById（P0 回归）：不存在的 ID 不会隐式插入")
    void updateByIdDoesNotInsertWhenAbsent() {
        repository.updateById(new Order("o-ghost", "PAID", null));

        assertThat(repository.findById("o-ghost")).isEmpty();
        assertThat(repository.count()).isZero();
    }

    /** insertOrUpdate\uff1a\u4e0d\u5b58\u5728\u63d2\u5165\u3001\u5b58\u5728\u66f4\u65b0\uff08upsert \u8bed\u4e49\uff09\u3002 */
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

    /** deleteById / existsById\uff1a\u5220\u9664\u540e\u4e0d\u53ef\u67e5\u3002 */
    @Test
    @DisplayName("deleteById / existsById：删除后不可查")
    void deleteByIdRemovesRow() {
        repository.save(new Order("o-4", "CREATED", null));
        assertThat(repository.existsById("o-4")).isTrue();

        repository.deleteById("o-4");

        assertThat(repository.existsById("o-4")).isFalse();
        assertThat(repository.findAll()).isEmpty();
    }

    /** \u539f\u7248\u6a21\u578b\u4e0e Query\uff1a\u76f4\u63a5\u4fdd\u5b58\u66f4\u65b0\u3001\u5355\u6761\u5217\u8868\u5206\u9875\u548c\u5b58\u5728\u5224\u65ad\uff0c\u65e0\u547d\u4ee4\u603b\u7ebf\u6216\u4e8b\u4ef6\u5b58\u50a8\u3002 */
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

    /** \u8f6c\u6362\u4e0e\u586b\u5145\uff1a\u8bfb\u56de PO \u6620\u5c04\u4e3a\u9886\u57df\u5bf9\u8c61\uff0c\u6309\u9700\u6267\u884c\u4e1a\u52a1\u4ed3\u50a8\u586b\u5145\u3002 */
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

    /** \u79df\u6237\uff1a\u539f\u7248\u4fdd\u5b58\u81ea\u52a8\u586b\u79df\u6237\uff0cQuery \u5355\u6761\u5217\u8868\u5206\u9875\u548c\u5b58\u5728\u5224\u65ad\u9694\u79bb\u79df\u6237\u3002 */
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

    /** \u9a8c\u8bc1<code>batch.saveBatch(List.of(first, second))</code> \u6309\u987a\u5e8f\u4ec5\u5305\u542b <code>first</code>\u3001<code>second</code>\uff0c<code>first.getId()</code>\u975e\u7a7a\uff0c<code>persisted.selectCount(null)</code> \u7b49\u4e8e <code>3</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>batch.saveBatch(List.of( new BatchModel(777L, "first"), new BatchModel(777L, "duplicate")))</code> \u629b\u51fa <code>RuntimeException.class</code>\uff0c<code>read.getMapper(BatchMapper.class).selectCount(null)</code>\u4e3a\u96f6\u3002 */
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

    /** \u9a8c\u8bc1<code>identities.save(model)</code> \u6307\u5411\u540c\u4e00\u5b9e\u4f8b <code>model</code>\uff0c<code>model.id()</code>\u975e\u7a7a\uff0c<code>identities.findById(model.id()).get().getName()</code> \u7b49\u4e8e <code>"updated"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>identities.count(query)</code> \u7b49\u4e8e <code>1</code>\uff0c<code>identities.maps(query).get(0)</code> \u542b\u5bf9\u5e94\u6761\u76ee <code>"order_pk"</code>\u3001<code>101L</code>\uff0c<code>identities.findFirst(poQuery).get().id()</code> \u7b49\u4e8e <code>101L</code>\u3002 */
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

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>identityRepository()</code>\u3001<code>identities.getBaseMapper()</code>\u3001<code>identities.toPersistenceObject()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void listConversionUsesExistingDomainObjectMapper() {
        IdentityRepository identities = identityRepository();
        identities.getBaseMapper().insert(identities.toPersistenceObject(identityOrder(101L, 901L, "one")));

        assertThat(identities.findAll()).extracting(IdentityOrder::id).containsExactly(101L);
    }

    /** \u9a8c\u8bc1<code>first.id()</code>\u975e\u7a7a\uff0c<code>second.id()</code>\u975e\u7a7a\uff0c<code>identities.findById(third.id()).get().getName()</code> \u7b49\u4e8e <code>"new despite ordinary id"</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>identities.update(patch, query)</code>\u4e3a\u771f\uff0c<code>patch.id()</code> \u7b49\u4e8e <code>101L</code>\uff0c<code>identities.findById(901L)</code>\u5b58\u5728\u503c\u3002 */
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
     * \u521b\u5efa <code>new IdentityRepository</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u8def\u5f84\u8c03\u7528\u3002
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>new IdentityRepository(identityMapper)</code>
     */
    private IdentityRepository identityRepository() {
        IdentityMapper identityMapper = session.getMapper(IdentityMapper.class);
        identityMapper.delete(null);
        return new IdentityRepository(identityMapper);
    }

    /** \u66ff\u4ee3\u6570\u636e\u5e93 Mapper\uff0c\u4f9b\u4ed3\u50a8\u6d4b\u8bd5\u68c0\u67e5 CRUD \u8c03\u7528\u548c\u6620\u5c04\u7ed3\u679c\u3002 */
    interface BatchMapper extends BaseMapper<BatchPo> {
    }

    /** \u4e3a\u8ba2\u5355\u6301\u4e45\u5316\u6d4b\u8bd5\u63d0\u4f9b\u7684\u6620\u5c04\u5165\u53e3\u3002 */
    interface OrderMapper extends BaseMapper<OrderPO> {
    }

    /** \u66ff\u4ee3\u6570\u636e\u5e93 Mapper\uff0c\u4f9b\u4ed3\u50a8\u6d4b\u8bd5\u68c0\u67e5 CRUD \u8c03\u7528\u548c\u6620\u5c04\u7ed3\u679c\u3002 */
    public interface IdentityMapper extends BaseMapper<IdentityPO> {
    }

    /** \u7ed1\u5b9a\u6d4b\u8bd5\u805a\u5408\u548c\u6301\u4e45\u5316\u7c7b\u578b\u7684\u6279\u91cf\u4ed3\u50a8\u3002 */
    static final class BatchRepository
            extends MybatisAggregateRepository<BatchMapper, BatchModel, BatchPo, BatchQuery, Long> {
        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>BatchRepository</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param mapper \u5f53\u524d\u6d4b\u8bd5\u767b\u8bb0\u6216\u4f7f\u7528\u7684\u6301\u4e45\u5316\u6620\u5c04\u7ec4\u4ef6
         */
        BatchRepository(BatchMapper mapper) {
            super(mapper);
        }
    }

    /** \u7528\u4e8e\u63a7\u5236\u67e5\u8be2\u6761\u4ef6\u6216\u8def\u7531\u7c7b\u578b\u7684\u6d4b\u8bd5\u67e5\u8be2\u3002 */
    static final class BatchQuery extends Query<BatchModel> {
    }

    /** \u4f9b\u6d4b\u8bd5\u9a8c\u8bc1\u4e8b\u4ef6\u5e94\u7528\u548c\u6301\u4e45\u5316\u7684\u805a\u5408\u6837\u672c\u3002 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class BatchModel extends AggregateRoot<Long> {
        /** \u7528\u4e8e\u6784\u9020\u548c\u5b9a\u4f4d\u6d4b\u8bd5\u8bb0\u5f55\u7684\u4e3b\u952e\u3002 */
        private Long id;
        /** \u6d4b\u8bd5\u5bf9\u8c61\u7684\u663e\u793a\u540d\u79f0\u3002 */
        private String name;
        /** \u8bb0\u5f55\u521b\u5efa\u7684\u65f6\u95f4\u3002 */
        private LocalDateTime createdAt;
        /** \u6700\u8fd1\u66f4\u65b0\u8bb0\u5f55\u7684\u65f6\u95f4\u3002 */
        private LocalDateTime updatedAt;

        /**
         * \u4ee5\u7ed9\u5b9a\u72b6\u6001\u6784\u9020 <code>BatchModel</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002
         * @param id \u7528\u4e8e\u6784\u9020\u548c\u5b9a\u4f4d\u6d4b\u8bd5\u8bb0\u5f55\u7684\u4e3b\u952e
         * @param name \u6d4b\u8bd5\u5bf9\u8c61\u7684\u663e\u793a\u540d\u79f0
         */
        BatchModel(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        /**
         * \u8bfb\u53d6<code>id</code> \u5b57\u6bb5\u3002
         * @return <code>id</code> \u5b57\u6bb5
         */
        @Override
        public Long id() {
            return id;
        }
    }

    /** \u4f7f\u7528 <code>id</code> \u9a8c\u8bc1\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u548c\u6570\u636e\u6620\u5c04\u3002 */
    @Getter
    @Setter
    public static class BatchIdentity {
        /** \u7528\u4e8e\u6784\u9020\u548c\u5b9a\u4f4d\u6d4b\u8bd5\u8bb0\u5f55\u7684\u4e3b\u952e\u3002 */
        @TableId(type = IdType.AUTO)
        private Long id;
    }

    /** \u6620\u5c04\u5230\u6d4b\u8bd5\u8868\uff0c\u4ee5\u9a8c\u8bc1 ORM \u5b57\u6bb5\u6216\u4e3b\u952e\u89c4\u5219\u7684\u6301\u4e45\u5316\u5bf9\u8c61\u3002 */
    @Getter
    @Setter
    @TableName("order_batch")
    public static class BatchPo extends BatchIdentity {
        /** \u6d4b\u8bd5\u5bf9\u8c61\u7684\u663e\u793a\u540d\u79f0\u3002 */
        private String name;
        /** \u8bb0\u5f55\u521b\u5efa\u7684\u65f6\u95f4\u3002 */
        @io.ddd4j.annotation.orm.OnCreate
        private LocalDateTime createdAt;
        /** \u6700\u8fd1\u66f4\u65b0\u8bb0\u5f55\u7684\u65f6\u95f4\u3002 */
        @io.ddd4j.annotation.orm.OnUpdate
        private LocalDateTime updatedAt;
    }

    /** \u5b9e\u73b0 <code>MybatisAggregateRepository</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>fill()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    static final class TestRepository
            extends MybatisAggregateRepository<OrderMapper, Order, OrderPO, OrderQuery, String> {

        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>TestRepository</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param mapper \u5f53\u524d\u6d4b\u8bd5\u767b\u8bb0\u6216\u4f7f\u7528\u7684\u6301\u4e45\u5316\u6620\u5c04\u7ec4\u4ef6
         */
        TestRepository(OrderMapper mapper) {
            super(mapper, Order.class, OrderPO.class);
        }

        /**
         * \u6839\u636e\u672c\u6b21\u8c03\u7528\u66f4\u65b0 <code>order.statusLabel = "\u5df2\u652f\u4ed8"</code>\uff0c\u7528\u4e8e\u540e\u7eed\u6d4b\u8bd5\u6b65\u9aa4\u3002
         * @param query \u7528\u6765\u8bfb\u53d6\u6d4b\u8bd5\u6570\u636e\u7684\u67e5\u8be2\u5bf9\u8c61
         * @param models \u672c\u6b21\u8c03\u7528\u4f7f\u7528\u7684 <code>List&lt;Order&gt;</code> \u53c2\u6570 <code>models</code>
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

    /** \u8ba2\u5355\u6d4b\u8bd5\u67e5\u8be2\u5bf9\u8c61\uff0c\u4f9b\u6784\u9020\u4ed3\u50a8\u68c0\u7d22\u6761\u4ef6\u3002 */
    static final class OrderQuery extends Query<Order> {
    }

    /** \u4f9b\u6d4b\u8bd5\u9a8c\u8bc1\u4e8b\u4ef6\u5e94\u7528\u548c\u6301\u4e45\u5316\u7684\u805a\u5408\u6837\u672c\u3002 */
    static final class Order extends AggregateRoot<String> {

        /** \u7528\u4e8e\u6784\u9020\u548c\u5b9a\u4f4d\u6d4b\u8bd5\u8bb0\u5f55\u7684\u4e3b\u952e\u3002 */
        private String id;
        /** \u6d4b\u8bd5\u5bf9\u8c61\u5f53\u524d\u7684\u4e1a\u52a1\u72b6\u6001\u3002 */
        private String status;
        /** \u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4\u3002 */
        private LocalDateTime deletedAt;
        /** \u66b4\u9732\u7ed9\u89c2\u5bdf\u5668\u6216\u5199\u5165\u54cd\u5e94\u7684\u72b6\u6001\u6807\u7b7e\u3002 */
        private String statusLabel;

        /** \u521b\u5efa\u5b57\u6bb5\u4f7f\u7528\u9ed8\u8ba4\u521d\u59cb\u503c\u7684 <code>Order</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002 */
        Order() {
        }

        /**
         * \u7528\u7ed9\u5b9a\u503c\u521d\u59cb\u5316 <code>Order</code> \u5b9e\u4f8b\u3002
         * @param id <code>id</code> \u5b57\u6bb5
         * @param status <code>status</code> \u5b57\u6bb5
         * @param deletedAt \u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4
         */
        Order(String id, String status, LocalDateTime deletedAt) {
            this.id = id;
            this.status = status;
            this.deletedAt = deletedAt;
        }

        /**
         * \u8bfb\u53d6<code>id</code> \u5b57\u6bb5\u3002
         * @return <code>id</code> \u5b57\u6bb5
         */
        @Override
        public String id() {
            return id;
        }

        /**
         * \u8bfb\u53d6<code>id</code> \u5b57\u6bb5\u3002
         * @return <code>id</code> \u5b57\u6bb5
         */
        public String getId() {
            return id;
        }

        /**
         * \u8bbe\u7f6e<code>id</code> \u5b57\u6bb5\u3002
         * @param id \u8981\u5199\u5165\u7684\u503c
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * \u8bfb\u53d6<code>status</code> \u5b57\u6bb5\u3002
         * @return <code>status</code> \u5b57\u6bb5
         */
        String getStatus() {
            return status;
        }

        /**
         * \u8bbe\u7f6e<code>status</code> \u5b57\u6bb5\u3002
         * @param status \u8981\u5199\u5165\u7684\u503c
         */
        public void setStatus(String status) {
            this.status = status;
        }

        /**
         * \u8bfb\u53d6\u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4\u3002
         * @return \u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4
         */
        LocalDateTime getDeletedAt() {
            return deletedAt;
        }

        /**
         * \u8bbe\u7f6e\u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4\u3002
         * @param deletedAt \u8981\u5199\u5165\u7684\u503c
         */
        public void setDeletedAt(LocalDateTime deletedAt) {
            this.deletedAt = deletedAt;
        }

        /**
         * \u8bfb\u53d6<code>statusLabel</code> \u5b57\u6bb5\u3002
         * @return <code>statusLabel</code> \u5b57\u6bb5
         */
        String getStatusLabel() {
            return statusLabel;
        }
    }

    /** \u6301\u4e45\u5316\u5c42\u4f7f\u7528\u7684\u8ba2\u5355\u6d4b\u8bd5\u6570\u636e\u6a21\u578b\u3002 */
    @TableName("orders")
    static final class OrderPO {

        /** \u7528\u4e8e\u6784\u9020\u548c\u5b9a\u4f4d\u6d4b\u8bd5\u8bb0\u5f55\u7684\u4e3b\u952e\u3002 */
        @TableId
        private String id;

        /** \u6d4b\u8bd5\u5bf9\u8c61\u5f53\u524d\u7684\u4e1a\u52a1\u72b6\u6001\u3002 */
        @TableField("order_status")
        private String status;

        /** \u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4\u3002 */
        @TableField("deleted_at")
        private LocalDateTime deletedAt;

        /** \u5f53\u524d\u4e1a\u52a1\u6570\u636e\u6240\u5c5e\u7684\u79df\u6237\u6807\u8bc6\u3002 */
        @TenantId
        private String tenantId;

        /** \u521b\u5efa\u5b57\u6bb5\u4f7f\u7528\u9ed8\u8ba4\u521d\u59cb\u503c\u7684 <code>OrderPO</code> \u6d4b\u8bd5\u5bf9\u8c61\u3002 */
        OrderPO() {
        }

        /**
         * \u8bfb\u53d6<code>id</code> \u5b57\u6bb5\u3002
         * @return <code>id</code> \u5b57\u6bb5
         */
        public String getId() {
            return id;
        }

        /**
         * \u8bbe\u7f6e<code>id</code> \u5b57\u6bb5\u3002
         * @param id \u8981\u5199\u5165\u7684\u503c
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * \u8bfb\u53d6<code>status</code> \u5b57\u6bb5\u3002
         * @return <code>status</code> \u5b57\u6bb5
         */
        public String getStatus() {
            return status;
        }

        /**
         * \u8bbe\u7f6e<code>status</code> \u5b57\u6bb5\u3002
         * @param status \u8981\u5199\u5165\u7684\u503c
         */
        public void setStatus(String status) {
            this.status = status;
        }

        /**
         * \u8bfb\u53d6\u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4\u3002
         * @return \u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4
         */
        public LocalDateTime getDeletedAt() {
            return deletedAt;
        }

        /**
         * \u8bbe\u7f6e\u8bb0\u5f55\u88ab\u903b\u8f91\u5220\u9664\u7684\u65f6\u95f4\u3002
         * @param deletedAt \u8981\u5199\u5165\u7684\u503c
         */
        public void setDeletedAt(LocalDateTime deletedAt) {
            this.deletedAt = deletedAt;
        }

        /**
         * \u8bfb\u53d6\u5f53\u524d\u4e1a\u52a1\u6570\u636e\u6240\u5c5e\u7684\u79df\u6237\u6807\u8bc6\u3002
         * @return \u5f53\u524d\u4e1a\u52a1\u6570\u636e\u6240\u5c5e\u7684\u79df\u6237\u6807\u8bc6
         */
        public String getTenantId() {
            return tenantId;
        }

        /**
         * \u8bbe\u7f6e\u5f53\u524d\u4e1a\u52a1\u6570\u636e\u6240\u5c5e\u7684\u79df\u6237\u6807\u8bc6\u3002
         * @param tenantId \u8981\u5199\u5165\u7684\u503c
         */
        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }
    }

    /** \u4f9b\u6d4b\u8bd5\u9a8c\u8bc1\u4e8b\u4ef6\u5e94\u7528\u548c\u6301\u4e45\u5316\u7684\u805a\u5408\u6837\u672c\u3002 */
    @Getter
    @Setter
    public static class IdentityModelBase extends AggregateRoot<Long> {
        /** \u533a\u5206\u6d4b\u8bd5\u4ea4\u6613\u8bb0\u5f55\u7684\u4e1a\u52a1\u6807\u8bc6\u3002 */
        @DomainField(poField = "orderKey")
        private Long businessId;

        /**
         * \u8bfb\u53d6<code>businessId</code> \u5b57\u6bb5\u3002
         * @return <code>businessId</code> \u5b57\u6bb5
         */
        @Override
        public Long id() {
            return businessId;
        }
    }

    /** \u4f7f\u7528 <code>id</code>\u3001<code>name</code> \u9a8c\u8bc1\u5f53\u524d\u6d4b\u8bd5\u7684\u8f93\u5165\u548c\u6570\u636e\u6620\u5c04\u3002 */
    @Getter
    @Setter
    public static class IdentityOrder extends IdentityModelBase {
        /** \u7528\u4e8e\u6784\u9020\u548c\u5b9a\u4f4d\u6d4b\u8bd5\u8bb0\u5f55\u7684\u4e3b\u952e\u3002 */
        private Long id;
        /** \u6d4b\u8bd5\u5bf9\u8c61\u7684\u663e\u793a\u540d\u79f0\u3002 */
        private String name;
    }

    /** \u5c06 <code>orderKey</code> \u7b49\u72b6\u6001\u4e00\u8d77\u4f20\u5165\u5f53\u524d\u6d4b\u8bd5\uff0c\u4f9b\u8c03\u7528\u6216\u7ed3\u679c\u65ad\u8a00\u4f7f\u7528\u3002 */
    @Getter
    @Setter
    public static class IdentityPoBase {
        /** \u7528\u4e8e\u5b9a\u4f4d\u540c\u4e00\u8ba2\u5355\u8bb0\u5f55\u7684\u6d4b\u8bd5\u4e1a\u52a1\u952e\u3002 */
        @TableId(value = "order_pk", type = IdType.AUTO)
        private Long orderKey;
    }

    /** \u6620\u5c04\u5230\u6d4b\u8bd5\u8868\uff0c\u4ee5\u9a8c\u8bc1 ORM \u5b57\u6bb5\u6216\u4e3b\u952e\u89c4\u5219\u7684\u6301\u4e45\u5316\u5bf9\u8c61\u3002 */
    @Getter
    @Setter
    @TableName("order_identity")
    public static class IdentityPO extends IdentityPoBase {
        /** \u7528\u4e8e\u6784\u9020\u548c\u5b9a\u4f4d\u6d4b\u8bd5\u8bb0\u5f55\u7684\u4e3b\u952e\u3002 */
        private Long id;
        /** \u6d4b\u8bd5\u5bf9\u8c61\u7684\u663e\u793a\u540d\u79f0\u3002 */
        private String name;
    }

    /** \u7528\u4e8e\u63a7\u5236\u67e5\u8be2\u6761\u4ef6\u6216\u8def\u7531\u7c7b\u578b\u7684\u6d4b\u8bd5\u67e5\u8be2\u3002 */
    public static class IdentityQuery extends Query<IdentityOrder> {
    }

    /** \u5b9e\u73b0 <code>MybatisAggregateRepository</code>\uff0c\u4f9b\u6d4b\u8bd5\u8c03\u7528 <code>toModel()</code>\u3001<code>toPersistenceObject()</code> \u5e76\u89c2\u5bdf\u7ed3\u679c\u3002 */
    static class IdentityRepository extends MybatisAggregateRepository<IdentityMapper, IdentityOrder, IdentityPO, IdentityQuery, Long> {
        /**
         * \u4f7f\u7528 <code>super()</code> \u521d\u59cb\u5316 <code>IdentityRepository</code> \u6d4b\u8bd5\u5939\u5177\u3002
         * @param mapper \u5f53\u524d\u6d4b\u8bd5\u767b\u8bb0\u6216\u4f7f\u7528\u7684\u6301\u4e45\u5316\u6620\u5c04\u7ec4\u4ef6
         */
        IdentityRepository(IdentityMapper mapper) {
            super(mapper, IdentityOrder.class, IdentityPO.class);
        }

        /**
         * \u8bfb\u53d6 <code>model</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
         * @param po \u4f20\u7ed9 <code>BeanKit.copy()</code> \u7684 <code>IdentityPO</code> \u8f93\u5165
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>model</code>
         */
        @Override
        public IdentityOrder toModel(IdentityPO po) {
            IdentityOrder model = BeanKit.copy(po, IdentityOrder.class);
            model.setBusinessId(po.getOrderKey());
            return model;
        }

        /**
         * \u8bfb\u53d6 <code>po</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
         * @param model \u4f20\u7ed9 <code>BeanKit.copy()</code> \u7684 <code>IdentityOrder</code> \u8f93\u5165
         * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>po</code>
         */
        @Override
        public IdentityPO toPersistenceObject(IdentityOrder model) {
            IdentityPO po = BeanKit.copy(model, IdentityPO.class);
            po.setOrderKey(model.id());
            return po;
        }
    }
}
