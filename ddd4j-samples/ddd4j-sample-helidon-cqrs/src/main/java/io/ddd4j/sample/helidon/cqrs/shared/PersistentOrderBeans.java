package io.ddd4j.sample.helidon.cqrs.shared;
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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Disposes;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.Configuration;
import org.eclipse.microprofile.config.ConfigProvider;
import org.postgresql.ds.PGSimpleDataSource;
import javax.sql.DataSource;
/** 显式 JDBC/ResourceLocal 事务边界；R10 管理唯一持久 Runner 和调度器。
 */
@ApplicationScoped
public class PersistentOrderBeans {
 private final ObjectMapper persistenceMapper = new ObjectMapper().findAndRegisterModules()
     .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

 private String required(String key) { return ConfigProvider.getConfig().getValue(key,String.class); }
 @Produces @Singleton DataSource source() {
  var source=new PGSimpleDataSource();source.setUrl(required("sample.jdbc.url"));source.setUser(required("sample.jdbc.user"));source.setPassword(required("sample.jdbc.password"));return source;
 }
 @Produces @Singleton EntityManagerFactory entities(DataSource source) {
  var configuration=new Configuration().addAnnotatedClass(StoredEventEntity.class).addAnnotatedClass(ProjectionPositionEntity.class);
  configuration.getProperties().put("hibernate.connection.datasource",source);
  configuration.setProperty("hibernate.hbm2ddl.auto",ConfigProvider.getConfig().getOptionalValue("sample.jpa.schema",String.class).orElse("validate"));
  return configuration.buildSessionFactory();
 }
 void close(@Disposes EntityManagerFactory factory) { factory.close(); }
 @Produces @Singleton ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
 @Produces @Singleton JdbcOrderTransactionPort transaction(DataSource source) { return new JdbcOrderTransactionPort(source); }
 @Produces @Singleton JdbcOrderRepository repository(JdbcOrderTransactionPort transaction) { return new JdbcOrderRepository(transaction); }
 @Produces @Singleton OrderReadModelPort queries(JdbcOrderTransactionPort transaction) { return new JdbcOrderReadModelPort(transaction); }
 @Produces @Singleton OutboxPort outbox(JdbcOrderTransactionPort transaction) { return new JdbcOutboxPort(transaction,persistenceMapper); }
 @Produces @Singleton OrderApplicationService orders(JdbcOrderRepository repository,OutboxPort outbox,OrderReadModelPort queries,JdbcOrderTransactionPort transaction) {
  return new OrderApplicationService(repository,outbox,new DeferredOrderReadModel(queries),new InMemoryOrderAdapters(),transaction);
 }
 @Produces @Singleton OrderProjection projection(JdbcOrderRepository repository,OutboxPort outbox,OrderReadModelPort queries,JdbcOrderTransactionPort transaction,EntityManagerFactory factory) {
  return new OrderProjection(repository,outbox,queries,transaction,factory,persistenceMapper);
 }
 @Produces @ApplicationScoped EventChunkReader<Object> reader(OrderProjection projection) { return projection.reader(); }
 @Produces @ApplicationScoped ProjectionPositionRepository positions(EntityManagerFactory factory) { return new PersistentPositions(factory); }
 @Produces @Singleton ProjectionView<Object> view(OrderProjection projection) {
  var delegate=projection.view();String cron=ConfigProvider.getConfig().getOptionalValue("sample.projection.cron",String.class).orElse("0/5 * * * * *");
  return new ProjectionView<>() {
   @Override public String getName() { return delegate.getName(); }
   @Override public String getCron() { return cron; }
   @Override public java.util.Collection<String> getEventTypes() { return delegate.getEventTypes(); }
   @Override public void handleEvents(java.util.Collection<Object> events) { delegate.handleEvents(events); }
  };
 }
}
