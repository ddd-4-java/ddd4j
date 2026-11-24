package io.ddd4j.sample.micronaut.cqrs.shared;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.application.OrderReadModelPort;
import io.ddd4j.sample.order.application.OutboxPort;
import io.ddd4j.sample.order.jdbc.JdbcOrderRepository;
import io.ddd4j.sample.order.jdbc.JdbcOrderReadModelPort;
import io.ddd4j.sample.order.jdbc.JdbcOrderTransactionPort;
import io.ddd4j.sample.order.jdbc.JdbcOutboxPort;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import io.ddd4j.core.cqrs.readmodel.EventChunkReader;
import io.ddd4j.core.cqrs.readmodel.ProjectionPositionRepository;
import io.ddd4j.core.cqrs.readmodel.ProjectionView;
import io.ddd4j.data.event.store.jpa.StoredEventEntity;
import io.ddd4j.data.projection.jpa.ProjectionPositionEntity;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.Configuration;
import org.postgresql.ds.PGSimpleDataSource;
import javax.sql.DataSource;
/** 显式 JDBC/ResourceLocal 装配；R09 独占原生 Runner、持久检查点及定时生命周期。 */
@Factory @Requires(property="ddd4j.cqrs.projection.enabled", value="true")
public class PersistentOrderFactory {
 @Singleton DataSource source(@Value("${sample.jdbc.url}") String url,
   @Value("${sample.jdbc.user}") String user,@Value("${sample.jdbc.password}") String password) {
  var source = new PGSimpleDataSource(); source.setUrl(url); source.setUser(user); source.setPassword(password); return source;
 }
 @Singleton @Bean(preDestroy="close") EntityManagerFactory entities(DataSource source,
   @Value("${sample.jpa.schema:validate}") String schema) {
  var configuration = new Configuration().addAnnotatedClass(StoredEventEntity.class).addAnnotatedClass(ProjectionPositionEntity.class);
  configuration.getProperties().put("hibernate.connection.datasource",source);
  configuration.setProperty("hibernate.hbm2ddl.auto",schema);
  return configuration.buildSessionFactory();
 }
 @Singleton JdbcOrderTransactionPort transaction(DataSource source) { return new JdbcOrderTransactionPort(source); }
 @Singleton JdbcOrderRepository repository(JdbcOrderTransactionPort transaction) { return new JdbcOrderRepository(transaction); }
 @Singleton OrderReadModelPort queries(JdbcOrderTransactionPort transaction) { return new JdbcOrderReadModelPort(transaction); }
 @Singleton OutboxPort outbox(JdbcOrderTransactionPort transaction,ObjectMapper mapper) {
  return new JdbcOutboxPort(transaction,mapper.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS));
 }
 @Singleton OrderApplicationService orders(JdbcOrderRepository repository,OutboxPort outbox,
   OrderReadModelPort queries,JdbcOrderTransactionPort transaction) {
  return new OrderApplicationService(repository,outbox,new DeferredOrderReadModel(queries),new InMemoryOrderAdapters(),transaction);
 }
 @Singleton OrderProjection projection(JdbcOrderRepository repository,OutboxPort outbox,OrderReadModelPort queries,
   JdbcOrderTransactionPort transaction,EntityManagerFactory factory,ObjectMapper mapper) {
  return new OrderProjection(repository,outbox,queries,transaction,factory,mapper);
 }
 @Singleton EventChunkReader<Object> reader(OrderProjection projection) { return projection.reader(); }
 @Singleton ProjectionPositionRepository positions(EntityManagerFactory factory) { return new PersistentPositions(factory); }
 @Singleton ProjectionView<Object> view(OrderProjection projection,@Value("${sample.projection.cron:0/5 * * * * *}") String cron) {
  var delegate = projection.view();
  return new ProjectionView<>() {
   @Override public String getName() { return delegate.getName(); }
   @Override public String getCron() { return cron; }
   @Override public java.util.Collection<String> getEventTypes() { return delegate.getEventTypes(); }
   @Override public void handleEvents(java.util.Collection<Object> events) { delegate.handleEvents(events); }
  };
 }
}
