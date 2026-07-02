package io.ddd4j.core.context;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.domain.event.DomainEvent;
import io.ddd4j.core.domain.event.DomainEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextLookupTest {

    private final String testKey = "ddd4j.test." + UUID.randomUUID();
    private static final String[] sharedKeys = {
            SpiKeys.DOMAIN_EVENT_PUBLISHER,
            SpiKeys.MQ_EVENT_PUBLISHER
    };

    private static DomainEventPublisher stubPublisher(String name) {
        return new DomainEventPublisher() {
            @Override
            public <T> void publish(DomainEvent<T> event) {
                // no-op for test
            }
            @Override
            public String toString() { return name; }
        };
    }

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
    void shouldInjectAndLookup() {
        DomainEventPublisher publisher = stubPublisher("p1");
        BaseContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, publisher);
        assertEquals(publisher,
                Contexts.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class).orElse(null));
    }

    @Test
    void shouldThrowOnMissingService() {
        BaseContext.remove(SpiKeys.DOMAIN_EVENT_PUBLISHER);
        assertThrows(IllegalStateException.class,
                () -> Contexts.injectOrThrow(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class));
    }

    @Test
    void shouldReturnEmptyForMissingService() {
        BaseContext.remove(SpiKeys.MQ_EVENT_PUBLISHER);
        assertTrue(Contexts.inject(SpiKeys.MQ_EVENT_PUBLISHER, DomainEventPublisher.class).isEmpty());
    }

    @Test
    void shouldRejectNullValue() {
        assertThrows(IllegalArgumentException.class,
                () -> BaseContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, null));
    }

    @Test
    void shouldReturnEmptyForTypeMismatch() {
        DomainEventPublisher publisher = stubPublisher("p2");
        BaseContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, publisher);
        assertTrue(Contexts.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, String.class).isEmpty());
    }

    @Test
    void threadContextShouldOverrideBaseContext() {
        DomainEventPublisher globalPub = stubPublisher("global");
        DomainEventPublisher threadPub = stubPublisher("thread");
        BaseContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, globalPub);
        ThreadContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, threadPub);
        assertEquals(threadPub,
                Contexts.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class).orElse(null));
    }

    @Test
    void shouldRemoveFromBaseContext() {
        BaseContext.inject(testKey, "value");
        assertTrue(BaseContext.contains(testKey));
        BaseContext.remove(testKey);
        assertFalse(BaseContext.contains(testKey));
    }
}
