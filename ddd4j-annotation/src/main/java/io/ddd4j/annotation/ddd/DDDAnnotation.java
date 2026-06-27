package io.ddd4j.annotation.ddd;

import java.lang.annotation.*;

/**
 * 领域模型标记（元注解）。
 *
 * <p>所有 DDD 相关注解（{@link DomainEntity}、{@link DomainValueObject}、{@link DomainEvent} 等）
 * 都以此注解作为元标记，便于运行时批量发现 DDD 构造型。
 *
 * <p>Retention 为 {@link RetentionPolicy#RUNTIME}（自 3.4.x 起，原为 SOURCE），
 * 使得 ArchUnit 校验和 AOP 切面可以在运行时通过反射读取这些注解。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2022/3/19
 * @see <a href="https://github.com/smingjie/bbq-ddd">bbq-ddd</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(value = {ElementType.TYPE,ElementType.METHOD,ElementType.FIELD})
public @interface DDDAnnotation {

}