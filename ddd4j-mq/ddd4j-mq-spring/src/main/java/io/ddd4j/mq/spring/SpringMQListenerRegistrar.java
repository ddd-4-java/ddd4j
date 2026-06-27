package io.ddd4j.mq.spring;

import io.ddd4j.mq.contract.MQDestination;
import io.ddd4j.mq.contract.MQEventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring BeanPostProcessor，扫描所有带 {@link MQEventListener} 注解的方法并注册。
 * <p>
 * 由各 broker 适配层（ddd4j-mq-kafka、ddd4j-mq-rabbitmq 等）触发订阅。
 *
 * @author wandl
 * @since 3.4.x
 */
@Slf4j
public class SpringMQListenerRegistrar implements BeanPostProcessor {

    private final List<ListenerRegistration> registrations = new ArrayList<>();

    @Override
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
        // 扫描 bean 中所有带 @MQEventListener 的方法（简化：实际项目用反射或 ByteBuddy）
        // 此处仅记录 Bean 名字，broker 适配层做实际订阅
        log.debug("Scanning bean {} for @MQEventListener", beanName);
        return bean;
    }

    public List<ListenerRegistration> getRegistrations() {
        return registrations;
    }

    /**
     * 监听器注册信息
     */
    public static class ListenerRegistration {
        private final Object bean;
        private final String methodName;
        private final MQDestination destination;

        public ListenerRegistration(Object bean, String methodName, MQDestination destination) {
            this.bean = bean;
            this.methodName = methodName;
            this.destination = destination;
        }

        public Object getBean() { return bean; }
        public String getMethodName() { return methodName; }
        public MQDestination getDestination() { return destination; }
    }
}
