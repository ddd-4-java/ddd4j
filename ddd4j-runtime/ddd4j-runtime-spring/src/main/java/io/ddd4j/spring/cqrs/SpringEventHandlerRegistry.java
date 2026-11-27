package io.ddd4j.spring.cqrs;

import io.ddd4j.annotation.cqrs.CreateEvent;
import io.ddd4j.annotation.cqrs.DeleteEvent;
import io.ddd4j.annotation.cqrs.UpdateEvent;
import io.ddd4j.core.cqrs.query.View;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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

    /**
     * Spring 应用上下文
     */
    private final ApplicationContext applicationContext;
    /**
     * 事件类型到处理器方法的路由表
     */
    private final ConcurrentMap<Class<?>, Method> handlerTable = new ConcurrentHashMap<>();

    /**
     * \u521b\u5efa Spring \u4e8b\u4ef6\u5904\u7406\u5668\u6ce8\u518c\u8868\u3002
     * @param applicationContext Spring \u5e94\u7528\u4e0a\u4e0b\u6587
     */
    public SpringEventHandlerRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /** \u5728\u5355\u4f8b Bean \u5b8c\u6210\u521d\u59cb\u5316\u540e\u626b\u63cf\u5e76\u6ce8\u518c\u4e8b\u4ef6\u5904\u7406\u5668\u3002 */
    @Override
    public void afterSingletonsInstantiated() {
        Map<String, View> viewBeans = applicationContext.getBeansOfType(View.class);

        for (View view : viewBeans.values()) {
            registerHandlers(view.getClass());
        }
        log.info("CQRS event handler registry initialized: {} event types registered", handlerTable.size());
    }

    /**
     * \u626b\u63cf\u6307\u5b9a\u7c7b\u7684\u6240\u6709\u65b9\u6cd5\uff0c\u6ce8\u518c @CreateEvent / @UpdateEvent / @DeleteEvent \u5904\u7406\u5668\u3002
     *
     * @param viewClass \u58f0\u660e\u4e8b\u4ef6\u5904\u7406\u65b9\u6cd5\u7684\u89c6\u56fe\u7c7b
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
    private void registerIfAnnotated(Method method, Class<? extends java.lang.annotation.Annotation> annotationType) {
        if (method.isAnnotationPresent(annotationType)) {
            java.lang.annotation.Annotation annotation = method.getAnnotation(annotationType);
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
     * \u4ece\u6ce8\u89e3 {@code value()} \u63d0\u53d6\u4e8b\u4ef6\u7c7b\u578b\u3002
     *
     * @param annotation \u58f0\u660e\u4e8b\u4ef6\u5904\u7406\u89c4\u5219\u7684\u6ce8\u89e3
     * @return \u6ce8\u89e3\u58f0\u660e\u7684\u4e8b\u4ef6\u7c7b\u578b
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
     * \u83b7\u53d6\u5df2\u6ce8\u518c\u7684\u5168\u90e8\u4e8b\u4ef6\u7c7b\u578b\u3002
     *
     * @return \u4e8b\u4ef6\u5904\u7406\u5668\u6620\u5c04\u7684\u526f\u672c
     */
    public Map<Class<?>, Method> getHandlerTable() {
        return new HashMap<>(handlerTable);
    }
}
