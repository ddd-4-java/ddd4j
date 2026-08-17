package io.ddd4j.dropwizard;

import io.ddd4j.core.cqrs.command.CommandExecutor;
import io.ddd4j.core.i18n.I18nProvider;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import com.codahale.metrics.health.HealthCheckRegistry;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Ddd4jBundleTest {

    @Mock
    private Environment environment;

    @Mock
    private HealthCheckRegistry healthChecks;

    @Mock
    private LifecycleEnvironment lifecycle;

    private static final class TestConfiguration extends Configuration {
    }

    @Test
    void defaultConstructorBuildsBundle() {
        Ddd4jBundle<TestConfiguration> bundle = new Ddd4jBundle<>();
        assertNotNull(bundle);
        assertThrows(NullPointerException.class, () -> bundle.initialize(null));
    }

    @Test
    void constructorRejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> new Ddd4jBundle<>(null,
                Collections.emptyList(), null, I18nProvider.DEFAULT));
        assertThrows(NullPointerException.class, () -> new Ddd4jBundle<>(Collections.emptyList(),
                null, null, I18nProvider.DEFAULT));
        assertThrows(NullPointerException.class, () -> new Ddd4jBundle<>(Collections.emptyList(),
                Collections.emptyList(), null, null));
    }

    @Test
    void runRegistersHealthCheckAndRuntime() {
        when(environment.healthChecks()).thenReturn(healthChecks);
        when(environment.lifecycle()).thenReturn(lifecycle);

        Ddd4jBundle<TestConfiguration> bundle = new Ddd4jBundle<>();
        bundle.run(new TestConfiguration(), environment);

        verify(healthChecks).register(org.mockito.ArgumentMatchers.eq("ddd4j-readiness"), any());
        verify(lifecycle).manage(any(Managed.class));
    }

    @Test
    void runRejectsNullConfigurationAndEnvironment() {
        Ddd4jBundle<TestConfiguration> bundle = new Ddd4jBundle<>();
        assertThrows(NullPointerException.class, () -> bundle.run(null, environment));
        assertThrows(NullPointerException.class, () -> bundle.run(new TestConfiguration(), null));
    }
}
