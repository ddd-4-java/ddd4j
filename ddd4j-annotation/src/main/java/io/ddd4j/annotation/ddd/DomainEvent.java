package io.ddd4j.annotation.ddd;

import java.lang.annotation.*;

/**
 * 领域事件标记：标注在领域事件类上。
 *
 * <p>领域事件表示领域中发生的事情，通常用于 CQRS/ES 架构中记录聚合状态变更。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DDDAnnotation
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(ElementType.TYPE)
public @interface DomainEvent {
}
