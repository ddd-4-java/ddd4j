package io.ddd4j.spring.cqrs;

import io.ddd4j.annotation.cqrs.CreateEvent;
import io.ddd4j.annotation.cqrs.DeleteEvent;
import io.ddd4j.annotation.cqrs.UpdateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Spring CQRS 事件处理器注册器。
 *
 * <p>扫描所有 View Bean，识别 {@link CreateEvent} / {@link UpdateEvent} / {@link DeleteEvent}
 * 注解的方法，构建事件类型 → 方法的路由表（注入到 {@code DddEventDispatcher}）。
 *
 * <p>本类实现 {@link SmartInitializingSingleton}，确保所有单例 Bean 初始化完成后
 * 才执行扫描，避免漏掉延迟初始化的 View Bean。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
@Slf4j(topic = "### DDD4J-SPRING : EventHandlerRegistry ###")
@Component
public class SpringEventHandlerRegistry implements SmartInitializingSingleton {

    private final ApplicationContext applicationContext;
    private final ConcurrentMap<Class<?>, Method> handlerTable = new ConcurrentHashMap<>();

    public SpringEventHandlerRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, org.fuin.cqrs4j.core.View> viewBeans = applicationContext.getBeansOfType(org.fuin.cqrs4j.core.View.class);

        for (org.fuin.cqrs4j.core.View view : viewBeans.values()) {
            registerHandlers(view.getClass());
        }
        log.info("CQRS event handler registry initialized: {} event types registered", handlerTable.size());
    }

    /**
     * 扫描指定类的所有方法，注册 @CreateEvent / @UpdateEvent / @DeleteEvent 处理器。
     */
    private void registerHandlers(Class<?> viewClass) {
        for (Method method : viewClass.getDeclaredMethods()) {
            registerIfAnnotated(method, CreateEvent.class);
            registerIfAnnotated(method, UpdateEvent.class);
            registerIfAnnotated(method, DeleteEvent.class);
        }
    }

    private void registerIfAnnotated(Method method, Class<? extends java.lang.annotation.Annotation> annotationType) {
        if (method.isAnnotationPresent(annotationType)) {
            java.lang.annotation.Annotation annotation = method.getAnnotation(annotationType);
            Class<?> eventType = extractEventType(annotation);
            if (java.util.Objects.nonNull(eventType)) {
                handlerTable.put(eventType, method);
                log.debug("Registered {} handler: {} -> {}#{}",
                        annotationType.getSimpleName(), eventType.getSimpleName(),
                        method.getDeclaringClass().getSimpleName(), method.getName());
            }
        }
    }

    /**
     * 从注解 {@code value()} 提取事件类型。
     */
    private Class<?> extractEventType(java.lang.annotation.Annotation annotation) {
        try {
            return (Class<?>) annotation.annotationType().getMethod("value").invoke(annotation);
        } catch (Exception e) {
            log.error("Failed to extract event type from annotation {}", annotation, e);
            return null;
        }
    }

    /**
     * 查找事件类型对应的处理器方法。
     *
     * @param eventType 事件类型
     * @return 处理器方法（未注册则返回 null）
     */
    public Method findHandler(Class<?> eventType) {
        return handlerTable.get(eventType);
    }

    /**
     * 获取已注册的全部事件类型。
     */
    public Map<Class<?>, Method> getHandlerTable() {
        return new HashMap<>(handlerTable);
    }
}
