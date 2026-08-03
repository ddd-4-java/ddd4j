package io.ddd4j.runtime.testkit;

import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.context.Contexts;
import io.ddd4j.core.context.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runtime 适配器共享的 SPI 注册、重复启动与关闭清理契约。
 */
public abstract class AbstractRuntimeContractTest {

    protected abstract RuntimeContract createRuntime();

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
