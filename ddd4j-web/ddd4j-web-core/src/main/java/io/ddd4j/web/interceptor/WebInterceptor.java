package io.ddd4j.web.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Web 拦截器 SPI（纯 Java，零框架依赖）
 * <p>
 * 各框架适配层（Spring MVC / Quarkus RESTEasy / Javalin）提供具体实现。
 *
 * @author Loong Wan
 * @公众号 PartMe.AI
 * @since 3.4.x
 */
public interface WebInterceptor {

    /** 拦截器顺序（数字越小越先执行） */
    int getOrder();

    /** 请求预处理 */
    boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception;
}
