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

    /**
     * 获取值。
     * @return 结果数值。
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    /**
     * postProcessAfterInitialization() 方法。
     * @param bean 对象参数。
     * @param beanName 字符串参数。
     * @return 结果对象。
     * @throws BeansException 出现异常时抛出。
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof SubjectProvider) {
            SubjectKit.register((SubjectProvider) bean);
        }
        return bean;
    }
}
