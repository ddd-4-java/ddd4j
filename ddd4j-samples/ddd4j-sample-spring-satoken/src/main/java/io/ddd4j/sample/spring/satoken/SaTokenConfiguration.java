package io.ddd4j.sample.spring.satoken;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

/** 原生 Sa-Token 权限数据及 MVC 入口拦截；演示只读和可写账号。 */
@Configuration(proxyBeanMethods = false)
public class SaTokenConfiguration implements WebMvcConfigurer {
    /** @return \u5f53\u524d\u6f14\u793a\u8d26\u53f7\u7684\u539f\u751f\u6743\u9650\u63d0\u4f9b\u5668 */
    @Bean
    public StpInterface orderPermissions() {
        return new StpInterface() {
            @Override public List<String> getPermissionList(Object loginId, String loginType) {
                if ("writer".equals(loginId)) {
                    return List.of("order:read", "order:write");
                }
                return "alice".equals(loginId) ? List.of("order:read") : List.of();
            }
            @Override public List<String> getRoleList(Object loginId, String loginType) {
                return "alice".equals(loginId) ? List.of("reader") : List.of();
            }
        };
    }

    /**
     * \u5411 Spring MVC \u6ce8\u518c\u5f53\u524d\u793a\u4f8b\u4f7f\u7528\u7684\u539f\u751f\u8ba4\u8bc1\u62e6\u622a\u5668\u3002
     * @param registry Spring MVC \u7684\u62e6\u622a\u5668\u6ce8\u518c\u5668
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                StpUtil.checkLogin();
                String path = ServletRequestPathUtils.getCachedPathValue(request);
                if (path.startsWith("/orders")) {
                    String permission = ("DELETE".equals(request.getMethod()) || path.endsWith("/cancel")) ? "order:cancel"
                            : ("GET".equals(request.getMethod()) || path.endsWith("/query"))
                            ? "order:read" : "order:write";
                    StpUtil.checkPermission(permission);
                }
                return true;
            }
        }).addPathPatterns("/orders/**", "/auth/me", "/auth/logout");
    }
}
