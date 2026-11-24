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

/** 原生 Sa-Token 权限数据及 MVC 入口拦截；演示账号仅授予读取订单权限。 */
@Configuration(proxyBeanMethods = false)
public class SaTokenConfiguration implements WebMvcConfigurer {
    /**
     * @return 当前演示账号的原生权限提供器
     */
    @Bean
    public StpInterface orderPermissions() {
        return new StpInterface() {
            @Override public List<String> getPermissionList(Object loginId, String loginType) {
                return "alice".equals(loginId) ? List.of("order:read") : List.of();
            }
            @Override public List<String> getRoleList(Object loginId, String loginType) {
                return "alice".equals(loginId) ? List.of("reader") : List.of();
            }
        };
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                StpUtil.checkLogin();
                String path = ServletRequestPathUtils.getCachedPathValue(request);
                if (path.startsWith("/orders")) {
                    String permission = "DELETE".equals(request.getMethod()) ? "order:cancel"
                            : ("GET".equals(request.getMethod()) || path.endsWith("/query"))
                            ? "order:read" : "order:write";
                    StpUtil.checkPermission(permission);
                }
                return true;
            }
        }).addPathPatterns("/orders/**", "/auth/me", "/auth/logout");
    }
}
