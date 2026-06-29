package io.ddd4j.annotation.ddd;

import java.lang.annotation.*;

/**
 * 领域转换器标记：标注在对象转换类上，负责领域对象与 DTO/VO 之间的相互转换。
 *
 * <p>区别于 Assembler，Converter 更通用，适用于任何两个对象图之间的映射。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DDDAnnotation
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(ElementType.TYPE)
public @interface DomainConverter {
}
