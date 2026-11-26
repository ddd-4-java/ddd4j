package io.ddd4j.sample.micronaut.satoken;

import cn.dev33.satoken.context.model.SaTokenContextModelBox;
import cn.dev33.satoken.stp.StpUtil;
import io.micronaut.http.HttpResponse;
import io.ddd4j.core.subject.SubjectProvider;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;

/** Micronaut 3 invokes the native scope and its cleanup on the actual synchronous route thread. */
@Singleton
@InterceptorBean(NativeAuthenticated.class)
final class NativeAuthInterceptor implements MethodInterceptor<Object, Object> {
    private final NativeAuth auth;
    private final SubjectProvider subjects;

    NativeAuthInterceptor(NativeAuth auth, SubjectProvider subjects) {
        this.auth = auth;
        this.subjects = subjects;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> invocation) {
        HttpRequest<?> request = ServerRequestContext.currentRequest().orElseThrow(
                () -> new IllegalStateException("Missing Micronaut HTTP request"));
        NativeContext scope = new NativeContext(new SaHttpContext(request),
                SaHttpContext.response(HttpResponse.ok()), SaHttpContext.storage(request));
        SaTokenContextModelBox previous = scope.updateThreadContext();
        try {
            String path = request.getPath();
            if (!"/auth/login".equals(path)) {
                io.ddd4j.core.subject.Subject subject = subjects.getSubject();
                if (!subject.isAuthenticated()) {
                    throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
                }
                String permission = "order:read";
                if ("cancel".equals(invocation.getMethodName())) {
                    permission = "order:cancel";
                } else if (path.startsWith("/orders") && request.getMethod() == HttpMethod.POST
                        && !"/orders/query".equals(path)) {
                    permission = "order:write";
                }
                if (!StpUtil.hasPermission(permission)) {
                    throw new HttpStatusException(HttpStatus.FORBIDDEN, "Permission denied");
                }
            }
            return invocation.proceed();
        } finally {
            scope.restoreThreadContext(previous);
        }
    }
}
