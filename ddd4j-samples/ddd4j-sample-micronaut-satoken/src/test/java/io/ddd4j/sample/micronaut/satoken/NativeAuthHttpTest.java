package io.ddd4j.sample.micronaut.satoken;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ddd4j.sample.order.testkit.HttpOrderSampleDriver;
import io.ddd4j.sample.order.testkit.AuthSampleDriver;
import io.ddd4j.sample.order.testkit.SampleContractAssertions;
import io.ddd4j.sample.order.testkit.SampleHttpTransport;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.filter.FilterContinuation;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.core.annotation.Order;
import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.stp.StpInterface;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import io.ddd4j.sample.order.application.CreateOrderCommand;
import io.ddd4j.sample.order.domain.OrderStatus;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
/** 原生 HTTP/会话/订单验证；观测器只读线程状态，绝不清理所测状态。 */
class NativeAuthHttpTest {
 static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules();
 static final ConcurrentLinkedQueue<Boolean> CLEAN=new ConcurrentLinkedQueue<>();
 static final ConcurrentLinkedQueue<String> THREADS=new ConcurrentLinkedQueue<>();
 static final ConcurrentLinkedQueue<String> FINISHED=new ConcurrentLinkedQueue<>();
 static volatile CountDownLatch entered=new CountDownLatch(1);
 static volatile CountDownLatch release=new CountDownLatch(1);
 static boolean nativePresent() { return cn.dev33.satoken.SaManager.getSaTokenContext().isValid(); }
 static Object nativeState() { var context=cn.dev33.satoken.SaManager.getSaTokenContext();return context.isValid()?context.getModelBox().getRequest():null; }
 @Test void unmatchedRoutesKeepNativeHttpStatus() throws Exception {
  try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("micronaut.server.port", -1))) {
   Driver driver = new Driver(server.getPort());
   String order = "/orders/parity-probe";
   assertAll("the router owns unmatched requests",
           () -> assertEquals(404, driver.send("GET", order + "/no-route", null, null).statusCode()),
           () -> assertEquals(405, driver.send("GET", order + "/payment", null, null).statusCode()));
  }
 }

 @Test void authorizationPrecedesRequiredPaymentParameter() throws Exception {
  try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("micronaut.server.port", -1))) {
   Driver driver = new Driver(server.getPort());
   String payment = "/orders/parity-probe/payment";
   int anonymous = driver.send("POST", payment, null, null).statusCode();
   String readerToken = driver.login("reader", "sample-password");
   assertEquals("reader", driver.authenticatedPrincipal(readerToken));
   int reader = driver.send("POST", payment, null, readerToken).statusCode();
   String writerToken = driver.login("alice", "sample-password");
   assertEquals("alice", driver.authenticatedPrincipal(writerToken));
   int writer = driver.send("POST", payment, null, writerToken).statusCode();
   assertAll("payment with no key",
           () -> assertEquals(401, anonymous, "anonymous must be challenged before binding"),
           () -> assertEquals(403, reader, "read-only user must be denied before binding"),
           () -> assertEquals(400, writer, "authorized writer reaches required parameter validation"));
  }
 }

 @Test void bearerSchemeCaseDoesNotChangeOrderAuthorization() throws Exception {
  try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("micronaut.server.port", -1))) {
   Driver driver = new Driver(server.getPort());
   String writerToken = driver.login("alice", "sample-password");
   assertEquals("alice", driver.authenticatedPrincipal(writerToken));
   String id = driver.create(new CreateOrderCommand("bearer-scheme", "buyer-1", "Alice"));
   String token = driver.login("reader", "sample-password");
   assertEquals("reader", driver.authenticatedPrincipal(token));
   String url = "http://127.0.0.1:" + server.getPort() + "/orders/" + id;
   SampleHttpTransport.Response canonical = readOrder(url, Map.of("Authorization", "Bearer " + token));
   assertEquals(200, canonical.statusCode());
   assertEquals(id, JSON.readTree(canonical.body()).path("id").asText());
   String tokenName = cn.dev33.satoken.stp.StpUtil.getTokenName();
   assertAll("authorization schemes for the same order and token",
           () -> assertSameOrder(canonical, readOrder(url, Map.of("Authorization", "bearer " + token)), "bearer"),
           () -> assertSameOrder(canonical, readOrder(url, Map.of("Authorization", "bEaReR " + token)), "bEaReR"),
           () -> assertSameOrder(canonical, readOrder(url, Map.of("Authorization", "Bearer  " + token)),
                   "Bearer with two separator spaces"),
           () -> assertSameOrder(canonical, readOrder(url, Map.of("Authorization", "bEaReR    " + token)),
                   "mixed-case Bearer with four separator spaces"),
           () -> assertEquals(401, readOrder(url, Map.of("Authorization", "Bearer")).statusCode(),
                   "Bearer without token"),
           () -> assertEquals(401, readOrder(url, Map.of("Authorization", "Bearer    ")).statusCode(),
                   "Bearer with only separator spaces"),
           () -> assertEquals(401, readOrder(url, Map.of("Authorization", "Bearer   invalid-token")).statusCode(),
                   "invalid Bearer token"),
           () -> assertEquals(401, readOrder(url, Map.of("Authorization", "BearerX " + token)).statusCode(), "BearerX"),
           () -> assertEquals(401, readOrder(url, Map.of()).statusCode(), "without credentials"),
           () -> assertSameOrder(canonical, readOrder(url, Map.of(tokenName, token)), "native token header"),
           () -> assertSameOrder(canonical, readOrder(url, Map.of("Authorization", "BearerX " + token,
                   tokenName, token)), "unsupported scheme falls back to the native token header"),
           () -> assertSameOrder(canonical, readOrder(url, Map.of("Authorization", "Bearer",
                   tokenName, token)), "empty scheme falls back to the native token header"),
           () -> assertEquals(401, readOrder(url, Map.of("Authorization", "Bearer   invalid-token",
                   tokenName, token)).statusCode(), "matched Bearer takes precedence over the native token header"),
           () -> assertEquals(401, readOrder(url, Map.of("Cookie", tokenName + "=" + token)).statusCode(),
                   "cookie credentials are disabled"));
  }
 }

 private static SampleHttpTransport.Response readOrder(String url, Map<String, String> headers)
         throws java.io.IOException {
  return SampleHttpTransport.request(url, "GET", headers, Map.of(), null);
 }

 private static void assertSameOrder(SampleHttpTransport.Response canonical, SampleHttpTransport.Response actual,
         String scheme) throws java.io.IOException {
  assertEquals(200, actual.statusCode(), scheme);
  assertEquals(JSON.readTree(canonical.body()), JSON.readTree(actual.body()), scheme);
 }

 @Test void nativeLoginAndOrderAuthorization() throws Exception {
  CLEAN.clear();THREADS.clear();FINISHED.clear();
  entered=new CountDownLatch(1);release=new CountDownLatch(1);
  Object previousData=io.ddd4j.core.util.SubjectKit.getDataProvider();
  Object previous=cn.hutool.core.util.ReflectUtil.getStaticFieldValue(cn.hutool.core.util.ReflectUtil.getField(cn.dev33.satoken.SaManager.class,"saTokenDao"));
  try (EmbeddedServer server=ApplicationContext.run(EmbeddedServer.class,Map.of("micronaut.server.port",-1,"sample.test.observer",true))) {
   Driver driver=new Driver(server.getPort());
   assertEquals(401,driver.send("GET","/auth/me",null,null).statusCode());
   assertEquals(401,driver.send("POST","/auth/login",Map.of("username","alice","password","wrong"),null).statusCode());
   String token=driver.login("alice","sample-password");
   driver.token=token;
   SampleContractAssertions.defaultOrder(driver,"satoken-native-order");
   String id=driver.create(new io.ddd4j.sample.order.application.CreateOrderCommand("satoken-permission","buyer-1","Alice"));
   driver.id=id;
   SampleContractAssertions.authentication("satoken",driver,"alice","sample-password","alice","order:read","order:cancel",id);
   // 同步请求在连接断开后仍按 finally 结束；不声称支持任意 Publisher/线程迁移。
   token=driver.login("alice","sample-password");
   String slashOrder = driver.create(new CreateOrderCommand("satoken-permission-slash", "buyer-1", "Alice"));
   assertCancellation(driver, id, token, "", 403, OrderStatus.DRAFT);
   assertCancellation(driver, slashOrder, token, "/", 403, OrderStatus.DRAFT);
   assertPermittedCancellation(driver, id, slashOrder);
   token = driver.login("alice", "sample-password");
   String observation = java.util.UUID.randomUUID().toString();
   try(Socket socket=new Socket("127.0.0.1",server.getPort())) {
    String request="GET /auth/me HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer "+token+"\r\nX-Test-Hold: "+observation+"\r\nConnection: close\r\n\r\n";
    socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().flush();
    assertTrue(entered.await(10,TimeUnit.SECONDS));
   } finally { release.countDown(); }
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
   while (!FINISHED.contains(observation) && System.nanoTime() < deadline) { Thread.sleep(10); }
   assertTrue(FINISHED.contains(observation), "the exact disconnected route completed native cleanup");
   assertFalse(CLEAN.contains(false),"native and ddd4j state clean on actual server thread");
   assertFalse(THREADS.isEmpty());
   System.out.println("S04 satoken server lifecycle observations="+CLEAN.size()+" threads="+new java.util.TreeSet<>(THREADS));
  }
  assertFalse(CLEAN.contains(false),"all server-thread restoration observations remain clean after Context closes");
  assertSame(previousData,io.ddd4j.core.util.SubjectKit.getDataProvider());
  assertSame(previous,cn.hutool.core.util.ReflectUtil.getStaticFieldValue(cn.hutool.core.util.ReflectUtil.getField(cn.dev33.satoken.SaManager.class,"saTokenDao")),"context close restores prior native global owner");
 }
 private static void assertCancellation(Driver driver, String id, String token, String suffix,
         int status, OrderStatus state) throws Exception {
  assertEquals(status, driver.send("POST", "/orders/" + id + "/cancel" + suffix, null, token).statusCode());
  assertEquals(state, driver.find(id).status());
 }

 private static void assertPermittedCancellation(Driver driver, String id, String slashOrder) throws Exception {
  StpInterface previous = SaManager.getStpInterface();
  SaManager.setStpInterface(new StpInterface() {
   @Override public List<String> getPermissionList(Object loginId, String loginType) {
    if ("alice".equals(loginId)) {
     return Arrays.asList("order:read", "order:cancel");
    }
    return previous.getPermissionList(loginId, loginType);
   }

   @Override public List<String> getRoleList(Object loginId, String loginType) {
    return previous.getRoleList(loginId, loginType);
   }
  });
  try {
   String token = driver.login("alice", "sample-password");
   assertEquals(403, driver.send("POST", "/orders", new CreateOrderCommand("no-write", "buyer-1", "Alice"), token).statusCode());
   assertCancellation(driver, id, token, "", 200, OrderStatus.CANCELLED);
   assertCancellation(driver, slashOrder, token, "/", 200, OrderStatus.CANCELLED);
   driver.logout(token);
  } finally {
   SaManager.setStpInterface(previous);
  }
 }

 static class Driver extends HttpOrderSampleDriver implements AuthSampleDriver {
  final String base;final HttpClient client=HttpClient.newHttpClient();String token;String id;
  Driver(int port) { super("http://127.0.0.1:"+port);base="http://127.0.0.1:"+port; }
  HttpResponse<String> send(String method,String path,Object body,String bearer) throws Exception {
   var builder=HttpRequest.newBuilder(URI.create(base+path)).header("Content-Type","application/json");
   if(Objects.nonNull(bearer)) { builder.header("Authorization","Bearer "+bearer); }
   return client.send(builder.method(method,Objects.isNull(body)?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
  }
  @Override public <T>T request(String method,String path,Object body,Class<T> type) {
   try {
    var response=send(method,path,body,token);
    if(response.statusCode()==401 || response.statusCode()==403) { throw new SecurityException(response.body()); }
    if(response.statusCode()>=400) { throw new IllegalStateException("HTTP "+response.statusCode()+" "+response.body()); }
    if(type==String.class) { return type.cast(response.body().startsWith("\"")?JSON.readValue(response.body(),String.class):response.body()); }
    return JSON.readValue(response.body(),type);
   } catch(java.io.IOException exception) { throw new IllegalStateException(exception); }
   catch(InterruptedException exception) { Thread.currentThread().interrupt();throw new IllegalStateException(exception); }
   catch(Exception exception) { if(exception instanceof RuntimeException runtime) { throw runtime; } throw new IllegalStateException(exception); }
  }
  @Override public String login(String user,String password) { token=null;token=request("POST","/auth/login",Map.of("username",user,"password",password),com.fasterxml.jackson.databind.JsonNode.class).path("token").asText();return token; }
  @Override public String authenticatedPrincipal(String token) { this.token=token;return request("GET","/auth/me",null,com.fasterxml.jackson.databind.JsonNode.class).path("principal").asText(); }
  @Override public String authorizedResource(String token,String permission) {
   this.token=token;
   if(permission.equals("order:cancel")) { try { request("POST","/orders/"+id+"/cancel",null,String.class);return id; }
    finally { assertEquals(io.ddd4j.sample.order.domain.OrderStatus.DRAFT,find(id).status(),"permission denial must not cancel the order"); } }
   return find(id).id();
  }
  @Override public void failingRequest(String token) { this.token=token;pay(id,"empty-order-business-failure"); }
  @Override public void logout(String token) { this.token=token;request("POST","/auth/logout",null,com.fasterxml.jackson.databind.JsonNode.class); }
  @Override public Optional<String> contextPrincipal() { return CLEAN.contains(false)?Optional.of("leaked"):Optional.empty(); }
 }
 @ServerFilter("/**") @Order(-200) @Requires(property="sample.test.observer",value="true")
 static class OuterObserver {
  @RequestFilter @ExecuteOn(TaskExecutors.BLOCKING)
  MutableHttpResponse<?> observe(io.micronaut.http.HttpRequest<?> request,FilterContinuation<MutableHttpResponse<?>> next, io.micronaut.core.propagation.MutablePropagatedContext propagated) {
   String observation = request.getHeaders().get("X-Test-Hold");
   propagated.add(new io.micronaut.core.propagation.ThreadPropagatedContextElement<Object>() {
    @Override public Object updateThreadContext() { return nativeState(); }
    @Override public void restoreThreadContext(Object previous) {
     CLEAN.add(previous == nativeState());THREADS.add("restored:"+Thread.currentThread().getName());
    }
   });
   try { return next.request(request).proceed(); } finally {
    CLEAN.add(!nativePresent() && Objects.isNull(io.ddd4j.core.context.ThreadContext.getSubject()));
    THREADS.add(Thread.currentThread().getName());
    if (Objects.nonNull(observation)) {
     FINISHED.add(observation);
    }
   }
  }
 }
 @ServerFilter("/**") @Order(-50) @Requires(property="sample.test.observer",value="true")
 static class InnerObserver {
  @RequestFilter MutableHttpResponse<?> observe(io.micronaut.http.HttpRequest<?> request,FilterContinuation<MutableHttpResponse<?>> next) throws InterruptedException {
   assertTrue(nativePresent(),"actual native context exists inside filter scope");
   if (cn.dev33.satoken.stp.StpUtil.isLogin()) {
    assertEquals(cn.dev33.satoken.stp.StpUtil.hasPermission("order:read"),new io.ddd4j.auth.satoken.subject.SaTokenSubjectProvider().getSubject().isPermitted("order:read"),"native and DDD permission source agree");
   }
   request.setAttribute("observed-thread",Thread.currentThread().getName());
   if(request.getHeaders().contains("X-Test-Hold")) { entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS)); }
   return next.proceed();
  }
 }
}
