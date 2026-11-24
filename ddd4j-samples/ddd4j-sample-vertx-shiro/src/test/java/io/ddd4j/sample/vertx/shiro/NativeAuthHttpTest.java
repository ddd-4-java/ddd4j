package io.ddd4j.sample.vertx.shiro;
import org.junit.jupiter.api.Test;
import java.util.Objects;
import java.util.Collections;
import java.net.URI;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import io.vertx.core.json.JsonObject;
import io.ddd4j.core.context.ThreadContext;
import io.ddd4j.sample.order.application.OrderApplicationService;
import io.ddd4j.sample.order.application.OrderReadModel;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.local.InMemoryOrderAdapters;
import io.ddd4j.sample.order.domain.Order;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
/** 实际启动原生 HTTP/凭证/权限/退出及工作线程清理；不创建 Subject 替身。 */
class NativeAuthHttpTest {
 private final HttpClient client=HttpClient.newHttpClient();
 private final Map<String,Boolean> completions=new ConcurrentHashMap<>();
 private String base;
 private HttpResponse<String> request(String method, String path, String token, String body, String id) throws Exception {
  Map<String, String> credentials = Objects.isNull(token) ? Collections.emptyMap()
          : Collections.singletonMap("Authorization", "Bearer " + token);
  return requestWithHeaders(method, path, credentials, body, id);
 }
 private HttpResponse<String> requestWithHeaders(String method, String path, Map<String, String> credentials,
         String body, String id) throws Exception {
  var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10))
          .header("X-Request-Id", id);
  credentials.forEach(request::header);
  if (Objects.nonNull(body)) {
   request.header("Content-Type", "application/json");
  }
  return client.send(request.method(method, Objects.isNull(body) ? HttpRequest.BodyPublishers.noBody()
          : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
 }
 private String login(String user) throws Exception {
  var response=request("POST","/auth/login",null,"{\"username\":\""+user+"\",\"password\":\"sample-password\"}","login-"+user);
  assertEquals(200,response.statusCode(),response.body());return new JsonObject(response.body()).getString("token");
 }
 @Test void nativeCredentialsPermissionsLogoutAndBusinessFailure() throws Exception {
  var memory=new InMemoryOrderAdapters();
  var app=VertxShiroApplication.start(0,new OrderApplicationService(memory,memory,memory,memory,memory),completions::put).toCompletionStage().toCompletableFuture().get(15,TimeUnit.SECONDS);
  base="http://127.0.0.1:"+app.port();
  try {
   assertEquals(401,request("POST","/auth/login",null,"{\"username\":\"alice\",\"password\":\"bad\"}","bad-login").statusCode());
   assertEquals(401,request("POST","/auth/login",null,"{\"password\":\"sample-password\"}","missing-user").statusCode(),"login without username");
   assertEquals(401,request("POST","/auth/login",null,"{\"username\":\"alice\"}","missing-password").statusCode(),"login without password");
   assertEquals(401,request("POST","/orders",null,"{}","anonymous").statusCode());
   String reader=login("reader");
   var denied=request("POST","/orders",reader,"{\"orderNo\":\"denied\",\"buyerId\":\"b\",\"buyerName\":\"Reader\"}","denied");
   assertEquals(403,denied.statusCode(),denied.body());
   assertTrue(memory.findByOrderNo("denied").isEmpty(),"deny before persistence");
   String token=login("alice");
   var created=request("POST","/orders",token,"{\"orderNo\":\"AUTH-1\",\"buyerId\":\"b\",\"buyerName\":\"Alice\"}","create");
   assertEquals(201,created.statusCode(),created.body());String id=new JsonObject(created.body()).getString("id");
   assertEquals(200,request("GET","/orders/"+id,reader,null,"reader-get").statusCode());
   var error=request("POST","/orders/"+id+"/lines",token,"{\"goodsId\":\"g\",\"goodsName\":\"Book\",\"quantity\":0,\"unitPrice\":5}","business-error");
   assertTrue(error.statusCode()>=400,error.body());
   assertEquals(200,request("POST","/auth/logout",token,null,"logout").statusCode());
   assertEquals(401,request("GET","/orders/"+id,token,null,"expired").statusCode());
   for(String key:java.util.List.of("bad-login","missing-user","missing-password","anonymous","denied","create","reader-get","business-error","logout","expired")) {
    assertEquals(Boolean.TRUE,completions.get(key),"passive completion "+key);
   }
  }finally{app.close().toCompletionStage().toCompletableFuture().get(15,TimeUnit.SECONDS);}
 }
 @Test void bearerSchemeCaseDoesNotChangeOrderAuthorization() throws Exception {
  InMemoryOrderAdapters memory = new InMemoryOrderAdapters();
  AtomicReference<String> principal = new AtomicReference<>();
  OrderApplicationService orders = new OrderApplicationService(memory, memory, memory, memory, memory) {
   @Override public OrderReadModel find(String orderId) {
    principal.set(ThreadContext.getSubject().getPrincipal().getLoginId().toString());
    return super.find(orderId);
   }
  };
  VertxShiroApplication app = VertxShiroApplication.start(0, orders, completions::put)
          .toCompletionStage().toCompletableFuture().get(15, TimeUnit.SECONDS);
  base = "http://127.0.0.1:" + app.port();
  try {
   String writer = login("alice");
   HttpResponse<String> created = request("POST", "/orders", writer,
           "{\"orderNo\":\"BEARER-SCHEME\",\"buyerId\":\"b\",\"buyerName\":\"Alice\"}", "bearer-create");
   assertEquals(201, created.statusCode(), created.body());
   String id = new JsonObject(created.body()).getString("id");
   String orderPath = "/orders/" + id;
   String reader = login("reader");
   HttpResponse<String> canonical = request("GET", orderPath, reader, null, "canonical-bearer");
   assertEquals(200, canonical.statusCode(), canonical.body());
   JsonObject expected = new JsonObject(canonical.body());
   assertEquals(id, expected.getString("id"));
   assertEquals("reader", principal.get(), "canonical Bearer principal");
   assertAll("authorization schemes for the same order and reader",
           () -> assertReaderOrder(expected, orderPath,
                   Collections.singletonMap("Authorization", "bearer " + reader), "lowercase-bearer", principal),
           () -> assertReaderOrder(expected, orderPath,
                   Collections.singletonMap("Authorization", "bEaReR " + reader), "mixed-case-bearer", principal),
           () -> assertReaderOrder(expected, orderPath,
                   Collections.singletonMap("Authorization", "Bearer  " + reader), "two-bearer-spaces", principal),
           () -> assertReaderOrder(expected, orderPath,
                   Collections.singletonMap("Authorization", "bEaReR    " + reader), "four-bearer-spaces", principal),
           () -> assertEquals(401, requestWithHeaders("GET", orderPath,
                   Collections.singletonMap("Authorization", "Bearer  invalid-token"), null, "invalid-token").statusCode(),
                   "invalid token after two spaces"),
           () -> assertEquals(401, requestWithHeaders("GET", orderPath,
                   Collections.singletonMap("Authorization", "BearerX " + reader), null, "wrong-scheme").statusCode(), "BearerX"),
           () -> assertEquals(401, request("GET", orderPath, null, null, "no-credentials").statusCode(), "anonymous"));
  } finally {
   app.close().toCompletionStage().toCompletableFuture().get(15, TimeUnit.SECONDS);
  }
 }

 private void assertReaderOrder(JsonObject expected, String path, Map<String, String> credentials, String id,
         AtomicReference<String> principal) throws Exception {
  principal.set(null);
  HttpResponse<String> actual = requestWithHeaders("GET", path, credentials, null, id);
  assertEquals(200, actual.statusCode(), id);
  assertEquals(expected, new JsonObject(actual.body()), id);
  assertEquals("reader", principal.get(), id);
 }

 @Test void disconnectWaitsForActualWorkerCompletionAndCloseDrainsIt() throws Exception {
  var memory=new InMemoryOrderAdapters();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var finished=new CountDownLatch(1);
  var service=new OrderApplicationService(memory,memory,memory,memory,memory){
   @Override public Order create(CreateOrderCommand command){
    assertTrue(Thread.currentThread().getName().contains("worker"));
    assertTrue(io.ddd4j.core.util.SubjectKit.getSubject().isAuthenticated());
    assertTrue(Objects.nonNull(org.apache.shiro.util.ThreadContext.getSecurityManager()),
            "authenticated worker owns its request manager");
    entered.countDown();try{assertTrue(release.await(10,TimeUnit.SECONDS));}catch(InterruptedException error){throw new IllegalStateException(error);}
    return super.create(command);
   }
  };
  var app=VertxShiroApplication.start(0,service,(id,clean)->{completions.put(id,clean);if("disconnected".equals(id)){finished.countDown();}}).toCompletionStage().toCompletableFuture().get(15,TimeUnit.SECONDS);
  base="http://127.0.0.1:"+app.port();
  try{
   String token=login("alice");String body="{\"orderNo\":\"HELD\",\"buyerId\":\"b\",\"buyerName\":\"Alice\"}";
   try(var socket=new Socket("127.0.0.1",app.port())){
    socket.getOutputStream().write(("POST /orders HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer "+token+"\r\nX-Request-Id: disconnected\r\nContent-Type: application/json\r\nContent-Length: "+body.length()+"\r\n\r\n"+body).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    socket.getOutputStream().flush();assertTrue(entered.await(5,TimeUnit.SECONDS));
    assertEquals(200,request("GET","/health",null,null,"heartbeat").statusCode(),"event loop remains responsive");
   }
   var close=app.close().toCompletionStage().toCompletableFuture();
   assertFalse(close.isDone());assertFalse(completions.containsKey("disconnected"));
   release.countDown();assertTrue(finished.await(5,TimeUnit.SECONDS));
   assertEquals(Boolean.TRUE,completions.get("disconnected"));close.get(15,TimeUnit.SECONDS);
   assertTrue(memory.findByOrderNo("HELD").isPresent(),"disconnect is not rollback");
  }finally{release.countDown();app.close().toCompletionStage().toCompletableFuture().get(15,TimeUnit.SECONDS);}
 }
 @Test void failedListenRestoresRunningApplicationOwner() throws Exception {
  var app=VertxShiroApplication.start(0).toCompletionStage().toCompletableFuture().get(15,TimeUnit.SECONDS);
  var provider=io.ddd4j.core.util.SubjectKit.subjectProvider;
  try {
   org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class,
    () -> VertxShiroApplication.start(app.port()).toCompletionStage().toCompletableFuture().get(10,TimeUnit.SECONDS));
   org.junit.jupiter.api.Assertions.assertSame(provider,io.ddd4j.core.util.SubjectKit.subjectProvider);
   base="http://127.0.0.1:"+app.port();assertTrue(login("alice").length()>0);
  }finally{app.close().toCompletionStage().toCompletableFuture().get(15,TimeUnit.SECONDS);}
 }
}
