package io.ddd4j.sample.dropwizard.shiro;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.BiConsumer;
/** 同步 Jersey 调用外围的原生主体作用域；客户端断开仍等待业务 finally。 */
final class NativeRequestFilter implements Filter {
 private final Supplier<NativeAuth> auth;
 private final BiConsumer<String,Boolean> completed;
 NativeRequestFilter(Supplier<NativeAuth> auth,BiConsumer<String,Boolean> completed){this.auth=auth;this.completed=completed;}
 @Override public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain) throws IOException,ServletException {
  var http=(HttpServletRequest)request;
  var before=new java.util.HashMap<>(io.ddd4j.core.context.ThreadContext.getResources());
  Subject previousSubject = ThreadContext.getSubject();
  SecurityManager previousManager = ThreadContext.getSecurityManager();
  try(var scope=new NativeScope(http.getHeader("Authorization"), auth.get())) { chain.doFilter(request,response); }
  finally {
   String id=http.getHeader("X-Request-Id");
   if(Objects.nonNull(id)) {
    boolean clean=ThreadContext.getSubject() == previousSubject
      && ThreadContext.getSecurityManager() == previousManager && before.equals(io.ddd4j.core.context.ThreadContext.getResources());
    completed.accept(id,clean);
   }
  }
 }
}
