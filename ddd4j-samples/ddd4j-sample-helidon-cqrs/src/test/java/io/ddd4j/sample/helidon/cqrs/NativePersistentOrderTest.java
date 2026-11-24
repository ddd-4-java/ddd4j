package io.ddd4j.sample.helidon.cqrs;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityId;
import io.ddd4j.core.ddd.event.StringEntityType;
import io.ddd4j.data.event.store.jpa.JpaEventStore;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.jdbc.JdbcOrderTransactionPort;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.NoSuchElementException;
import io.ddd4j.sample.helidon.cqrs.shared.OrderProjection;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.application.OrderReadModelPort;
import io.ddd4j.sample.order.application.OutboxMessage;
import io.ddd4j.sample.order.application.OutboxPort;
import io.ddd4j.sample.order.domain.OrderQuery;
import io.ddd4j.sample.order.testkit.HttpOrderSampleDriver;
import io.ddd4j.sample.order.testkit.CqrsOrderSampleDriver;
import io.ddd4j.sample.order.testkit.SampleContractAssertions;
import io.helidon.microprofile.server.Server;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
/** 真 PostgreSQL + 原生 Helidon 的独立读模型、源位置、失败恢复及关闭重开。 */
class NativePersistentOrderTest {
 
 
 @Test void persistentOrderRouteIsNative() {
  try (var postgres = new PostgreSQLContainer<>("postgres:16-alpine").withTmpFs(Map.of("/var/lib/postgresql/data","rw,size=512m"))) {
   postgres.start();
   var source = new PGSimpleDataSource();source.setUrl(postgres.getJdbcUrl());source.setUser(postgres.getUsername());source.setPassword(postgres.getPassword());
   Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
   Map<String,Object> properties = new HashMap<>(Map.of("ddd4j.cqrs.projection.enabled",true,
      "sample.jdbc.url",postgres.getJdbcUrl(),"sample.jdbc.user",postgres.getUsername(),"sample.jdbc.password",postgres.getPassword(),
      "sample.jpa.schema","create","sample.projection.cron","0 0 0 1 1 *"));
   String id;long position; EntityManagerFactory factory;
   properties.forEach((key,value)->System.setProperty(key,String.valueOf(value)));
   Server server=HelidonCqrsApplication.start(0);
   try {
    var context=CDI.current();factory=context.select(EntityManagerFactory.class).get();
    var projection=context.select(OrderProjection.class).get();var queries=context.select(OrderReadModelPort.class).get();
    Driver driver=new Driver(server,projection,queries);
    assertTrue(context.select(io.ddd4j.core.cqrs.readmodel.ViewManager.class).get().getClass().getName().contains("HelidonProjectionViewManager"));
    SampleContractAssertions.cqrsOrder(driver,"helidon-persisted");
    id=driver.create(new io.ddd4j.sample.order.application.CreateOrderCommand("helidon-recovery","buyer-1","Alice"));
    driver.addLine(new io.ddd4j.sample.order.application.AddOrderLineCommand(id,"goods-1","DDD Book",2,new java.math.BigDecimal("59.90")));
    driver.pay(id,"helidon-recovery-key");
    long previous=projection.position();
    FailureInjection.FAIL_ACK.set(true);
    assertThrows(IllegalStateException.class,projection::relay);
    assertEquals(previous,projection.position());
    assertEquals(1,projection.events(id).size(),"event commit survives Outbox acknowledgement rollback");
    projection.relay();
    assertEquals(3,projection.events(id).size(),"retry preserves IDs and does not append duplicates");
    long sourceTail;
    try(var manager=factory.createEntityManager()) {
     manager.getTransaction().begin();
     var tail=manager.createQuery("select e from StoredEventEntity e order by e.position desc",io.ddd4j.data.event.store.jpa.StoredEventEntity.class).setMaxResults(1).getSingleResult();
     sourceTail=tail.getPosition()+17;tail.setPosition(sourceTail);manager.getTransaction().commit();
    }
    AtomicBoolean checkpointFailure=new AtomicBoolean(true);
    var registry=factory.unwrap(org.hibernate.engine.spi.SessionFactoryImplementor.class).getServiceRegistry().getService(org.hibernate.event.service.spi.EventListenerRegistry.class);
    registry.appendListeners(org.hibernate.event.spi.EventType.PRE_UPDATE,(org.hibernate.event.spi.PreUpdateEventListener) event -> {
     if (event.getEntity() instanceof io.ddd4j.data.projection.jpa.ProjectionPositionEntity && checkpointFailure.getAndSet(false)) { throw new IllegalStateException("after query commit before checkpoint commit"); }
     return false;
    });
    assertThrows(IllegalStateException.class,driver::project);
    assertEquals(previous,projection.position());
    assertTrue(queries.findProjectionById(id).isPresent(),"query commit survives checkpoint rollback");
    driver.project();position=projection.position();assertEquals(sourceTail+1,position);
   }
   finally { server.stop(); }
   assertFalse(factory.isOpen(),"context close owns JPA factory");
   properties.put("sample.jpa.schema","validate");
   System.setProperty("sample.jpa.schema","validate");
   server=HelidonCqrsApplication.start(0);
   try {
    var projection=CDI.current().select(OrderProjection.class).get();
    assertEquals(position,projection.position());assertEquals(3,projection.events(id).size());
    var driver=new Driver(server,projection,CDI.current().select(OrderReadModelPort.class).get());
    assertEquals(0,new java.math.BigDecimal("119.80").compareTo(driver.find(id).totalAmount()));
    driver.project();assertEquals(position,projection.position());
    assertAll(
        () -> assertMissingWriteModelCanBeRetried(driver),
        () -> assertProjectionFiltersByAggregateType(driver, CDI.current().select(EntityManagerFactory.class).get()));
   } finally { server.stop();properties.keySet().forEach(System::clearProperty); }
  }
 }
 private void assertMissingWriteModelCanBeRetried(Driver driver) {
  String id = driver.create(new CreateOrderCommand("helidon-missing-write", "buyer-1", "Alice"));
  String displacedId = id + "-fixture";
  long previous = driver.position();
  JdbcOrderTransactionPort transaction = CDI.current().select(JdbcOrderTransactionPort.class).get();
  changeOrderId(transaction, id, displacedId);
  NoSuchElementException failure;
  try {
   failure = assertThrows(NoSuchElementException.class, () -> driver.persisted(id));
   IllegalStateException triggerFailure = assertThrows(IllegalStateException.class, driver::project);
   assertTrue(triggerFailure.getMessage().startsWith("HTTP 404"), triggerFailure.getMessage());
   assertTrue(triggerFailure.getMessage().contains("No value present"), triggerFailure.getMessage());
   assertEquals(previous, driver.position());
   assertFalse(driver.projection(id).isPresent());
   IllegalStateException readFailure = assertThrows(IllegalStateException.class, () -> driver.find(id));
   assertTrue(readFailure.getMessage().startsWith("HTTP 404"), readFailure.getMessage());
  } finally {
   changeOrderId(transaction, displacedId, id);
  }
  driver.project();
  assertTrue(driver.position() > previous);
  assertEquals(id, driver.find(id).id());
  assertEquals("No value present", failure.getMessage());
 }

 private void changeOrderId(JdbcOrderTransactionPort transaction, String original, String replacement) {
  transaction.execute(() -> {
   try (PreparedStatement statement = transaction.connection().prepareStatement(
       "UPDATE sample_orders SET id = ? WHERE id = ?")) {
    statement.setString(1, replacement);
    statement.setString(2, original);
    assertEquals(1, statement.executeUpdate());
   } catch (SQLException failure) {
    throw new IllegalStateException("Unable to move the test order", failure);
   }
  });
 }

 private void assertProjectionFiltersByAggregateType(Driver driver, EntityManagerFactory factory) {
  long foreignTail = appendFixtureEvent(factory, "Unrelated", "foreign-only");
  driver.project();
  assertEquals(foreignTail + 1, driver.position());

  String id = driver.create(new CreateOrderCommand("helidon-mixed-source", "buyer-1", "Alice"));
  driver.projection.relay();
  long mixedTail = appendFixtureEvent(factory, "Unrelated", "foreign-after-order");
  driver.project();
  assertEquals(id, driver.find(id).id());
  assertEquals(mixedTail + 1, driver.position());

  appendFixtureEvent(factory, "Order", "unsupported-order-envelope");
  IllegalStateException failure = assertThrows(IllegalStateException.class, driver::project);
  assertTrue(failure.getMessage().startsWith("HTTP 500"), failure.getMessage());
  assertEquals(mixedTail + 1, driver.position());
 }

 private long appendFixtureEvent(EntityManagerFactory factory, String type, String value) {
  AggregateRootId id = new AggregateRootId() {
   @Override public EntityType getType() { return new StringEntityType(type); }
   @Override public String asString() { return value; }
   @Override public String asTypedString() { return type + ":" + value; }
  };
  try (var manager = factory.createEntityManager()) {
   JpaEventStore store = new JpaEventStore(manager);
   store.append(type, id, Collections.singletonList(new ForeignFixtureEvent()), 0);
   return store.read(type, id).get(0).position();
  }
 }

 /** 可由事件库重新读取的夹具，用来区分聚合类型和载荷类型。 */
 public static final class ForeignFixtureEvent extends DomainEvent<StringEntityId> {
  private static final long serialVersionUID = 1L;

  public ForeignFixtureEvent() {
   super("fixture");
  }
 }

 static class Driver extends HttpOrderSampleDriver implements CqrsOrderSampleDriver {
  final OrderProjection projection;final OrderReadModelPort queries;
  Driver(Server server,OrderProjection projection,OrderReadModelPort queries) { super("http://127.0.0.1:"+server.port()+"/sample");this.projection=projection;this.queries=queries; }
  @Override public OrderReadModel persisted(String id) { return projection.persisted(id); }
  @Override public List<OutboxMessage> events(String id) { projection.relay();return projection.events(id); }
  @Override public Optional<OrderReadModel> projection(String id) { return queries.findProjectionById(id); }
  @Override public long position() { return projection.position(); }
  @Override public void failNextProjection() { FailureInjection.FAIL_QUERY.set(true); }
  @Override public void project() { request("POST","/projections/orders/run",null,String.class); }
 }
}
