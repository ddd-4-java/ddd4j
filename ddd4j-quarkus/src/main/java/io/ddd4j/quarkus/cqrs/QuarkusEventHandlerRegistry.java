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

    private final ConcurrentMap<Class<?>, Method> handlerTable = new ConcurrentHashMap<>();
    @Inject
    BeanManager beanManager;

    void onStart(@Observes StartupEvent event) {
        Set<Bean<?>> beans = beanManager.getBeans(Object.class);
        for (Bean<?> bean : beans) {
            Class<?> beanClass = bean.getBeanClass();
            if (beanClass == null || beanClass.isSynthetic()) {
                continue;
            }
            registerHandlers(beanClass);
        }
        log.info("CQRS event handler registry initialized: {} event types registered", handlerTable.size());
    }

    private void registerHandlers(Class<?> viewClass) {
        for (Method method : viewClass.getDeclaredMethods()) {
            registerIfAnnotated(method, CreateEvent.class);
            registerIfAnnotated(method, UpdateEvent.class);
            registerIfAnnotated(method, DeleteEvent.class);
        }
    }

    private void registerIfAnnotated(Method method, Class<? extends Annotation> annotationType) {
        if (method.isAnnotationPresent(annotationType)) {
            Annotation annotation = method.getAnnotation(annotationType);
            Class<?> eventType = extractEventType(annotation);
            if (eventType != null) {
                handlerTable.put(eventType, method);
                log.debug("Registered {} handler: {} -> {}#{}",
                        annotationType.getSimpleName(), eventType.getSimpleName(),
                        method.getDeclaringClass().getSimpleName(), method.getName());
            }
        }
    }

    private Class<?> extractEventType(Annotation annotation) {
        try {
            return (Class<?>) annotation.annotationType().getMethod("value").invoke(annotation);
        } catch (Exception e) {
            log.error("Failed to extract event type from annotation {}", annotation, e);
            return null;
        }
    }

    public Method findHandler(Class<?> eventType) {
        return handlerTable.get(eventType);
    }

    public Map<Class<?>, Method> getHandlerTable() {
        return new HashMap<>(handlerTable);
    }
}
