package io.ddd4j.quarkus.annotation.ddd;

import io.ddd4j.annotation.ddd.DDDAnnotation;
import jakarta.enterprise.context.ApplicationScoped;

import java.lang.annotation.*;

/**
 * Quarkus 领域仓储。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DDDAnnotation
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ApplicationScoped
@Inherited
public @interface DomainRepository {
}
