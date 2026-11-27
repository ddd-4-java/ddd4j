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
package io.ddd4j.helidon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AfterDeploymentValidation;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.configurator.BeanConfigurator;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeShutdown;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
@ExtendWith(MockitoExtension.class)
class Ddd4jHelidonExtensionTest {

    /** \u7528\u4e8e\u9694\u79bb <code>BeanManager</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private BeanManager beanManager;

    /** \u7528\u4e8e\u9694\u79bb <code>AfterBeanDiscovery</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private AfterBeanDiscovery afterBeanDiscovery;

    /** \u7528\u4e8e\u9694\u79bb <code>AfterDeploymentValidation</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private AfterDeploymentValidation afterDeploymentValidation;

    /** \u7528\u4e8e\u9694\u79bb <code>BeforeShutdown</code> \u8c03\u7528\u5e76\u8bb0\u5f55\u4ea4\u4e92\u7684\u6d4b\u8bd5\u66ff\u8eab\u3002 */
    @Mock
    private BeforeShutdown beforeShutdown;

    /** \u5728\u6d4b\u8bd5\u6846\u67b6\u4e2d\u6267\u884c <code>mock()</code>\u3001<code>afterBeanDiscovery.addBean()</code>\u3001<code>configurator.addType()</code>\uff0c\u9a8c\u8bc1\u8be5\u573a\u666f\u3002 */
    @Test
    void registerReadinessRegistryAddsBean() {
        BeanConfigurator<Object> configurator = mock(BeanConfigurator.class);
        when(afterBeanDiscovery.addBean()).thenReturn(configurator);
        when(configurator.addType(any(Class.class))).thenReturn(configurator);
        when(configurator.scope(any(Class.class))).thenReturn(configurator);
        when(configurator.createWith(any())).thenReturn(configurator);

        Ddd4jHelidonExtension extension = new Ddd4jHelidonExtension();
        extension.registerReadinessRegistry(afterBeanDiscovery);

        verify(afterBeanDiscovery).addBean();
        verify(configurator).addType(io.ddd4j.core.health.RuntimeReadinessRegistry.class);
    }

    /** \u9a8c\u8bc1<code>extension.start(afterDeploymentValidation, beanManager)</code> \u6b63\u5e38\u8fd4\u56de\u3002 */
    @Test
    void startInitializesRuntimeWithDefaults() {
        when(beanManager.getBeans(any(Class.class))).thenReturn(Collections.emptySet());

        Ddd4jHelidonExtension extension = new Ddd4jHelidonExtension();
        assertDoesNotThrow(() -> extension.start(afterDeploymentValidation, beanManager));
    }

    /** \u9a8c\u8bc1<code>extension.stop(beforeShutdown)</code> \u6b63\u5e38\u8fd4\u56de\u3002 */
    @Test
    void stopClosesRuntimeWhenStarted() {
        when(beanManager.getBeans(any(Class.class))).thenReturn(Collections.emptySet());

        Ddd4jHelidonExtension extension = new Ddd4jHelidonExtension();
        extension.start(afterDeploymentValidation, beanManager);
        assertDoesNotThrow(() -> extension.stop(beforeShutdown));
    }

    /** \u9a8c\u8bc1<code>extension.stop(beforeShutdown)</code> \u6b63\u5e38\u8fd4\u56de\u3002 */
    @Test
    void stopIsSafeWithoutStart() {
        Ddd4jHelidonExtension extension = new Ddd4jHelidonExtension();
        assertDoesNotThrow(() -> extension.stop(beforeShutdown));
    }
}

/**
 * 类型说明。
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 3.0.x
 */
class Ddd4jHelidonExtensionBeanBranchTest {

    /** \u6309 <code>(type == io.ddd4j.core.subject.SubjectProvider.class)</code> \u7b49\u5b9e\u9645\u6761\u4ef6\u9009\u62e9\u5f53\u524d\u7528\u4f8b\u8fd4\u56de\u7684\u7ed3\u679c\u3002 */
    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
    void startUsesExistingBeans() {
        jakarta.enterprise.inject.spi.BeanManager beanManager = org.mockito.Mockito.mock(jakarta.enterprise.inject.spi.BeanManager.class);
        jakarta.enterprise.inject.spi.AfterDeploymentValidation event = org.mockito.Mockito.mock(jakarta.enterprise.inject.spi.AfterDeploymentValidation.class);

        io.ddd4j.core.subject.SubjectProvider subjectProvider = org.mockito.Mockito.mock(io.ddd4j.core.subject.SubjectProvider.class);
        io.ddd4j.core.i18n.I18nProvider i18nProvider = org.mockito.Mockito.mock(io.ddd4j.core.i18n.I18nProvider.class);
        io.ddd4j.core.cqrs.command.CommandBus commandBus = org.mockito.Mockito.mock(io.ddd4j.core.cqrs.command.CommandBus.class);

        when(beanManager.getBeans(org.mockito.ArgumentMatchers.any(Class.class)))
                .thenReturn(java.util.Collections.singleton(org.mockito.Mockito.mock(jakarta.enterprise.inject.spi.Bean.class)));
        when(beanManager.resolve(org.mockito.ArgumentMatchers.any(java.util.Set.class)))
                .thenAnswer(inv -> inv.getArgument(0, java.util.Set.class).iterator().next());
        when(beanManager.createCreationalContext(org.mockito.ArgumentMatchers.any()))
                .thenReturn(org.mockito.Mockito.mock(jakarta.enterprise.context.spi.CreationalContext.class));
        when(beanManager.getReference(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(Class.class), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> {
                    Class<?> type = inv.getArgument(1);
                    if (type == io.ddd4j.core.subject.SubjectProvider.class) {
                        return subjectProvider;
                    }
                    if (type == io.ddd4j.core.i18n.I18nProvider.class) {
                        return i18nProvider;
                    }
                    if (type == io.ddd4j.core.cqrs.command.CommandBus.class) {
                        return commandBus;
                    }
                    return null;
                });

        Ddd4jHelidonExtension extension = new Ddd4jHelidonExtension();
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> extension.start(event, beanManager));
    }
}
