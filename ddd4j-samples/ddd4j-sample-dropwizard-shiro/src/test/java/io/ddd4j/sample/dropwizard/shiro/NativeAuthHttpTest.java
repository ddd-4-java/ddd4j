package io.ddd4j.sample.dropwizard.shiro;
import io.ddd4j.cache.CacheKit;
import io.ddd4j.core.cache.Cache;
import io.ddd4j.core.util.SubjectKit;
import io.dropwizard.testing.DropwizardTestSupport;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import org.junit.jupiter.api.Test;
import java.util.Objects;
import java.net.URI;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 private final ObjectMapper mapper=new ObjectMapper();
 private io.dropwizard.testing.DropwizardTestSupport<AuthConfiguration> start(io.ddd4j.sample.order.application.OrderApplicationService orders) throws Exception { return start(orders,0); }
 private io.dropwizard.testing.DropwizardTestSupport<AuthConfiguration> start(io.ddd4j.sample.order.application.OrderApplicationService orders,int port) throws Exception {
  return start(orders, port, new AuthConfiguration());
 }
 private DropwizardTestSupport<AuthConfiguration> start(OrderApplicationService orders, int port, AuthConfiguration config) throws Exception {
  var server=(io.dropwizard.core.server.DefaultServerFactory)config.getServerFactory();
  io.dropwizard.jetty.HttpConnectorFactory applicationConnector = (io.dropwizard.jetty.HttpConnectorFactory)server.getApplicationConnectors().get(0);
  applicationConnector.setBindHost("127.0.0.1");
  applicationConnector.setPort(port);
  io.dropwizard.jetty.HttpConnectorFactory adminConnector = (io.dropwizard.jetty.HttpConnectorFactory)server.getAdminConnectors().get(0);
  adminConnector.setBindHost("127.0.0.1");
  adminConnector.setPort(0);
  var app=new DropwizardShiroApplication(orders,completions::put);
  var support=new io.dropwizard.testing.DropwizardTestSupport<AuthConfiguration>(DropwizardShiroApplication.class,config){
   @Override public io.dropwizard.core.Application<AuthConfiguration> newApplication(){return app;}
  };support.before();return support;
 }

 private final HttpClient client=HttpClient.newHttpClient();
 private final Map<String,Boolean> completions=new ConcurrentHashMap<>();
 private String base;
 private HttpResponse<String> request(String method,String path,String token,String body,String id) throws Exception {
  return requestWithHeaders(method, path, Objects.isNull(token) ? Map.of()
          : Map.of("Authorization", "Bearer " + token), body, id);
 }
 private HttpResponse<String> requestWithHeaders(String method, String path, Map<String, String> credentials,
         String body, String id) throws Exception {
  return requestWithHeaders(client, method, path, credentials, body, id);
 }

 private HttpResponse<String> requestOnFreshConnection(String method, String path, Map<String, String> credentials,
         String body, String id) throws Exception {
  // Jetty 的连接级头缓存可能复用同一令牌此前请求的方案写法。
  HttpClient isolatedClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
  return requestWithHeaders(isolatedClient, method, path, credentials, body, id);
 }

 private HttpResponse<String> requestWithHeaders(HttpClient transport, String method, String path,
         Map<String, String> credentials, String body, String id) throws Exception {
  var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(10)).header("X-Request-Id",id).header("Idempotency-Key",id);
  credentials.forEach(request::header);
  if(Objects.nonNull(body)){request.header("Content-Type","application/json");}
  return transport.send(request.method(method,Objects.isNull(body)?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
 }
 private String login(String user) throws Exception {
  var response=request("POST","/auth/login",null,"{\"username\":\""+user+"\",\"password\":\"sample-password\"}","login-"+user);
  assertEquals(200,response.statusCode(),response.body());return mapper.readTree(response.body()).path("token").asText();
 }
 @Test void nativeCredentialsPermissionsLogoutAndBusinessFailure() throws Exception {
  var memory=new InMemoryOrderAdapters();
  var app=start(new OrderApplicationService(memory,memory,memory,memory,memory));
  base="http://127.0.0.1:"+app.getLocalPort();
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
   assertEquals(201,created.statusCode(),created.body());String id=mapper.readTree(created.body()).path("id").asText();
   assertEquals(200,request("GET","/orders/"+id,reader,null,"reader-get").statusCode());
   var error=request("POST","/orders/"+id+"/lines",token,"{\"goodsId\":\"g\",\"goodsName\":\"Book\",\"quantity\":0,\"unitPrice\":5}","business-error");
   assertEquals(400,error.statusCode(),error.body());
   assertEquals(200,request("POST","/auth/logout",token,null,"logout").statusCode());
   assertEquals(401,request("GET","/orders/"+id,token,null,"expired").statusCode());
   for(String key:java.util.List.of("bad-login","missing-user","missing-password","anonymous","denied","create","reader-get","business-error","logout","expired")) {
    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
    while(!completions.containsKey(key) && System.nanoTime()<deadline){Thread.sleep(10);}
    assertEquals(Boolean.TRUE,completions.get(key),"passive completion "+key);
   }
  }finally{app.after();}
 }

 @Test void unsupportedOrderMethodPreservesNativeAllowHeader() throws Exception {
  InMemoryOrderAdapters memory = new InMemoryOrderAdapters();
  DropwizardTestSupport<AuthConfiguration> app = start(new OrderApplicationService(memory, memory, memory, memory, memory));
  try {
   base = "http://127.0.0.1:" + app.getLocalPort();
   String order = "/orders/route-boundary";
   assertEquals(404, request("GET", order + "/no-route", null, null, "route-not-found").statusCode());
   assertEquals(401, request("POST", order + "/pay", null, null, "route-auth-required").statusCode());
   HttpURLConnection wrongMethod = (HttpURLConnection) new URL(base + order + "/pay").openConnection();
   wrongMethod.setConnectTimeout(5000);
   wrongMethod.setReadTimeout(5000);
   try {
    assertEquals(405, wrongMethod.getResponseCode());
    try (java.io.InputStream body = wrongMethod.getErrorStream()) {
     assertEquals(405, mapper.readTree(body).path("code").asInt());
    }
    String allow = wrongMethod.getHeaderField("Allow");
    assertNotNull(allow, "Jersey's Allow header must survive JSON error translation");
    Set<String> allowedMethods = Arrays.stream(allow.split(",")).map(String::trim).collect(Collectors.toSet());
    assertEquals(new HashSet<>(Arrays.asList("POST", "OPTIONS")), allowedMethods);
   } finally {
    wrongMethod.disconnect();
   }
  } finally {
   app.after();
  }
 }

 @Test void bearerSchemeCaseDoesNotChangeOrderAuthorization() throws Exception {
  InMemoryOrderAdapters memory = new InMemoryOrderAdapters();
  AtomicReference<String> principal = new AtomicReference<>();
  OrderApplicationService orders = new OrderApplicationService(memory, memory, memory, memory, memory) {
   @Override public OrderReadModel find(String id) {
    principal.set(SubjectKit.getSubject().getPrincipal().getLoginId().toString());
    return super.find(id);
   }
  };
  DropwizardTestSupport<AuthConfiguration> app = start(orders);
  base = "http://127.0.0.1:" + app.getLocalPort();
  try {
   String writerToken = login("alice");
   HttpResponse<String> created = request("POST", "/orders", writerToken,
           "{\"orderNo\":\"BEARER-SCHEME\",\"buyerId\":\"b\",\"buyerName\":\"Alice\"}", "bearer-create");
   assertEquals(201, created.statusCode(), created.body());
   String orderId = mapper.readTree(created.body()).path("id").asText();
   assertFalse(orderId.isEmpty());
   String path = "/orders/" + orderId;
   String readerToken = login("reader");
   assertFalse(readerToken.isEmpty());
   HttpResponse<String> canonical = request("GET", path, readerToken, null, "canonical-bearer");
   assertEquals(200, canonical.statusCode(), canonical.body());
   assertEquals("reader", principal.get(), "canonical Bearer principal");
   assertEquals(orderId, mapper.readTree(canonical.body()).path("id").asText());
   assertAll("authorization schemes and logout for the same order and reader",
           () -> assertSameOrder(path, readerToken, "bearer ", "lowercase-bearer", canonical, principal),
           () -> assertSameOrder(path, readerToken, "bEaReR ", "mixed-case-bearer", canonical, principal),
           () -> assertSameOrder(path, readerToken, "Bearer  ", "two-bearer-spaces", canonical, principal),
           () -> assertSameOrder(path, readerToken, "bEaReR    ", "four-bearer-spaces", canonical, principal),
           () -> assertEquals(401, requestOnFreshConnection("GET", path, Map.of("Authorization", "Bearer    invalid-token"),
                   null, "spaced-invalid-token").statusCode(), "spaced-invalid-token"),
           () -> assertEquals(401, requestWithHeaders("GET", path, Map.of("Authorization", "BearerX " + readerToken),
                   null, "invalid-scheme").statusCode(), "invalid-scheme"),
           () -> assertEquals(401, requestWithHeaders("GET", path, Map.of(), null,
                   "no-bearer").statusCode(), "no-bearer"),
           () -> assertEquals(200, requestOnFreshConnection("POST", "/auth/logout",
                   Map.of("Authorization", "bEaReR    " + readerToken), null, "spaced-mixed-case-logout").statusCode(),
                   "spaced-mixed-case-logout"),
           () -> assertEquals(401, request("GET", path, readerToken, null,
                   "revoked-reader").statusCode(), "revoked-reader"),
           () -> assertEquals(200, request("GET", path, writerToken, null,
                   "remaining-writer").statusCode(), "remaining-writer"));
  } finally {
   app.after();
  }
 }

 private void assertSameOrder(String path, String token, String authorizationPrefix, String requestId,
         HttpResponse<String> canonical, AtomicReference<String> principal) throws Exception {
  principal.set(null);
  HttpResponse<String> actual = requestOnFreshConnection("GET", path, Map.of("Authorization", authorizationPrefix + token),
          null, requestId);
  assertEquals(200, actual.statusCode(), requestId);
  assertEquals("reader", principal.get(), requestId);
  assertEquals(mapper.readTree(canonical.body()), mapper.readTree(actual.body()), requestId);
 }

 @Test void disconnectedRequestFinishesBeforeNativeOwnerStops() throws Exception {
  var memory=new InMemoryOrderAdapters();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
  var service=new OrderApplicationService(memory,memory,memory,memory,memory){
   @Override public Order create(CreateOrderCommand command){
    assertTrue(Thread.currentThread().getName().contains("dw-"));
    assertTrue(io.ddd4j.core.util.SubjectKit.getSubject().isAuthenticated());
    entered.countDown();try{assertTrue(release.await(10,TimeUnit.SECONDS));}catch(InterruptedException error){throw new IllegalStateException(error);}
    return super.create(command);
   }
  };
  var app=start(service);base="http://127.0.0.1:"+app.getLocalPort();
  try {
   String token=login("alice");String body="{\"orderNo\":\"HELD\",\"buyerId\":\"b\",\"buyerName\":\"Alice\"}";
   try(var socket=new Socket("127.0.0.1",app.getLocalPort())) {
    socket.getOutputStream().write(("POST /orders HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer "+token+"\r\nIdempotency-Key: disconnected\r\nX-Request-Id: disconnected\r\nContent-Type: application/json\r\nContent-Length: "+body.length()+"\r\n\r\n"+body).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    socket.getOutputStream().flush();assertTrue(entered.await(5,TimeUnit.SECONDS));
   }
   var closing=java.util.concurrent.CompletableFuture.runAsync(app::after);
   org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.TimeoutException.class,()->closing.get(300,TimeUnit.MILLISECONDS));
   assertFalse(completions.containsKey("disconnected"));
   release.countDown();closing.get(15,TimeUnit.SECONDS);
   assertEquals(Boolean.TRUE,completions.get("disconnected"));
   assertTrue(memory.findByOrderNo("HELD").isPresent(),"disconnect does not roll back accepted writes");
  }finally{release.countDown();app.after();}
 }
 @Test void shutdownPreservesCallerReplacementAndLoadingCache() throws Exception {
  InMemoryOrderAdapters memory = new InMemoryOrderAdapters();
  AuthConfiguration config = new AuthConfiguration();
  DropwizardTestSupport<AuthConfiguration> app = start(new OrderApplicationService(memory,memory,memory,memory,memory), 0, config);
  String name = config.getDdd4jWeb().getIdempotencyCacheName();
  Cache<String, Object> caller;
  try {
   CacheKit.build(name, 300);
   caller = CacheKit.getCache(name);
   CacheKit.put(name, "caller-entry", "retained");
   CacheKit.buildWithLoader(name, key -> "loaded", builder -> builder);
  } finally {
   app.after();
  }
  try {
   assertSame(caller, CacheKit.getCache(name));
   assertEquals("retained", CacheKit.get(name, "caller-entry"));
   CacheKit.unregisterLocalIfOwned(name, caller);
   assertEquals("loaded", CacheKit.get(name, "caller-entry"));
  } finally {
   CacheKit.unregister(name);
  }
 }

 @Test void disabledCacheStillRunsAndStopsNativeAuth() throws Exception {
  InMemoryOrderAdapters memory = new InMemoryOrderAdapters();
  AuthConfiguration config = new AuthConfiguration();
  config.getDdd4jWeb().setIdempotencyEnabled(false);
  Object previousProvider = SubjectKit.subjectProvider;
  DropwizardTestSupport<AuthConfiguration> app = start(new OrderApplicationService(memory,memory,memory,memory,memory), 0, config);
  try {
   base = "http://127.0.0.1:" + app.getLocalPort();
   assertFalse(login("alice").isEmpty());
   assertNull(CacheKit.getCache(config.getDdd4jWeb().getIdempotencyCacheName()));
  } finally {
   app.after();
  }
  assertSame(previousProvider, SubjectKit.subjectProvider);
 }

 @Test void failedBindRestoresPriorSubjectOwner() throws Exception {
  var prior=io.ddd4j.core.util.SubjectKit.subjectProvider;
  var memory=new InMemoryOrderAdapters();
  try(var occupied=new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))){
   org.junit.jupiter.api.Assertions.assertThrows(Exception.class,()->start(new OrderApplicationService(memory,memory,memory,memory,memory),occupied.getLocalPort()));
   org.junit.jupiter.api.Assertions.assertSame(prior,io.ddd4j.core.util.SubjectKit.subjectProvider);
  }
 }
}
