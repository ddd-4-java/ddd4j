package io.ddd4j.sample.dropwizard.cqrs.persistent;
import io.dropwizard.lifecycle.Managed;
import io.ddd4j.dropwizard.Ddd4jBundle;
import io.ddd4j.dropwizard.command.DropwizardCommandBus;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.sample.dropwizard.cqrs.DropwizardCqrsConfiguration;
import io.ddd4j.sample.order.jdbc.JdbcOrderTransactionPort;
import io.ddd4j.sample.order.jdbc.JdbcOrderRepository;
import io.ddd4j.sample.order.jdbc.JdbcOrderReadModelPort;
import io.ddd4j.sample.order.jdbc.JdbcOutboxPort;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.data.event.store.jpa.StoredEventEntity;
import io.ddd4j.data.projection.jpa.ProjectionPositionEntity;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
/** 原生 Managed 存储所有者。先启动存储再启动 Bundle；反序停止保证 Runner 先结束。 */
@Slf4j
public final class PersistentOrders implements Managed {
 private final DataSource source;
 private EntityManagerFactory factory;
 private final JdbcOrderReadModelPort queries;
 private final OrderProjection projection;
 private final DropwizardCommandBus commands;
 private final Ddd4jBundle<DropwizardCqrsConfiguration> bundle;
 /** @param source 外部已迁移的专用订单数据库，数据源由调用方拥有 */
 public PersistentOrders(DataSource source) {
  this.source=Objects.requireNonNull(source);
  var tx=new JdbcOrderTransactionPort(source);var repo=new JdbcOrderRepository(tx);
  queries=new JdbcOrderReadModelPort(tx);
  var mapper=new ObjectMapper().findAndRegisterModules()
    .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
  var outbox=new JdbcOutboxPort(tx,mapper);
  var service=new OrderApplicationService(repo,outbox,new DeferredOrderReadModel(queries),new InMemoryOrderAdapters(),tx);
  projection=new OrderProjection(repo,outbox,queries,tx,()->factory,mapper);
  List<io.ddd4j.core.cqrs.command.CommandExecutor<?>> executors=List.of(new OrderCommandExecutor(service));
  commands=new DropwizardCommandBus(executors);
  bundle=new Ddd4jBundle<DropwizardCqrsConfiguration>(executors,List.of(),null,I18nProvider.DEFAULT)
   .withProjection(new PersistentPositions(()->factory),projection.reader(),List.of(projection.view()));
 }
 @Override public void start(){
  var config=new org.hibernate.cfg.Configuration().addAnnotatedClass(StoredEventEntity.class).addAnnotatedClass(ProjectionPositionEntity.class);
  config.getProperties().put("hibernate.connection.datasource",source);
  config.setProperty("hibernate.hbm2ddl.auto","validate");factory=config.buildSessionFactory();
  log.info("DROPWIZARD_CQRS_STORAGE_STARTED");
 }
 @Override public void stop(){if(Objects.nonNull(factory)){factory.close();log.info("DROPWIZARD_CQRS_STORAGE_CLOSED");}}
 /** @return Bundle 的显式持久投影装配 */
 public Ddd4jBundle<DropwizardCqrsConfiguration> bundle(){return bundle;}
 /** @return HTTP 协议资源，手动 trigger 与后台使用相同 Runner */
 public PersistentOrderResource resource(){return new PersistentOrderResource(commands,queries,()->bundle.runtime().orElseThrow().projectionManager().orElseThrow().triggerOnce());}
 /** @return 只读验收的持久源访问 */
 public OrderProjection projection(){return projection;}
 /** @return 本应用拥有的工厂，可验证关闭边界 */
 public EntityManagerFactory entities(){return factory;}
}
