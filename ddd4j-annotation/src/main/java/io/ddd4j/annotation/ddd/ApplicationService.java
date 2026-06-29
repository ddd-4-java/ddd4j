package io.ddd4j.annotation.ddd;

import java.lang.annotation.*;

/**
 * 应用服务标记：标注在应用服务类上，承担用例编排职责。
 *
 * <p>区别于领域服务，应用服务负责协调多个领域对象完成业务流程，
 * 通常对应一个用例（Use Case）或应用故事（Story）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DDDAnnotation
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(ElementType.TYPE)
public @interface ApplicationService {
}
