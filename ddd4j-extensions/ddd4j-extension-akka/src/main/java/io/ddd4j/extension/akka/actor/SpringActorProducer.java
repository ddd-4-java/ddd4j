package io.ddd4j.extension.akka.actor;


import akka.actor.Actor;
import akka.actor.IndirectActorProducer;
import org.springframework.context.ApplicationContext;

/**
 * This class is used by the Spring extension of Akka to create the actor beans.
 */
public class SpringActorProducer implements IndirectActorProducer {

    private final ApplicationContext applicationContext;
    private final String beanActorName;

    public SpringActorProducer(ApplicationContext applicationContext, String beanActorName) {
        this.applicationContext = applicationContext;
        this.beanActorName = beanActorName;
    }

    public Actor produce() {
        return (Actor) this.applicationContext.getBean(this.beanActorName);
    }

    public Class<? extends Actor> actorClass() {
        return (Class<? extends Actor>) this.applicationContext.getType(this.beanActorName);
    }

}
