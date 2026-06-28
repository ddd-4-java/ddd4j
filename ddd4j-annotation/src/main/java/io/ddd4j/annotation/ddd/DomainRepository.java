package io.ddd4j.annotation.ddd;

import java.lang.annotation.*;

/**
 * 领域模型标记-仓储接口（纯 Java 注解，零框架依赖）
 *
 * <p>各框架适配层自行识别此注解并注册为 Bean。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@DDDAnnotation
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {ElementType.TYPE})
public @interface DomainRepository {
}