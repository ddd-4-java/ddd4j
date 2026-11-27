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
     * 为认证主体登记器提供 Spring 后处理器排序。
     *
     * @return 高于普通后处理器的顺序值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    /**
     * 将初始化完毕的认证主体提供器登记到 DDD4J 主体入口。
     *
     * @param bean     Spring 完成初始化的 Bean
     * @param beanName Bean 在容器中的注册名称
     * @return 原始 Spring Bean
     * @throws BeansException 处理当前 Bean 的生命周期回调失败
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof SubjectProvider) {
            SubjectKit.register((SubjectProvider) bean);
        }
        return bean;
    }
}
