package io.ddd4j.sample.helidon.shiro;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
/** 同步 JAX-RS 请求作用域；响应及 Jersey FINISHED 保底，按 owner 恢复前任上下文。
 */
@Provider @Priority(900)
public class NativeAuthFilter implements ContainerRequestFilter,ContainerResponseFilter,ApplicationEventListener {
 private static final String CLEANUP=NativeAuthFilter.class.getName()+".cleanup";
 @Inject NativeAuth auth;
 @Override public void filter(ContainerRequestContext request) {
  Subject previous = ThreadContext.getSubject();
  SecurityManager previousManager = ThreadContext.getSecurityManager();
  SecurityManager manager = auth.manager();
  Subject.Builder builder = new Subject.Builder(manager);
  String bearer = request.getHeaderString("Authorization");
  if (Objects.nonNull(bearer) && bearer.regionMatches(true, 0, "Bearer ", 0, 7)) {
   builder.sessionId(bearer.substring(7).trim());
  }
  Subject owned = builder.buildSubject();
  Thread owner = Thread.currentThread();
  AtomicBoolean cleaned = new AtomicBoolean();
  request.setProperty(CLEANUP, (Runnable) () -> {
   if (Thread.currentThread() != owner || !cleaned.compareAndSet(false, true)) {
    return;
   }
   if (ThreadContext.getSubject() == owned) {
    ThreadContext.unbindSubject();
    if (Objects.nonNull(previous)) {
     ThreadContext.bind(previous);
    }
   }
   if (ThreadContext.getSecurityManager() == manager) {
    ThreadContext.unbindSecurityManager();
    if (Objects.nonNull(previousManager)) {
     ThreadContext.bind(previousManager);
    }
   }
  });
  ThreadContext.bind(manager);
  ThreadContext.bind(owned);
 }
 @Override public void filter(ContainerRequestContext request,ContainerResponseContext response) { cleanup(request); }
 private void cleanup(ContainerRequestContext request) { Runnable action=(Runnable)request.getProperty(CLEANUP);if(Objects.nonNull(action)) { action.run(); } }
 @Override public void onEvent(ApplicationEvent event) { }
 @Override public RequestEventListener onRequest(RequestEvent event) {
  return finished -> { if(finished.getType()==RequestEvent.Type.FINISHED) { cleanup(finished.getContainerRequest()); } };
 }
}
