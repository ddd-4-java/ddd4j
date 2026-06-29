package io.ddd4j.annotation.api;

import java.lang.annotation.*;

/**
 * Api模块注解
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Inherited
public @interface ApiModule {

    /**
     * 操作模块
     */
    String module() default "";

    /**
     * 业务名称
     */
    String business() default "";

}
