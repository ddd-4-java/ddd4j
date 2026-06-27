package io.ddd4j.annotation.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 内部调用注解（纯 Java）
 * <p>
 * 标注在方法上，表示该方法仅允许内部服务间调用（微服务场景）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.4.x
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Inside {

    /** 允许的内部服务名（空表示不限制） */
    String[] services() default {};
}
