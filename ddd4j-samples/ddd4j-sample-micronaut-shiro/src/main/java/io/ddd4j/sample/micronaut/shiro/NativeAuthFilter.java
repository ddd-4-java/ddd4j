package io.ddd4j.sample.micronaut.shiro;

import io.ddd4j.core.subject.SubjectProvider;
import io.micronaut.core.annotation.Order;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.FilterContinuation;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.web.router.RouteAttributes;
import org.apache.shiro.subject.Subject;

import java.util.Objects;

/**
 * 同步 JSON 路由的原生认证边界；使用 Micronaut 传播作用域恢复各参与线程的前任状态。
 */
@ServerFilter("/**")
@Order(-100)
public final class NativeAuthFilter {
    /**
     * 从认证请求头提取令牌时识别的协议前缀。
     */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 解析请求认证主体的提供器或管理其注册生命周期的作用域。
     */
    private final SubjectProvider subjects;
    /**
     * 为当前请求创建原生认证作用域的组件。
     */
    private final NativeAuth auth;

    /**
     * @param auth     原生全局资源
     * @param subjects 原生 Subject 适配
     */
    public NativeAuthFilter(NativeAuth auth, SubjectProvider subjects) {
        this.subjects = subjects;
        this.auth = auth;
    }

    /**
     * @param request    当前请求
     * @param next       同步下游
     * @param propagated Micronaut 可传播上下文
     * @return 原生响应
     */
    @RequestFilter
    @ExecuteOn(TaskExecutors.BLOCKING)
    public MutableHttpResponse<?> filter(HttpRequest<?> request, FilterContinuation<MutableHttpResponse<?>> next,
                                         io.micronaut.core.propagation.MutablePropagatedContext propagated) {
        var routeMatch = RouteAttributes.getRouteMatch(request);
        if (routeMatch.isEmpty()) {
            return next.request(request).proceed();
        }
        var builder = new Subject.Builder(auth.manager());
        String bearer = request.getHeaders().get("Authorization");
        if (Objects.nonNull(bearer) && bearer.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            builder.sessionId(bearer.substring(BEARER_PREFIX.length()).trim());
        }
        var nativeContext = new NativeContext(builder.buildSubject());
        propagated.add(nativeContext);
        try (var scope = io.micronaut.core.propagation.PropagatedContext.getOrEmpty().plus(nativeContext).propagate()) {
            if (!request.getPath().equals("/auth/login")) {
                var subject = subjects.getSubject();
                if (!subject.isAuthenticated()) {
                    return HttpResponse.unauthorized();
                }
                boolean cancellation = routeMatch
                        .flatMap(route -> route.getAnnotationMetadata().stringValue(Post.class))
                        .filter("/orders/{orderNo}/cancel"::equals).isPresent();
                String permission = cancellation ? "order:cancel" : request.getPath().startsWith("/orders") && request.getMethodName().equals("POST")
                        && !request.getPath().equals("/orders/query") ? "order:write" : "order:read";
                if (!subject.isPermitted(permission)) {
                    return HttpResponse.status(io.micronaut.http.HttpStatus.FORBIDDEN);
                }
            }
            // Micronaut 4.10.26 的 blocking continuation 在 request(...) 中合入 MutablePropagatedContext。
            return next.request(request).proceed();
        }
    }
}
