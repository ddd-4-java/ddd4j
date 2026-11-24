package io.ddd4j.sample.vertx.cqrs.persistent;
import io.ddd4j.core.ddd.event.AggregateRootId;
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
import io.vertx.core.json.JsonObject;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
/** 原生 Vert.x + PostgreSQL：源游标、事务失败、重放和关闭重开。 */
class PersistentOrderHttpTest {
 private final HttpClient client=HttpClient.newHttpClient();
 private String base;
 private HttpResponse<String> request(String method,String path,String body) throws Exception {
  return client.send(HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(15))
   .header("Content-Type","application/json").header("Idempotency-Key","persistent-pay")
   .method(method,Objects.isNull(body)?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
 }
 @Test void sourceAwarePersistedOrderSurvivesFailureRetryAndReopen() throws Exception {
  String previous=System.getProperty("sample.projection.cron");System.setProperty("sample.projection.cron","0 0 0 1 1 *");
  try(var postgres=new PostgreSQLContainer<>("postgres:16-alpine").withTmpFs(Map.of("/var/lib/postgresql/data","rw,size=512m"))){
   postgres.start();var raw=new PGSimpleDataSource();raw.setUrl(postgres.getJdbcUrl());raw.setUser(postgres.getUsername());raw.setPassword(postgres.getPassword());
   Flyway.configure().dataSource(raw).locations("classpath:db/migration").load().migrate();
   var source=new FailingDataSource(raw);var app=PersistentOrders.start(0,source,"create").toCompletionStage().toCompletableFuture().get(30,TimeUnit.SECONDS);
   String id;
   String lockedOrderId;
   long finalPosition;
   try{
    base="http://127.0.0.1:"+app.port();
    var created=request("POST","/orders","{\"orderNo\":\"PERSIST-1\",\"buyerId\":\"buyer\",\"buyerName\":\"Alice\"}");
    assertEquals(201,created.statusCode(),created.body());id=new JsonObject(created.body()).getString("orderId");
    assertEquals("PERSIST-1",app.projection().persisted(id).orderNo(),"HTTP must write shared durable repository");
    assertEquals(404,request("GET","/orders/"+id,null).statusCode(),"no write-side query fallback");
    assertEquals(409,request("POST","/orders","{\"orderNo\":\"PERSIST-1\",\"buyerId\":\"buyer\",\"buyerName\":\"Alice\"}").statusCode());
    assertEquals(200,request("POST","/orders/"+id+"/lines","{\"goodsId\":\"g\",\"goodsName\":\"Book\",\"quantity\":2,\"unitPrice\":59.90}").statusCode());
    assertEquals(200,request("POST","/orders/"+id+"/pay",null).statusCode());
    source.failAck.set(true);
    assertThrows(RuntimeException.class,app.projection()::relay);
    assertEquals(0,app.projection().position());assertEquals(1,app.projection().events(id).size());
    app.projection().relay();assertEquals(3,app.projection().events(id).size(),"outbox IDs deduplicate committed event append");
    long sourceTail;
    try(var manager=app.entities().createEntityManager()){
     manager.getTransaction().begin();
     var tail=manager.createQuery("select e from StoredEventEntity e order by e.position desc",io.ddd4j.data.event.store.jpa.StoredEventEntity.class).setMaxResults(1).getSingleResult();
     sourceTail=tail.getPosition()+17;tail.setPosition(sourceTail);manager.getTransaction().commit();
    }
    source.failQuery.set(true);
    assertEquals(409,request("POST","/projections/orders/run",null).statusCode());assertEquals(0,app.projection().position());
    assertEquals(404,request("GET","/orders/"+id,null).statusCode());
    AtomicBoolean checkpointFailure=new AtomicBoolean(true);
    var registry=app.entities().unwrap(org.hibernate.engine.spi.SessionFactoryImplementor.class).getServiceRegistry().getService(org.hibernate.event.service.spi.EventListenerRegistry.class);
    registry.appendListeners(org.hibernate.event.spi.EventType.PRE_INSERT,(org.hibernate.event.spi.PreInsertEventListener) event->{
     if(event.getEntity() instanceof io.ddd4j.data.projection.jpa.ProjectionPositionEntity && checkpointFailure.getAndSet(false)){throw new IllegalStateException("checkpoint commit fails");}return false;
    });
    assertEquals(409,request("POST","/projections/orders/run",null).statusCode());assertEquals(0,app.projection().position());
    assertEquals(200,request("GET","/orders/"+id,null).statusCode(),"query commits before failed checkpoint");
    assertEquals(200,request("POST","/projections/orders/run",null).statusCode());finalPosition=app.projection().position();assertEquals(sourceTail+1,finalPosition);
    var read=new JsonObject(request("GET","/orders/"+id,null).body());assertEquals("PAID",read.getString("status"));assertEquals(5,read.size(),"old five-field HTTP contract");
    // 真数据库锁阻塞写操作时，原生事件循环健康请求仍有响应。
    try(var lock=raw.getConnection();var statement=lock.createStatement()){
     lock.setAutoCommit(false);statement.execute("LOCK TABLE sample_orders IN ACCESS EXCLUSIVE MODE");
     var held=client.sendAsync(HttpRequest.newBuilder(URI.create(base+"/orders")).header("Content-Type","application/json")
      .POST(HttpRequest.BodyPublishers.ofString("{\"orderNo\":\"LOCKED\",\"buyerId\":\"b\",\"buyerName\":\"Bob\"}")).build(),HttpResponse.BodyHandlers.ofString());
     long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
     while(!waiting && System.nanoTime()<deadline){
      try(var connection=raw.getConnection();var check=connection.createStatement();var rows=check.executeQuery("select count(*) from pg_stat_activity where wait_event_type = 'Lock' and datname = current_database()")){rows.next();waiting=rows.getInt(1)>0;}
      if(!waiting){Thread.sleep(10);}
     }
     assertTrue(waiting,"real write blocked on PostgreSQL lock");assertEquals(200,request("GET","/health",null).statusCode());
     lock.commit();
     HttpResponse<String> response = held.get(10, TimeUnit.SECONDS);
     assertEquals(201, response.statusCode());
     lockedOrderId = new JsonObject(response.body()).getString("orderId");
    }
   }finally{app.close().toCompletionStage().toCompletableFuture().get(30,TimeUnit.SECONDS);assertFalse(app.entities().isOpen());}
   var reopened=PersistentOrders.start(0,source,"validate").toCompletionStage().toCompletableFuture().get(30,TimeUnit.SECONDS);
   try{base="http://127.0.0.1:"+reopened.port();assertEquals(finalPosition,reopened.projection().position());assertEquals(3,reopened.projection().events(id).size());assertEquals("PAID",new JsonObject(request("GET","/orders/"+id,null).body()).getString("status"));
    // 上次锁释放后创建的订单仍待中继，先中继，再追加一个其他聚合事件形成混合页。
    reopened.projection().relay();
    long mixedTail = appendFixtureEvent(reopened.entities(), "Unrelated", "mixed-tail");
    assertEquals(200, request("POST", "/projections/orders/run", null).statusCode());
    assertEquals(mixedTail + 1, reopened.projection().position());
    assertEquals("LOCKED", new JsonObject(request("GET", "/orders/" + lockedOrderId, null).body()).getString("orderNo"));
    long foreignTail = appendFixtureEvent(reopened.entities(), "Unrelated", "foreign-only");
    assertEquals(200, request("POST", "/projections/orders/run", null).statusCode());
    assertEquals(foreignTail + 1, reopened.projection().position());
    java.util.NoSuchElementException missing = assertThrows(java.util.NoSuchElementException.class,
      () -> reopened.projection().persisted("missing-order"));
    assertEquals("No value present", missing.getMessage());
    appendFixtureEvent(reopened.entities(), "Order", "invalid-order");
    assertEquals(409, request("POST", "/projections/orders/run", null).statusCode());
    assertEquals(foreignTail + 1, reopened.projection().position());
   }
   finally{reopened.close().toCompletionStage().toCompletableFuture().get(30,TimeUnit.SECONDS);}
  }finally{if(Objects.isNull(previous)){System.clearProperty("sample.projection.cron");}else{System.setProperty("sample.projection.cron",previous);}}
 }
 @Test void failedListenReleasesFactoryBeforeCompletingTheFuture() throws Exception {
  org.hibernate.internal.SessionFactoryRegistry registry = org.hibernate.internal.SessionFactoryRegistry.INSTANCE;
  assertFalse(registry.hasRegistrations());
  try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
          .withTmpFs(Collections.singletonMap("/var/lib/postgresql/data", "rw,size=512m"));
       java.net.ServerSocket occupied = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
   postgres.start();
   PGSimpleDataSource source = new PGSimpleDataSource();
   source.setUrl(postgres.getJdbcUrl());
   source.setUser(postgres.getUsername());
   source.setPassword(postgres.getPassword());
   Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
   assertListenFailure(occupied.getLocalPort(), source, java.net.BindException.class);
   assertFalse(registry.hasRegistrations(), "bind failure closes its JPA factory");
   assertListenFailure(65536, source, IllegalArgumentException.class);
   assertFalse(registry.hasRegistrations(), "invalid port closes its JPA factory");
  }
 }

 private void assertListenFailure(int port, PGSimpleDataSource source, Class<? extends Throwable> failureType) {
  io.vertx.core.Future<PersistentOrders> startup = PersistentOrders.start(port, source, "create");
  java.util.concurrent.ExecutionException failure = assertThrows(java.util.concurrent.ExecutionException.class,
    () -> startup.toCompletionStage().toCompletableFuture().get(15, TimeUnit.SECONDS));
  Throwable cause = failure.getCause();
  while (Objects.nonNull(cause.getCause()) && !failureType.isInstance(cause)) {
   cause = cause.getCause();
  }
  assertTrue(failureType.isInstance(cause), cause.toString());
 }

 @Test void closeDrainsEnteredNativeTimerBeforeClosingJpaAndVertx() throws Exception {
  String previous=System.getProperty("sample.projection.cron");System.setProperty("sample.projection.cron","*/1 * * * * *");
  var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
  var interrupted=new AtomicBoolean();var armed=new AtomicBoolean(true);
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
        assertTrue(Thread.currentThread().getName().contains("worker"));entered.countDown();
        boolean waiting=true;while(waiting){try{release.await();waiting=false;}catch(InterruptedException error){interrupted.set(true);}}
       }
       return invoke(operation,connection,values);
      });
     }
     return result;
    });
   var app=PersistentOrders.start(0,holding,"create").toCompletionStage().toCompletableFuture().get(30,TimeUnit.SECONDS);
   try{
    base="http://127.0.0.1:"+app.port();
    assertEquals(201,request("POST","/orders","{\"orderNo\":\"TIMER-CLOSE\",\"buyerId\":\"buyer\",\"buyerName\":\"Timer\"}").statusCode());
    assertTrue(entered.await(8,TimeUnit.SECONDS),"real native timer has entered read-model persistence; no manual trigger");
    var closing=app.close().toCompletionStage().toCompletableFuture();
    assertThrows(java.util.concurrent.TimeoutException.class,()->closing.get(300,TimeUnit.MILLISECONDS),"close must wait for the native timer's whole Runner task");
    assertTrue(app.entities().isOpen(),"JPA factory must remain open while entered timer is held");
    try(var manager=app.entities().createEntityManager()){
     assertEquals(1L,manager.createQuery("select count(e) from StoredEventEntity e",Long.class).getSingleResult());
    }
    assertFalse(interrupted.get(),"close does not interrupt admitted persistence");
    release.countDown();closing.get(15,TimeUnit.SECONDS);assertFalse(app.entities().isOpen());
    try(var connection=raw.getConnection();var statement=connection.createStatement()){
     try(var rows=statement.executeQuery("select count(*) from sample_order_read_models where order_no='TIMER-CLOSE'")){rows.next();assertEquals(1,rows.getInt(1));}
     try(var rows=statement.executeQuery("select next_event_number from ddd4j_projection_position where stream_id='vertx-orders'")){assertTrue(rows.next());assertEquals(2,rows.getLong(1));}
    }
    System.out.println("FIX1_TIMER_CLOSE_ORDER=native-timer-entered,close-pending,JPA-usable,release,query-and-checkpoint-committed,JPA-closed");
   }finally{release.countDown();app.close().toCompletionStage().toCompletableFuture().get(15,TimeUnit.SECONDS);}
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
}
