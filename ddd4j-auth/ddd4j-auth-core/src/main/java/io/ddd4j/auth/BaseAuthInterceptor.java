package io.ddd4j.auth;

/**
 * 认证拦截器 SPI（纯 Java）
 * <p>
 * 各框架适配层（Spring MVC/WebFlux/Quarkus RESTEasy/Javalin）提供具体实现。
 *
 * @author Loong Wan
 * @公众号 PartMe.AI
 * @since 3.4.x
 */
public interface BaseAuthInterceptor {

    /**
     * 拦截器顺序（数字越小越先执行）
     */
    int getOrder();

    /**
     * 是否匹配
     */
    boolean matches(String path);

    /**
     * 鉴权逻辑：返回 true 表示通过，false 表示拒绝
     */
    boolean authorize(Subject subject, String path);
}
