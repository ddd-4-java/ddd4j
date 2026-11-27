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
package io.ddd4j.dropwizard;

import com.codahale.metrics.health.HealthCheckRegistry;
import io.ddd4j.core.i18n.I18nProvider;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 类型说明。
 * @author ddd4j
 * @since 3.0.x
 */
@ExtendWith(MockitoExtension.class)
class Ddd4jBundleTest {

    /** \u7528\u4e8e\u9694\u79bb <code>Environment</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private Environment environment;

    /** \u7528\u4e8e\u9694\u79bb <code>HealthCheckRegistry</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private HealthCheckRegistry healthChecks;

    /** \u7528\u4e8e\u9694\u79bb <code>LifecycleEnvironment</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private LifecycleEnvironment lifecycle;

    /** \u9a8c\u8bc1<code>bundle</code>\u975e\u7a7a\uff0c<code>bundle.initialize(null)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void defaultConstructorBuildsBundle() {
        Ddd4jBundle<TestConfiguration> bundle = new Ddd4jBundle<>();
        assertNotNull(bundle);
        assertThrows(NullPointerException.class, () -> bundle.initialize(null));
    }

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void constructorRejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> new Ddd4jBundle<>(null,
                Collections.emptyList(), null, I18nProvider.DEFAULT));
        assertThrows(NullPointerException.class, () -> new Ddd4jBundle<>(Collections.emptyList(),
                null, null, I18nProvider.DEFAULT));
        assertThrows(NullPointerException.class, () -> new Ddd4jBundle<>(Collections.emptyList(),
                Collections.emptyList(), null, null));
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>environment.healthChecks()</code>\u3001<code>environment.lifecycle()</code>\u3001<code>bundle.run()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void runRegistersHealthCheckAndRuntime() {
        when(environment.healthChecks()).thenReturn(healthChecks);
        when(environment.lifecycle()).thenReturn(lifecycle);

        Ddd4jBundle<TestConfiguration> bundle = new Ddd4jBundle<>();
        bundle.run(new TestConfiguration(), environment);

        verify(healthChecks).register(org.mockito.ArgumentMatchers.eq("ddd4j-readiness"), any());
        verify(lifecycle).manage(any(Managed.class));
    }

    /** \u9a8c\u8bc1<code>bundle.run(null, environment)</code> \u629b\u51fa <code>NullPointerException.class</code>\uff0c<code>bundle.run(new TestConfiguration(), null)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void runRejectsNullConfigurationAndEnvironment() {
        Ddd4jBundle<TestConfiguration> bundle = new Ddd4jBundle<>();
        assertThrows(NullPointerException.class, () -> bundle.run(null, environment));
        assertThrows(NullPointerException.class, () -> bundle.run(new TestConfiguration(), null));
    }

    /**
     * 类型说明。
     * @author ddd4j
     * @since 3.0.x
     */
    private static final class TestConfiguration extends Configuration {
    }
}
