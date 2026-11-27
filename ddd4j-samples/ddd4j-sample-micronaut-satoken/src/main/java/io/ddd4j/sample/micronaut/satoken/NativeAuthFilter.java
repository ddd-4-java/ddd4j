package io.ddd4j.sample.micronaut.satoken;

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

/**
 * 同步 JSON 路由的原生认证边界；使用 Micronaut 传播作用域恢复各参与线程的前任状态。
 */
@ServerFilter("/**")
@Order(-100)
public final class NativeAuthFilter {
    /** \u89e3\u6790\u8bf7\u6c42\u8ba4\u8bc1\u4e3b\u4f53\u7684\u63d0\u4f9b\u5668\u6216\u7ba1\u7406\u5176\u6ce8\u518c\u751f\u547d\u5468\u671f\u7684\u4f5c\u7528\u57df\u3002 */
    private final SubjectProvider subjects;

    /**
     * @param auth     原生全局资源
     * @param subjects 原生 Subject 适配
     */
    public NativeAuthFilter(NativeAuth auth, SubjectProvider subjects) {
        this.subjects = subjects;
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
        var nativeContext = new NativeContext(new SaHttpContext(request), SaHttpContext.response(HttpResponse.ok()), SaHttpContext.storage(request));
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
                if (!cn.dev33.satoken.stp.StpUtil.hasPermission(permission)) {
                    return HttpResponse.status(io.micronaut.http.HttpStatus.FORBIDDEN);
                }
            }
            // Micronaut 4.10.26 的 blocking continuation 在 request(...) 中合入 MutablePropagatedContext。
            return next.request(request).proceed();
        }
    }
}
