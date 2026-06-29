package io.ddd4j.annotation.ddd;

import java.lang.annotation.*;

/**
 * 领域仓储标记：标注在仓储接口或实现类上，表明该类承担仓储职责。
 *
 * <p>区别于 Spring 的 {@code @Repository}，本注解仅作语义标记，
 * 不携带任何框架关联。框架适配层负责将其注册为对应框架的组件。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DDDAnnotation
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(ElementType.TYPE)
public @interface DomainRepository {
}
