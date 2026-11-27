package io.ddd4j.spring.annotation;

import io.ddd4j.annotation.ddd.DDDAnnotation;
import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * Spring 领域转换器注解。
 * <p>
 * 标记一个类为 DDD 领域转换器，自动融合 Spring {@link Component} 元注解，
 * 使被标注的类自动被 Spring 容器扫描并注册为 Component Bean。
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
public @interface DomainConverter {

    /** @return Spring \u7ec4\u4ef6\u540d\u79f0\uff1b\u7a7a\u5b57\u7b26\u4e32\u8868\u793a\u4f7f\u7528\u9ed8\u8ba4\u540d\u79f0 */
    @AliasFor(annotation = Component.class, attribute = "value")
    String value() default "";
}
