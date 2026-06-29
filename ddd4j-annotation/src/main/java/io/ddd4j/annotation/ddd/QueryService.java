package io.ddd4j.annotation.ddd;

import java.lang.annotation.*;

/**
 * 查询服务标记：标注在查询处理类上，通常与 CQRS 查询端配合使用。
 *
 * <p>用于标识处理查询（Query）的组件。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DDDAnnotation
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(ElementType.TYPE)
public @interface QueryService {
}
