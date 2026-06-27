package io.ddd4j.mq.spring.registry;

import io.ddd4j.core.contract.annotation.MQEventListener;
import io.ddd4j.mq.config.Ddd4jMQProperties;
import io.ddd4j.mq.registry.MQListenerDefinition;
import io.ddd4j.mq.registry.MQListenerDefinitionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.lang.NonNull;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

/**
 * 基于 Spring {@link BeanPostProcessor} 的 {@link MQEventListener} 发现器（从 ddd4j-mq-core 迁出）。
 * <p>
 * 在 Bean 完成初始化（含 AOP 代理）后内省目标类方法，写入 {@link MQListenerDefinitionRegistry}。
 * 模式对齐 Spring {@code EventListenerMethodProcessor} 与 Cloud {@code FunctionalConsumerRegistrar}。
 */
@Slf4j
@RequiredArgsConstructor
public class MQListenerBeanPostProcessor implements BeanPostProcessor, Ordered, EnvironmentAware {

    private final MQListenerDefinitionRegistry registry;
    private final Ddd4jMQProperties properties;

    private String defaultGroupPrefix = "application";

    @Override
    public void setEnvironment(Environment environment) {
        this.defaultGroupPrefix = environment.getProperty("spring.application.name", "application");
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /**
     * Bean 初始化完成后扫描 {@link MQEventListener} 方法。
     */
    @Override
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
        if (!properties.isEnabled()) {
            return bean;
        }
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        if (isInfrastructureClass(targetClass)) {
            return bean;
        }
        ReflectionUtils.doWithMethods(targetClass, method -> {
            MQEventListener annotation = AnnotationUtils.findAnnotation(method, MQEventListener.class);
            if (annotation != null) {
                registry.register(buildDefinition(bean, beanName, method, annotation));
            }
        }, ReflectionUtils.USER_DECLARED_METHODS);
        return bean;
    }

    /**
     * 过滤 Spring 基础设施类，避免误扫描。
     */
    private boolean isInfrastructureClass(Class<?> clazz) {
        return clazz.getName().startsWith("org.springframework")
                || clazz.getName().startsWith("java.")
                || clazz.getName().startsWith("jakarta.");
    }

    /**
     * 从注解与方法元数据构建监听器定义，补齐 group / namespace 默认值。
     */
    private MQListenerDefinition buildDefinition(
            Object bean,
            String beanName,
            Method method,
            MQEventListener annotation) {

        String group = StringUtils.hasText(annotation.group())
                ? annotation.group()
                : defaultGroupPrefix + "_" + method.getName();
        String namespace = StringUtils.hasText(annotation.namespace())
                ? annotation.namespace()
                : properties.getNamespace();

        return MQListenerDefinition.builder()
                .bean(bean)
                .beanName(beanName)
                .method(method)
                .group(group)
                .namespace(namespace)
                .topic(annotation.topic())
                .tags(annotation.tags())
                .supports(List.of(annotation.supports()))
                .concat(annotation.concat())
                .build();
    }

    /**
     * 校验监听器定义非空且方法可访问。
     *
     * @param definition 监听器定义
     */
    public static void prepareMethod(MQListenerDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        Method method = definition.getMethod();
        Object bean = definition.getBean();
        if (bean != null && !method.canAccess(bean)) {
            method.setAccessible(true);
        }
    }
}
