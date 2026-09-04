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

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("ddd4j_it")
            .withUsername("ddd4j")
            .withPassword("ddd4j");

    private static SqlSessionFactory sqlSessionFactory;
    private static OrderMapper mapper;
    private static TestRepository repository;

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

    @BeforeEach
    void cleanTable() {
        mapper.delete(null);
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

    // =================== 测试夹具 ===================

    static final class TestRepository
            extends MybatisAggregateRepository<OrderMapper, Order, OrderPO, OrderQuery, String> {

        TestRepository(OrderMapper mapper) {
            super(mapper, Order.class, OrderPO.class);
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
    }
}
