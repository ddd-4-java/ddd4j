package io.ddd4j.quarkus.cqrs;

import io.ddd4j.annotation.cqrs.CreateEvent;
import io.ddd4j.annotation.cqrs.DeleteEvent;
import io.ddd4j.annotation.cqrs.UpdateEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Quarkus CQRS 事件处理器注册器。
 *
 * <p>通过 CDI {@link BeanManager} 发现所有 View Bean，扫描
 * {@link CreateEvent} / {@link UpdateEvent} / {@link DeleteEvent} 注解，构建路由表。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-QUARKUS : EventHandlerRegistry ###")
@ApplicationScoped
public class QuarkusEventHandlerRegistry {

    /**
     * 事件类型到处理器方法的路由表
     */
    private final ConcurrentMap<Class<?>, Method> handlerTable = new ConcurrentHashMap<>();
    /**
     * CDI Bean 管理器
     */
    @Inject
    BeanManager beanManager;

    /**
     * \u626b\u63cf CDI Bean\uff0c\u767b\u8bb0\u5e26\u6295\u5f71\u5904\u7406\u6ce8\u89e3\u7684\u65b9\u6cd5\u3002
     * @param event \u5f53\u524d\u6846\u67b6\u751f\u547d\u5468\u671f\u6216\u5bb9\u5668\u542f\u52a8\u4e8b\u4ef6
     */
    void onStart(@Observes StartupEvent event) {
        Set<Bean<?>> beans = beanManager.getBeans(Object.class);
        for (Bean<?> bean : beans) {
            Class<?> beanClass = bean.getBeanClass();
            if (Objects.isNull(beanClass) || beanClass.isSynthetic()) {
                continue;
            }
            registerHandlers(beanClass);
        }
        log.info("CQRS event handler registry initialized: {} event types registered", handlerTable.size());
    }

    /**
     * \u67e5\u627e\u89c6\u56fe\u7c7b\u7684\u5904\u7406\u65b9\u6cd5\u5e76\u6ce8\u518c\u5176\u4e8b\u4ef6\u8def\u7531\u3002
     * @param viewClass \u58f0\u660e\u6295\u5f71\u5904\u7406\u65b9\u6cd5\u7684\u89c6\u56fe\u7c7b
     */
    private void registerHandlers(Class<?> viewClass) {
        for (Method method : viewClass.getDeclaredMethods()) {
            registerIfAnnotated(method, CreateEvent.class);
            registerIfAnnotated(method, UpdateEvent.class);
            registerIfAnnotated(method, DeleteEvent.class);
        }
    }

    /**
     * \u4ec5\u5728\u65b9\u6cd5\u5e26\u5f53\u524d\u6ce8\u89e3\u65f6\u89e3\u6790\u5e76\u767b\u8bb0\u4e8b\u4ef6\u5904\u7406\u8def\u7531\u3002
     * @param method \u5f85\u68c0\u67e5\u6ce8\u89e3\u5e76\u767b\u8bb0\u7684\u53cd\u5c04\u65b9\u6cd5
     * @param annotationType \u5f53\u524d\u8981\u8bc6\u522b\u7684\u6ce8\u89e3\u7c7b\u578b
     */
    private void registerIfAnnotated(Method method, Class<? extends Annotation> annotationType) {
        if (method.isAnnotationPresent(annotationType)) {
            Annotation annotation = method.getAnnotation(annotationType);
            Class<?> eventType = extractEventType(annotation);
            if (Objects.nonNull(eventType)) {
                handlerTable.put(eventType, method);
                log.debug("Registered {} handler: {} -> {}#{}",
                        annotationType.getSimpleName(), eventType.getSimpleName(),
                        method.getDeclaringClass().getSimpleName(), method.getName());
            }
        }
    }

    /**
     * \u4ece\u5904\u7406\u65b9\u6cd5\u7684\u6ce8\u89e3\u4e2d\u8bfb\u53d6\u4e8b\u4ef6\u7c7b\u578b\u3002
     * @param annotation \u63d0\u4f9b\u5904\u7406\u4e8b\u4ef6\u7c7b\u578b\u7684\u6ce8\u89e3\u5b9e\u4f8b
     * @return \u8be5\u6ce8\u89e3\u7ed1\u5b9a\u7684\u4e8b\u4ef6\u7c7b\u578b
     */
    private Class<?> extractEventType(Annotation annotation) {
        try {
            return (Class<?>) annotation.annotationType().getMethod("value").invoke(annotation);
        } catch (Exception e) {
            log.error("Failed to extract event type from annotation {}", annotation, e);
            return null;
        }
    }

    /**
     * \u6309\u4e8b\u4ef6\u7c7b\u578b\u67e5\u627e\u5904\u7406\u5668\u65b9\u6cd5\u3002
     * @param eventType \u4e8b\u4ef6\u7c7b\u578b
     * @return \u5339\u914d\u7684\u5904\u7406\u5668\u65b9\u6cd5
     */
    public Method findHandler(Class<?> eventType) {
        return handlerTable.get(eventType);
    }

    /** @return \u5df2\u6ce8\u518c\u7684\u4e8b\u4ef6\u5904\u7406\u5668\u6620\u5c04\u526f\u672c */
    public Map<Class<?>, Method> getHandlerTable() {
        return new HashMap<>(handlerTable);
    }
}
