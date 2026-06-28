package io.ddd4j.annotation.ddd;

import java.lang.annotation.*;

/**
 * 领域模型标记-领域服务（纯 Java 注解，零框架依赖）
 *
 * <p>各框架适配层自行识别此注解并注册为 Bean：
 * <ul>
 *   <li>Spring: {@code ddd4j-spring} 通过 {@code DddAnnotationBeanPostProcessor} 注册</li>
 *   <li>Quarkus: {@code ddd4j-quarkus} 通过 CDI 扫描注册</li>
 *   <li>Javalin/Guice: {@code ddd4j-javalin} 通过 Guice Module 绑定</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@DDDAnnotation
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {ElementType.TYPE})
public @interface DomainService {
}