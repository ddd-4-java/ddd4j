package io.ddd4j.guice.config;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GuicePropertyLoaderTest {

    @Test
    void loadsDefaultConfigFile() {
        Injector injector = Guice.createInjector(new GuicePropertyLoader());

        assertEquals("from-default",
                injector.getInstance(Key.get(String.class, Names.named("ddd4j.test.key"))));
    }

    @Test
    void loadsCustomConfigFile() {
        Injector injector = Guice.createInjector(new GuicePropertyLoader("custom-config.properties"));

        assertEquals("custom-value",
                injector.getInstance(Key.get(String.class, Names.named("custom.key"))));
    }

    @Test
    void toleratesMissingConfigFile() {
        Injector injector = Guice.createInjector(new GuicePropertyLoader("no-such-file.properties"));

        assertNotNull(injector);
        assertThrows(com.google.inject.ConfigurationException.class,
                () -> injector.getInstance(Key.get(String.class, Names.named("missing.key"))));
    }
}
