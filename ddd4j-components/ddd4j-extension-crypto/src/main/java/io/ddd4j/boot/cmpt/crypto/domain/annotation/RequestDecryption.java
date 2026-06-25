package io.ddd4j.boot.cmpt.crypto.domain.annotation;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface RequestDecryption {
}
