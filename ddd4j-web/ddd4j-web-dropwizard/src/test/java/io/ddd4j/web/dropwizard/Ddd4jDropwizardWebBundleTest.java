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

import io.dropwizard.Configuration;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.setup.Environment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** \u9a8c\u8bc1 <code>Ddd4jDropwizardWebBundle</code> \u5728<code>constructor</code>\u3001<code>run</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
@ExtendWith(MockitoExtension.class)
class Ddd4jDropwizardWebBundleTest {

    /** \u7528\u4e8e\u9694\u79bb <code>Environment</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private Environment environment;

    /** \u7528\u4e8e\u9694\u79bb <code>JerseyEnvironment</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private JerseyEnvironment jersey;

    /** \u9a8c\u8bc1\u88ab\u6d4b\u8c03\u7528 \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void constructorRejectsNullResolver() {
        assertThrows(NullPointerException.class, () -> new Ddd4jDropwizardWebBundle<>(null));
    }

    /** \u9a8c\u8bc1<code>bundle</code>\u975e\u7a7a\u3002 */
    @Test
    void defaultConstructorBuildsDefaultConfiguration() {
        Ddd4jDropwizardWebBundle<TestConfiguration> bundle = new Ddd4jDropwizardWebBundle<>();
        assertNotNull(bundle);
        assertDoesNotThrow(() -> bundle.initialize(null));
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>environment.jersey()</code>\u3001<code>bundle.run()</code>\u3001<code>atLeastOnce()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void runRegistersFiltersAndExceptionMappers() {
        when(environment.jersey()).thenReturn(jersey);

        Ddd4jDropwizardWebBundle<TestConfiguration> bundle = new Ddd4jDropwizardWebBundle<>();
        bundle.run(new TestConfiguration(), environment);

        verify(jersey, atLeastOnce()).register(any(Ddd4jDropwizardRequestFilter.class));
        verify(jersey, atLeastOnce()).register(any(Ddd4jDropwizardResponseFilter.class));
        verify(jersey, atLeastOnce()).register(any(Ddd4jDropwizardExceptionMapper.class));
    }

    /** \u9a8c\u8bc1<code>bundle.run(new TestConfiguration(), environment)</code> \u629b\u51fa <code>NullPointerException.class</code>\u3002 */
    @Test
    void runRejectsNullWebConfiguration() {
        Ddd4jDropwizardWebBundle<TestConfiguration> bundle =
                new Ddd4jDropwizardWebBundle<>(configuration -> null);

        assertThrows(NullPointerException.class, () -> bundle.run(new TestConfiguration(), environment));
    }

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>environment.jersey()</code>\u3001<code>webConfiguration.setTrustForwardedHeaders()</code>\u3001<code>bundle.run()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
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

    /** \u63d0\u4f9b\u8fd0\u884c HTTP \u5408\u540c\u6d4b\u8bd5\u6240\u9700\u7684\u914d\u7f6e\u3002 */
    private static final class TestConfiguration extends Configuration {
    }
}
