package io.ddd4j.core.context;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;

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
    }

    private final static class Service {

        private static final long serialVersionUID = 0L;

        private final String name;

        @JsonCreator()
        private Service(@JsonProperty("name") String name) {
            this.name = name;
        }

        @JsonProperty("name")
        public String name() {
            return name;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (Objects.isNull(obj) || getClass() != obj.getClass()) {
                return false;
            }
            Service other = (Service) obj;
            return Objects.equals(this.name, other.name);
        }

        @Override
        public int hashCode() {
            int result = 0;
            result = 31 * result + Objects.hashCode(name);
            return result;
        }

        @Override
        public String toString() {
            return "Service[name=" + name + "]";
        }
    }
}
