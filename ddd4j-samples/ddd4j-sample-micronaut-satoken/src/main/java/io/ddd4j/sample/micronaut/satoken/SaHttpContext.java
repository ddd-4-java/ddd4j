package io.ddd4j.sample.micronaut.satoken;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.context.model.SaResponse;
import cn.dev33.satoken.context.model.SaStorage;
import cn.dev33.satoken.stp.StpUtil;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
/** 当前 Micronaut 请求的原生 Sa-Token HTTP 适配；不使用 mock request 或 Subject。 */
final class SaHttpContext implements SaRequest {
 private static final String BEARER_PREFIX = "Bearer ";

 private final HttpRequest<?> request;
 SaHttpContext(HttpRequest<?> request) { this.request = request; }
 @Override public Object getSource() { return request; }
 @Override public String getParam(String name) { return request.getParameters().get(name); }
 @Override public Collection<String> getParamNames() { return request.getParameters().names(); }
 @Override public Map<String,String> getParamMap() { return request.getParameters().asMap(String.class,String.class); }
 @Override public String getHeader(String name) {
  String bearer = request.getHeaders().get("Authorization");
  if (name.equals(StpUtil.getTokenName()) && Objects.nonNull(bearer)
          && bearer.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
   return bearer.substring(BEARER_PREFIX.length()).trim();
  }
  return request.getHeaders().get(name);
 }
 @Override public String getCookieValue(String name) { return request.getCookies().findCookie(name).map(io.micronaut.http.cookie.Cookie::getValue).orElse(null); }
 @Override public String getCookieFirstValue(String name) { return getCookieValue(name); }
 @Override public String getCookieLastValue(String name) { return getCookieValue(name); }
 @Override public String getRequestPath() { return request.getPath(); }
 @Override public String getUrl() { return request.getUri().toString(); }
 @Override public String getMethod() { return request.getMethodName(); }
 @Override public String getHost() { return request.getHeaders().get("Host"); }
 @Override public Object forward(String path) { throw new UnsupportedOperationException("sample supports synchronous JSON routes only"); }
 static SaResponse response(MutableHttpResponse<?> response) {
  return new SaResponse() {
   @Override public Object getSource() { return response; }
   @Override public SaResponse setStatus(int status) { response.status(status); return this; }
   @Override public SaResponse setHeader(String name,String value) { response.header(name,value); return this; }
   @Override public SaResponse addHeader(String name,String value) { response.getHeaders().add(name,value); return this; }
   @Override public Object redirect(String url) { return response.status(302).header("Location",url); }
  };
 }
 static SaStorage storage(HttpRequest<?> request) {
  return new SaStorage() {
   @Override public Object getSource() { return request; }
   @Override public Object get(String key) { return request.getAttributes().get(key,Object.class).orElse(null); }
   @Override public SaStorage set(String key,Object value) { request.setAttribute(key,value); return this; }
   @Override public SaStorage delete(String key) { request.getAttributes().remove(key); return this; }
  };
 }
}
