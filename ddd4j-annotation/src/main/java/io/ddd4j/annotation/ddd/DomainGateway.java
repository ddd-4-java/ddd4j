package io.ddd4j.annotation.ddd;

import java.lang.annotation.*;

/**
 * 领域网关标记：标注在外部系统访问类上（如 HTTP 调用、第三方 SDK 封装等）。
 *
 * <p>用于标识跨限界上下文通信的入口/出口点。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DDDAnnotation
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(ElementType.TYPE)
public @interface DomainGateway {
}
