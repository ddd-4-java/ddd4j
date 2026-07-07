package io.ddd4j.extension.akka.actor;


import akka.actor.AbstractExtensionId;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;
import akka.actor.Props;

/**
 * Akka 扩展：用于创建注册在 {@link io.ddd4j.core.context.Contexts} 中的 Actor Bean（纯 Java，无 Spring 依赖）。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class SpringExtension extends AbstractExtensionId<SpringExtension.SpringExt> {

    public static final SpringExtension SPRING_EXTENSION_PROVIDER = new SpringExtension();

    public SpringExtension() {
    }

    @Override
    public SpringExt createExtension(ExtendedActorSystem system) {
        return new SpringExt();
    }

    /**
     * Akka 扩展实例：Actor 实例由 {@link SpringActorProducer} 通过
     * {@link io.ddd4j.core.context.Contexts} 按 bean 名查找。
     */
    public static class SpringExt implements Extension {

        public SpringExt() {
        }

        /**
         * 创建 Actor Props。
         *
         * @param actorBeanName Actor Bean 名称（对应注册到 Contexts 中的 bean 名）
         * @return Props 对象
         */
        public Props props(String actorBeanName) {
            return Props.create(SpringActorProducer.class, new Object[]{actorBeanName});
        }
    }

}
