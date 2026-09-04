package io.ddd4j.spring.annotation;

import io.ddd4j.annotation.ddd.DDDAnnotation;
import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * Spring 领域实体注解。
 * <p>
 * 标记一个类为 DDD 领域实体，自动融合 Spring {@link Component} 元注解，
 * 使被标注的类自动被 Spring 容器扫描并注册为 Component Bean。
 * 可通过 {@link #aggregateRoot()} 指定是否为聚合根。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DDDAnnotation
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
@Inherited
public @interface DomainEntity {

    boolean aggregateRoot() default false;

    @AliasFor(annotation = Component.class, attribute = "value")
    String value() default "";
}
