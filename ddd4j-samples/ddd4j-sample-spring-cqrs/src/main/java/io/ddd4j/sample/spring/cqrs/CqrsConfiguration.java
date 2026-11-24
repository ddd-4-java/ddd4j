package io.ddd4j.sample.spring.cqrs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.data.event.store.jpa.StoredEventEntity;
import io.ddd4j.data.projection.jpa.ProjectionPositionEntity;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.application.OrderReadModelPort;
import io.ddd4j.sample.order.application.OutboxPort;
import io.ddd4j.sample.order.domain.OrderRepository;
import io.ddd4j.sample.order.jdbc.JdbcOrderRepository;
import io.ddd4j.sample.order.jdbc.JdbcOrderReadModelPort;
import io.ddd4j.sample.order.jdbc.JdbcOrderTransactionPort;
import io.ddd4j.sample.order.jdbc.JdbcOutboxPort;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import io.ddd4j.spring.config.SpringCommandConfig;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;

/** PostgreSQL 持久化 CQRS 装配；只复用已有订单表、事件表和检查点实体。 */
@org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
@Import(SpringCommandConfig.class)
public class CqrsConfiguration {
    /**
     * @param environment 数据库参数
     * @return 既有业务库数据源
     */
    @Bean
    public DataSource dataSource(Environment environment) {
        return new DriverManagerDataSource(environment.getRequiredProperty("sample.jdbc.url"),
                environment.getRequiredProperty("sample.jdbc.username"), environment.getRequiredProperty("sample.jdbc.password"));
    }
    /**
     * @param dataSource 数据源
     * @param environment 校验策略
     * @return 原生实体工厂
     */
    @Bean(name = "entityManagerFactory", destroyMethod = "close")
    public EntityManagerFactory eventEntityManagerFactory(DataSource dataSource, Environment environment) {
        return buildFactory(dataSource, environment);
    }
    private EntityManagerFactory buildFactory(DataSource dataSource, Environment environment) {
        Configuration configuration = new Configuration().addAnnotatedClass(StoredEventEntity.class)
                .addAnnotatedClass(ProjectionPositionEntity.class);
        configuration.getProperties().put("hibernate.connection.datasource", dataSource);
        configuration.setProperty("hibernate.hbm2ddl.auto", environment.getProperty("sample.jpa.schema-action", "validate"));
        return configuration.buildSessionFactory();
    }
    /**
     * @param dataSource 数据源
     * @return 共享 JDBC 事务边界
     */
    @Bean
    public JdbcOrderTransactionPort orderTransaction(DataSource dataSource) { return new JdbcOrderTransactionPort(dataSource); }
    /**
     * @param transaction 共享事务
     * @return 写仓储
     */
    @Bean
    public OrderRepository orderRepository(JdbcOrderTransactionPort transaction) { return new JdbcOrderRepository(transaction); }
    /**
     * @param transaction 共享事务
     * @param mapper 序列化器
     * @return CQRS 独占消费的 Outbox
     */
    @Bean
    public OutboxPort orderOutbox(JdbcOrderTransactionPort transaction, ObjectMapper mapper) { return new JdbcOutboxPort(transaction, mapper.copy().findAndRegisterModules()); }
    /**
     * @param transaction 共享事务
     * @return 独立查询仓储
     */
    @Bean
    public OrderReadModelPort orderQueries(JdbcOrderTransactionPort transaction) { return new JdbcOrderReadModelPort(transaction); }
    /**
     * @param repository 写仓储
     * @param outbox 事件 Outbox
     * @param queries 查询仓储
     * @param transaction 写事务
     * @return 共享订单服务
     */
    @Bean
    public OrderApplicationService orders(OrderRepository repository, OutboxPort outbox, OrderReadModelPort queries,
            JdbcOrderTransactionPort transaction) {
        return new OrderApplicationService(repository, outbox, new DeferredOrderReadModel(queries),
                new InMemoryOrderAdapters(), transaction);
    }
    /**
     * @param repository 写仓储
     * @param outbox 独占 Outbox
     * @param queries 查询仓储
     * @param transaction 事务
     * @param factory 实体工厂
     * @param mapper 映射器
     * @return 显式运行的持久化投影
     */
    @Bean
    public OrderProjection orderProjection(OrderRepository repository, OutboxPort outbox, OrderReadModelPort queries,
            JdbcOrderTransactionPort transaction, EntityManagerFactory factory, ObjectMapper mapper) {
        return new OrderProjection(repository, outbox, queries, transaction, factory, mapper.copy().findAndRegisterModules());
    }
}
