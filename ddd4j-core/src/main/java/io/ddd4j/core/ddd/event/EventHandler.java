package io.ddd4j.core.ddd.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标记聚合根中负责应用领域事件的方法。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventHandler {
    /** 历史回放时是否忽略该处理器。 */
    boolean ignoreOnReplay() default false;
}
