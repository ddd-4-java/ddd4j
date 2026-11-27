package io.ddd4j.sample.micronaut.shiro;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import io.ddd4j.core.subject.SubjectProvider;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.filter.FilterContinuation;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.core.annotation.Order;
import io.micronaut.web.router.RouteAttributes;
import java.util.Objects;
/** 同步 JSON 路由的原生认证边界；使用 Micronaut 传播作用域恢复各参与线程的前任状态。 */
@ServerFilter("/**") @Order(-100)
public final class NativeAuthFilter {
 /** \u4ece\u8ba4\u8bc1\u8bf7\u6c42\u5934\u63d0\u53d6\u4ee4\u724c\u65f6\u8bc6\u522b\u7684\u534f\u8bae\u524d\u7f00\u3002 */
 private static final String BEARER_PREFIX = "Bearer ";

 /** \u89e3\u6790\u8bf7\u6c42\u8ba4\u8bc1\u4e3b\u4f53\u7684\u63d0\u4f9b\u5668\u6216\u7ba1\u7406\u5176\u6ce8\u518c\u751f\u547d\u5468\u671f\u7684\u4f5c\u7528\u57df\u3002 */
 private final SubjectProvider subjects;
 /** \u4e3a\u5f53\u524d\u8bf7\u6c42\u521b\u5efa\u539f\u751f\u8ba4\u8bc1\u4f5c\u7528\u57df\u7684\u7ec4\u4ef6\u3002 */
 private final NativeAuth auth;
 /**
  * 初始化 <code>NativeAuthFilter</code> 使用的 <code>subjects</code>、<code>auth</code>。
  *
  * @param auth \u539f\u751f\u5168\u5c40\u8d44\u6e90
  * @param subjects \u539f\u751f Subject \u9002\u914d
  */
 public NativeAuthFilter(NativeAuth auth, SubjectProvider subjects) { this.subjects = subjects; this.auth = auth; }
 /**
  * 执行 <code>filter</code>，调用 <code>RouteAttributes.getRouteMatch</code>、<code>routeMatch.isEmpty</code>、<code>next.request</code>。
  *
  * @param request \u5f53\u524d\u8bf7\u6c42
  * @param next \u540c\u6b65\u4e0b\u6e38
  * @param propagated Micronaut \u53ef\u4f20\u64ad\u4e0a\u4e0b\u6587
  * @return \u539f\u751f\u54cd\u5e94
  */
 @RequestFilter @ExecuteOn(TaskExecutors.BLOCKING)
 public MutableHttpResponse<?> filter(HttpRequest<?> request, FilterContinuation<MutableHttpResponse<?>> next,
   io.micronaut.core.propagation.MutablePropagatedContext propagated) {
  var routeMatch = RouteAttributes.getRouteMatch(request);
  if (routeMatch.isEmpty()) {
   return next.request(request).proceed();
  }
  var builder=new Subject.Builder(auth.manager());
  String bearer=request.getHeaders().get("Authorization");
  if (Objects.nonNull(bearer) && bearer.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
   builder.sessionId(bearer.substring(BEARER_PREFIX.length()).trim());
  }
  var nativeContext=new NativeContext(builder.buildSubject());
  propagated.add(nativeContext);
  try(var scope=io.micronaut.core.propagation.PropagatedContext.getOrEmpty().plus(nativeContext).propagate()) {
   if (!request.getPath().equals("/auth/login")) {
    var subject = subjects.getSubject();
    if (!subject.isAuthenticated()) { return HttpResponse.unauthorized(); }
    boolean cancellation = routeMatch
      .flatMap(route -> route.getAnnotationMetadata().stringValue(Post.class))
      .filter("/orders/{orderNo}/cancel"::equals).isPresent();
    String permission = cancellation ? "order:cancel" : request.getPath().startsWith("/orders") && request.getMethodName().equals("POST")
      && !request.getPath().equals("/orders/query") ? "order:write" : "order:read";
    if (!subject.isPermitted(permission)) { return HttpResponse.status(io.micronaut.http.HttpStatus.FORBIDDEN); }
   }
   // Micronaut 4.10.26 的 blocking continuation 在 request(...) 中合入 MutablePropagatedContext。
   return next.request(request).proceed();
  }
 }
}
