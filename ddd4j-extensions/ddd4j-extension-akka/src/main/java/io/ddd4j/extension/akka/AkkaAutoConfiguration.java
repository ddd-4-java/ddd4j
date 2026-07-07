package io.ddd4j.extension.akka;

import akka.actor.ActorSystem;
import io.ddd4j.extension.akka.actor.SpringExtension;

import java.util.Objects;

/**
 * Akka Auto Configuration（纯 Java 工厂，无 Spring 依赖）。
 * <p>
 * Actor 实例的查找由 {@link SpringExtension} / {@link io.ddd4j.extension.akka.actor.SpringActorProducer}
 * 通过 {@link io.ddd4j.core.context.Contexts} 完成，无需在此注入应用上下文。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class AkkaAutoConfiguration {

    public AkkaAutoConfiguration() {
    }

    /**
     * 创建并配置 ActorSystem。
     *
     * @param properties Akka 配置
     * @return ActorSystem 实例
     */
    public ActorSystem actorSystem(AkkaProperties properties) {
        return ActorSystem.create(properties.getName());
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof AkkaAutoConfiguration)) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(AkkaAutoConfiguration.class);
    }

    @Override
    public String toString() {
        return "AkkaAutoConfiguration()";
    }

}
