package io.ddd4j.annotation;

import java.lang.annotation.*;

/**
 * ddd4j 总契约标记：所有 ddd4j 提供的注解（包括 ddd4j-annotation / ddd4j-runtime-spring /
 * ddd4j-runtime-guice 注解 / ddd4j-runtime-quarkus）都应标注此契约。
 *
 * <p>作用：
 * <ul>
 *   <li>统一被 ArchUnit 规则识别（"所有 ddd4j 注解都标注了 @Contract"）</li>
 *   <li>提供 ddd4j 注解体系的元数据基础（版本演进、归档分析等）</li>
 *   <li>区分"项目自定义注解"与"ddd4j 框架注解"</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface Contract {
}
