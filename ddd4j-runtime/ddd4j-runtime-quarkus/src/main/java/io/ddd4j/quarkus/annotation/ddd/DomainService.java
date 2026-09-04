package io.ddd4j.annotation;

import org.springframework.stereotype.Service;

import java.lang.annotation.*;

/**
 * 领域模型标记-领域服务
 */
@DDDAnnotation
@Documented
@Service
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {ElementType.TYPE})
public @interface DomainService {
}