package io.ddd4j.sample.helidon.satoken;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

import java.util.Objects;

/**
 * 同步 JAX-RS 请求作用域；响应及 Jersey FINISHED 保底，按 owner 恢复前任上下文。
 */
@Provider
@Priority(900)
public class NativeAuthFilter implements ContainerRequestFilter, ContainerResponseFilter, ApplicationEventListener {
    /** \u5b58\u653e\u5f53\u524d\u8bf7\u6c42\u539f\u751f\u8ba4\u8bc1\u6062\u590d\u52a8\u4f5c\u7684\u5c5e\u6027\u952e\u3002 */
    private static final String CLEANUP = NativeAuthFilter.class.getName() + ".cleanup";
    /** \u4e3a\u5f53\u524d\u8bf7\u6c42\u521b\u5efa\u539f\u751f\u8ba4\u8bc1\u4f5c\u7528\u57df\u7684\u7ec4\u4ef6\u3002 */
    @Inject
    NativeAuth auth;

    /**
     * \u7ed1\u5b9a\u5f53\u524d\u8bf7\u6c42\u7684\u539f\u751f\u8ba4\u8bc1\u4e0a\u4e0b\u6587\uff0c\u5e76\u767b\u8bb0\u7ed3\u675f\u65f6\u6062\u590d\u5148\u524d\u72b6\u6001\u7684\u52a8\u4f5c\u3002
     * @param request \u8fdb\u5165\u793a\u4f8b\u8ba4\u8bc1\u94fe\u7684 JAX-RS \u8bf7\u6c42
     */
    @Override
    public void filter(ContainerRequestContext request) {
        cn.dev33.satoken.context.SaTokenContext context = cn.dev33.satoken.SaManager.getSaTokenContext();
        cn.dev33.satoken.context.model.SaTokenContextModelBox previous = context.isValid() ? context.getModelBox() : null;
        context.setContext(new SaHttpContext(request), SaHttpContext.response(javax.ws.rs.core.Response.ok()), SaHttpContext.storage(request));
        cn.dev33.satoken.context.model.SaTokenContextModelBox owned = context.getModelBox();
        request.setProperty(CLEANUP, (Runnable) () -> {
            if (context.isValid() && context.getModelBox() == owned) {
                context.clearContext();
                if (Objects.nonNull(previous)) {
                    context.setContext(previous.getRequest(), previous.getResponse(), previous.getStorage());
                }
            }
        });
    }

    /**
     * \u5728\u54cd\u5e94\u9636\u6bb5\u6267\u884c\u8bf7\u6c42\u6ce8\u518c\u7684\u539f\u751f\u8ba4\u8bc1\u6e05\u7406\u52a8\u4f5c\u3002
     * @param request \u6301\u6709\u6e05\u7406\u52a8\u4f5c\u7684 JAX-RS \u8bf7\u6c42
     * @param response \u6b63\u5728\u63d0\u4ea4\u7684 JAX-RS \u54cd\u5e94
     */
    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        cleanup(request);
    }

    /**
     * \u6267\u884c\u8bf7\u6c42\u5c5e\u6027\u4e2d\u4fdd\u5b58\u7684\u8ba4\u8bc1\u6e05\u7406\u52a8\u4f5c\uff0c\u6062\u590d\u8fdb\u5165\u8bf7\u6c42\u524d\u7684\u7ed1\u5b9a\u3002
     * @param request \u5f53\u524d\u8fdb\u5165 Sa-Token \u7684\u539f\u751f HTTP \u8bf7\u6c42
     */
    private void cleanup(ContainerRequestContext request) {
        Runnable action = (Runnable) request.getProperty(CLEANUP);
        if (Objects.nonNull(action)) {
            action.run();
        }
    }

    /**
     * \u63a5\u6536 Jersey \u5e94\u7528\u751f\u547d\u5468\u671f\u4e8b\u4ef6\u3002
     * @param event \u542f\u52a8\u3001\u505c\u6b62\u6216\u8bf7\u6c42\u5904\u7406\u671f\u95f4\u7684\u6846\u67b6\u4e8b\u4ef6
     */
    @Override
    public void onEvent(ApplicationEvent event) {
    }

    /**
     * \u4e3a Jersey \u8bf7\u6c42\u5b89\u88c5\u76d1\u542c\u56de\u8c03\u4ee5\u6e05\u7406\u8ba4\u8bc1\u4f5c\u7528\u57df\u3002
     * @param event \u542f\u52a8\u3001\u505c\u6b62\u6216\u8bf7\u6c42\u5904\u7406\u671f\u95f4\u7684\u6846\u67b6\u4e8b\u4ef6
     * @return \u5f53\u524d\u8bf7\u6c42\u4f7f\u7528\u7684\u76d1\u542c\u5668
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
