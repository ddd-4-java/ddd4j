package io.ddd4j.annotation.ddd;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * 领域模型标记-模型装配器
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@DDDAnnotation
@Documented
@Component
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {ElementType.TYPE})
public @interface DomainAssembler {
}