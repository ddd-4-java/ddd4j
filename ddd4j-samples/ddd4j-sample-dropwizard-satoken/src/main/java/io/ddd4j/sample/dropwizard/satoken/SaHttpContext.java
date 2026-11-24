package io.ddd4j.sample.dropwizard.satoken;

import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.context.model.SaResponse;
import cn.dev33.satoken.context.model.SaStorage;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
/** 当前 Dropwizard 请求的原生 Sa-Token HTTP 适配；不使用 mock request 或 Subject。
 */
final class SaHttpContext implements SaRequest {
 private final HttpServletRequest request;
 SaHttpContext(HttpServletRequest request) { this.request = request; }
 @Override public Object getSource() { return request; }
 @Override public String getParam(String name) { return request.getParameter(name); }
 @Override public Collection<String> getParamNames() { return java.util.Collections.list(request.getParameterNames()); }
 @Override public Map<String,String> getParamMap() { return request.getParameterMap().entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue()[0])); }
 @Override public String getHeader(String name) {
  String bearer = request.getHeader("Authorization");
  if (name.equals(StpUtil.getTokenName()) && Objects.nonNull(bearer) && bearer.regionMatches(true, 0, "Bearer ", 0, 7)) { return bearer.substring(7).trim(); }
  return request.getHeader(name);
 }
 @Override public String getCookieValue(String name) { return java.util.Arrays.stream(java.util.Optional.ofNullable(request.getCookies()).orElse(new jakarta.servlet.http.Cookie[0])).filter(cookie -> name.equals(cookie.getName())).map(jakarta.servlet.http.Cookie::getValue).findFirst().orElse(null); }
 @Override public String getCookieFirstValue(String name) { return getCookieValue(name); }
 @Override public String getCookieLastValue(String name) { return getCookieValue(name); }
 @Override public String getRequestPath() { return request.getRequestURI(); }
 @Override public String getUrl() { return request.getRequestURL().toString(); }
 @Override public String getMethod() { return request.getMethod(); }
 @Override public String getHost() { return request.getHeader("Host"); }
 @Override public Object forward(String path) { throw new UnsupportedOperationException("sample supports synchronous JSON routes only"); }
 static SaResponse response(HttpServletResponse response) {
  return new SaResponse() {
   @Override public Object getSource() { return response; }
   @Override public SaResponse setStatus(int status) { response.setStatus(status); return this; }
   @Override public SaResponse setHeader(String name,String value) { response.setHeader(name,value); return this; }
   @Override public SaResponse addHeader(String name,String value) { response.setHeader(name,value); return this; }
   @Override public Object redirect(String url) { response.setStatus(302);response.setHeader("Location",url);return null; }
  };
 }
 static SaStorage storage(HttpServletRequest request) {
  return new SaStorage() {
   @Override public Object getSource() { return request; }
   @Override public Object get(String key) { return request.getAttribute(key); }
   @Override public SaStorage set(String key,Object value) { request.setAttribute(key,value); return this; }
   @Override public SaStorage delete(String key) { request.removeAttribute(key); return this; }
  };
 }
}
