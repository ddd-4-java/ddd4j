package io.ddd4j.core.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpiRegistrationScopeTest {

    private static final String KEY = "test.spi.lifecycle";

    @AfterEach
    void clearContexts() {
        ThreadContext.clear();
        BaseContext.clear();
    }

    @Test
    void shouldRestorePreviousServiceOnClose() {
        Service previous = new Service("previous");
        Service installed = new Service("installed");
        BaseContext.inject(KEY, Service.class, previous);

        try (SpiRegistrationScope scope = new SpiRegistrationScope()
                .register(KEY, Service.class, installed)) {
            scope.start();
            assertThat(BaseContext.get(KEY, Service.class)).contains(installed);
        }

        assertThat(BaseContext.get(KEY, Service.class)).contains(previous);
    }

    @Test
    void shouldNotRemoveServiceReplacedByApplicationAfterStart() {
        Service installed = new Service("installed");
        Service replacement = new Service("replacement");
        SpiRegistrationScope scope = new SpiRegistrationScope().register(KEY, Service.class, installed);
        scope.start();
        BaseContext.inject(KEY, Service.class, replacement);

        scope.close();

        assertThat(BaseContext.get(KEY, Service.class)).contains(replacement);
    }

    private record Service(String name) {
    }
}
