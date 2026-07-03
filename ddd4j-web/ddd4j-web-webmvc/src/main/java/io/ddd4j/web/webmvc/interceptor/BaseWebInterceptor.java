package io.ddd4j.web.webmvc.interceptor;

import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 基础拦截器抽象类。
 * <p>实现了 {@link HandlerInterceptor} 和 {@link Ordered} 接口，提供默认的拦截路径配置。
 * 实现该接口后需要加载到 Spring 容器中。</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public abstract class BaseWebInterceptor implements HandlerInterceptor, Ordered {

    /**
     * 获取拦截的请求路径模式。
     *
     * @return 路径模式数组，默认拦截所有路径 "/**"
     */
    public String[] pathPatterns() {
        return new String[]{"/**"};
    }

    /**
     * 获取不拦截的请求路径模式。
     *
     * @return 排除路径模式数组，默认排除 "/error"
     */
    public String[] excludePathPatterns() {
        return new String[]{"/error"};
    }
}