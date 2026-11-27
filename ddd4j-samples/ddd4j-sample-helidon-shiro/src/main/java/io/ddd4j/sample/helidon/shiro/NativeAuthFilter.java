package io.ddd4j.sample.helidon.shiro;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 同步 JAX-RS 请求作用域；响应及 Jersey FINISHED 保底，按 owner 恢复前任上下文。
 */
@Provider
@Priority(900)
public class NativeAuthFilter implements ContainerRequestFilter, ContainerResponseFilter, ApplicationEventListener {
    /**
     * 存放当前请求原生认证恢复动作的属性键。
     */
    private static final String CLEANUP = NativeAuthFilter.class.getName() + ".cleanup";
    /**
     * 为当前请求创建原生认证作用域的组件。
     */
    @Inject
    NativeAuth auth;

    /**
     * 绑定当前请求的原生认证上下文，并登记结束时恢复先前状态的动作。
     *
     * @param request 进入示例认证链的 JAX-RS 请求
     */
    @Override
    public void filter(ContainerRequestContext request) {
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

    /**
     * 在响应阶段执行请求注册的原生认证清理动作。
     *
     * @param request  持有清理动作的 JAX-RS 请求
     * @param response 正在提交的 JAX-RS 响应
     */
    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        cleanup(request);
    }

    /**
     * 执行请求属性中保存的认证清理动作，恢复进入请求前的绑定。
     *
     * @param request 当前进入 Sa-Token 的原生 HTTP 请求
     */
    private void cleanup(ContainerRequestContext request) {
        Runnable action = (Runnable) request.getProperty(CLEANUP);
        if (Objects.nonNull(action)) {
            action.run();
        }
    }

    /**
     * 接收 Jersey 应用生命周期事件。
     *
     * @param event 启动、停止或请求处理期间的框架事件
     */
    @Override
    public void onEvent(ApplicationEvent event) {
    }

    /**
     * 为 Jersey 请求安装监听回调以清理认证作用域。
     *
     * @param event 启动、停止或请求处理期间的框架事件
     * @return 当前请求使用的监听器
     */
    @Override
    public RequestEventListener onRequest(RequestEvent event) {
        return finished -> {
            if (finished.getType() == RequestEvent.Type.FINISHED) {
                cleanup(finished.getContainerRequest());
            }
        };
    }
}
