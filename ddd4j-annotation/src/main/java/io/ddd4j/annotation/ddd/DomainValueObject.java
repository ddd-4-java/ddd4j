package io.ddd4j.annotation.ddd;

import java.lang.annotation.*;

/**
 * 领域值对象标记：标注在值对象类上。
 *
 * <p>值对象是不可变的、用于描述事物特征的轻量对象。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DDDAnnotation
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(ElementType.TYPE)
public @interface DomainValueObject {
}
