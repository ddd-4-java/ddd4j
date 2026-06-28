package io.ddd4j.annotation.ddd;

import java.lang.annotation.*;

/**
 * DDD注解-应用层服务（纯 Java 注解，零框架依赖）
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2022/3/20
 * @see <a href="https://github.com/smingjie/bbq-ddd">bbq-ddd</a>
 */
@DDDAnnotation
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {ElementType.TYPE})
public @interface ApplicationService {
}