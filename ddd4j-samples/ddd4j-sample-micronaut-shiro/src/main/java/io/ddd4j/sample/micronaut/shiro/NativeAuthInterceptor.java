package io.ddd4j.sample.micronaut.shiro;

import io.micronaut.http.HttpHeaders;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadState;
import io.ddd4j.kit.lang.StrKit;

import java.util.Objects;
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
    /** 保存 <code>auth</code> 对应的 <code>NativeAuth</code> 数据。 */
    private final NativeAuth auth;
    /** 保存 <code>subjects</code> 对应的 <code>SubjectProvider</code> 数据。 */
    private final SubjectProvider subjects;

    /**
     * 初始化 <code>NativeAuthInterceptor</code> 使用的 <code>auth</code>、<code>subjects</code>。
     *
     * @param auth 传入此操作的 <code>NativeAuth</code> 类型 <code>auth</code>
     * @param subjects 传入此操作的 <code>SubjectProvider</code> 类型 <code>subjects</code>
     */
    NativeAuthInterceptor(NativeAuth auth, SubjectProvider subjects) {
        this.auth = auth;
        this.subjects = subjects;
    }

    /**
     * 执行 <code>intercept</code>，调用 <code>ServerRequestContext.currentRequest</code>、<code>Subject.Builder</code>、<code>auth.manager</code>。
     *
     * @param invocation 传入此操作的 <code>MethodInvocationContext&lt;Object, Object&gt;</code> 类型 <code>invocation</code>
     * @return 返回表达式 <code>invocation.proceed()</code>
     */
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> invocation) {
        HttpRequest<?> request = ServerRequestContext.currentRequest().orElseThrow(
                () -> new IllegalStateException("Missing Micronaut HTTP request"));
        Subject.Builder builder = new Subject.Builder(auth.manager());
        String bearer = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (Objects.nonNull(bearer) && bearer.regionMatches(true, 0, "Bearer ", 0, 7) && StrKit.isNotBlank(bearer.substring(7))) {
            builder.sessionId(bearer.substring(7).trim());
        }
        NativeContext scope = new NativeContext(builder.buildSubject());
        ThreadState previous = scope.updateThreadContext();
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
                if (!subject.isPermitted(permission)) {
                    throw new HttpStatusException(HttpStatus.FORBIDDEN, "Permission denied");
                }
            }
            return invocation.proceed();
        } finally {
            scope.restoreThreadContext(previous);
        }
    }
}
