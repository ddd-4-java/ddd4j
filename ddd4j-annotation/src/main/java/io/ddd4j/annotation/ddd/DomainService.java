package io.ddd4j.annotation.ddd;

import java.lang.annotation.*;

/**
 * 领域服务标记：标注在业务编排类上，表明该类承担领域服务职责。
 *
 * <p>区别于 Spring 的 {@code @Service}，本注解仅作语义标记，
 * 不携带任何框架关联。框架适配层（如 ddd4j-spring）负责将其注册为 Spring Bean。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DDDAnnotation
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(ElementType.TYPE)
public @interface DomainService {
}
