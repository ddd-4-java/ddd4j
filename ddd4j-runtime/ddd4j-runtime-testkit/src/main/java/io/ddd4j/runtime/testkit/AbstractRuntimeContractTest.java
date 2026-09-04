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

    protected abstract RuntimeContract createRuntime();

    @BeforeEach
    void cleanContexts() {
        ThreadContext.clear();
        BaseContext.clear();
    }

    @AfterEach
    void clearContexts() {
        ThreadContext.clear();
        BaseContext.clear();
    }

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

    private <T> void assertRequestOverride(String key, Class<T> type) {
        T requestScoped = type.cast(Proxy.newProxyInstance(type.getClassLoader(),
                new Class<?>[]{type}, (proxy, method, args) -> null));
        ThreadContext.inject(key, type, requestScoped);
        assertThat(Contexts.get(key, type)).hasValueSatisfying(value -> assertThat(value).isSameAs(requestScoped));
    }
}
