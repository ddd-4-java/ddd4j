package io.ddd4j.annotation.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller or handler method as internally accessible.
 *
 * <p>Framework adapters may use this annotation to bypass internal bearer-token checks
 * for endpoints that already have an explicit internal access contract.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface InternalAccess {
}
