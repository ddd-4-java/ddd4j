package io.ddd4j.guice.annotation.ddd;

import com.google.inject.Singleton;
import io.ddd4j.annotation.ddd.DDDAnnotation;

import java.lang.annotation.*;

/**
 * Javalin 领域实体
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DDDAnnotation
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Singleton
@Inherited
public @interface DomainEntity {

    /**
     * \u662f\u5426\u662f\u805a\u5408\u6839
     *
     * @return \u662f\u805a\u5408\u6839\u65f6\u4e3a true
     */
    boolean aggregateRoot() default false;
}
