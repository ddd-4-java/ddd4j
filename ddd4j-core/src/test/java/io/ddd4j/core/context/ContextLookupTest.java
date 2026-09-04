package io.ddd4j.core.context;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContextLookupTest {

    private static final String[] sharedKeys = {
            SpiKeys.DOMAIN_EVENT_PUBLISHER,
            SpiKeys.MQ_EVENT_PUBLISHER
    };
    private final String testKey = "ddd4j.test." + UUID.randomUUID();

    @BeforeEach
    void setUp() {
        clearSharedKeys();
    }

    @AfterEach
    void tearDown() {
        clearSharedKeys();
    }

    private void clearSharedKeys() {
        BaseContext.remove(testKey);
        ThreadContext.remove(testKey);
        for (String sharedKey : sharedKeys) {
            BaseContext.remove(sharedKey);
            ThreadContext.remove(sharedKey);
        }
    }

    @Test
    void shouldThrowOnMissingService() {
        BaseContext.remove(SpiKeys.DOMAIN_EVENT_PUBLISHER);
        assertThrows(IllegalStateException.class, () -> Contexts.getOrThrow(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class));
    }

    @Test
    void shouldReturnEmptyForMissingService() {
        BaseContext.remove(SpiKeys.MQ_EVENT_PUBLISHER);
        assertFalse(Contexts.get(SpiKeys.MQ_EVENT_PUBLISHER, DomainEventPublisher.class).isPresent());
    }

    @Test
    void shouldRejectNullValue() {
        assertThrows(IllegalArgumentException.class,
                () -> BaseContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, null));
    }

    @Test
    void shouldRemoveFromBaseContext() {
        BaseContext.inject(testKey, "value");
        assertTrue(BaseContext.contains(testKey));
        BaseContext.remove(testKey);
        assertFalse(BaseContext.contains(testKey));
    }
}
