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
 * 验证 <code>Ddd4jBundle</code> 在<code>constructor</code>、<code>run</code>等操作中的行为契约。
 */
@ExtendWith(MockitoExtension.class)
class Ddd4jBundleTest {

    /**
     * 用于隔离 <code>Environment</code> 调用并记录交互的测试替身。
     */
    @Mock
    private Environment environment;

    /**
     * 用于隔离 <code>HealthCheckRegistry</code> 调用并记录交互的测试替身。
     */
    @Mock
    private HealthCheckRegistry healthChecks;

    /**
     * 用于隔离 <code>LifecycleEnvironment</code> 调用并记录交互的测试替身。
     */
    @Mock
    private LifecycleEnvironment lifecycle;

    /**
     * 验证<code>bundle</code>非空，<code>bundle.initialize(null)</code> 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void defaultConstructorBuildsBundle() {
        Ddd4jBundle<TestConfiguration> bundle = new Ddd4jBundle<>();
        assertNotNull(bundle);
        assertThrows(NullPointerException.class, () -> bundle.initialize(null));
    }

    /**
     * 验证被测调用 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void constructorRejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> new Ddd4jBundle<>(null,
                Collections.emptyList(), null, I18nProvider.DEFAULT));
        assertThrows(NullPointerException.class, () -> new Ddd4jBundle<>(Collections.emptyList(),
                null, null, I18nProvider.DEFAULT));
        assertThrows(NullPointerException.class, () -> new Ddd4jBundle<>(Collections.emptyList(),
                Collections.emptyList(), null, null));
    }

    /**
     * 在测试框架中执行 <code>environment.healthChecks()</code>、<code>environment.lifecycle()</code>、<code>bundle.run()</code>，验证该场景。
     */
    @Test
    void runRegistersHealthCheckAndRuntime() {
        when(environment.healthChecks()).thenReturn(healthChecks);
        when(environment.lifecycle()).thenReturn(lifecycle);

        Ddd4jBundle<TestConfiguration> bundle = new Ddd4jBundle<>();
        bundle.run(new TestConfiguration(), environment);

        verify(healthChecks).register(org.mockito.ArgumentMatchers.eq("ddd4j-readiness"), any());
        verify(lifecycle).manage(any(Managed.class));
    }

    /**
     * 验证<code>bundle.run(null, environment)</code> 抛出 <code>NullPointerException.class</code>，<code>bundle.run(new TestConfiguration(), null)</code> 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void runRejectsNullConfigurationAndEnvironment() {
        Ddd4jBundle<TestConfiguration> bundle = new Ddd4jBundle<>();
        assertThrows(NullPointerException.class, () -> bundle.run(null, environment));
        assertThrows(NullPointerException.class, () -> bundle.run(new TestConfiguration(), null));
    }

    /**
     * 提供运行 HTTP 合同测试所需的配置。
     */
    private static final class TestConfiguration extends Configuration {
    }
}
