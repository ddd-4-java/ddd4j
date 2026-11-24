package io.ddd4j.sample.spring;

import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.jdbc.JdbcOrderRepository;
import io.ddd4j.sample.order.jdbc.JdbcOrderReadModelPort;
import io.ddd4j.sample.order.jdbc.JdbcOrderTransactionPort;
import io.ddd4j.sample.order.jdbc.JdbcOutboxPort;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;

/** 复用现有 PostgreSQL 订单、Outbox 与查询适配器；不启动 MQ 或后台发布器。 */
@Configuration(proxyBeanMethods = false)
@Profile("postgres")
public class PostgresOrderConfiguration {
    /**
     * @param environment 显式数据库连接参数
     * @return 既有订单表的数据源
     */
    @Bean
    public DataSource orderDataSource(Environment environment) {
        return new DriverManagerDataSource(environment.getRequiredProperty("sample.jdbc.url"),
                environment.getRequiredProperty("sample.jdbc.username"), environment.getRequiredProperty("sample.jdbc.password"));
    }
    /**
     * @param dataSource 数据源
     * @param mapper JSON 映射器
     * @return 共享订单应用服务
     */
    @Bean
    public OrderApplicationService orderApplicationService(DataSource dataSource, ObjectMapper mapper) {
        JdbcOrderTransactionPort transaction = new JdbcOrderTransactionPort(dataSource);
        return new OrderApplicationService(new JdbcOrderRepository(transaction), new JdbcOutboxPort(transaction, mapper),
                new JdbcOrderReadModelPort(transaction), new InMemoryOrderAdapters(), transaction);
    }
}
