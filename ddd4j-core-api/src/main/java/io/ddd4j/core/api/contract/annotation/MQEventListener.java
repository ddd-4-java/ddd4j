package io.ddd4j.core.api.contract.annotation;

import java.lang.annotation.*;

/**
 * MQ 事件监听器注解
 *
 * @author Jensen
 * @公众号 架构师修行录
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MQEventListener {

    /**
     * 消费组
     */
    String group() default "";

    /**
     * 命名空间
     */
    String namespace() default "";

    /**
     * 主题
     */
    String topic() default "";

    /**
     * 标签（支持多个，用 || 分隔）
     */
    String tags() default "*";

    /**
     * 策略匹配项
     */
    String[] supports() default {"*"};
}
