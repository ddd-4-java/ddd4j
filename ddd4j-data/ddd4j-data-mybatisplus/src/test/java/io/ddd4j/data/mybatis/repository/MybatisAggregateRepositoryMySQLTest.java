package io.ddd4j.data.mybatis.repository;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.ddd4j.core.cqrs.query.Query;
import io.ddd4j.core.ddd.model.AggregateRoot;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MybatisAggregateRepository} 的 MySQL 集成测试。
 *
 * <p>使用 Testcontainers MySQL 模块（org.testcontainers:testcontainers-mysql，
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
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("ddd4j_it")
            .withUsername("ddd4j")
            .withPassword("ddd4j");

    /** \u4e3a MyBatis \u5408\u540c\u6d4b\u8bd5\u6253\u5f00\u4f1a\u8bdd\u7684\u5de5\u5382\u3002 */
    private static SqlSessionFactory sqlSessionFactory;
    /** \u5f53\u524d\u88ab\u9a8c\u8bc1\u6216\u66ff\u6362\u7684\u6301\u4e45\u5316 Mapper \u5b9e\u4f8b\u3002 */
    private static OrderMapper mapper;
    /** \u88ab\u6d4b\u4ed3\u50a8\u6216\u7528\u4e8e\u63d0\u4f9b\u6d4b\u8bd5\u6570\u636e\u7684\u4ed3\u50a8\u5b9e\u4f8b\u3002 */
    private static TestRepository repository;

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
                    + "deleted_at DATETIME NULL)");
        }

        // 2. 独立自举 MyBatis-Plus（无 Spring）
        PooledDataSource dataSource = new PooledDataSource(
                "com.mysql.cj.jdbc.Driver", MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("tc", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(OrderMapper.class);
        sqlSessionFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);

        SqlSession session = sqlSessionFactory.openSession(true);
        mapper = session.getMapper(OrderMapper.class);
        repository = new TestRepository(mapper);
    }

    /** \u6d4b\u8bd5\u524d\u4f9d\u6b21\u6e05\u9664<code>ThreadContext</code>\u3002 */
    @BeforeEach
    void cleanTable() {
        mapper.delete(null);
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

    // =================== 测试夹具 ===================

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
    }

    /** \u4e3a\u8ba2\u5355\u6301\u4e45\u5316\u6d4b\u8bd5\u63d0\u4f9b\u7684\u6620\u5c04\u5165\u53e3\u3002 */
    interface OrderMapper extends BaseMapper<OrderPO> {
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
    }
}
