package io.ddd4j.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public @interface WebSocketMapping {

    String value() default "";

}
