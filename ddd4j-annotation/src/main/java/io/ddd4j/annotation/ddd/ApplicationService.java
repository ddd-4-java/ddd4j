package io.ddd4j.annotation.ddd;

import org.springframework.stereotype.Service;

import java.lang.annotation.*;

/**
 * DDD注解-应用层服务
 * @author mingjie
 * @since 2022/3/20
 * @see <a href="https://github.com/smingjie/bbq-ddd">bbq-ddd</a>
 */
@DDDAnnotation
@Documented
@Service
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {ElementType.TYPE})
public @interface ApplicationService {
}