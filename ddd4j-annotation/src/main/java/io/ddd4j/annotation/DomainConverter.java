package io.ddd4j.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * 领域模型标记-模型转换器
 */
@DDDAnnotation
@Documented
@Component
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {ElementType.TYPE})
public @interface DomainConverter {
}