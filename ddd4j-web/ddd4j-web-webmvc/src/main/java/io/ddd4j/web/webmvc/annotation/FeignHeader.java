package io.ddd4j.web.webmvc.annotation;

import java.lang.annotation.*;

/**
 * Feign 内部请求
 * 自动填充 内部请求标识符 header={"from":"Y}
 * <p>
 * 优先于读取请web请求头 租户、系统id;如果请求头没有；根据配置开启自动从 TenantContextHolder 填充租户ID
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Target(ElementType.METHOD)
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface FeignHeader {


    /**
     * 是否自动从上下文 TenantContextHolder 读取系统id sysem-id；
     * 如果方法入参已经传递了；该方法也会覆盖入参请求头
     */
    boolean autoFillSystemId() default true;

    /**
     * 是否自动从上下文 TenantContextHolder 读取系统tenant-id;
     * 如果方法入参已经传递了；该方法也会覆盖入参请求头
     */
    boolean autoFillTenantId() default true;


    /**
     * 是否使用 Http请求头的参数；该参数会覆盖 autoFillTenantId  autoFillSysemId
     * 往feign的请求头自动填充该参数 {"tenant-id","system-id","third-session","enterprise-id","shop-id","app-id","switch-tenant-id","Authorization",
     * "client-type", "own-language"};
     * <p>
     * 当同一个认证体系的时候 可以开启；比如： mall-admin 调用 upms 的时候
     */
    boolean useWebRequestHeader() default false;
}
