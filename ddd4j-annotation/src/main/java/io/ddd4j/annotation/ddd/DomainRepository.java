package io.ddd4j.annotation.ddd;

import org.springframework.stereotype.Repository;

import java.lang.annotation.*;

/**
 * 领域模型标记-仓储接口
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@DDDAnnotation
@Documented
@Repository
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {ElementType.TYPE})
public @interface DomainRepository {
}