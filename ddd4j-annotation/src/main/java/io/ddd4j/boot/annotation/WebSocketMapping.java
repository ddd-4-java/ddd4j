package io.ddd4j.boot.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface WebSocketMapping {

    String value() default "";

}
