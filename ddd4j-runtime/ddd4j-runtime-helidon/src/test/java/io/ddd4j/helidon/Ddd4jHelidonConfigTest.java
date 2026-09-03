package io.ddd4j.helidon;

import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Ddd4jHelidonConfigTest {

    @Mock
    private Config config;

    @Test
    void valueReturnsConfiguredValue() {
        when(config.getOptionalValue("ddd4j.test", String.class))
                .thenReturn(Optional.of("configured"));

        Ddd4jHelidonConfig ddd4jConfig = new Ddd4jHelidonConfig(config);

        assertEquals(Optional.of("configured"), ddd4jConfig.value("ddd4j.test", String.class));
    }

    @Test
    void valueReturnsEmptyWhenAbsent() {
        when(config.getOptionalValue("missing", Integer.class))
                .thenReturn(Optional.empty());

        Ddd4jHelidonConfig ddd4jConfig = new Ddd4jHelidonConfig(config);

        assertEquals(Optional.empty(), ddd4jConfig.value("missing", Integer.class));
    }

    @Test
    void constructorRejectsNullConfig() {
        assertThrows(NullPointerException.class, () -> new Ddd4jHelidonConfig(null));
    }
}
