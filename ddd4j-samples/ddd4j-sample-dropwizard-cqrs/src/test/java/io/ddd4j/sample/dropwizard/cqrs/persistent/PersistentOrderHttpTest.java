package io.ddd4j.sample.dropwizard.cqrs.persistent;
import io.ddd4j.core.ddd.event.AggregateRootId;
import io.ddd4j.sample.dropwizard.cqrs.DropwizardCqrsApplication;
import io.ddd4j.sample.dropwizard.cqrs.DropwizardCqrsConfiguration;
import io.dropwizard.core.server.DefaultServerFactory;
import io.dropwizard.testing.DropwizardTestSupport;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.EntityType;
import io.ddd4j.core.ddd.event.StringEntityId;
import io.ddd4j.core.ddd.event.StringEntityType;
import io.ddd4j.data.event.store.jpa.JpaEventStore;
import java.util.Collections;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import java.util.Objects;
import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
/** 原生 Dropwizard + PostgreSQL：源游标、事务失败、重放和关闭重开。 */
class PersistentOrderHttpTest {
 private final ObjectMapper mapper=new ObjectMapper();
 private io.ddd4j.sample.dropwizard.cqrs.persistent.PersistentOrders persistent;
 private io.ddd4j.sample.dropwizard.cqrs.DropwizardCqrsApplication lastApplication;
 private DropwizardTestSupport<DropwizardCqrsConfiguration> start(javax.sql.DataSource source) throws Exception {
  return start(new DropwizardCqrsApplication(source), new DropwizardCqrsConfiguration());
 }

 private DropwizardTestSupport<DropwizardCqrsConfiguration> start(
         DropwizardCqrsApplication productApp, DropwizardCqrsConfiguration config) throws Exception {
  DefaultServerFactory server = (DefaultServerFactory) config.getServerFactory();
  ((io.dropwizard.jetty.HttpConnectorFactory) server.getApplicationConnectors().get(0)).setPort(0);
  ((io.dropwizard.jetty.HttpConnectorFactory) server.getAdminConnectors().get(0)).setPort(0);
  lastApplication = productApp;
  DropwizardTestSupport<DropwizardCqrsConfiguration> support =
          new DropwizardTestSupport<DropwizardCqrsConfiguration>(DropwizardCqrsApplication.class, config) {
   @Override
   public io.dropwizard.core.Application<DropwizardCqrsConfiguration> newApplication() {
    return productApp;
   }
  };
  support.before();
  persistent = productApp.persistent();
  return support;
 }

 private void prepareEntities(javax.sql.DataSource source){
  var config=new org.hibernate.cfg.Configuration().addAnnotatedClass(io.ddd4j.data.event.store.jpa.StoredEventEntity.class).addAnnotatedClass(io.ddd4j.data.projection.jpa.ProjectionPositionEntity.class);
  config.getProperties().put("hibernate.connection.datasource",source);config.setProperty("hibernate.hbm2ddl.auto","create");config.buildSessionFactory().close();
 }

 private final HttpClient client=HttpClient.newHttpClient();
 private String base;
 private HttpResponse<String> request(String method,String path,String body) throws Exception {
  return client.send(HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(15))
   .header("Content-Type","application/json").header("Idempotency-Key","persistent-pay")
   .method(method,Objects.isNull(body)?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
 }

 @Test
 void reusedApplicationDropsDurableRoutesWhenRestartedWithoutJdbc() throws Exception {
  DropwizardCqrsApplication productApp = new DropwizardCqrsApplication();
  try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
          .withTmpFs(Collections.singletonMap("/var/lib/postgresql/data", "rw,size=512m"))) {
   postgres.start();
   PGSimpleDataSource source = new PGSimpleDataSource();
   source.setUrl(postgres.getJdbcUrl());
   source.setUser(postgres.getUsername());
   source.setPassword(postgres.getPassword());
   Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
   prepareEntities(source);
   DropwizardCqrsConfiguration durableConfig = new DropwizardCqrsConfiguration();
   durableConfig.setJdbcUrl(postgres.getJdbcUrl());
   durableConfig.setJdbcUser(postgres.getUsername());
   durableConfig.setJdbcPassword(postgres.getPassword());
   DropwizardTestSupport<DropwizardCqrsConfiguration> durable = start(productApp, durableConfig);
   PersistentOrders durableOrders = productApp.persistent();
   try {
    base = "http://127.0.0.1:" + durable.getLocalPort();
    assertEquals(200, request("OPTIONS", "/sample/orders", null).statusCode());
    assertTrue(durableOrders.entities().isOpen());
   } finally {
    durable.after();
   }
   assertFalse(durableOrders.entities().isOpen());

   DropwizardCqrsConfiguration memoryConfig = new DropwizardCqrsConfiguration();
   memoryConfig.setJdbcUrl(null);
   DropwizardTestSupport<DropwizardCqrsConfiguration> memory = start(productApp, memoryConfig);
   try {
    base = "http://127.0.0.1:" + memory.getLocalPort();
    assertEquals(404, request("OPTIONS", "/sample/orders", null).statusCode());
    assertEquals(404, request("OPTIONS", "/sample/projections/orders/run", null).statusCode());
    assertNull(productApp.persistent());
    assertEquals(201, request("POST", "/orders",
            "{\"orderNo\":\"MEMORY-RESTART-1\",\"buyerId\":\"buyer\",\"buyerName\":\"Alice\"}").statusCode());
   } finally {
    memory.after();
   }
  }
 }

 @Test void sourceAwarePersistedOrderSurvivesFailureRetryAndReopen() throws Exception {
  String previous=System.getProperty("sample.projection.cron");System.setProperty("sample.projection.cron","0 0 0 1 1 *");
  try(var postgres=new PostgreSQLContainer<>("postgres:16-alpine").withTmpFs(Map.of("/var/lib/postgresql/data","rw,size=512m"))){
   postgres.start();var raw=new PGSimpleDataSource();raw.setUrl(postgres.getJdbcUrl());raw.setUser(postgres.getUsername());raw.setPassword(postgres.getPassword());
   Flyway.configure().dataSource(raw).locations("classpath:db/migration").load().migrate();
   prepareEntities(raw);var source=new FailingDataSource(raw);var app=start(source);
   String id;String lockedOrderId;long finalPosition;
   try{
    base="http://127.0.0.1:"+app.getLocalPort();
    var created=request("POST","/sample/orders","{\"orderNo\":\"PERSIST-1\",\"buyerId\":\"buyer\",\"buyerName\":\"Alice\"}");
    assertEquals(201,created.statusCode(),created.body());id=mapper.readTree(created.body()).path("orderId").asText();
    assertEquals("PERSIST-1",persistent.projection().persisted(id).orderNo(),"HTTP must write shared durable repository");
    assertEquals(404,request("GET","/sample/orders/"+id,null).statusCode(),"no write-side query fallback");
    assertEquals(409,request("POST","/sample/orders","{\"orderNo\":\"PERSIST-1\",\"buyerId\":\"buyer\",\"buyerName\":\"Alice\"}").statusCode());
    assertEquals(200,request("POST","/sample/orders/"+id+"/lines","{\"goodsId\":\"g\",\"goodsName\":\"Book\",\"quantity\":2,\"unitPrice\":59.90}").statusCode());
    assertEquals(200,request("POST","/sample/orders/"+id+"/pay",null).statusCode());
    source.failAck.set(true);
    assertThrows(RuntimeException.class,persistent.projection()::relay);
    assertEquals(0,persistent.projection().position());assertEquals(1,persistent.projection().events(id).size());
    persistent.projection().relay();assertEquals(3,persistent.projection().events(id).size(),"outbox IDs deduplicate committed event append");
    long sourceTail;
    try(var manager=persistent.entities().createEntityManager()){
     manager.getTransaction().begin();
     var tail=manager.createQuery("select e from StoredEventEntity e order by e.position desc",io.ddd4j.data.event.store.jpa.StoredEventEntity.class).setMaxResults(1).getSingleResult();
     sourceTail=tail.getPosition()+17;tail.setPosition(sourceTail);manager.getTransaction().commit();
    }
    source.failQuery.set(true);
    assertEquals(409,request("POST","/sample/projections/orders/run",null).statusCode());assertEquals(0,persistent.projection().position());
    assertEquals(404,request("GET","/sample/orders/"+id,null).statusCode());
    AtomicBoolean checkpointFailure=new AtomicBoolean(true);
    var registry=persistent.entities().unwrap(org.hibernate.engine.spi.SessionFactoryImplementor.class).getServiceRegistry().getService(org.hibernate.event.service.spi.EventListenerRegistry.class);
    registry.appendListeners(org.hibernate.event.spi.EventType.PRE_INSERT,(org.hibernate.event.spi.PreInsertEventListener) event->{
     if(event.getEntity() instanceof io.ddd4j.data.projection.jpa.ProjectionPositionEntity && checkpointFailure.getAndSet(false)){throw new IllegalStateException("checkpoint commit fails");}return false;
    });
    assertEquals(409,request("POST","/sample/projections/orders/run",null).statusCode());assertEquals(0,persistent.projection().position());
    assertEquals(200,request("GET","/sample/orders/"+id,null).statusCode(),"query commits before failed checkpoint");
    assertEquals(200,request("POST","/sample/projections/orders/run",null).statusCode());finalPosition=persistent.projection().position();assertEquals(sourceTail+1,finalPosition);
    var read=mapper.readTree(request("GET","/sample/orders/"+id,null).body());assertEquals("PAID",read.path("status").asText());assertEquals(6,read.size(),"shared six-field persistent contract");
    // 真实 Jetty graceful shutdown 在已进入 JDBC 的请求完成前保留工厂。
    try(var lock=raw.getConnection();var statement=lock.createStatement()){
     lock.setAutoCommit(false);statement.execute("LOCK TABLE sample_orders IN ACCESS EXCLUSIVE MODE");
     var held=client.sendAsync(HttpRequest.newBuilder(URI.create(base+"/sample/orders")).header("Content-Type","application/json")
      .POST(HttpRequest.BodyPublishers.ofString("{\"orderNo\":\"LOCKED\",\"buyerId\":\"b\",\"buyerName\":\"Bob\"}")).build(),HttpResponse.BodyHandlers.ofString());
     long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
     while(!waiting && System.nanoTime()<deadline){
      try(var connection=raw.getConnection();var check=connection.createStatement();var rows=check.executeQuery("select count(*) from pg_stat_activity where wait_event_type = 'Lock' and datname = current_database()")){rows.next();waiting=rows.getInt(1)>0;}
      if(!waiting){Thread.sleep(10);}
     }
     assertTrue(waiting,"real write blocked on PostgreSQL lock");
     var closing=java.util.concurrent.CompletableFuture.runAsync(app::after);
     assertThrows(java.util.concurrent.TimeoutException.class,()->closing.get(300,TimeUnit.MILLISECONDS));
     assertTrue(persistent.entities().isOpen(),"native graceful boundary retains JPA for active HTTP");
     lock.commit();assertEquals(201,held.get(10,TimeUnit.SECONDS).statusCode());lockedOrderId=mapper.readTree(held.get().body()).path("orderId").asText();closing.get(15,TimeUnit.SECONDS);
     assertFalse(persistent.entities().isOpen());
    }
   }finally{app.after();assertFalse(persistent.entities().isOpen());}
   var reopened=start(source);
   try{base="http://127.0.0.1:"+reopened.getLocalPort();assertEquals(finalPosition,persistent.projection().position());assertEquals(3,persistent.projection().events(id).size());assertEquals("PAID",mapper.readTree(request("GET","/sample/orders/"+id,null).body()).path("status").asText());
    persistent.projection().relay();
    long mixed = appendFixtureEvent(persistent.entities(), "Unrelated", "mixed");
    assertEquals(200, request("POST", "/sample/projections/orders/run", null).statusCode());
    assertEquals(mixed + 1, persistent.projection().position());
    assertEquals("LOCKED", mapper.readTree(request("GET", "/sample/orders/" + lockedOrderId, null).body()).path("orderNo").asText());
    long foreign = appendFixtureEvent(persistent.entities(), "Unrelated", "foreign");
    assertEquals(200, request("POST", "/sample/projections/orders/run", null).statusCode());
    assertEquals(foreign + 1, persistent.projection().position());
    java.util.NoSuchElementException missing = assertThrows(java.util.NoSuchElementException.class,
      () -> persistent.projection().persisted("missing-order"));
    assertEquals("No value present", missing.getMessage());
    appendFixtureEvent(persistent.entities(), "Order", "invalid-order");
    assertEquals(500, request("POST", "/sample/projections/orders/run", null).statusCode());
    assertEquals(foreign + 1, persistent.projection().position());
   }
   finally{reopened.after();}
  }finally{if(Objects.isNull(previous)){System.clearProperty("sample.projection.cron");}else{System.setProperty("sample.projection.cron",previous);}}
 }

 @Test void nativeManagedStopsEnteredRunnerBeforeClosingJpa() throws Exception {
  String previous=System.getProperty("sample.projection.cron");System.setProperty("sample.projection.cron","*/1 * * * * *");
  var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
  var interrupted=new java.util.concurrent.CountDownLatch(1);var armed=new AtomicBoolean(true);
  try(var postgres=new PostgreSQLContainer<>("postgres:16-alpine").withTmpFs(Map.of("/var/lib/postgresql/data","rw,size=512m"))){
   postgres.start();var raw=new PGSimpleDataSource();raw.setUrl(postgres.getJdbcUrl());raw.setUser(postgres.getUsername());raw.setPassword(postgres.getPassword());
   Flyway.configure().dataSource(raw).locations("classpath:db/migration").load().migrate();
   javax.sql.DataSource holding=(javax.sql.DataSource)java.lang.reflect.Proxy.newProxyInstance(
    javax.sql.DataSource.class.getClassLoader(),new Class<?>[]{javax.sql.DataSource.class},(proxy,method,args)->{
     Object result=invoke(method,raw,args);
     if(result instanceof java.sql.Connection){
      var connection=(java.sql.Connection)result;
      return java.lang.reflect.Proxy.newProxyInstance(java.sql.Connection.class.getClassLoader(),new Class<?>[]{java.sql.Connection.class},(connectionProxy,operation,values)->{
       if("prepareStatement".equals(operation.getName()) && values[0] instanceof String
         && ((String)values[0]).startsWith("INSERT INTO sample_order_read_models") && armed.getAndSet(false)){
        assertTrue(Thread.currentThread().getName().contains("projection"));entered.countDown();
        boolean waiting=true;while(waiting){try{release.await();waiting=false;}catch(InterruptedException error){interrupted.countDown();}}
       }
       return invoke(operation,connection,values);
      });
     }
     return result;
    });
   prepareEntities(raw);var app=start(holding);
   try{
    base="http://127.0.0.1:"+app.getLocalPort();
    assertEquals(201,request("POST","/sample/orders","{\"orderNo\":\"TIMER-CLOSE\",\"buyerId\":\"buyer\",\"buyerName\":\"Timer\"}").statusCode());
    assertTrue(entered.await(8,TimeUnit.SECONDS),"real native timer has entered read-model persistence; no manual trigger");
    var closing=java.util.concurrent.CompletableFuture.runAsync(app::after);
    assertThrows(java.util.concurrent.TimeoutException.class,()->closing.get(300,TimeUnit.MILLISECONDS),"close must wait for the native timer's whole Runner task");
    assertTrue(interrupted.await(5,TimeUnit.SECONDS),"R12 owned scheduler interrupts entered work and waits for actual termination");
    assertTrue(persistent.entities().isOpen(),"JPA factory must remain open while entered timer is held");
    try(var manager=persistent.entities().createEntityManager()){
     assertEquals(1L,manager.createQuery("select count(e) from StoredEventEntity e",Long.class).getSingleResult());
    }
    release.countDown();closing.get(15,TimeUnit.SECONDS);assertFalse(persistent.entities().isOpen());
    try(var connection=raw.getConnection();var statement=connection.createStatement()){
     try(var rows=statement.executeQuery("select count(*) from sample_order_read_models where order_no='TIMER-CLOSE'")){rows.next();assertEquals(1,rows.getInt(1));}
     try(var rows=statement.executeQuery("select next_event_number from ddd4j_projection_position where stream_id='dropwizard-orders'")){assertTrue(rows.next());assertEquals(2,rows.getLong(1));}
    }
    System.out.println("FIX1_TIMER_CLOSE_ORDER=native-timer-entered,close-pending,JPA-usable,release,query-and-checkpoint-committed,JPA-closed");
   }finally{release.countDown();java.util.concurrent.CompletableFuture.runAsync(app::after).get(15,TimeUnit.SECONDS);}
  }finally{if(Objects.isNull(previous)){System.clearProperty("sample.projection.cron");}else{System.setProperty("sample.projection.cron",previous);}}
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

 private static Object invoke(java.lang.reflect.Method method,Object target,Object[] args) throws Throwable {
  try{return method.invoke(target,args);}catch(java.lang.reflect.InvocationTargetException error){throw error.getCause();}
 }
 @Test void invalidProjectionCronRollsBackNativeStartupAndClosesFactory() throws Exception {
  String previous=System.getProperty("sample.projection.cron");System.setProperty("sample.projection.cron","invalid");
  var prior=io.ddd4j.core.util.SubjectKit.subjectProvider;
  try(var postgres=new PostgreSQLContainer<>("postgres:16-alpine").withTmpFs(Map.of("/var/lib/postgresql/data","rw,size=512m"))){
   postgres.start();var raw=new PGSimpleDataSource();raw.setUrl(postgres.getJdbcUrl());raw.setUser(postgres.getUsername());raw.setPassword(postgres.getPassword());
   Flyway.configure().dataSource(raw).locations("classpath:db/migration").load().migrate();prepareEntities(raw);
   assertThrows(Exception.class,()->start(raw));
   assertFalse(lastApplication.persistent().entities().isOpen(),"partially started native server closes owned JPA");
   org.junit.jupiter.api.Assertions.assertSame(prior,io.ddd4j.core.util.SubjectKit.subjectProvider);
  } finally {if(Objects.isNull(previous)){System.clearProperty("sample.projection.cron");}else{System.setProperty("sample.projection.cron",previous);}}
 }
}
