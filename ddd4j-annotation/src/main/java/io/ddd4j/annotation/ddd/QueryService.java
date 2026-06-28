package io.ddd4j.annotation.ddd;

import java.lang.annotation.*;

/**
 * 应用层标记-查询服务（纯 Java 注解，零框架依赖）。
 *
 * <p>标记 CQRS 读侧的查询服务，对应 COLA 架构的 {@code application.query} 包。
 * 查询服务负责：
 * <ul>
 *   <li>接收查询对象（Query）</li>
 *   <li>从读模型（View/Projection）查询数据</li>
 *   <li>不涉及业务逻辑和事务</li>
 * </ul>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @see CommandExecutor
 * @since 3.4.x
 */
@DDDAnnotation
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {ElementType.TYPE})
public @interface QueryService {
}
