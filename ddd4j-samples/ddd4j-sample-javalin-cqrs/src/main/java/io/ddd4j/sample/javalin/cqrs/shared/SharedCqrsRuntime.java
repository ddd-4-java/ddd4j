package io.ddd4j.sample.javalin.cqrs.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import io.ddd4j.core.cqrs.command.CommandBus;
import io.ddd4j.core.cqrs.readmodel.EventChunkReader;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ProjectionRunner;
import io.ddd4j.data.event.store.jpa.StoredEventEntity;
import io.ddd4j.data.projection.jpa.ProjectionPositionEntity;
import io.ddd4j.guice.Ddd4jGuiceModule;
import io.ddd4j.guice.Ddd4jProjectionGuiceModule;
import io.ddd4j.guice.Ddd4jGuiceRuntime;
import io.ddd4j.guice.context.GuiceContext;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.application.OrderReadModelPort;
import io.ddd4j.sample.order.jdbc.JdbcOrderRepository;
import io.ddd4j.sample.order.jdbc.JdbcOrderTransactionPort;
import io.ddd4j.sample.order.jdbc.JdbcOrderReadModelPort;
import io.ddd4j.sample.order.jdbc.JdbcOutboxPort;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.hibernate.cfg.Configuration;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/** 共享订单的 PostgreSQL/JPA/Guice 显式 CQRS 装配；不创建新表结构或新业务模型。 */
public final class SharedCqrsRuntime implements AutoCloseable {
    private final EntityManagerFactory factory;
    private final Injector previous = GuiceContext.isInitialized() ? GuiceContext.getInjector() : null;
    private final Injector injector;
    private final OrderReadModelPort queries;
    private final OrderProjection projection;

    /**
     * @param source 已有订单数据源
     * @param schema validate（测试可用已有实体创建隔离表）
     * @param scheduled 是否启用实际后台投影
     * @param decorate 查询边界装饰器
     */
    public SharedCqrsRuntime(DataSource source, String schema, boolean scheduled, UnaryOperator<OrderReadModelPort> decorate) {
        Configuration configuration = new Configuration().addAnnotatedClass(StoredEventEntity.class).addAnnotatedClass(ProjectionPositionEntity.class);
        configuration.getProperties().put("hibernate.connection.datasource", source);
        configuration.setProperty("hibernate.hbm2ddl.auto", schema);
        factory = configuration.buildSessionFactory();
        try {
            var transaction = new JdbcOrderTransactionPort(source);
            var repository = new JdbcOrderRepository(transaction);
            queries = decorate.apply(new JdbcOrderReadModelPort(transaction));
            var mapper = new ObjectMapper().findAndRegisterModules();
            var outbox = new JdbcOutboxPort(transaction, mapper);
            var service = new OrderApplicationService(repository, outbox, new DeferredOrderReadModel(queries), new InMemoryOrderAdapters(), transaction);
            projection = new OrderProjection(repository, outbox, queries, transaction, factory, mapper);
            injector = Guice.createInjector(Ddd4jGuiceModule.basic(),
                    new Ddd4jProjectionGuiceModule(scheduled ? List.of(projection.view()) : List.of()), new AbstractModule() {
                @Override protected void configure() {
                    bind(OrderCommandExecutor.class).toInstance(new OrderCommandExecutor(service));
                    bind(ProjectionPositionRepository.class).toInstance(new PersistentPositions(factory));
                    bind(new TypeLiteral<EventChunkReader<Object>>() { }).toInstance(projection.reader());
                }
            });
            projection.useRunner(injector.getInstance(Key.get(new TypeLiteral<ProjectionRunner<Object>>() { })));
        } catch (RuntimeException | Error failure) {
            factory.close();
            throw failure;
        }
    }
    /** @return 独立查询仓储 */
    public OrderReadModelPort queries() { return queries; }
    /** @return 持久化中继和公共投影运行器 */
    public OrderProjection projection() { return projection; }
    /** @return 实际 Guice 命令总线 */
    public CommandBus commands() { return injector.getInstance(CommandBus.class); }
    /** @return 已有 JPA 实体工厂（用于运行监控与集成验证） */
    public EntityManagerFactory factory() { return factory; }
    @Override public void close() {
            try (var callerContext = io.ddd4j.core.context.ThreadContext.open()) {
        try { injector.getInstance(Ddd4jGuiceRuntime.class).close(); }
        finally {
            factory.close();
            if (Objects.nonNull(previous)) { GuiceContext.setInjector(previous); }
        }
    
            }
        }
}
