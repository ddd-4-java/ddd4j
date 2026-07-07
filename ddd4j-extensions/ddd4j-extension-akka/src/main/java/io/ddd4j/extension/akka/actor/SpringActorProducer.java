package io.ddd4j.extension.akka.actor;


import akka.actor.Actor;
import akka.actor.IndirectActorProducer;
import io.ddd4j.core.context.Contexts;

import java.util.Optional;

/**
 * 该类由 Akka 的扩展用于创建 Actor 实例（纯 Java，无 Spring 依赖）。
 * <p>
 * Actor 实例通过 {@link Contexts} 查找：使用
 * {@code AkkaAutoConfiguration.ACTOR_BEAN_KEY_PREFIX + beanActorName} 作为 SPI key。
 * 框架适配层在启动期应将 Actor 注册到 {@link Contexts} 中。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class SpringActorProducer implements IndirectActorProducer {

    /**
     * Actor 在 {@link Contexts} 中查找时使用的 SPI key 前缀。
     */
    public static final String ACTOR_BEAN_KEY_PREFIX = "ddd4j.akka.actor.";

    private final String beanActorName;

    /**
     * 构造函数。
     *
     * @param beanActorName Actor Bean 名称
     */
    public SpringActorProducer(String beanActorName) {
        this.beanActorName = beanActorName;
    }

    /**
     * 创建 Actor 实例。
     *
     * @return Actor 实例
     */
    @Override
    public Actor produce() {
        Optional<Actor> actor = Contexts.inject(ACTOR_BEAN_KEY_PREFIX + this.beanActorName, Actor.class);
        return actor.orElseThrow(() -> new IllegalStateException(
                "Actor bean not found: name=" + this.beanActorName
                        + ". Ensure the actor is registered into Contexts under key '"
                        + ACTOR_BEAN_KEY_PREFIX + this.beanActorName + "'."));
    }

    /**
     * 获取 Actor 类类型。
     * <p>
     * 通过 {@link Contexts} 已注册的实例解析其运行时类型。
     *
     * @return Actor 的 Class 对象
     */
    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends Actor> actorClass() {
        Actor actor = Contexts.inject(ACTOR_BEAN_KEY_PREFIX + this.beanActorName, Actor.class).orElse(null);
        return actor == null ? Actor.class : (Class<? extends Actor>) actor.getClass();
    }

}
