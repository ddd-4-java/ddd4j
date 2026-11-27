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
package io.ddd4j.guice.context;

import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import lombok.extern.slf4j.Slf4j;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * Guice IoC 容器上下文（等价于 Spring 的 ApplicationContext）。
 * <p>
 * 静态持有 {@link Injector}，提供全局访问能力：
 * <ul>
 *   <li>{@link #getInstance(Class)} - 按类型获取实例</li>
 *   <li>{@link #getInstance(String, Class)} - 按名称获取实例</li>
 *   <li>{@link #getInstances(Class)} - 获取某类型的所有绑定实例</li>
 *   <li>{@link #getInjector()} - 获取底层 Injector</li>
 * </ul>
 * <p>
 * 在应用启动时调用 {@link #setInjector(Injector)} 注入。
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
public class GuiceContext {

    /**
     * 自定义属性存储
     */
    private static final Map<String, Object> ATTRIBUTES = new ConcurrentHashMap<>();
    /**
     * 初始化等待信号
     */
    private static volatile CountDownLatch initSignal = new CountDownLatch(1);
    /**
     * Guice 注入器实例
     */
    private static volatile Injector injector;

    /** \u9650\u5236\u8c03\u7528\u65b9\u76f4\u63a5\u521b\u5efa <code>GuiceContext</code> \u5b9e\u4f8b\u3002 */
    private GuiceContext() {
    }

    /**
     * \u83b7\u53d6 Injector\uff08\u963b\u585e\u7b49\u5f85\u521d\u59cb\u5316\u5b8c\u6210\uff09
     *
     * @return \u5df2\u521d\u59cb\u5316\u7684 Guice Injector
     */
    public static Injector getInjector() {
        if (Objects.isNull(injector)) {
            try {
                initSignal.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for GuiceContext initialization", e);
            }
        }
        return injector;
    }

    /**
     * \u8bbe\u7f6e Injector\uff08\u5e94\u7528\u542f\u52a8\u65f6\u8c03\u7528\u4e00\u6b21\uff09
     *
     * @param inj Guice Injector
     */
    public static void setInjector(Injector inj) {
        if (Objects.nonNull(injector)) {
            log.warn("GuiceContext already initialized, overwriting existing Injector");
        }
        injector = inj;
        initSignal.countDown();
        log.info("GuiceContext initialized with Injector: {}", inj);
    }

    /**
     * \u6309\u7c7b\u578b\u83b7\u53d6\u5b9e\u4f8b
     *
     * @param <T> \u5b9e\u4f8b\u7c7b\u578b
     * @param clazz \u5b9e\u4f8b\u7c7b\u578b
     * @return \u5bb9\u5668\u4e2d\u7684\u5b9e\u4f8b
     */
    public static <T> T getInstance(Class<T> clazz) {
        return getInjector().getInstance(clazz);
    }

    /**
     * \u6309\u540d\u79f0\u83b7\u53d6\u5b9e\u4f8b
     *
     * @param <T> \u5b9e\u4f8b\u7c7b\u578b
     * @param name \u7ed1\u5b9a\u540d\u79f0
     * @param clazz \u5b9e\u4f8b\u7c7b\u578b
     * @return \u5bb9\u5668\u4e2d\u7684\u5b9e\u4f8b
     */
    public static <T> T getInstance(String name, Class<T> clazz) {
        return getInjector().getInstance(Key.get(clazz, Names.named(name)));
    }

    /**
     * \u6309\u6ce8\u89e3\u83b7\u53d6\u5b9e\u4f8b
     *
     * @param <T> \u5b9e\u4f8b\u7c7b\u578b
     * @param clazz \u5b9e\u4f8b\u7c7b\u578b
     * @param annotationType \u7ed1\u5b9a\u6ce8\u89e3\u7c7b\u578b
     * @return \u5bb9\u5668\u4e2d\u7684\u5b9e\u4f8b
     */
    public static <T> T getInstance(Class<T> clazz, Class<? extends Annotation> annotationType) {
        return getInjector().getInstance(Key.get(clazz, annotationType));
    }

    /**
     * \u83b7\u53d6\u67d0\u7c7b\u578b\u7684\u6240\u6709\u7ed1\u5b9a\u5b9e\u4f8b
     *
     * @param <T> \u5b9e\u4f8b\u7c7b\u578b
     * @param clazz \u5b9e\u4f8b\u7c7b\u578b
     * @return \u53ef\u5b9e\u4f8b\u5316\u7684\u7ed1\u5b9a\u96c6\u5408
     */
    public static <T> Collection<T> getInstances(Class<T> clazz) {
        List<T> instances = new ArrayList<>();
        Map<Key<?>, Binding<?>> bindings = getInjector().getAllBindings();
        for (Binding<?> binding : bindings.values()) {
            Class<?> rawType = binding.getKey().getTypeLiteral().getRawType();
            if (clazz.isAssignableFrom(rawType) && rawType != clazz) {
                try {
                    @SuppressWarnings("unchecked")
                    T instance = (T) getInjector().getInstance(binding.getKey());
                    instances.add(instance);
                } catch (Exception e) {
                    log.debug("Cannot instantiate binding: {}", binding.getKey(), e);
                }
            }
        }
        return instances;
    }

    /**
     * \u83b7\u53d6\u73af\u5883\u5c5e\u6027
     *
     * @param key \u5c5e\u6027\u540d\u79f0
     * @return \u5c5e\u6027\u503c\uff0c\u7f3a\u5931\u65f6\u4e3a\u7a7a
     */
    public static String getProperty(String key) {
        return getProperty(key, null);
    }

    /**
     * \u83b7\u53d6\u73af\u5883\u5c5e\u6027\uff08\u5e26\u9ed8\u8ba4\u503c\uff09
     *
     * @param key \u5c5e\u6027\u540d\u79f0
     * @param defaultValue \u7f3a\u5931\u65f6\u7684\u9ed8\u8ba4\u503c
     * @return \u5c5e\u6027\u503c\u6216\u9ed8\u8ba4\u503c
     */
    public static String getProperty(String key, String defaultValue) {
        // 优先从系统属性获取
        String value = System.getProperty(key);
        if (Objects.nonNull(value)) {
            return value;
        }
        // 再从环境变量获取
        value = System.getenv(key);
        if (Objects.nonNull(value)) {
            return value;
        }
        // 再从自定义属性获取
        Object attr = ATTRIBUTES.get(key);
        if (Objects.nonNull(attr)) {
            return attr.toString();
        }
        return defaultValue;
    }

    /**
     * \u8bbe\u7f6e\u81ea\u5b9a\u4e49\u5c5e\u6027
     *
     * @param key \u5c5e\u6027\u540d\u79f0
     * @param value \u5c5e\u6027\u503c
     */
    public static void setAttribute(String key, Object value) {
        ATTRIBUTES.put(key, value);
    }

    /**
     * \u83b7\u53d6\u81ea\u5b9a\u4e49\u5c5e\u6027
     *
     * @param key \u5c5e\u6027\u540d\u79f0
     * @return \u5c5e\u6027\u503c\uff0c\u7f3a\u5931\u65f6\u4e3a\u7a7a
     */
    public static Object getAttribute(String key) {
        return ATTRIBUTES.get(key);
    }

    /**
     * \u5224\u65ad Injector \u662f\u5426\u5df2\u521d\u59cb\u5316
     *
     * @return \u5df2\u521d\u59cb\u5316\u65f6\u4e3a true
     */
    public static boolean isInitialized() {
        return Objects.nonNull(injector);
    }

    /**
     * 清理全局 Injector 与运行期属性，供运行时关闭和测试隔离使用。
     */
    public static synchronized void clear() {
        injector = null;
        ATTRIBUTES.clear();
        initSignal = new CountDownLatch(1);
    }

    /**
     * \u83b7\u53d6\u6ce8\u5165\u5668\u4e2d\u6240\u6709\u7ed1\u5b9a\u7684\u7c7b\u578b
     *
     * @return \u6240\u6709\u7ed1\u5b9a\u7c7b\u578b
     */
    public static Set<Class<?>> getBoundTypes() {
        Set<Class<?>> types = new HashSet<>();
        Map<Key<?>, Binding<?>> bindings = getInjector().getAllBindings();
        for (Binding<?> binding : bindings.values()) {
            types.add(binding.getKey().getTypeLiteral().getRawType());
        }
        return types;
    }
}
