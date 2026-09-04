package io.ddd4j.spring.annotation;

import io.ddd4j.annotation.ddd.DDDAnnotation;
import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Service;

import java.lang.annotation.*;

/**
 * Spring 领域服务注解。
 * <p>
 * 标记一个类为 DDD 领域服务，自动融合 Spring {@link Service} 元注解，
 * 使被标注的类自动被 Spring 容器扫描并注册为 Service Bean。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DDDAnnotation
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Service
@Inherited
public @interface DomainService {

    @AliasFor(annotation = Service.class, attribute = "value")
    String value() default "";
}
