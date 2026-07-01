package io.ddd4j.auth.spring;

import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKit;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;

/**
 * Subject 注册器（修复 SubjectKit 注册断链的【关键】组件）。
 *
 * <p>当 Spring 容器中出现 {@link SubjectProvider} Bean 时，
 * 自动将其写回 {@link SubjectKit} 静态字段，使 {@code SubjectKit.getSubject()} 可用。
 *
 * <p>这解决了原 auth 模块的致命缺陷：三个适配模块都注册了 SubjectProvider Bean，
 * 但没有任何代码写回 SubjectKit.subjectProvider 静态字段。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public class SubjectRegistrar implements BeanPostProcessor, Ordered {

    /**
     * 注册顺序：在所有 SubjectProvider Bean 初始化完成后立即注册，
     * 但晚于框架自身的 BeanPostProcessor（HIGHEST_PRECEDENCE + 100）。
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 【关键】把 Spring 容器中的 SubjectProvider Bean 写回 SubjectKit 静态字段
        if (bean instanceof SubjectProvider provider) {
            SubjectKit.register(provider);
        }
        return bean;
    }

}
