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

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.context.BaseContext;
import io.ddd4j.core.cqrs.readmodel.ViewManager;
import io.ddd4j.core.i18n.I18nProvider;
import io.ddd4j.core.subject.SubjectProvider;
import io.ddd4j.core.util.SubjectKit;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.*;
import jakarta.enterprise.inject.spi.configurator.BeanConfigurator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Ddd4jHelidonExtensionTest {

    @Mock
    private BeanManager beanManager;

    @Mock
    private AfterBeanDiscovery afterBeanDiscovery;

    @Mock
    private AfterDeploymentValidation afterDeploymentValidation;

    @Mock
    private BeforeShutdown beforeShutdown;

    @Test
    void rollsBackRuntimeAndReferencesOnExplicitProjectionStartupError() {
        String key = "ddd4j.cqrs.projection.enabled";
        SubjectProvider previousSubject = SubjectKit.subjectProvider;
        Optional<SubjectProvider> previousSpi = BaseContext.get(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class);
        ViewManager manager = mock(ViewManager.class);
        CreationalContext<ViewManager> reference = bind(ViewManager.class, manager);
        LinkageError startupError = new LinkageError("projection scheduler unavailable");
        doThrow(startupError).when(manager).start();
        Ddd4jHelidonExtension extension = new Ddd4jHelidonExtension();
        String previousValue = System.setProperty(key, "true");

        try {
            assertSame(startupError, assertThrows(LinkageError.class,
                    () -> extension.start(afterDeploymentValidation, beanManager)));
            assertSame(previousSubject, SubjectKit.subjectProvider);
            assertEquals(previousSpi, BaseContext.get(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class));
            verify(reference).release();
        } finally {
            extension.stop(beforeShutdown);
            if (Objects.isNull(previousValue)) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previousValue);
            }
        }
    }

    @Test
    void releasesRemainingReferencesAndKeepsFirstShutdownFailure() {
        CreationalContext<SubjectProvider> first = bind(SubjectProvider.class, mock(SubjectProvider.class));
        CreationalContext<I18nProvider> second = bind(I18nProvider.class, I18nProvider.DEFAULT);
        LinkageError firstFailure = new LinkageError("first context release failed");
        IllegalStateException secondFailure = new IllegalStateException("second context release failed");
        doThrow(firstFailure).when(first).release();
        doThrow(secondFailure).when(second).release();
        Ddd4jHelidonExtension extension = new Ddd4jHelidonExtension();
        extension.start(afterDeploymentValidation, beanManager);

        try {
            LinkageError thrown = assertThrows(LinkageError.class, () -> extension.stop(beforeShutdown));
            assertSame(firstFailure, thrown);
            assertArrayEquals(new Throwable[]{secondFailure}, thrown.getSuppressed());
            assertDoesNotThrow(() -> extension.stop(beforeShutdown));
            verify(first).release();
            verify(second).release();
        } finally {
            reset(first, second);
            extension.stop(beforeShutdown);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> CreationalContext<T> bind(Class<T> type, T instance) {
        Bean<T> bean = mock(Bean.class);
        CreationalContext<T> context = mock(CreationalContext.class);
        Set<Bean<?>> beans = Collections.singleton(bean);
        lenient().when(beanManager.getBeans(type)).thenReturn(beans);
        doReturn(bean).when(beanManager).resolve(beans);
        when(bean.getTypes()).thenReturn(Collections.singleton(type));
        when(beanManager.createCreationalContext(bean)).thenReturn(context);
        when(beanManager.getReference(bean, type, context)).thenReturn(instance);
        return context;
    }

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

    @Test
    void startInitializesRuntimeWithDefaults() {
        when(beanManager.getBeans(any(Class.class))).thenReturn(Collections.emptySet());

        Ddd4jHelidonExtension extension = new Ddd4jHelidonExtension();
        assertDoesNotThrow(() -> extension.start(afterDeploymentValidation, beanManager));
    }

    @Test
    void stopClosesRuntimeWhenStarted() {
        when(beanManager.getBeans(any(Class.class))).thenReturn(Collections.emptySet());

        Ddd4jHelidonExtension extension = new Ddd4jHelidonExtension();
        extension.start(afterDeploymentValidation, beanManager);
        assertDoesNotThrow(() -> extension.stop(beforeShutdown));
    }

    @Test
    void stopIsSafeWithoutStart() {
        Ddd4jHelidonExtension extension = new Ddd4jHelidonExtension();
        assertDoesNotThrow(() -> extension.stop(beforeShutdown));
    }
}

class Ddd4jHelidonExtensionBeanBranchTest {

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
