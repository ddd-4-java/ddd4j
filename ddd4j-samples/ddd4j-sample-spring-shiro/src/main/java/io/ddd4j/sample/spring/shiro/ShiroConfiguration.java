package io.ddd4j.sample.spring.shiro;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.realm.text.IniRealm;
import org.apache.shiro.config.Ini;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.shiro.web.servlet.AbstractShiroFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 使用原生 Shiro Realm、Servlet Subject 绑定及 finally 恢复上下文。 */
@Configuration(proxyBeanMethods = false)
public class ShiroConfiguration implements WebMvcConfigurer {
    /**
     * @return 独立演示安全管理器；关闭时释放原生资源
     */
    @Bean(destroyMethod = "destroy")
    public DefaultWebSecurityManager securityManager() {
        Ini ini = new Ini();
        ini.load("[users]\nalice = demo-password, reader\nwriter = demo-password, writer\n[roles]\nreader = order:read\nwriter = order:read, order:write\n");
        return new DefaultWebSecurityManager(new IniRealm(ini));
    }
    /**
     * @param manager 原生安全管理器
     * @return 真正创建及释放请求 Subject 的 Shiro 过滤器
     */
    @Bean
    public FilterRegistrationBean<AbstractShiroFilter> shiroFilter(DefaultWebSecurityManager manager) {
        AbstractShiroFilter filter = new AbstractShiroFilter() { };
        filter.setSecurityManager(manager);
        FilterRegistrationBean<AbstractShiroFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(-100);
        return registration;
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
                var subject = SecurityUtils.getSubject();
                if (!subject.isAuthenticated()) {
                    throw new AuthenticationException("login required");
                }
                String path = ServletRequestPathUtils.getCachedPathValue(request);
                if (path.startsWith("/orders")) {
                    String permission = ("DELETE".equals(request.getMethod()) || path.endsWith("/cancel")) ? "order:cancel"
                            : ("GET".equals(request.getMethod()) || path.endsWith("/query"))
                            ? "order:read" : "order:write";
                    subject.checkPermission(permission);
                }
                return true;
            }
        }).addPathPatterns("/orders/**", "/auth/me", "/auth/logout");
    }
}
