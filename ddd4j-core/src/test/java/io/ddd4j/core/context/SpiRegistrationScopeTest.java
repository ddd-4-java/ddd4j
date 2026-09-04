package io.ddd4j.core.context;

import java.util.Objects;
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
    }private static final class Service  {
        private final String name;

        public Service(String name) {
            this.name = name;
        }
        public String name() { return name; }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Service)) return false;
            Service other = (Service) o;
            return Objects.equals(this.name, other.name);
        }
        @Override
        public int hashCode() {
            return java.util.Objects.hash(name);
        }
        @Override
        public String toString() {
            return "Service{" + "name=" + name + "}";
        }
    
    }
}
