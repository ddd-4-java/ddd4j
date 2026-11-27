package io.ddd4j.mq.spring.registry;

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

    /** \u4ea4\u7ed9\u6d88\u606f\u5ba2\u6237\u7aef\u8ba2\u9605\u548c\u8c03\u7528\u7684\u76d1\u542c\u5668\u96c6\u5408\u3002 */
    private final List<MQListener> listeners = new CopyOnWriteArrayList<>();
    /** \u5f53\u524d\u6d88\u606f\u4ee3\u7406\u5ba2\u6237\u7aef\u4f7f\u7528\u7684\u8fde\u63a5\u548c\u8ba2\u9605\u914d\u7f6e\u3002 */
    private final MQProperties properties;

    /** \u4ece Spring \u73af\u5883\u786e\u5b9a\u7684\u9ed8\u8ba4\u6d88\u8d39\u8005\u7ec4\u524d\u7f00\u3002 */
    private String defaultGroupPrefix = "application";

    /**
     * \u4ece Spring \u914d\u7f6e\u73af\u5883\u63d0\u53d6\u9ed8\u8ba4\u6d88\u8d39\u8005\u7ec4\u540d\u79f0\u914d\u7f6e\u3002
     * @param environment Spring \u5bb9\u5668\u63d0\u4f9b\u7684\u914d\u7f6e\u73af\u5883
     */
    @Override
    public void setEnvironment(Environment environment) {
        this.defaultGroupPrefix = environment.getProperty("spring.application.name", "application");
    }

    /**
     * \u58f0\u660e\u6d88\u606f\u76d1\u542c\u5668 Bean \u540e\u5904\u7406\u5668\u7684\u6267\u884c\u4f18\u5148\u7ea7\u3002
     * @return Spring \u6700\u4f4e\u4f18\u5148\u7ea7
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /**
     * Bean \u521d\u59cb\u5316\u5b8c\u6210\u540e\u626b\u63cf {@link MQEventListener} \u65b9\u6cd5\u3002
     *
     * @param bean Spring \u521d\u59cb\u5316\u540e\u7684 Bean \u5b9e\u4f8b
     * @param beanName Bean \u5728\u5bb9\u5668\u4e2d\u7684\u540d\u79f0
     * @return \u4ea4\u8fd8\u7ed9 Spring \u7684\u539f Bean \u5b9e\u4f8b
     * @throws BeansException \u5904\u7406\u5df2\u521d\u59cb\u5316\u7684\u76d1\u542c Bean \u5931\u8d25
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
     * \u8fd4\u56de\u5df2\u767b\u8bb0\u7684\u76d1\u542c\u5668\u5b9a\u4e49\uff08\u4e0d\u53ef\u53d8\u5feb\u7167\uff09\u3002
     *
     * @return \u76d1\u542c\u5668\u5b9a\u4e49\u5feb\u7167
     */
    public List<MQListener> getListeners() {
        return List.copyOf(listeners);
    }

    /**
     * \u8fc7\u6ee4 Spring \u57fa\u7840\u8bbe\u65bd\u7c7b\uff0c\u907f\u514d\u8bef\u626b\u63cf\u3002
     *
     * @param clazz \u5f85\u68c0\u67e5\u7684\u7c7b
     * @return \u57fa\u7840\u8bbe\u65bd\u7c7b\u9700\u8981\u8df3\u8fc7\u76d1\u542c\u5668\u626b\u63cf\u65f6\u8fd4\u56de true
     */
    private boolean isInfrastructureClass(Class<?> clazz) {
        return clazz.getName().startsWith("org.springframework")
                || clazz.getName().startsWith("java.")
                || clazz.getName().startsWith("jakarta.");
    }

    /**
     * \u4ece\u6ce8\u89e3\u4e0e\u65b9\u6cd5\u5143\u6570\u636e\u6784\u5efa\u76d1\u542c\u5668\u5b9a\u4e49\uff0c\u8865\u9f50 group / namespace \u9ed8\u8ba4\u503c\u3002
     *
     * @param bean Spring \u521d\u59cb\u5316\u540e\u7684 Bean \u5b9e\u4f8b
     * @param beanName Bean \u5728\u5bb9\u5668\u4e2d\u7684\u540d\u79f0
     * @param method \u76d1\u542c\u6ce8\u89e3\u6807\u8bb0\u7684\u65b9\u6cd5
     * @param annotation \u4ece\u6d88\u8d39\u65b9\u6cd5\u8bfb\u53d6\u7684\u76d1\u542c\u6ce8\u89e3
     * @return \u6839\u636e\u65b9\u6cd5\u548c\u6ce8\u89e3\u6784\u9020\u7684\u6d88\u606f\u76d1\u542c\u5668
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
                .supports(List.of(annotation.supports()))
                .separator(annotation.separator())
                .build();
    }
}
