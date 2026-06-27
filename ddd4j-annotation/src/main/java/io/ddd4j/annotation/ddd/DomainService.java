package io.ddd4j.annotation.ddd;

import org.springframework.stereotype.Service;

import java.lang.annotation.*;

/**
 * 领域模型标记-领域服务
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@DDDAnnotation
@Documented
@Service
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {ElementType.TYPE})
public @interface DomainService {
}