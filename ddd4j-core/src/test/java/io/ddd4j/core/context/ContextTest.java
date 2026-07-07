package io.ddd4j.core.context;

import io.ddd4j.core.constant.SpiKeys;
import io.ddd4j.core.ddd.event.DomainEvent;
import io.ddd4j.core.ddd.event.DomainEventPublisher;
import org.fuin.ddd4j.core.EntityId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link BaseContext}, {@link ThreadContext}, and {@link Contexts}.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
class ContextTest {

    private String testKey;

    @BeforeEach
    void setUp() {
        testKey = "ddd4j.test." + UUID.randomUUID();
        BaseContext.clear();
        ThreadContext.clear();
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
        ThreadContext.clear();
    }

    private DomainEventPublisher stub(String name) {
        return new DomainEventPublisher() {
            @Override
            public <ID extends EntityId> void publish(DomainEvent<ID> event) {

            }

            @Override
            public void publish(Object event) {
            }

            @Override
            public <ID extends EntityId> void publishAll(Collection<DomainEvent<ID>> domainEvents) {
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

    // ========================= BaseContext =========================

    @Test
    void baseContext_injectTyped_shouldLookupBySameType() {
        DomainEventPublisher publisher = stub("p1");
        BaseContext.inject(testKey, DomainEventPublisher.class, publisher);

        Optional<DomainEventPublisher> resolved = BaseContext.get(testKey, DomainEventPublisher.class);

        assertThat(resolved).contains(publisher);
    }

    @Test
    void baseContext_injectTyped_shouldRejectNullValue() {
        assertThatThrownBy(() -> BaseContext.inject(testKey, DomainEventPublisher.class, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void baseContext_injectTyped_shouldRejectNullKey() {
        assertThatThrownBy(() -> BaseContext.inject(null, DomainEventPublisher.class, stub("p")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void baseContext_injectTyped_shouldRejectTypeMismatch() {
        // Use a raw-typed reference to defeat generic inference so the runtime isInstance check fires.
        Class targetType = DomainEventPublisher.class;
        assertThatThrownBy(() -> BaseContext.inject(testKey, targetType, "not-a-publisher"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void baseContext_injectTyped_shouldReturnEmptyForTypeMismatch() {
        BaseContext.inject(testKey, "value");

        assertThat(BaseContext.get(testKey, Integer.class)).isEmpty();
    }

    @Test
    void baseContext_injectUntyped_shouldStoreAndRetrieveValue() {
        BaseContext.inject(testKey, "value");

        assertThat((String) BaseContext.get(testKey)).isEqualTo("value");
        assertThat(BaseContext.contains(testKey)).isTrue();
    }

    @Test
    void baseContext_get_shouldReturnNullForMissingKey() {
        assertThat((Object) BaseContext.get("missing")).isNull();
    }

    @Test
    void baseContext_getWithDefault_shouldReturnDefaultForMissingKey() {
        assertThat(BaseContext.get("missing", "default")).isEqualTo("default");
    }

    @Test
    void baseContext_contains_shouldReturnFalseForNullKey() {
        assertThat(BaseContext.contains(null)).isFalse();
    }

    @Test
    void baseContext_remove_shouldDeleteEntry() {
        BaseContext.inject(testKey, "value");
        assertThat(BaseContext.contains(testKey)).isTrue();

        BaseContext.remove(testKey);

        assertThat(BaseContext.contains(testKey)).isFalse();
    }

    @Test
    void baseContext_clear_shouldRemoveAllEntries() {
        BaseContext.inject(testKey, "v1");
        BaseContext.inject(testKey + "2", "v2");

        BaseContext.clear();

        assertThat(BaseContext.contains(testKey)).isFalse();
    }

    // ========================= ThreadContext =========================

    @Test
    void threadContext_injectTyped_shouldLookupOnSameThread() {
        DomainEventPublisher publisher = stub("tp");
        ThreadContext.inject(testKey, DomainEventPublisher.class, publisher);

        Optional<DomainEventPublisher> resolved = ThreadContext.get(testKey, DomainEventPublisher.class);

        assertThat(resolved).contains(publisher);
    }

    @Test
    void threadContext_injectTyped_shouldReturnEmptyForTypeMismatch() {
        ThreadContext.inject(testKey, String.class, "value");

        assertThat(ThreadContext.get(testKey, Integer.class)).isEmpty();
    }

    @Test
    void threadContext_injectTyped_shouldReturnEmptyForMissing() {
        assertThat(ThreadContext.get(testKey, String.class)).isEmpty();
    }

    @Test
    void threadContext_set_shouldStoreValue() {
        ThreadContext.set(testKey, "value");

        assertThat((String) ThreadContext.get(testKey)).isEqualTo("value");
    }

    @Test
    void threadContext_set_shouldIgnoreNullString() {
        ThreadContext.set(testKey, "");

        assertThat((Object) ThreadContext.get(testKey)).isNull();
    }

    @Test
    void threadContext_setConditional_shouldStoreOnlyWhenTrue() {
        ThreadContext.set(false, testKey, "v1");
        assertThat((Object) ThreadContext.get(testKey)).isNull();

        ThreadContext.set(true, testKey, "v1");
        assertThat((String) ThreadContext.get(testKey)).isEqualTo("v1");
    }

    @Test
    void threadContext_remove_shouldDeleteEntry() {
        ThreadContext.set(testKey, "value");
        ThreadContext.remove(testKey);

        assertThat((Object) ThreadContext.get(testKey)).isNull();
    }

    @Test
    void threadContext_clear_shouldRemoveThreadBindings() {
        ThreadContext.set(testKey, "value");
        ThreadContext.clear();

        assertThat((Object) ThreadContext.get(testKey)).isNull();
    }

    // ========================= Contexts =========================

    @Test
    void contexts_inject_shouldFallbackToBaseContext() {
        DomainEventPublisher publisher = stub("base");
        Contexts.register(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, publisher);

        Optional<DomainEventPublisher> resolved = Contexts.get(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class);

        assertThat(resolved).contains(publisher);
    }

    @Test
    void contexts_getOrThrow_shouldThrowWhenNotFound() {
        BaseContext.remove(SpiKeys.DOMAIN_EVENT_PUBLISHER);
        ThreadContext.clear();

        assertThatThrownBy(() -> Contexts.getOrThrow(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPI service not found");
    }

    @Test
    void contexts_inject_threadShouldOverrideBase() {
        DomainEventPublisher base = stub("base");
        DomainEventPublisher thread = stub("thread");
        Contexts.register(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, base);
        ThreadContext.inject(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class, thread);

        Optional<DomainEventPublisher> resolved = Contexts.get(SpiKeys.DOMAIN_EVENT_PUBLISHER, DomainEventPublisher.class);

        assertThat(resolved).contains(thread);
    }

    @Test
    void contexts_register_shouldDelegateToBaseContext() {
        DomainEventPublisher publisher = stub("registered");
        Contexts.register(testKey, DomainEventPublisher.class, publisher);

        assertThat(BaseContext.get(testKey, DomainEventPublisher.class)).contains(publisher);
    }
}
