package io.ddd4j.annotation.ddd;

import java.lang.annotation.*;

/**
 * 领域实体标记：标注在领域实体类上。
 *
 * <p>领域实体具有唯一标识，可在整个生命周期中保持连续性。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DDDAnnotation
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(ElementType.TYPE)
public @interface DomainEntity {
}
