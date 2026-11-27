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
package io.ddd4j.web.dropwizard;

import io.dropwizard.core.Configuration;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 验证 <code>Ddd4jDropwizardWebBundle</code> 在<code>constructor</code>、<code>run</code>等操作中的行为契约。
 */
@ExtendWith(MockitoExtension.class)
class Ddd4jDropwizardWebBundleTest {

    /**
     * 用于隔离 <code>Environment</code> 调用并记录交互的测试替身。
     */
    @Mock
    private Environment environment;

    /**
     * 用于隔离 <code>JerseyEnvironment</code> 调用并记录交互的测试替身。
     */
    @Mock
    private JerseyEnvironment jersey;

    /**
     * 验证被测调用 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void constructorRejectsNullResolver() {
        assertThrows(NullPointerException.class, () -> new Ddd4jDropwizardWebBundle<>(null));
    }

    /**
     * 验证<code>bundle</code>非空。
     */
    @Test
    void defaultConstructorBuildsDefaultConfiguration() {
        Ddd4jDropwizardWebBundle<TestConfiguration> bundle = new Ddd4jDropwizardWebBundle<>();
        assertNotNull(bundle);
        assertDoesNotThrow(() -> bundle.initialize(null));
    }

    /**
     * 在测试框架中执行 <code>environment.jersey()</code>、<code>bundle.run()</code>、<code>atLeastOnce()</code>，验证该场景。
     */
    @Test
    void runRegistersFiltersAndExceptionMappers() {
        when(environment.jersey()).thenReturn(jersey);

        Ddd4jDropwizardWebBundle<TestConfiguration> bundle = new Ddd4jDropwizardWebBundle<>();
        bundle.run(new TestConfiguration(), environment);

        verify(jersey, atLeastOnce()).register(any(Ddd4jDropwizardRequestFilter.class));
        verify(jersey, atLeastOnce()).register(any(Ddd4jDropwizardResponseFilter.class));
        verify(jersey, atLeastOnce()).register(any(Ddd4jDropwizardExceptionMapper.class));
    }

    /**
     * 验证<code>bundle.run(new TestConfiguration(), environment)</code> 抛出 <code>NullPointerException.class</code>。
     */
    @Test
    void runRejectsNullWebConfiguration() {
        Ddd4jDropwizardWebBundle<TestConfiguration> bundle =
                new Ddd4jDropwizardWebBundle<>(configuration -> null);

        assertThrows(NullPointerException.class, () -> bundle.run(new TestConfiguration(), environment));
    }

    /**
     * 在测试框架中执行 <code>environment.jersey()</code>、<code>webConfiguration.setTrustForwardedHeaders()</code>、<code>bundle.run()</code>，验证该场景。
     */
    @Test
    void runUsesCustomResolver() {
        when(environment.jersey()).thenReturn(jersey);

        Ddd4jDropwizardWebBundle<TestConfiguration> bundle = new Ddd4jDropwizardWebBundle<>(
                configuration -> {
                    Ddd4jDropwizardWebConfiguration webConfiguration = new Ddd4jDropwizardWebConfiguration();
                    webConfiguration.setTrustForwardedHeaders(true);
                    return webConfiguration;
                });
        bundle.run(new TestConfiguration(), environment);

        verify(jersey, atLeastOnce()).register(any(Ddd4jDropwizardRequestFilter.class));
    }

    /**
     * 提供运行 HTTP 合同测试所需的配置。
     */
    private static final class TestConfiguration extends Configuration {
    }
}
