package io.ddd4j.boot.web.auth.annotation;

import io.ddd4j.boot.web.auth.config.BaseAuthConfig;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(BaseAuthConfig.class)
public @interface EnableBaseAuth {
}
