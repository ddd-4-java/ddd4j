package io.ddd4j.helidon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.configurator.BeanConfigurator;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeShutdown;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void registerReadinessRegistryAddsBean() {
        BeanConfigurator<Object> configurator = mock(BeanConfigurator.class);
        when(afterBeanDiscovery.addBean()).thenReturn(configurator);
        when(configurator.addType(any(Class.class))).thenReturn(configurator);
        when(configurator.scope(any(Class.class))).thenReturn(configurator);
        when(configurator.createWith(any())).thenReturn(configurator);

        Ddd4jHelidonExtension extension = new Ddd4jHelidonExtension();
        extension.registerReadinessRegistry(afterBeanDiscovery);

        verify(afterBeanDiscovery).addBean();
        verify(configurator).addType(io.ddd4j.runtime.health.RuntimeReadinessRegistry.class);
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
        javax.enterprise.inject.spi.BeanManager beanManager = org.mockito.Mockito.mock(javax.enterprise.inject.spi.BeanManager.class);
        javax.enterprise.inject.spi.AfterDeploymentValidation event = org.mockito.Mockito.mock(javax.enterprise.inject.spi.AfterDeploymentValidation.class);

        io.ddd4j.core.subject.SubjectProvider subjectProvider = org.mockito.Mockito.mock(io.ddd4j.core.subject.SubjectProvider.class);
        io.ddd4j.core.i18n.I18nProvider i18nProvider = org.mockito.Mockito.mock(io.ddd4j.core.i18n.I18nProvider.class);
        io.ddd4j.core.cqrs.command.CommandBus commandBus = org.mockito.Mockito.mock(io.ddd4j.core.cqrs.command.CommandBus.class);

        when(beanManager.getBeans(org.mockito.ArgumentMatchers.any(Class.class)))
                .thenReturn(java.util.Collections.singleton(org.mockito.Mockito.mock(javax.enterprise.inject.spi.Bean.class)));
        when(beanManager.resolve(org.mockito.ArgumentMatchers.any(java.util.Set.class)))
                .thenAnswer(inv -> inv.getArgument(0, java.util.Set.class).iterator().next());
        when(beanManager.createCreationalContext(org.mockito.ArgumentMatchers.any()))
                .thenReturn(org.mockito.Mockito.mock(javax.enterprise.context.spi.CreationalContext.class));
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
