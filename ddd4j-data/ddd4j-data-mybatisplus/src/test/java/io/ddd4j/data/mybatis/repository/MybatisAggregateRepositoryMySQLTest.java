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

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import io.ddd4j.annotation.orm.TenantId;
import io.ddd4j.annotation.orm.DomainField;
import io.ddd4j.kit.lang.BeanKit;
import io.ddd4j.core.api.Page;
import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import io.ddd4j.core.ddd.repository.RepositoryRegistry;
import io.ddd4j.core.exception.BizRuntimeException;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            // 数据仅用于本轮集成验证，避免占用宿主 Docker 持久盘。
            .withTmpFs(Map.of("/var/lib/mysql", "rw"))
            .withDatabaseName("ddd4j_it")
            .withUsername("ddd4j")
            .withPassword("ddd4j");

    private static SqlSessionFactory sqlSessionFactory;
    private static OrderMapper mapper;
    private static TestRepository repository;
    private static SqlSession session;

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

    @BeforeEach
    void cleanTable() {
        ThreadContext.clear();
        mapper.delete(null);
        session.getMapper(BatchMapper.class).delete(null);
    }

    @AfterEach
    void clearContext() {
        ThreadContext.clear();
    }

    @AfterAll
    static void closeSession() {
        if (Objects.nonNull(session)) {
            session.close();
        }
        RepositoryRegistry.unregister(Order.class);
        RepositoryRegistry.unregisterQuery(OrderQuery.class);
    }

    @Test
    @DisplayName("save：新聚合插入数据库并可按 ID 读回")
    void saveInsertsNewAggregate() {
        Order order = new Order("o-1", "CREATED", null);

        repository.save(order);

        Optional<Order> found = repository.findById("o-1");
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo("CREATED");
    }

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

    @Test
    @DisplayName("updateById（P0 回归）：不存在的 ID 不会隐式插入")
    void updateByIdDoesNotInsertWhenAbsent() {
        repository.updateById(new Order("o-ghost", "PAID", null));

        assertThat(repository.findById("o-ghost")).isEmpty();
        assertThat(repository.count()).isZero();
    }

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

    @Test
    @DisplayName("deleteById / existsById：删除后不可查")
    void deleteByIdRemovesRow() {
        repository.save(new Order("o-4", "CREATED", null));
        assertThat(repository.existsById("o-4")).isTrue();

        repository.deleteById("o-4");

        assertThat(repository.existsById("o-4")).isFalse();
        assertThat(repository.findAll()).isEmpty();
    }

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

    interface BatchMapper extends BaseMapper<BatchPo> { }

    static final class BatchRepository
            extends MybatisAggregateRepository<BatchMapper, BatchModel, BatchPo, BatchQuery, Long> {
        BatchRepository(BatchMapper mapper) { super(mapper); }
    }

    static final class BatchQuery extends Query<BatchModel> { }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class BatchModel extends AggregateRoot<Long> {
        private Long id;
        private String name;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        BatchModel(Long id, String name) { this.id = id; this.name = name; }
        @Override
        public Long id() { return id; }
    }

    @Getter
    @Setter
    public static class BatchIdentity {
        @TableId(type = IdType.AUTO)
        private Long id;
    }

    @Getter
    @Setter
    @TableName("order_batch")
    public static class BatchPo extends BatchIdentity {
        private String name;
        @io.ddd4j.annotation.orm.OnCreate
        private LocalDateTime createdAt;
        @io.ddd4j.annotation.orm.OnUpdate
        private LocalDateTime updatedAt;
    }

    // =================== 测试夹具 ===================

    static final class TestRepository
            extends MybatisAggregateRepository<OrderMapper, Order, OrderPO, OrderQuery, String> {

        TestRepository(OrderMapper mapper) {
            super(mapper, Order.class, OrderPO.class);
        }

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

    interface OrderMapper extends BaseMapper<OrderPO> {
    }

    static final class OrderQuery extends Query<Order> {
    }

    static final class Order extends AggregateRoot<String> {

        private String id;
        private String status;
        private LocalDateTime deletedAt;
        private String statusLabel;

        Order() {
        }

        Order(String id, String status, LocalDateTime deletedAt) {
            this.id = id;
            this.status = status;
            this.deletedAt = deletedAt;
        }

        @Override
        public String id() {
            return id;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        LocalDateTime getDeletedAt() {
            return deletedAt;
        }

        String getStatusLabel() {
            return statusLabel;
        }

        public void setDeletedAt(LocalDateTime deletedAt) {
            this.deletedAt = deletedAt;
        }
    }

    @TableName("orders")
    static final class OrderPO {

        @TableId
        private String id;

        @TableField("order_status")
        private String status;

        @TableField("deleted_at")
        private LocalDateTime deletedAt;

        @TenantId
        private String tenantId;

        OrderPO() {
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public LocalDateTime getDeletedAt() {
            return deletedAt;
        }

        public void setDeletedAt(LocalDateTime deletedAt) {
            this.deletedAt = deletedAt;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }
    }

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

    @Test
    void listConversionUsesExistingDomainObjectMapper() {
        IdentityRepository identities = identityRepository();
        identities.getBaseMapper().insert(identities.toPersistenceObject(identityOrder(101L, 901L, "one")));

        assertThat(identities.findAll()).extracting(IdentityOrder::id).containsExactly(101L);
    }

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

    private IdentityRepository identityRepository() {
        IdentityMapper identityMapper = session.getMapper(IdentityMapper.class);
        identityMapper.delete(null);
        return new IdentityRepository(identityMapper);
    }

    private static IdentityOrder identityOrder(Long key, Long ordinaryId, String name) {
        IdentityOrder model = new IdentityOrder();
        model.setBusinessId(key);
        model.setId(ordinaryId);
        model.setName(name);
        return model;
    }

    @Getter
    @Setter
    public static class IdentityModelBase extends AggregateRoot<Long> {
        @DomainField(poField = "orderKey")
        private Long businessId;

        @Override
        public Long id() {
            return businessId;
        }
    }

    @Getter
    @Setter
    public static class IdentityOrder extends IdentityModelBase {
        private Long id;
        private String name;
    }

    @Getter
    @Setter
    public static class IdentityPoBase {
        @TableId(value = "order_pk", type = IdType.AUTO)
        private Long orderKey;
    }

    @Getter
    @Setter
    @TableName("order_identity")
    public static class IdentityPO extends IdentityPoBase {
        private Long id;
        private String name;
    }

    public interface IdentityMapper extends BaseMapper<IdentityPO> {
    }

    public static class IdentityQuery extends Query<IdentityOrder> {
    }

    static class IdentityRepository extends MybatisAggregateRepository<IdentityMapper, IdentityOrder, IdentityPO, IdentityQuery, Long> {
        IdentityRepository(IdentityMapper mapper) {
            super(mapper, IdentityOrder.class, IdentityPO.class);
        }

        @Override
        public IdentityOrder toModel(IdentityPO po) {
            IdentityOrder model = BeanKit.copy(po, IdentityOrder.class);
            model.setBusinessId(po.getOrderKey());
            return model;
        }

        @Override
        public IdentityPO toPersistenceObject(IdentityOrder model) {
            IdentityPO po = BeanKit.copy(model, IdentityPO.class);
            po.setOrderKey(model.id());
            return po;
        }
    }
}
