package io.ddd4j.mq.annotation;

import java.lang.annotation.*;

/**
 * MQ事件监听器，配合MQEvent使用
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MQEventListener {

    // 消费者组，不设置默认为当前启动的应用名${spring.application.name}，一般多实例同一个消费者需要保持一致，避免同一业务重复消费
    String group() default "";

    // 命名空间
    String namespace() default "";

    // 主题，大分类，消费线程隔离，用于区分不同业务。也可作为小分类使用
    String topic() default "DEFAULT";

    // 标签列表，小分类，同一主题下共享消费线程，支持：通配符*、或||、非-，支持复合表达式如：A || B -C
    String tags() default "*";

    // 支持处理的列表，策略模式。线程非隔离，即使topic和tags符合，最后也要符合该策略
    String[] supports() default "*";

    // 连接符，默认{namespace}:{topic}:{tags}
    String concat() default "";
}