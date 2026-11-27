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
     * \u4e3a\u8ba4\u8bc1\u4e3b\u4f53\u767b\u8bb0\u5668\u63d0\u4f9b Spring \u540e\u5904\u7406\u5668\u6392\u5e8f\u3002
     * @return \u9ad8\u4e8e\u666e\u901a\u540e\u5904\u7406\u5668\u7684\u987a\u5e8f\u503c
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    /**
     * \u5c06\u521d\u59cb\u5316\u5b8c\u6bd5\u7684\u8ba4\u8bc1\u4e3b\u4f53\u63d0\u4f9b\u5668\u767b\u8bb0\u5230 DDD4J \u4e3b\u4f53\u5165\u53e3\u3002
     * @param bean Spring \u5b8c\u6210\u521d\u59cb\u5316\u7684 Bean
     * @param beanName Bean \u5728\u5bb9\u5668\u4e2d\u7684\u6ce8\u518c\u540d\u79f0
     * @return \u539f\u59cb Spring Bean
     * @throws BeansException \u5904\u7406\u5f53\u524d Bean \u7684\u751f\u547d\u5468\u671f\u56de\u8c03\u5931\u8d25
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof SubjectProvider) {
            SubjectKit.register((SubjectProvider) bean);
        }
        return bean;
    }
}
