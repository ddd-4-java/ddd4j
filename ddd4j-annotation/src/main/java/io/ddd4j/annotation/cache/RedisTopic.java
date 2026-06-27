package io.ddd4j.annotation.cache;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
/**
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public @interface RedisTopic {

    String pattern() default "";

    String channel() default "";

}
