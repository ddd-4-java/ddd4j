package io.ddd4j.annotation.ddd;

import java.lang.annotation.*;

/**
 * 领域模型标记-实体。
 *
 * <p>Retention 为 {@link RetentionPolicy#RUNTIME}（自 3.4.x 起，原为 SOURCE），
 * 使 ArchUnit 校验和 AOP 切面可在运行时读取。
 */
@DDDAnnotation
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(value = {ElementType.TYPE, ElementType.FIELD})
public @interface DomainEntity {

    /**
     * 是否是聚合根
     */
    boolean aggregateRoot() default false;

}