package io.ddd4j.mq.spring.registry;

import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import io.ddd4j.mq.MQProperties;
import io.ddd4j.mq.annotation.MQEventListener;
import io.ddd4j.mq.listener.MQListener;
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
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 基于 Spring {@link BeanPostProcessor} 的 {@link MQEventListener} 发现器。
 * <p>
 * 在 Bean 完成初始化（含 AOP 代理）后内省目标类方法，构建 {@link MQListener} 并登记到本地列表，
 * 供 {@link MQListenerRegistrar} 在应用就绪时统一订阅。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@RequiredArgsConstructor
public class MQListenerBeanPostProcessor implements BeanPostProcessor, Ordered, EnvironmentAware {

    private final List<MQListener> listeners = new CopyOnWriteArrayList<>();
    private final MQProperties properties;

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
            if (Objects.nonNull(annotation)) {
                listeners.add(buildListener(bean, beanName, method, annotation));
            }
        }, ReflectionUtils.USER_DECLARED_METHODS);
        return bean;
    }

    /**
     * 返回已登记的监听器定义（不可变快照）。
     */
    public List<MQListener> getListeners() {
        return Collections.unmodifiableList(new ArrayList<>(listeners));
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
    private MQListener buildListener(Object bean, String beanName, Method method, MQEventListener annotation) {
        String group = StringUtils.hasText(annotation.group())
                ? annotation.group()
                : defaultGroupPrefix + "_" + method.getName();
        String namespace = StringUtils.hasText(annotation.namespace())
                ? annotation.namespace()
                : properties.getNamespace();

        return MQListener.builder()
                .bean(bean)
                .method(method)
                .group(group)
                .namespace(namespace)
                .topic(annotation.topic())
                .tags(annotation.tags())
                .supports(Arrays.asList(annotation.supports()))
                .separator(annotation.separator())
                .build();
    }
}
