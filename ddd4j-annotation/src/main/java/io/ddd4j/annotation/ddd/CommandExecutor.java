package io.ddd4j.annotation.ddd;

import java.lang.annotation.*;

/**
 * 命令执行器标记：标注在命令处理类上，通常与 CQRS 命令端配合使用。
 *
 * <p>用于标识处理命令（Command）的组件。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@DDDAnnotation
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(ElementType.TYPE)
public @interface CommandExecutor {
}
