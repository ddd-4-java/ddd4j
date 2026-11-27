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
package io.ddd4j.runtime.testkit;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.Contexts;
import io.ddd4j.core.context.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runtime 适配器共享的 SPI 注册、重复启动与关闭清理契约。
 */
public abstract class AbstractRuntimeContractTest {

    /** @return \u5f85\u9a8c\u8bc1\u7684\u8fd0\u884c\u65f6\u9002\u914d\u5668 */
    protected abstract RuntimeContract createRuntime();

    /** \u6bcf\u4e2a\u5951\u7ea6\u6d4b\u8bd5\u5f00\u59cb\u524d\u6e05\u7a7a\u7ebf\u7a0b\u4e0a\u4e0b\u6587\u548c\u5168\u5c40\u670d\u52a1\u6ce8\u518c\u3002 */
    @BeforeEach
    void cleanContexts() {
        ThreadContext.clear();
        BaseContext.clear();
    }

    /** \u5728\u5951\u7ea6\u6d4b\u8bd5\u7ed3\u675f\u540e\u6e05\u7a7a\u7ebf\u7a0b\u4e0a\u4e0b\u6587\u548c\u5168\u5c40\u670d\u52a1\u6ce8\u518c\u3002 */
    @AfterEach
    void clearContexts() {
        ThreadContext.clear();
        BaseContext.clear();
    }

    /** \u9a8c\u8bc1\u8fd0\u884c\u65f6\u91cd\u590d\u542f\u52a8\u65f6\u4fdd\u6301\u6b63\u5e38\u5c31\u7eea\uff0c\u5173\u95ed\u65f6\u91ca\u653e\u767b\u8bb0\u7684\u670d\u52a1\u3002 */
    @Test
    void shouldRegisterServicesAndCleanThemOnClose() {
        RuntimeContract runtime = createRuntime();
        runtime.start();
        runtime.start();

        assertThat(runtime.readiness().ready()).isTrue();

        for (Map.Entry<String, Class<?>> service : runtime.services().entrySet()) {
            assertThat(BaseContext.get(service.getKey(), service.getValue())).isPresent();
            assertRequestOverride(service.getKey(), service.getValue());
        }

        runtime.close();
        assertThat(ThreadContext.getResources()).isEmpty();
        for (Map.Entry<String, Class<?>> service : runtime.services().entrySet()) {
            assertThat(BaseContext.get(service.getKey(), service.getValue())).isEmpty();
        }
    }

    /**
     * \u7ed1\u5b9a\u540c\u7c7b\u578b\u7684\u8bf7\u6c42\u7ea7\u4ee3\u7406\uff0c\u5e76\u68c0\u67e5\u8bf7\u6c42\u8bfb\u53d6\u65f6\u4f18\u5148\u89e3\u6790\u5b83\u3002
     * @param <T> \u670d\u52a1\u63a5\u53e3\u7c7b\u578b
     * @param key \u670d\u52a1\u6ce8\u518c\u952e
     * @param type \u7528\u4e8e\u6784\u9020\u8bf7\u6c42\u7ea7\u4ee3\u7406\u7684\u670d\u52a1\u63a5\u53e3
     */
    private <T> void assertRequestOverride(String key, Class<T> type) {
        T requestScoped = type.cast(Proxy.newProxyInstance(type.getClassLoader(),
                new Class<?>[]{type}, (proxy, method, args) -> null));
        ThreadContext.inject(key, type, requestScoped);
        assertThat(Contexts.get(key, type)).hasValueSatisfying(value -> assertThat(value).isSameAs(requestScoped));
    }
}
