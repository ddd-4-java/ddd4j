package io.ddd4j.auth.spring;

import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKit;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;

/**
 * 将 Spring 容器中的 {@link SubjectProvider} 注册到 {@link SubjectKit}。
 */
public class SubjectRegistrar implements BeanPostProcessor, Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof SubjectProvider) {
            SubjectKit.register((SubjectProvider) bean);
        }
        return bean;
    }
}
