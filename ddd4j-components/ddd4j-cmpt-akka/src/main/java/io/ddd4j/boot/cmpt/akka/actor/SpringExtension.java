package io.ddd4j.boot.cmpt.akka.actor;


import akka.actor.AbstractExtensionId;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;
import akka.actor.Props;
import org.springframework.context.ApplicationContext;

/**
 * This class is used by the Spring extension of Akka to create the actor beans.
 */
public class SpringExtension extends AbstractExtensionId<SpringExtension.SpringExt> {

    public static final SpringExtension SPRING_EXTENSION_PROVIDER = new SpringExtension();

    public SpringExtension() {
    }

    public SpringExt createExtension(ExtendedActorSystem system) {
        return new SpringExt();
    }

    public static class SpringExt implements Extension {
        private volatile ApplicationContext applicationContext;

        public SpringExt() {
        }

        public void initialize(ApplicationContext applicationContext) {
            this.applicationContext = applicationContext;
        }

        public Props props(String actorBeanName) {
            return Props.create(SpringActorProducer.class, new Object[]{this.applicationContext, actorBeanName});
        }
    }

}
