package io.ddd4j.boot.annotation;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface RedisTopic {

    String pattern() default "";

    String channel() default "";

}
