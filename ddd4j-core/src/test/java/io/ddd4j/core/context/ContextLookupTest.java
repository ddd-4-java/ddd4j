package io.ddd4j.core.context;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** \u901a\u8fc7 <code>BaseContext.remove()</code>\u3001<code>Contexts.getOrThrow()</code>\u3001<code>Contexts.get()</code> \u9a8c\u8bc1 <code>ContextLookup</code> \u7684\u8fd0\u884c\u884c\u4e3a\u3002 */
class ContextLookupTest {

    /** \u8bb0\u5f55\u5f53\u524d\u7528\u4f8b\u68c0\u67e5\u5171\u4eab\u6ce8\u518c\u6240\u4f7f\u7528\u7684\u670d\u52a1\u952e\u3002 */
    private static final String[] sharedKeys = {
            SpiKeys.DOMAIN_EVENT_PUBLISHER,
            SpiKeys.MQ_EVENT_PUBLISHER
    };
    /** \u4f9b\u4e0d\u540c\u7528\u4f8b\u5b9a\u4f4d\u540c\u4e00\u9879\u6d4b\u8bd5\u8bbe\u7f6e\u7684\u952e\u3002 */
    private final String testKey = "ddd4j.test." + UUID.randomUUID();

    /** \u6d4b\u8bd5\u6267\u884c\u524d\u6267\u884c <code>clearSharedKeys()</code> \u5b8c\u6210\u8d44\u6e90\u51c6\u5907\u3002 */
    @BeforeEach
    void setUp() {
        clearSharedKeys();
    }

    /** \u6d4b\u8bd5\u7ed3\u675f\u540e\u6267\u884c <code>clearSharedKeys()</code> \u5b8c\u6210\u8d44\u6e90\u56de\u6536\u6216\u72b6\u6001\u6062\u590d\u3002 */
    @AfterEach
    void tearDown() {
        clearSharedKeys();
    }

    /** \u8c03\u7528 <code>BaseContext.remove()</code>\u3001<code>ThreadContext.remove()</code> \u6267\u884c\u5f53\u524d\u5939\u5177\u64cd\u4f5c\u3002 */
    private void clearSharedKeys() {
        BaseContext.remove(testKey);
        ThreadContext.remove(testKey);
        for (String sharedKey : sharedKeys) {
            BaseContext.remove(sharedKey);
            ThreadContext.remove(sharedKey);
        }
    }

    /** \u9a8c\u8bc1<code>Contexts.getOrThrow(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class)</code> \u629b\u51fa <code>IllegalStateException.class</code>\u3002 */
    @Test
    void shouldThrowOnMissingService() {
        BaseContext.remove(SpiKeys.DOMAIN_EVENT_PUBLISHER);
        assertThrows(IllegalStateException.class, () -> Contexts.getOrThrow(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class));
    }

    /** \u9a8c\u8bc1<code>Contexts.get(SpiKeys.MQ_EVENT_PUBLISHER, DomainEventPublisher.class).isPresent()</code>\u4e3a\u5047\u3002 */
    @Test
    void shouldReturnEmptyForMissingService() {
        BaseContext.remove(SpiKeys.MQ_EVENT_PUBLISHER);
        assertTrue(Contexts.get(SpiKeys.MQ_EVENT_PUBLISHER, DomainEventPublisher.class).isEmpty());
    }

    /** \u9a8c\u8bc1<code>BaseContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, null)</code> \u629b\u51fa <code>IllegalArgumentException.class</code>\u3002 */
    @Test
    void shouldRejectNullValue() {
        assertThrows(IllegalArgumentException.class,
                () -> BaseContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, null));
    }

    /** \u9a8c\u8bc1<code>BaseContext.contains(testKey)</code>\u4e3a\u771f\uff0c<code>BaseContext.contains(testKey)</code>\u4e3a\u5047\u3002 */
    @Test
    void shouldRemoveFromBaseContext() {
        BaseContext.inject(testKey, "value");
        assertTrue(BaseContext.contains(testKey));
        BaseContext.remove(testKey);
        assertFalse(BaseContext.contains(testKey));
    }
}
