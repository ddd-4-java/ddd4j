package io.ddd4j.extension.akka.actor;


import akka.actor.Actor;
import akka.actor.IndirectActorProducer;
import org.springframework.context.ApplicationContext;

/**
 * This class is used by the Spring extension of Akka to create the actor beans.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
public class SpringActorProducer implements IndirectActorProducer {

    private final ApplicationContext applicationContext;
    private final String beanActorName;

    /**
     * 构造函数
     *
     * @param applicationContext Spring 应用上下文
     * @param beanActorName      Actor Bean 名称
     */
    public SpringActorProducer(ApplicationContext applicationContext, String beanActorName) {
        this.applicationContext = applicationContext;
        this.beanActorName = beanActorName;
    }

    /**
     * 创建 Actor 实例
     *
     * @return Actor 实例
     */
    public Actor produce() {
        return (Actor) this.applicationContext.getBean(this.beanActorName);
    }

    /**
     * 获取 Actor 类类型
     *
     * @return Actor 的 Class 对象
     */
    public Class<? extends Actor> actorClass() {
        return (Class<? extends Actor>) this.applicationContext.getType(this.beanActorName);
    }

}
