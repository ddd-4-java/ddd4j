package io.ddd4j.web.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Web 拦截器 SPI（纯 Java，零框架依赖）
 * <p>
 * 各框架适配层（Spring MVC / Quarkus RESTEasy / Javalin）提供具体实现。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.4.x
 */
public interface WebInterceptor {

    /** 拦截器顺序（数字越小越先执行） */
    int getOrder();

    /** 请求预处理 */
    boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception;

    /** 请求完成后清理（默认空实现，子类可重写） */
    default void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 空实现
    }
}
