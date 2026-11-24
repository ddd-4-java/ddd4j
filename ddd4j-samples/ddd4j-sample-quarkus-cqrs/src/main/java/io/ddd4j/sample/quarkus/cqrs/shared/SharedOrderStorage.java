package io.ddd4j.sample.quarkus.cqrs.shared;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.jdbc.JdbcOrderRepository;
import io.ddd4j.sample.order.jdbc.JdbcOrderReadModelPort;
import io.ddd4j.sample.order.jdbc.JdbcOrderTransactionPort;
import io.ddd4j.sample.order.jdbc.JdbcOutboxPort;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.postgresql.ds.PGSimpleDataSource;

/** 复用已有 PostgreSQL 订单表和本地事务；此资源不假装参与投影的 JTA。 */
@ApplicationScoped
public class SharedOrderStorage {
    @ConfigProperty(name="quarkus.datasource.jdbc.url") String url;
    @ConfigProperty(name="quarkus.datasource.username") String user;
    @ConfigProperty(name="quarkus.datasource.password") String password;
    @Inject ObjectMapper mapper;
    private JdbcOrderTransactionPort transaction;
    private JdbcOrderRepository repository;
    private JdbcOrderReadModelPort queries;
    private JdbcOutboxPort outbox;
    private OrderApplicationService service;

    @PostConstruct void initialize() {
        var source = new PGSimpleDataSource();
        source.setUrl(url); source.setUser(user); source.setPassword(password);
        transaction = new JdbcOrderTransactionPort(source);
        repository = new JdbcOrderRepository(transaction);
        queries = new JdbcOrderReadModelPort(transaction);
        outbox = new JdbcOutboxPort(transaction, mapper);
        service = new OrderApplicationService(repository, outbox, new DeferredOrderReadModel(queries), new InMemoryOrderAdapters(), transaction);
    }
    /** @return 共享业务服务 */
    public OrderApplicationService service() { return service; }
    /** @return 独立读模型仓储 */
    public JdbcOrderReadModelPort queries() { return queries; }
    /** @return 同事务 Outbox */
    public JdbcOutboxPort outbox() { return outbox; }
    /** @return 显式本地 JDBC 事务 */
    public JdbcOrderTransactionPort transaction() { return transaction; }
    /**
     * @param id 订单标识
     * @return 已提交写模型，限投影和验收使用
     */
    public OrderReadModel persisted(String id) {
        var order = repository.findById(id).orElseThrow();
        return new OrderReadModel(order.id(), order.orderNo(), order.buyerId(), order.buyerName(), order.status(), order.totalAmount().amount());
    }
}
