package io.ddd4j.annotation.ddd;

import java.lang.annotation.*;

/**
 * 领域装配器标记：标注在聚合组装器类上，负责聚合根的构建与还原。
 *
 * <p>通常用于将 PO/Spec 转换为聚合根，或将聚合根拆解为 DTO。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DDDAnnotation
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(ElementType.TYPE)
public @interface DomainAssembler {
}
