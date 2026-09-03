package io.ddd4j.web.dropwizard;

import io.dropwizard.Configuration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Ddd4jDropwizardWebBundleTest {

    @Mock
    private Environment environment;

    @Mock
    private JerseyEnvironment jersey;

    private static final class TestConfiguration extends Configuration {
    }

    @Test
    void constructorRejectsNullResolver() {
        assertThrows(NullPointerException.class, () -> new Ddd4jDropwizardWebBundle<>(null));
    }

    @Test
    void defaultConstructorBuildsDefaultConfiguration() {
        Ddd4jDropwizardWebBundle<TestConfiguration> bundle = new Ddd4jDropwizardWebBundle<>();
        assertNotNull(bundle);
        assertDoesNotThrow(() -> bundle.initialize(null));
    }

    @Test
    void runRegistersFiltersAndExceptionMappers() {
        when(environment.jersey()).thenReturn(jersey);

        Ddd4jDropwizardWebBundle<TestConfiguration> bundle = new Ddd4jDropwizardWebBundle<>();
        bundle.run(new TestConfiguration(), environment);

        verify(jersey, atLeastOnce()).register(any(Ddd4jDropwizardRequestFilter.class));
        verify(jersey, atLeastOnce()).register(any(Ddd4jDropwizardResponseFilter.class));
        verify(jersey, atLeastOnce()).register(any(Ddd4jDropwizardExceptionMapper.class));
    }

    @Test
    void runRejectsNullWebConfiguration() {
        Ddd4jDropwizardWebBundle<TestConfiguration> bundle =
                new Ddd4jDropwizardWebBundle<>(configuration -> null);

        assertThrows(NullPointerException.class, () -> bundle.run(new TestConfiguration(), environment));
    }

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
}
