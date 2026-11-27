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
package io.ddd4j.spring.context;

import io.ddd4j.core.constant.ContextConstants;
import io.ddd4j.core.context.BaseContext;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Spring上下文：显式获取SpringBean、注册Bean；SpringEvent事件发布
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j(topic = "### BASE-CORE : SpringContext ###")
@Primary
@Order(PriorityOrdered.HIGHEST_PRECEDENCE)
public class SpringContext implements ApplicationContextAware {

    /**
     * 应用启动完成的信号
     */
    public static final CountDownLatch APP_START_SIGNAL = new CountDownLatch(1);
    /**
     * Spring 上下文初始化完成的信号
     */
    public static final CountDownLatch APPLICATION_CONTEXT_START_SIGNAL = new CountDownLatch(1);
    /**
     * 用于异步执行启动后置逻辑的线程池
     */
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();
    /**
     * Spring ApplicationContext 实例
     */
    private static ApplicationContext APPLICATION_CONTEXT;

    /** \u521d\u59cb\u5316 Spring \u4e0a\u4e0b\u6587\u6865\u63a5\u5668\u3002 */
    public SpringContext() {
        log.debug("Loading SpringContext");
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTraceElements) {
            if ("main".equals(element.getMethodName())) {
                try {
                    String projectPackage = Class.forName(element.getClassName()).getPackage().getName();
                    BaseContext.inject(ContextConstants.PROJECT_PACKAGE, projectPackage);
                    log.debug("PROJECT_PACKAGE: {}", projectPackage);
                } catch (ClassNotFoundException e) {
                    log.error("Cannot find class: {}", element.getClassName());
                }
                break;
            }
        }
    }

    /**
     * \u5728\u5e94\u7528\u542f\u52a8\u540e\u5f02\u6b65\u6267\u884c\u56de\u8c03\u3002
     * @param then \u63a5\u6536\u5e94\u7528\u4e0a\u4e0b\u6587\u7684\u56de\u8c03
     */
    public static void onAppStarted(Consumer<ApplicationContext> then) {
        EXECUTOR_SERVICE.submit(() -> {
            try {
                APP_START_SIGNAL.await();
                then.accept(APPLICATION_CONTEXT);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("应用初始化时线程被中断", e);
            } catch (Exception e) {
                log.error("执行应用启动后置逻辑失败", e);
            }
        });
    }

    // 获取ApplicationContext，需等待ApplicationContext初始化完成
    /**
     * \u7b49\u5f85\u5e76\u83b7\u53d6\u5df2\u521d\u59cb\u5316\u7684\u5e94\u7528\u4e0a\u4e0b\u6587\u3002
     * @return Spring \u5e94\u7528\u4e0a\u4e0b\u6587
     */
    @SneakyThrows
    public static ApplicationContext getApplicationContext() {
        // 阻塞等待初始化完成
        if (Objects.isNull(APPLICATION_CONTEXT)) {
            APPLICATION_CONTEXT_START_SIGNAL.await();
        }
        return APPLICATION_CONTEXT;
    }

    /**
     * \u8bb0\u5f55\u5bb9\u5668\u63d0\u4f9b\u7684 Spring \u5e94\u7528\u4e0a\u4e0b\u6587\uff0c\u4f9b\u540e\u7eed\u67e5\u8be2 Bean\u3002
     * @param applicationContext \u5f53\u524d Spring \u5e94\u7528\u5bb9\u5668
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        SpringContext.APPLICATION_CONTEXT = applicationContext;
        APPLICATION_CONTEXT_START_SIGNAL.countDown();
    }

    /**
     * \u6309\u540d\u79f0\u83b7\u53d6 Bean\u3002
     * @param name Bean \u540d\u79f0
     * @param <T> Bean \u7c7b\u578b
     * @return \u5bf9\u5e94\u7684 Bean
     */
    @SuppressWarnings("unchecked")
    public static <T> T getBean(@NonNull String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("获取的Bean名称不能为空");
        }
        return (T) getApplicationContext().getBean(name);
    }

    /**
     * \u6309\u540d\u79f0\u548c\u7c7b\u578b\u83b7\u53d6 Bean\u3002
     * @param name Bean \u540d\u79f0
     * @param clazz Bean \u7c7b\u578b
     * @param <T> Bean \u7c7b\u578b
     * @return \u5bf9\u5e94\u7684 Bean
     */
    public static <T> T getBean(@NonNull String name, @NonNull Class<T> clazz) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("获取的Bean名称不能为空");
        }
        return getApplicationContext().getBean(name, clazz);
    }

    /**
     * \u6309\u7c7b\u578b\u83b7\u53d6 Bean\u3002
     * @param clazz Bean \u7c7b\u578b
     * @param <T> Bean \u7c7b\u578b
     * @return \u5bf9\u5e94\u7684 Bean
     */
    public static <T> T getBean(@NonNull Class<T> clazz) {
        return getApplicationContext().getBean(clazz);
    }

    // 获取Bean，需等待应用完全启动
    /**
     * \u7b49\u5f85\u5e94\u7528\u542f\u52a8\u540e\u6309\u7c7b\u578b\u83b7\u53d6 Bean\u3002
     * @param clazz Bean \u7c7b\u578b
     * @param <T> Bean \u7c7b\u578b
     * @return \u5bf9\u5e94\u7684 Bean
     */
    @SneakyThrows
    public static <T> T getBeanAwait(@NonNull Class<T> clazz) {
        APP_START_SIGNAL.await();
        return getBean(clazz);
    }

    // 获取Bean，需等待应用完全启动
    /**
     * \u7b49\u5f85\u5e94\u7528\u542f\u52a8\u540e\u5c06 Bean \u4f20\u7ed9\u56de\u8c03\u3002
     * @param beanClazz Bean \u7c7b\u578b
     * @param then \u63a5\u6536 Bean \u7684\u56de\u8c03
     * @param <T> Bean \u7c7b\u578b
     */
    public static <T> void getBean(@NonNull Class<T> beanClazz, Consumer<T> then) {
        then.accept(getBeanAwait(beanClazz));
    }

    /**
     * \u6309\u7c7b\u578b\u83b7\u53d6\u5168\u90e8 Bean\u3002
     * @param clazz Bean \u7c7b\u578b
     * @param <T> Bean \u7c7b\u578b
     * @return \u5339\u914d\u7684 Bean \u96c6\u5408
     */
    public static <T> Collection<T> getBeans(@NonNull Class<T> clazz) {
        Map<String, T> beansOfType = getApplicationContext().getBeansOfType(clazz);
        if (CollectionUtils.isEmpty(beansOfType)) {
            return new ArrayList<>();
        }
        return beansOfType.values();
    }

    /**
     * \u83b7\u53d6 Spring \u73af\u5883\u3002
     * @return \u5f53\u524d\u5e94\u7528\u7684\u73af\u5883
     */
    public static Environment getEnv() {
        return getApplicationContext().getEnvironment();
    }

    /**
     * 应用上下文刷新完成后释放启动等待信号。
     * @param event 事件
     */
    @EventListener
    public void onContextRefreshed(ContextRefreshedEvent event) {
        APP_START_SIGNAL.countDown();
    }

}
