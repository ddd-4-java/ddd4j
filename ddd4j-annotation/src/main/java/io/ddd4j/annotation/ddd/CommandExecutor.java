package io.ddd4j.annotation.ddd;

import java.lang.annotation.*;

/**
 * 应用层标记-命令执行器（纯 Java 注解，零框架依赖）。
 *
 * <p>标记 CQRS 写侧的命令执行器，对应 COLA 架构的 {@code application.executor} 包。
 * 命令执行器负责：
 * <ul>
 *   <li>接收命令对象（Command）</li>
 *   <li>编排领域服务和聚合根</li>
 *   <li>管理事务边界</li>
 *   <li>发布领域事件</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see QueryService
 * @since 3.4.x
 */
@DDDAnnotation
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {ElementType.TYPE})
public @interface CommandExecutor {
}
