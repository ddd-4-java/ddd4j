/*
 * Copyright (c) 2024-2026 ddd4j project. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
     * 扫描 CDI Bean，登记带投影处理注解的方法。
     *
     * @param event 当前框架生命周期或容器启动事件
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
     * 查找视图类的处理方法并注册其事件路由。
     *
     * @param viewClass 声明投影处理方法的视图类
     */
    private void registerHandlers(Class<?> viewClass) {
        for (Method method : viewClass.getDeclaredMethods()) {
            registerIfAnnotated(method, CreateEvent.class);
            registerIfAnnotated(method, UpdateEvent.class);
            registerIfAnnotated(method, DeleteEvent.class);
        }
    }

    /**
     * 仅在方法带当前注解时解析并登记事件处理路由。
     *
     * @param method         待检查注解并登记的反射方法
     * @param annotationType 当前要识别的注解类型
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
     * 从处理方法的注解中读取事件类型。
     *
     * @param annotation 提供处理事件类型的注解实例
     * @return 该注解绑定的事件类型
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
     * 按事件类型查找处理器方法。
     *
     * @param eventType 事件类型
     * @return 匹配的处理器方法
     */
    public Method findHandler(Class<?> eventType) {
        return handlerTable.get(eventType);
    }

    /**
     * @return 已注册的事件处理器映射副本
     */
    public Map<Class<?>, Method> getHandlerTable() {
        return new HashMap<>(handlerTable);
    }
}
