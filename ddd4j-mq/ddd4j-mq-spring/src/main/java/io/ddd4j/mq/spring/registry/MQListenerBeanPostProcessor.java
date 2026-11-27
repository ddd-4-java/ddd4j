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

    /**
     * 交给消息客户端订阅和调用的监听器集合。
     */
    private final List<MQListener> listeners = new CopyOnWriteArrayList<>();
    /**
     * 当前消息代理客户端使用的连接和订阅配置。
     */
    private final MQProperties properties;

    /**
     * 从 Spring 环境确定的默认消费者组前缀。
     */
    private String defaultGroupPrefix = "application";

    /**
     * 从 Spring 配置环境提取默认消费者组名称配置。
     *
     * @param environment Spring 容器提供的配置环境
     */
    @Override
    public void setEnvironment(Environment environment) {
        this.defaultGroupPrefix = environment.getProperty("spring.application.name", "application");
    }

    /**
     * 声明消息监听器 Bean 后处理器的执行优先级。
     *
     * @return Spring 最低优先级
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /**
     * Bean 初始化完成后扫描 {@link MQEventListener} 方法。
     *
     * @param bean     Spring 初始化后的 Bean 实例
     * @param beanName Bean 在容器中的名称
     * @return 交还给 Spring 的原 Bean 实例
     * @throws BeansException 处理已初始化的监听 Bean 失败
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
     *
     * @return 监听器定义快照
     */
    public List<MQListener> getListeners() {
        return List.copyOf(listeners);
    }

    /**
     * 过滤 Spring 基础设施类，避免误扫描。
     *
     * @param clazz 待检查的类
     * @return 基础设施类需要跳过监听器扫描时返回 true
     */
    private boolean isInfrastructureClass(Class<?> clazz) {
        return clazz.getName().startsWith("org.springframework")
                || clazz.getName().startsWith("java.")
                || clazz.getName().startsWith("jakarta.");
    }

    /**
     * 从注解与方法元数据构建监听器定义，补齐 group / namespace 默认值。
     *
     * @param bean       Spring 初始化后的 Bean 实例
     * @param beanName   Bean 在容器中的名称
     * @param method     监听注解标记的方法
     * @param annotation 从消费方法读取的监听注解
     * @return 根据方法和注解构造的消息监听器
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
                .required(annotation.required())
                .build();
    }
}
