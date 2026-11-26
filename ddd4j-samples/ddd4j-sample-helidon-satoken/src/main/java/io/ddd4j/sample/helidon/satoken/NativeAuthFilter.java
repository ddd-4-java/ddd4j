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
    private static final String CLEANUP = NativeAuthFilter.class.getName() + ".cleanup";
    @Inject
    NativeAuth auth;

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

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        cleanup(request);
    }

    private void cleanup(ContainerRequestContext request) {
        Runnable action = (Runnable) request.getProperty(CLEANUP);
        if (Objects.nonNull(action)) {
            action.run();
        }
    }

    @Override
    public void onEvent(ApplicationEvent event) {
    }

    @Override
    public RequestEventListener onRequest(RequestEvent event) {
        return finished -> {
            if (finished.getType() == RequestEvent.Type.FINISHED) {
                cleanup(finished.getContainerRequest());
            }
        };
    }
}
