package io.ddd4j.annotation.ddd;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * 领域模型标记-模型装配器
 */
@DDDAnnotation
@Documented
@Component
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {ElementType.TYPE})
public @interface DomainAssembler {
}