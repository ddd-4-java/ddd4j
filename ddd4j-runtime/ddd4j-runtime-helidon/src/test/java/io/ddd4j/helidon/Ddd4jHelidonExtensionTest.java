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

/** \u9a8c\u8bc1 <code>Ddd4jHelidonExtension</code> \u5728<code>startInitializesRuntime</code>\u3001<code>stopClosesRuntime</code>\u3001<code>stopIsSafe</code>\u7b49\u64cd\u4f5c\u4e2d\u7684\u884c\u4e3a\u5951\u7ea6\u3002 */
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

    /** \u9a8c\u8bc1<code>extension.start(afterDeploymentValidation, beanManager)</code> \u629b\u51fa <code>LinkageError.class</code>\uff0c<code>SubjectKit.subjectProvider</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>previousSubject</code>\uff0c<code>BaseContext.get(SpiKeys.SUBJECT_PROVIDER, SubjectProvider.class)</code> \u7b49\u4e8e <code>previousSpi</code>\u3002 */
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

    /** \u9a8c\u8bc1<code>extension.stop(beforeShutdown)</code> \u629b\u51fa <code>LinkageError.class</code>\uff0c<code>thrown</code> \u4e0e\u2026\u4e3a\u540c\u4e00\u5b9e\u4f8b <code>firstFailure</code>\u3002 */
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

    /**
     * \u8bfb\u53d6 <code>context</code> \u4f9b\u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u3002
     * @param <T> \u5f53\u524d\u5951\u7ea6\u6d4b\u8bd5\u6240\u4f7f\u7528\u7684 Java \u7c7b\u578b
     * @param type \u7528\u6765\u89e3\u6790\u3001\u68c0\u67e5\u6216\u6784\u9020\u6d4b\u8bd5\u5bf9\u8c61\u7684 Java \u7c7b\u578b
     * @param instance \u4f20\u7ed9 <code>when()</code> \u7684 <code>T</code> \u8f93\u5165
     * @return \u5f53\u524d\u6d4b\u8bd5\u4f7f\u7528\u7684\u7ed3\u679c\uff0c\u53d6\u81ea <code>context</code>
     */
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

/** \u8986\u76d6 Helidon \u6269\u5c55\u53d1\u73b0 Bean\u3001\u83b7\u53d6\u5f15\u7528\u548c\u6e05\u7406\u8d44\u6e90\u7684\u5206\u652f\u3002 */
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
